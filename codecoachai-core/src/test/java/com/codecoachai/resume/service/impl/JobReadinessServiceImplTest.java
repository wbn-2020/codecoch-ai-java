package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobReadinessQueryDTO;
import com.codecoachai.resume.domain.entity.JobReadinessSnapshot;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.mapper.JobReadinessSnapshotMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.JobRequirementService;
import com.codecoachai.resume.service.support.ReadinessDimensionCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobReadinessServiceImplTest {

    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobReadinessSnapshotMapper jobReadinessSnapshotMapper;
    @Mock
    private JobRequirementService jobRequirementService;

    private JobReadinessServiceImpl service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        initTableInfo(TargetJob.class);
        initTableInfo(JobReadinessSnapshot.class);
        objectMapper = new ObjectMapper();
        service = new JobReadinessServiceImpl(
                targetJobMapper,
                jobReadinessSnapshotMapper,
                jobRequirementService,
                objectMapper,
                new ReadinessDimensionCodec(objectMapper),
                new SimpleMeterRegistry());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        LoginUserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createsEvidenceWeightedSnapshotAndReusesSameHash() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobRequirementMatrixVO matrix = matrix(
                requirement("java", "MUST", "STRONG", BigDecimal.ONE, false, "HIGH"),
                requirement("redis", "NICE_TO_HAVE", "WEAK", new BigDecimal("0.5000"), false, "MEDIUM"));
        matrix.getRequirements().get(0).setEvidences(List.of(
                evidence("RESUME_MATCH", 90),
                evidence("PROJECT_EVIDENCE", 80),
                evidence("QUESTION_PRACTICE", 70),
                evidence("INTERVIEW_REPORT", 85),
                evidence("APPLICATION_RESULT", 100)));
        when(jobRequirementService.refreshMatrix(11L)).thenReturn(matrix);

        AtomicReference<JobReadinessSnapshot> stored = new AtomicReference<>();
        when(jobReadinessSnapshotMapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        when(jobReadinessSnapshotMapper.insert(any(JobReadinessSnapshot.class))).thenAnswer(invocation -> {
            JobReadinessSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(901L);
            stored.set(snapshot);
            return 1;
        });

        var first = service.createSnapshot(11L);
        var second = service.createSnapshot(11L);

        assertEquals(84, first.getReadinessScore());
        assertEquals("READY", first.getReadinessLevel());
        assertEquals("MEDIUM", first.getConfidenceLevel());
        assertFalse(first.getFallback());
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getSnapshotHash(), second.getSnapshotHash());
        assertEquals(5, first.getDimensions().size());
        assertEquals(5, stored.get().getDimensionJson() == null ? 0
                : first.getDimensions().size());
        assertEquals(ReadinessDimensionCodec.SCHEMA_VERSION, stored.get().getSchemaVersion());
        assertEquals("VALID", stored.get().getValidationStatus());
        assertTrue(stored.get().getSourceHash() != null && stored.get().getSourceHash().length() == 64);
        verify(jobReadinessSnapshotMapper).insert(any(JobReadinessSnapshot.class));
    }

    @Test
    void latestSkipsInvalidSnapshotAndReturnsMostRecentValidHistory() throws Exception {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot invalid = snapshot(906L, LocalDateTime.of(2026, 8, 14, 10, 0));
        invalid.setDimensionJson("\"[]\"");
        invalid.setSchemaVersion(ReadinessDimensionCodec.SCHEMA_VERSION);
        JobReadinessSnapshot valid = snapshot(905L, LocalDateTime.of(2026, 8, 14, 9, 0));
        valid.setDimensionJson(objectMapper.writeValueAsString(
                ReadinessDimensionCodecTestFixtures.validDimensions()));
        valid.setSchemaVersion(ReadinessDimensionCodec.SCHEMA_VERSION);
        when(jobReadinessSnapshotMapper.selectList(any())).thenReturn(List.of(invalid, valid));

        var result = service.latest(11L);

        assertEquals(905L, result.getId());
        assertTrue(result.getHistoryFallback());
        assertEquals(906L, result.getInvalidLatestSnapshotId());
        assertTrue(result.getWarnings().contains("READINESS_LATEST_SNAPSHOT_INVALID"));
        assertEquals(5, result.getDimensions().size());
    }

    @Test
    void latestSkipsSnapshotWithMalformedSummaryOrMatrixContract() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot invalid = snapshot(906L, LocalDateTime.of(2026, 8, 14, 10, 0));
        invalid.setSummaryJson("\"not-an-object\"");
        JobReadinessSnapshot valid = snapshot(905L, LocalDateTime.of(2026, 8, 14, 9, 0));
        when(jobReadinessSnapshotMapper.selectList(any())).thenReturn(List.of(invalid, valid));

        var result = service.latest(11L);

        assertEquals(905L, result.getId());
        assertTrue(result.getHistoryFallback());
        assertEquals(906L, result.getInvalidLatestSnapshotId());
    }

    @Test
    void latestSkipsSnapshotWithOutOfRangeScoreOrMismatchedCounts() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot invalid = snapshot(906L, LocalDateTime.of(2026, 8, 14, 10, 0));
        invalid.setReadinessScore(101);
        invalid.setMatrixJson("""
                {
                  "requirementCount":1,
                  "strongCount":0,
                  "weakCount":1,
                  "missingCount":0,
                  "requirements":[{}]
                }
                """);
        JobReadinessSnapshot valid = snapshot(905L, LocalDateTime.of(2026, 8, 14, 9, 0));
        when(jobReadinessSnapshotMapper.selectList(any())).thenReturn(List.of(invalid, valid));

        var result = service.latest(11L);

        assertEquals(905L, result.getId());
        assertEquals(70, result.getReadinessScore());
    }

    @Test
    void latestRegeneratesWhenAllStoredSnapshotsAreInvalid() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot invalid = snapshot(906L, LocalDateTime.of(2026, 8, 14, 10, 0));
        invalid.setDimensionJson("{\"dimension\":\"RESUME\"}");
        invalid.setSchemaVersion(ReadinessDimensionCodec.SCHEMA_VERSION);
        when(jobReadinessSnapshotMapper.selectList(any())).thenReturn(List.of(invalid));
        when(jobRequirementService.refreshMatrix(11L)).thenReturn(matrix(
                requirement("java", "MUST", "STRONG", BigDecimal.ONE, false, "HIGH")));
        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(null);
        when(jobReadinessSnapshotMapper.insert(any(JobReadinessSnapshot.class))).thenAnswer(invocation -> {
            JobReadinessSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(907L);
            return 1;
        });

        var result = service.latest(11L);

        assertEquals(907L, result.getId());
        assertTrue(result.getRegenerated());
        assertEquals(906L, result.getInvalidLatestSnapshotId());
        assertEquals("VALID", result.getValidationStatus());
        assertTrue(result.getWarnings().contains("READINESS_SNAPSHOT_REGENERATED"));
    }

    @Test
    void lowConfidenceRequirementKeepsReadinessDegraded() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        when(jobRequirementService.refreshMatrix(11L)).thenReturn(matrix(
                requirement("java", "MUST", "WEAK", BigDecimal.ONE, true, "LOW")));
        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(null);
        when(jobReadinessSnapshotMapper.insert(any(JobReadinessSnapshot.class))).thenAnswer(invocation -> {
            JobReadinessSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(902L);
            return 1;
        });

        var result = service.createSnapshot(11L);

        assertEquals(0, result.getReadinessScore());
        assertEquals("NEEDS_WORK", result.getReadinessLevel());
        assertEquals("LOW", result.getConfidenceLevel());
        assertEquals(true, result.getFallback());
        assertEquals(1, result.getMustMissingCount());
        assertTrue(result.getDimensions().stream()
                .filter(dimension -> "KNOWLEDGE".equals(dimension.getDimension()))
                .allMatch(dimension -> "LOW".equals(dimension.getConfidenceLevel())));
    }

    @Test
    void insufficientDimensionSamplesConservativelyBlockReady() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobRequirementMatrixVO matrix = matrix(
                requirement("java", "MUST", "STRONG", BigDecimal.ONE, false, "HIGH"));
        matrix.getRequirements().get(0).setEvidences(List.of(evidence("RESUME_MATCH", 90)));
        when(jobRequirementService.refreshMatrix(11L)).thenReturn(matrix);
        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(null);
        when(jobReadinessSnapshotMapper.insert(any(JobReadinessSnapshot.class))).thenAnswer(invocation -> {
            JobReadinessSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(903L);
            return 1;
        });

        var result = service.createSnapshot(11L);

        assertEquals("NEEDS_WORK", result.getReadinessLevel());
        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getFallback());
        assertTrue(result.getDimensions().stream()
                .anyMatch(dimension -> dimension.getSampleCount() == 0
                        && "LOW".equals(dimension.getConfidenceLevel())));
    }

    @Test
    void exposesSampleInsufficientWhenRequirementCountIsTooLowWithoutFallback() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobRequirementMatrixVO matrix = matrix(
                requirement("java", "MUST", "STRONG", BigDecimal.ONE, false, "HIGH"));
        matrix.getRequirements().get(0).setEvidences(List.of(
                evidence("RESUME_MATCH", 90),
                evidence("PROJECT_EVIDENCE", 80),
                evidence("QUESTION_PRACTICE", 70),
                evidence("INTERVIEW_REPORT", 85),
                evidence("APPLICATION_RESULT", 100)));
        when(jobRequirementService.refreshMatrix(11L)).thenReturn(matrix);
        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(null);
        when(jobReadinessSnapshotMapper.insert(any(JobReadinessSnapshot.class))).thenAnswer(invocation -> {
            JobReadinessSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(904L);
            return 1;
        });

        var result = service.createSnapshot(11L);

        assertEquals("LOW", result.getConfidenceLevel());
        assertFalse(result.getFallback());
        assertTrue(result.getSampleInsufficient());
        assertTrue(result.getSummary().path("sampleInsufficient").asBoolean());
    }

    @Test
    void getsSnapshotByIdOnlyForOwnedTargetJob() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot snapshot = snapshot(901L, LocalDateTime.of(2026, 8, 14, 10, 0));
        snapshot.setJdAnalysisId(101L);
        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(snapshot);

        assertEquals(901L, service.getSnapshot(11L, 901L).getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<TargetJob>> targetQuery = ArgumentCaptor.forClass(Wrapper.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<JobReadinessSnapshot>> snapshotQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(targetJobMapper).selectOne(targetQuery.capture());
        verify(jobReadinessSnapshotMapper).selectOne(snapshotQuery.capture());
        assertOwnedReadQuery(targetQuery.getValue(), 11L, 1001L);
        assertOwnedReadQuery(snapshotQuery.getValue(), 901L, 1001L);
        assertTrue(queryValues(snapshotQuery.getValue()).contains(11L));

        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getSnapshot(11L, 999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void rejectsInvalidSnapshotDimensionJsonInsteadOfReturningNormalVo() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot invalid = snapshot(901L, LocalDateTime.of(2026, 8, 14, 10, 0));
        invalid.setDimensionJson("{\"dimension\":\"RESUME\"}");
        when(jobReadinessSnapshotMapper.selectOne(any())).thenReturn(invalid);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getSnapshot(11L, 901L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void pagesOwnedSnapshotHistoryWithStableOrderingAndMetadata() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        AtomicReference<Wrapper<JobReadinessSnapshot>> snapshotQuery = new AtomicReference<>();
        when(jobReadinessSnapshotMapper.selectList(any())).thenAnswer(invocation -> {
            snapshotQuery.set(invocation.getArgument(0));
            return List.of(
                    snapshot(906L, LocalDateTime.of(2026, 7, 15, 13, 0)),
                    snapshot(905L, LocalDateTime.of(2026, 7, 15, 12, 0)),
                    snapshot(904L, LocalDateTime.of(2026, 7, 15, 11, 0)),
                    snapshot(903L, LocalDateTime.of(2026, 7, 15, 10, 0)),
                    snapshot(902L, LocalDateTime.of(2026, 7, 15, 9, 0)));
        });

        var result = service.page(11L, 2L, 2L);

        assertEquals(2L, result.getPageNo());
        assertEquals(2L, result.getPageSize());
        assertEquals(5L, result.getTotal());
        assertEquals(3L, result.getPages());
        assertEquals(List.of(904L, 903L),
                result.getRecords().stream().map(item -> item.getId()).toList());

        String sql = snapshotQuery.get().getSqlSegment().toLowerCase().replace("`", "");
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("target_job_id"));
        assertTrue(sql.contains("deleted"));
        assertTrue(sql.matches("(?s).*order by\\s+generated_at\\s+desc\\s*,\\s*id\\s+desc.*"));
        List<Object> values = queryValues(snapshotQuery.get());
        assertTrue(values.contains(1001L));
        assertTrue(values.contains(11L));
        assertTrue(values.contains(CommonConstants.NO));
    }

    @Test
    void pageAndListExcludeInvalidSnapshotsAndBackfillFromOlderValidHistory() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobReadinessSnapshot newestValid = snapshot(906L, LocalDateTime.of(2026, 8, 14, 12, 0));
        JobReadinessSnapshot invalid = snapshot(905L, LocalDateTime.of(2026, 8, 14, 11, 0));
        invalid.setDimensionJson("\"[]\"");
        JobReadinessSnapshot olderValid = snapshot(904L, LocalDateTime.of(2026, 8, 14, 10, 0));
        when(jobReadinessSnapshotMapper.selectList(any()))
                .thenReturn(List.of(newestValid, invalid, olderValid));

        var page = service.page(11L, 1L, 20L);
        JobReadinessQueryDTO query = new JobReadinessQueryDTO();
        query.setLimit(2);
        var list = service.list(11L, query);

        assertEquals(2L, page.getTotal());
        assertEquals(List.of(906L, 904L),
                page.getRecords().stream().map(item -> item.getId()).toList());
        assertEquals(List.of(906L, 904L),
                list.stream().map(item -> item.getId()).toList());
    }

    @Test
    void rejectsInvalidSnapshotHistoryPaginationBeforeQuerying() {
        List<Executable> invalidCalls = List.of(
                () -> service.page(11L, null, 20L),
                () -> service.page(11L, 0L, 20L),
                () -> service.page(11L, 1L, null),
                () -> service.page(11L, 1L, 0L),
                () -> service.page(11L, 1L, 101L));

        for (Executable invalidCall : invalidCalls) {
            BusinessException exception = assertThrows(BusinessException.class, invalidCall);
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        }

        verify(targetJobMapper, never()).selectOne(any());
        verify(jobReadinessSnapshotMapper, never()).selectList(any());
    }

    @Test
    void missingOrForeignTargetJobReturnsResourceNotFound() {
        when(targetJobMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getSnapshot(11L, 901L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void missingOrForeignTargetJobForCreateLatestAndListRemainsParameterError() {
        when(targetJobMapper.selectOne(any())).thenReturn(null);

        BusinessException createError = assertThrows(BusinessException.class,
                () -> service.createSnapshot(11L));
        BusinessException latestError = assertThrows(BusinessException.class,
                () -> service.latest(11L));
        BusinessException pageError = assertThrows(BusinessException.class,
                () -> service.page(11L, 1L, 20L));
        BusinessException listError = assertThrows(BusinessException.class,
                () -> service.list(11L, null));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), createError.getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), latestError.getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), pageError.getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), listError.getCode());
    }

    @Test
    void nullTargetJobOrSnapshotIdRemainsParameterError() {
        BusinessException targetJobError = assertThrows(BusinessException.class,
                () -> service.getSnapshot(null, 901L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), targetJobError.getCode());

        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        BusinessException snapshotError = assertThrows(BusinessException.class,
                () -> service.getSnapshot(11L, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), snapshotError.getCode());
    }

    private TargetJob targetJob() {
        TargetJob targetJob = new TargetJob();
        targetJob.setId(11L);
        targetJob.setUserId(1001L);
        targetJob.setDeleted(CommonConstants.NO);
        return targetJob;
    }

    private JobReadinessSnapshot snapshot(Long id, LocalDateTime generatedAt) {
        JobReadinessSnapshot snapshot = new JobReadinessSnapshot();
        snapshot.setId(id);
        snapshot.setUserId(1001L);
        snapshot.setTargetJobId(11L);
        snapshot.setSnapshotHash("hash-" + id);
        snapshot.setPolicyVersion(JobReadinessServiceImpl.POLICY_VERSION);
        snapshot.setReadinessScore(70);
        snapshot.setReadinessLevel("NEAR_READY");
        snapshot.setConfidenceLevel("MEDIUM");
        snapshot.setFallback(CommonConstants.NO);
        snapshot.setRequirementCount(1);
        snapshot.setStrongCount(1);
        snapshot.setWeakCount(0);
        snapshot.setMissingCount(0);
        snapshot.setMustRequirementCount(1);
        snapshot.setMustMissingCount(0);
        snapshot.setSummaryJson("""
                {
                  "policyVersion":"five-dimension-readiness-v3",
                  "readinessScore":70,
                  "readinessLevel":"NEAR_READY",
                  "confidenceLevel":"MEDIUM",
                  "fallback":false,
                  "requirementCount":1,
                  "strongCount":1,
                  "weakCount":0,
                  "missingCount":0,
                  "mustRequirementCount":1,
                  "mustMissingCount":0,
                  "dimensions":[{},{},{},{},{}]
                }
                """);
        snapshot.setMatrixJson("""
                {
                  "requirementCount":1,
                  "strongCount":1,
                  "weakCount":0,
                  "missingCount":0,
                  "requirements":[{}]
                }
                """);
        snapshot.setDimensionJson(objectMapper.valueToTree(
                ReadinessDimensionCodecTestFixtures.validDimensions()).toString());
        snapshot.setSchemaVersion(ReadinessDimensionCodec.SCHEMA_VERSION);
        snapshot.setGeneratedAt(generatedAt);
        return snapshot;
    }

    private static final class ReadinessDimensionCodecTestFixtures {

        private static List<com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO.DimensionScore>
                validDimensions() {
            return List.of(
                    dimension("RESUME"),
                    dimension("PROJECT_EVIDENCE"),
                    dimension("KNOWLEDGE"),
                    dimension("INTERVIEW"),
                    dimension("APPLICATION"));
        }

        private static com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO.DimensionScore
                dimension(String name) {
            var item =
                    new com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO.DimensionScore();
            item.setDimension(name);
            item.setScore(70);
            item.setSampleCount(1);
            item.setConfidenceLevel("MEDIUM");
            item.setFallback(false);
            item.setSampleInsufficient(false);
            return item;
        }
    }

    private void assertOwnedReadQuery(Wrapper<?> wrapper, Long resourceId, Long userId) {
        String sql = wrapper.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("deleted"));
        List<Object> values = queryValues(wrapper);
        assertTrue(values.contains(resourceId));
        assertTrue(values.contains(userId));
        assertTrue(values.contains(CommonConstants.NO));
    }

    private List<Object> queryValues(Object wrapper) {
        if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query) {
            query.getSqlSegment();
            return List.copyOf(query.getParamNameValuePairs().values());
        }
        return List.of();
    }

    private JobRequirementMatrixVO matrix(JobRequirementMatrixVO.RequirementItem... requirements) {
        JobRequirementMatrixVO matrix = new JobRequirementMatrixVO();
        matrix.setTargetJobId(11L);
        matrix.setJdAnalysisId(101L);
        matrix.setRequirements(List.of(requirements));
        matrix.setRequirementCount(requirements.length);
        matrix.setStrongCount((int) List.of(requirements).stream()
                .filter(item -> "STRONG".equals(item.getCoverageLevel())).count());
        matrix.setWeakCount((int) List.of(requirements).stream()
                .filter(item -> "WEAK".equals(item.getCoverageLevel())).count());
        matrix.setMissingCount((int) List.of(requirements).stream()
                .filter(item -> "MISSING".equals(item.getCoverageLevel())).count());
        return matrix;
    }

    private JobRequirementMatrixVO.RequirementItem requirement(
            String key, String priority, String coverage, BigDecimal weight,
            boolean fallback, String confidence) {
        JobRequirementMatrixVO.RequirementItem item = new JobRequirementMatrixVO.RequirementItem();
        item.setRequirementId((long) key.hashCode());
        item.setRequirementKey(key);
        item.setRequirementName(key);
        item.setRequirementType("SKILL");
        item.setPriority(priority);
        item.setCoverageLevel(coverage);
        item.setWeight(weight);
        item.setRequirementFallback(fallback);
        item.setRequirementConfidence(confidence);
        return item;
    }

    private JobRequirementMatrixVO.EvidenceItem evidence(String type, int score) {
        JobRequirementMatrixVO.EvidenceItem item = new JobRequirementMatrixVO.EvidenceItem();
        item.setEvidenceType(type);
        item.setScore(score);
        item.setConfidenceLevel("HIGH");
        item.setConfirmed(true);
        item.setFallback(false);
        item.setCoverageLevel("STRONG");
        return item;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
