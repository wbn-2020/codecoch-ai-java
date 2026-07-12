package com.codecoachai.resume.experimentv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AttributionView;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisCreate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisUpdate;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.HypothesisView;
import com.codecoachai.resume.experimentv2.entity.ExperimentAttribution;
import com.codecoachai.resume.experimentv2.entity.ExperimentAssignment;
import com.codecoachai.resume.experimentv2.entity.ExperimentCohort;
import com.codecoachai.resume.experimentv2.entity.ExperimentHypothesis;
import com.codecoachai.resume.experimentv2.entity.ExperimentVariant;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAssignmentMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAttributionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentCohortMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentHypothesisMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentVariantMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ExperimentV2ServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private ExperimentHypothesisMapper hypothesisMapper;
    @Mock
    private JobSearchExperimentMapper legacyExperimentMapper;
    @Mock
    private ExperimentVariantMapper variantMapper;
    @Mock
    private ExperimentAssignmentMapper assignmentMapper;
    @Mock
    private ExperimentCohortMapper cohortMapper;
    @Mock
    private ExperimentAttributionMapper attributionMapper;
    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private JobApplicationEventMapper applicationEventMapper;
    @Mock
    private ExperimentAttributionCalculator attributionCalculator;

    private ExperimentV2ServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(ExperimentHypothesis.class);
        initTableInfo(ExperimentCohort.class);
        initTableInfo(ExperimentAttribution.class);
        initTableInfo(ExperimentAssignment.class);
        initTableInfo(JobSearchExperiment.class);
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("experiment-user")
                .build());
        service = new ExperimentV2ServiceImpl(
                hypothesisMapper,
                legacyExperimentMapper,
                variantMapper,
                assignmentMapper,
                cohortMapper,
                attributionMapper,
                applicationMapper,
                applicationEventMapper,
                attributionCalculator,
                new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void listHypothesesUsesOneOwnedBoundedQueryWithoutNestedLoads() {
        ExperimentHypothesis hypothesis = hypothesis(7L, "RUNNING");
        hypothesis.setLegacyExperimentId(91L);
        when(hypothesisMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(hypothesis));

        List<HypothesisView> result = service.listHypotheses("running", "backend", 91L, 25);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        assertEquals(91L, result.get(0).getLegacyExperimentId());
        ArgumentCaptor<LambdaQueryWrapper<ExperimentHypothesis>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(hypothesisMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("legacy_experiment_id"), sql);
        assertTrue(sql.contains("name") && sql.contains("statement"), sql);
        assertTrue(sql.toLowerCase().contains("limit 25"), sql);
        verifyNoInteractions(variantMapper, cohortMapper);
    }

    @Test
    void updateRejectsDesignChangesAfterAssignmentsExist() {
        ExperimentHypothesis hypothesis = hypothesis(7L, "DRAFT");
        when(hypothesisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(hypothesis);
        when(assignmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        HypothesisUpdate request = new HypothesisUpdate();
        request.setAttributionWindowDays(21);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateHypothesis(7L, request));

        assertTrue(exception.getMessage().contains("before assignments"));
        verify(hypothesisMapper, never()).updateById(any(ExperimentHypothesis.class));
    }

    @Test
    void createRejectsLegacyExperimentOwnedByAnotherUser() {
        when(legacyExperimentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        HypothesisCreate request = hypothesisCreate(91L);

        assertThrows(BusinessException.class, () -> service.createHypothesis(request));

        ArgumentCaptor<LambdaQueryWrapper<JobSearchExperiment>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(legacyExperimentMapper).selectOne(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"), sql);
        verify(hypothesisMapper, never()).insert(any(ExperimentHypothesis.class));
    }

    @Test
    void createRejectsDuplicateLegacyExperimentAssociation() {
        JobSearchExperiment legacyExperiment = new JobSearchExperiment();
        legacyExperiment.setId(91L);
        legacyExperiment.setUserId(USER_ID);
        when(legacyExperimentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(legacyExperiment);
        ExperimentHypothesis existing = hypothesis(7L, "DRAFT");
        existing.setLegacyExperimentId(91L);
        when(hypothesisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createHypothesis(hypothesisCreate(91L)));

        assertTrue(exception.getMessage().contains("already linked"));
        verify(hypothesisMapper, never()).insert(any(ExperimentHypothesis.class));
    }

    @Test
    void updateAllowsMetadataAndRunningToPausedTransition() {
        ExperimentHypothesis hypothesis = hypothesis(7L, "RUNNING");
        when(hypothesisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(hypothesis);
        when(variantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(cohortMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        HypothesisUpdate request = new HypothesisUpdate();
        request.setName("Sharper backend positioning");
        request.setStatus("paused");

        HypothesisView result = service.updateHypothesis(7L, request);

        assertEquals("Sharper backend positioning", result.getName());
        assertEquals("PAUSED", result.getStatus());
        verify(hypothesisMapper).updateById(hypothesis);
    }

    @Test
    void completedHypothesisCannotReturnToRunning() {
        when(hypothesisMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(hypothesis(7L, "COMPLETED"));
        HypothesisUpdate request = new HypothesisUpdate();
        request.setStatus("RUNNING");

        assertThrows(BusinessException.class, () -> service.updateHypothesis(7L, request));

        verify(hypothesisMapper, never()).updateById(any(ExperimentHypothesis.class));
    }

    @Test
    void pausedHypothesisStillReturnsAnExistingIdempotentAssignment() {
        ExperimentHypothesis hypothesis = hypothesis(7L, "PAUSED");
        when(hypothesisMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(hypothesis);
        JobApplication application = new JobApplication();
        application.setId(41L);
        application.setUserId(USER_ID);
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application);
        ExperimentAssignment assignment = new ExperimentAssignment();
        assignment.setId(55L);
        assignment.setUserId(USER_ID);
        assignment.setHypothesisId(7L);
        assignment.setVariantId(3L);
        assignment.setApplicationId(41L);
        when(assignmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(assignment);
        ExperimentVariant variant = new ExperimentVariant();
        variant.setId(3L);
        variant.setUserId(USER_ID);
        variant.setHypothesisId(7L);
        variant.setVariantCode("CONTROL");
        when(variantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(variant);
        AssignmentCreate request = new AssignmentCreate();
        request.setApplicationId(41L);

        assertEquals(55L, service.assign(7L, request).getId());

        verify(assignmentMapper, never()).insert(any(ExperimentAssignment.class));
    }

    @Test
    void assignmentUniqueConflictReturnsActiveWinner() {
        prepareAssignableApplication();
        ExperimentAssignment winner = assignment(55L, 3L);
        when(assignmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(assignmentMapper.insert(any(ExperimentAssignment.class))).thenThrow(mysqlDuplicate());
        when(assignmentMapper.selectActiveWinnerForUpdate(USER_ID, 7L, 41L))
                .thenReturn(winner);

        assertEquals(55L, service.assign(7L, assignmentCreate()).getId());

        verify(assignmentMapper).selectActiveWinnerForUpdate(USER_ID, 7L, 41L);
    }

    @Test
    void assignmentOtherUniqueConflictIsRethrown() {
        prepareAssignableApplication();
        when(assignmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        DuplicateKeyException conflict = mysqlDuplicate();
        when(assignmentMapper.insert(any(ExperimentAssignment.class))).thenThrow(conflict);
        when(assignmentMapper.selectActiveWinnerForUpdate(USER_ID, 7L, 41L))
                .thenReturn(null);

        assertEquals(conflict, assertThrows(
                DuplicateKeyException.class, () -> service.assign(7L, assignmentCreate())));
        verify(assignmentMapper).selectActiveWinnerForUpdate(USER_ID, 7L, 41L);
    }

    @Test
    void assignmentUniqueConflictWithoutActiveWinnerIsRethrown() {
        prepareAssignableApplication();
        when(assignmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        DuplicateKeyException conflict = mysqlDuplicate();
        when(assignmentMapper.insert(any(ExperimentAssignment.class))).thenThrow(conflict);
        when(assignmentMapper.selectActiveWinnerForUpdate(USER_ID, 7L, 41L))
                .thenReturn(null);

        assertEquals(conflict, assertThrows(
                DuplicateKeyException.class, () -> service.assign(7L, assignmentCreate())));
    }

    @Test
    void assignmentDuplicateKeyWithoutMysqlIntegrityMetadataIsRethrownWithoutWinnerRead() {
        prepareAssignableApplication();
        when(assignmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        DuplicateKeyException conflict = duplicateKey("uk_jea_hypothesis_application");
        when(assignmentMapper.insert(any(ExperimentAssignment.class))).thenThrow(conflict);

        assertEquals(conflict, assertThrows(
                DuplicateKeyException.class, () -> service.assign(7L, assignmentCreate())));

        verify(assignmentMapper, never()).selectActiveWinnerForUpdate(any(), any(), any());
    }

    @Test
    void latestAttributionRequiresAnOwnedCohortBeforeReadingSnapshots() {
        when(cohortMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getLatestAttribution(99L));

        ArgumentCaptor<LambdaQueryWrapper<ExperimentCohort>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cohortMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("user_id"));
        verifyNoInteractions(attributionMapper);
    }

    @Test
    void latestAttributionRestoresStoredResultAndSnapshotId() {
        when(cohortMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cohort(9L, 7L));
        ExperimentAttribution snapshot = snapshot(33L, 9L);
        snapshot.setResultJson("{\"cohortId\":9,\"comparable\":false,\"eligibleSampleCount\":3}");
        when(attributionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(snapshot);

        AttributionView result = service.getLatestAttribution(9L);

        assertEquals(33L, result.getSnapshotId());
        assertEquals(9L, result.getCohortId());
        assertEquals(3, result.getEligibleSampleCount());
        ArgumentCaptor<LambdaQueryWrapper<ExperimentAttribution>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(attributionMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("user_id"));
    }

    @Test
    void attributionHistoryIsBoundedAndUsesOneSnapshotQuery() {
        when(cohortMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cohort(9L, 7L));
        ExperimentAttribution snapshot = snapshot(33L, 9L);
        snapshot.setResultJson("{\"cohortId\":9}");
        when(attributionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(snapshot));

        List<AttributionView> result = service.listAttributions(9L, 15);

        assertEquals(1, result.size());
        ArgumentCaptor<LambdaQueryWrapper<ExperimentAttribution>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(attributionMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.toLowerCase().contains("limit 15"), sql);
    }

    @Test
    void repeatedAttributionWithinReuseWindowReturnsStoredSnapshot() {
        when(cohortMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cohort(9L, 7L));
        ExperimentAttribution snapshot = snapshot(33L, 9L);
        snapshot.setResultJson("{\"cohortId\":9,\"eligibleSampleCount\":4}");
        snapshot.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        when(attributionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(snapshot);

        AttributionView result = service.attribute(9L, null);

        assertEquals(33L, result.getSnapshotId());
        assertEquals(4, result.getEligibleSampleCount());
        verifyNoInteractions(hypothesisMapper, assignmentMapper, applicationMapper,
                applicationEventMapper, attributionCalculator);
        verify(attributionMapper, never()).insert(any(ExperimentAttribution.class));
    }

    @Test
    void rejectsHistoryLimitAboveHardCap() {
        when(cohortMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cohort(9L, 7L));

        assertThrows(BusinessException.class, () -> service.listAttributions(9L, 101));

        verify(attributionMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    private static ExperimentHypothesis hypothesis(Long id, String status) {
        ExperimentHypothesis hypothesis = new ExperimentHypothesis();
        hypothesis.setId(id);
        hypothesis.setUserId(USER_ID);
        hypothesis.setName("Backend positioning");
        hypothesis.setStatement("Focused positioning improves interview conversion");
        hypothesis.setPrimaryMetric("INTERVIEW");
        hypothesis.setStatus(status);
        hypothesis.setAttributionWindowDays(14);
        hypothesis.setMinSamplePerVariant(10);
        return hypothesis;
    }

    private static HypothesisCreate hypothesisCreate(Long legacyExperimentId) {
        HypothesisCreate request = new HypothesisCreate();
        request.setLegacyExperimentId(legacyExperimentId);
        request.setName("Backend positioning");
        request.setStatement("Focused positioning improves interview conversion");
        return request;
    }

    private static ExperimentCohort cohort(Long id, Long hypothesisId) {
        ExperimentCohort cohort = new ExperimentCohort();
        cohort.setId(id);
        cohort.setUserId(USER_ID);
        cohort.setHypothesisId(hypothesisId);
        return cohort;
    }

    private static ExperimentAttribution snapshot(Long id, Long cohortId) {
        ExperimentAttribution snapshot = new ExperimentAttribution();
        snapshot.setId(id);
        snapshot.setUserId(USER_ID);
        snapshot.setCohortId(cohortId);
        snapshot.setAsOf(LocalDateTime.now());
        return snapshot;
    }

    private void prepareAssignableApplication() {
        when(hypothesisMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(hypothesis(7L, "DRAFT"));
        JobApplication application = new JobApplication();
        application.setId(41L);
        application.setUserId(USER_ID);
        application.setJobTitle("Backend Engineer");
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application);
        when(variantMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(variant(3L, "CONTROL"), variant(4L, "TREATMENT")));
        when(variantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(variant(3L, "CONTROL"));
    }

    private static AssignmentCreate assignmentCreate() {
        AssignmentCreate request = new AssignmentCreate();
        request.setApplicationId(41L);
        request.setVariantId(3L);
        return request;
    }

    private static ExperimentAssignment assignment(Long id, Long variantId) {
        ExperimentAssignment assignment = new ExperimentAssignment();
        assignment.setId(id);
        assignment.setUserId(USER_ID);
        assignment.setHypothesisId(7L);
        assignment.setVariantId(variantId);
        assignment.setApplicationId(41L);
        return assignment;
    }

    private static ExperimentVariant variant(Long id, String code) {
        ExperimentVariant variant = new ExperimentVariant();
        variant.setId(id);
        variant.setUserId(USER_ID);
        variant.setHypothesisId(7L);
        variant.setVariantCode(code);
        variant.setAllocationWeight(1);
        return variant;
    }

    private static DuplicateKeyException duplicateKey(String constraintName) {
        return new DuplicateKeyException(
                "outer",
                new IllegalStateException("middle", new RuntimeException("Duplicate entry for key '"
                        + constraintName + "'")));
    }

    private static DuplicateKeyException mysqlDuplicate() {
        return new DuplicateKeyException(
                "outer",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry", "23000", 1062));
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
