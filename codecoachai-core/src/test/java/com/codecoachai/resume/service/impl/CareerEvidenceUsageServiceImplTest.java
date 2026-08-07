package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.config.V9FeatureGate;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageCreateDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultCommandDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultQueryDTO;
import com.codecoachai.resume.domain.dto.CareerEvidenceUsageResultWriteDTO;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResultSnapshot;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageResultVO;
import com.codecoachai.resume.domain.vo.CareerEvidenceUsageVO;
import com.codecoachai.resume.experimentv2.entity.ExperimentAttribution;
import com.codecoachai.resume.experimentv2.entity.ExperimentAssignment;
import com.codecoachai.resume.experimentv2.entity.ExperimentHypothesis;
import com.codecoachai.resume.experimentv2.entity.ExperimentVariant;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultSnapshotMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceVersionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAttributionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentAssignmentMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentHypothesisMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentVariantMapper;
import com.codecoachai.resume.service.EvidenceProfileFeedbackOutboxService;
import com.codecoachai.resume.service.support.CareerEvidenceSourceResolver;
import com.codecoachai.resume.service.support.CareerEvidenceSourceResolver.AssetResolution;
import com.codecoachai.resume.service.support.CareerEvidenceSourceResolver.EventResolution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerEvidenceUsageServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long APPLICATION_ID = 71L;
    private static final long USAGE_ID = 91L;
    private static final long RESULT_ID = 101L;

    @Mock
    private CareerEvidenceUsageMapper usageMapper;
    @Mock
    private CareerEvidenceUsageResultMapper resultMapper;
    @Mock
    private CareerEvidenceUsageResultSnapshotMapper resultSnapshotMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private ProjectEvidenceVersionMapper projectEvidenceVersionMapper;
    @Mock
    private ExperimentAttributionMapper attributionMapper;
    @Mock
    private ExperimentAssignmentMapper assignmentMapper;
    @Mock
    private ExperimentHypothesisMapper hypothesisMapper;
    @Mock
    private ExperimentVariantMapper variantMapper;
    @Mock
    private CareerEvidenceSourceResolver sourceResolver;
    @Mock
    private V9FeatureGate featureGate;
    @Mock
    private EvidenceProfileFeedbackOutboxService profileFeedbackOutboxService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CareerEvidenceUsageServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(CareerEvidenceUsage.class);
        initTableInfo(CareerEvidenceUsageResult.class);
        initTableInfo(ProjectEvidence.class);
        initTableInfo(ProjectEvidenceVersion.class);
        initTableInfo(ExperimentAttribution.class);
        initTableInfo(ExperimentAssignment.class);
        initTableInfo(ExperimentHypothesis.class);
        initTableInfo(ExperimentVariant.class);
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("v9-evidence-user")
                .build());
        service = new CareerEvidenceUsageServiceImpl(
                usageMapper,
                resultMapper,
                resultSnapshotMapper,
                projectEvidenceMapper,
                projectEvidenceVersionMapper,
                attributionMapper,
                assignmentMapper,
                hypothesisMapper,
                variantMapper,
                sourceResolver,
                featureGate,
                objectMapper,
                profileFeedbackOutboxService);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createUsageRejectsApplicationOwnedByAnotherUserBeforeWrites() {
        when(sourceResolver.ownedApplication(USER_ID, APPLICATION_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "forbidden"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createUsage(APPLICATION_ID, usageRequest("usage-1")));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(usageMapper, resultMapper, resultSnapshotMapper);
    }

    @Test
    void createUsageIsIdempotentForTheSameKeyAndPayload() {
        stubUsageResolution();
        AtomicReference<CareerEvidenceUsage> stored = new AtomicReference<>();
        when(usageMapper.selectByIdempotencyKey(eq(USER_ID), anyString()))
                .thenAnswer(invocation -> stored.get());
        when(usageMapper.selectByUsageKey(eq(USER_ID), anyString())).thenReturn(null);
        when(usageMapper.insert(any(CareerEvidenceUsage.class))).thenAnswer(invocation -> {
            CareerEvidenceUsage row = invocation.getArgument(0);
            row.setId(USAGE_ID);
            stored.set(row);
            return 1;
        });

        CareerEvidenceUsageVO first =
                service.createUsage(APPLICATION_ID, usageRequest("usage-1"));
        CareerEvidenceUsageVO second =
                service.createUsage(APPLICATION_ID, usageRequest("usage-1"));

        assertEquals(USAGE_ID, first.getId());
        assertEquals(USAGE_ID, second.getId());
        verify(usageMapper).insert(any(CareerEvidenceUsage.class));
        verify(usageMapper).selectByUsageKey(eq(USER_ID), anyString());
    }

    @Test
    void createUsageRejectsSameKeyWithDifferentPayload() {
        stubUsageResolution();
        AtomicReference<CareerEvidenceUsage> stored = new AtomicReference<>();
        when(usageMapper.selectByIdempotencyKey(eq(USER_ID), anyString()))
                .thenAnswer(invocation -> stored.get());
        when(usageMapper.selectByUsageKey(eq(USER_ID), anyString())).thenReturn(null);
        when(usageMapper.insert(any(CareerEvidenceUsage.class))).thenAnswer(invocation -> {
            CareerEvidenceUsage row = invocation.getArgument(0);
            row.setId(USAGE_ID);
            stored.set(row);
            return 1;
        });

        service.createUsage(APPLICATION_ID, usageRequest("usage-1"));
        CareerEvidenceUsageCreateDTO changed = usageRequest("usage-1");
        changed.setUsageScene("INTERVIEW");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createUsage(APPLICATION_ID, changed));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
        verify(usageMapper).insert(any(CareerEvidenceUsage.class));
    }

    @Test
    void createUsageRejectsDifferentPayloadForExistingSemanticUsage() {
        stubUsageResolution();
        AtomicReference<CareerEvidenceUsage> stored = new AtomicReference<>();
        when(usageMapper.selectByIdempotencyKey(eq(USER_ID), anyString())).thenReturn(null);
        when(usageMapper.selectByUsageKey(eq(USER_ID), anyString()))
                .thenAnswer(invocation -> stored.get());
        when(usageMapper.insert(any(CareerEvidenceUsage.class))).thenAnswer(invocation -> {
            CareerEvidenceUsage row = invocation.getArgument(0);
            row.setId(USAGE_ID);
            stored.set(row);
            return 1;
        });
        CareerEvidenceUsageCreateDTO original = usageRequest("usage-original");
        original.setUsedAt(LocalDateTime.of(2026, 7, 22, 9, 0));
        service.createUsage(APPLICATION_ID, original);

        CareerEvidenceUsageCreateDTO changed = usageRequest("usage-retry");
        changed.setUsedAt(LocalDateTime.of(2026, 7, 22, 10, 0));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createUsage(APPLICATION_ID, changed));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
        verify(usageMapper).insert(any(CareerEvidenceUsage.class));
    }

    @Test
    void usageDetailRejectsForeignUsage() {
        when(usageMapper.selectOwned(USAGE_ID, USER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.usage(USAGE_ID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void createResultAppendsSnapshotAndNeverOverwritesExistingSnapshot() {
        CareerEvidenceUsageResult root = resultRoot(0, "RECORDED", 200L, 1);
        CareerEvidenceUsageResultSnapshot current = resultSnapshot(
                200L, 1, "NO_RESPONSE", "old-content");
        stubCreateResultResolution(root, current);
        when(resultSnapshotMapper.selectByIdempotencyKey(eq(RESULT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);
        when(resultSnapshotMapper.insert(any(CareerEvidenceUsageResultSnapshot.class)))
                .thenAnswer(invocation -> {
                    CareerEvidenceUsageResultSnapshot snapshot = invocation.getArgument(0);
                    snapshot.setId(201L);
                    return 1;
                });
        when(resultMapper.updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(201L), eq(2), eq("RECORDED"), eq(0)))
                .thenReturn(1);

        CareerEvidenceUsageResultVO result =
                service.createResult(USAGE_ID, resultRequest("result-1", "REPLIED"));

        assertEquals(RESULT_ID, result.getId());
        assertEquals(2, result.getSnapshotVersion());
        assertEquals("RECORDED", result.getStatus());
        assertEquals(201L, result.getCurrentSnapshotId());
        assertEquals(200L, current.getId());
        assertEquals(1, current.getSnapshotVersion());

        ArgumentCaptor<CareerEvidenceUsageResultSnapshot> captor =
                ArgumentCaptor.forClass(CareerEvidenceUsageResultSnapshot.class);
        verify(resultSnapshotMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getSnapshotVersion());
        assertEquals(200L, captor.getValue().getSupersedesSnapshotId());
        verify(resultMapper).updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(201L), eq(2), eq("RECORDED"), eq(0));
        verify(resultSnapshotMapper, never()).updateById(any(CareerEvidenceUsageResultSnapshot.class));
    }

    @Test
    void createResultWithSameContentDoesNotAppendDuplicateSnapshot() {
        CareerEvidenceUsageResult root = resultRoot(0, "RECORDED", null, 0);
        AtomicReference<CareerEvidenceUsageResultSnapshot> stored = new AtomicReference<>();
        CareerEvidenceUsage usage = usageRow();
        JobApplication application = application();
        EventResolution event = new EventResolution(
                "APPLICATION_EVENT", 701L, "v1", "event-hash",
                LocalDateTime.of(2026, 7, 21, 10, 0), "application event");
        when(usageMapper.selectOwned(USAGE_ID, USER_ID)).thenReturn(usage);
        when(sourceResolver.ownedApplication(USER_ID, APPLICATION_ID)).thenReturn(application);
        when(sourceResolver.resolveEvent(eq(USER_ID), eq(application), any())).thenReturn(event);
        when(resultMapper.selectByEventKey(eq(USER_ID), eq(USAGE_ID), anyString()))
                .thenReturn(root);
        when(resultSnapshotMapper.selectByIdempotencyKey(
                eq(RESULT_ID), eq(USER_ID), anyString())).thenReturn(null);
        when(resultSnapshotMapper.selectOwned(any(), eq(RESULT_ID), eq(USER_ID)))
                .thenAnswer(invocation -> stored.get());
        when(resultSnapshotMapper.insert(any(CareerEvidenceUsageResultSnapshot.class)))
                .thenAnswer(invocation -> {
                    CareerEvidenceUsageResultSnapshot snapshot = invocation.getArgument(0);
                    snapshot.setId(201L);
                    stored.set(snapshot);
                    return 1;
                });
        when(resultMapper.updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(201L), eq(1), eq("RECORDED"), eq(0)))
                .thenReturn(1);

        CareerEvidenceUsageResultWriteDTO first = resultRequest("result-1", "REPLIED");
        CareerEvidenceUsageResultWriteDTO repeated = resultRequest("result-2", "REPLIED");
        CareerEvidenceUsageResultVO firstResult = service.createResult(USAGE_ID, first);
        CareerEvidenceUsageResultVO repeatedResult = service.createResult(USAGE_ID, repeated);

        assertEquals(201L, firstResult.getCurrentSnapshotId());
        assertEquals(201L, repeatedResult.getCurrentSnapshotId());
        assertEquals(1, repeatedResult.getSnapshotVersion());
        verify(resultSnapshotMapper).insert(any(CareerEvidenceUsageResultSnapshot.class));
        verify(resultMapper).updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(201L), eq(1), eq("RECORDED"), eq(0));
    }

    @Test
    void resultMutationRejectsStaleOptimisticLockBeforeAppending() {
        CareerEvidenceUsageResult root = resultRoot(2, "RECORDED", 200L, 1);
        CareerEvidenceUsageResultSnapshot current = resultSnapshot(
                200L, 1, "NO_RESPONSE", "old-content");
        stubMutationResolution(root, current);
        when(resultSnapshotMapper.selectByIdempotencyKey(eq(RESULT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.correctResult(RESULT_ID, command("correct-1", 1)));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        verify(resultSnapshotMapper, never()).insert(any(CareerEvidenceUsageResultSnapshot.class));
        verify(resultMapper, never()).updateCurrentSnapshot(any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void correctResultAppendsCorrectedSnapshot() {
        CareerEvidenceUsageResult root = resultRoot(2, "RECORDED", 300L, 3);
        CareerEvidenceUsageResultSnapshot current = resultSnapshot(
                300L, 3, "REPLIED", "old-content");
        stubMutationResolution(root, current);
        when(resultSnapshotMapper.selectByIdempotencyKey(eq(RESULT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);
        when(resultSnapshotMapper.insert(any(CareerEvidenceUsageResultSnapshot.class)))
                .thenAnswer(invocation -> {
                    CareerEvidenceUsageResultSnapshot snapshot = invocation.getArgument(0);
                    snapshot.setId(301L);
                    return 1;
                });
        when(resultMapper.updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(301L), eq(4), eq("CORRECTED"), eq(2)))
                .thenReturn(1);

        CareerEvidenceUsageResultVO result =
                service.correctResult(RESULT_ID, command("correct-1", 2));

        assertEquals("CORRECTED", result.getStatus());
        assertEquals(4, result.getSnapshotVersion());
        assertEquals(301L, result.getCurrentSnapshotId());
        assertEquals(3, result.getLockVersion());
        verify(profileFeedbackOutboxService).enqueue(RESULT_ID, USER_ID, 4);
    }

    @Test
    void confirmOnAlreadyConfirmedResultDoesNotNotifyProfileFeedback() {
        CareerEvidenceUsageResult root = resultRoot(2, "CONFIRMED", 300L, 3);
        CareerEvidenceUsageResultSnapshot current = resultSnapshot(
                300L, 3, "REPLIED", "old-content");
        stubMutationResolution(root, current);
        when(resultSnapshotMapper.selectByIdempotencyKey(eq(RESULT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);

        CareerEvidenceUsageResultVO result =
                service.confirmResult(RESULT_ID, command("confirm-noop", 2));

        assertEquals("CONFIRMED", result.getStatus());
        verify(resultSnapshotMapper, never())
                .insert(any(CareerEvidenceUsageResultSnapshot.class));
        verifyNoInteractions(profileFeedbackOutboxService);
    }

    @Test
    void alreadyCorrectedResultCanAppendAnotherCorrectionSnapshot() {
        CareerEvidenceUsageResult root = resultRoot(3, "CORRECTED", 301L, 4);
        CareerEvidenceUsageResultSnapshot current = resultSnapshot(
                301L, 4, "REPLIED", "first-correction");
        stubMutationResolution(root, current);
        when(resultSnapshotMapper.selectByIdempotencyKey(eq(RESULT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);
        when(resultSnapshotMapper.insert(any(CareerEvidenceUsageResultSnapshot.class)))
                .thenAnswer(invocation -> {
                    CareerEvidenceUsageResultSnapshot snapshot = invocation.getArgument(0);
                    snapshot.setId(302L);
                    return 1;
                });
        when(resultMapper.updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(302L), eq(5), eq("CORRECTED"), eq(3)))
                .thenReturn(1);

        CareerEvidenceUsageResultVO result =
                service.correctResult(RESULT_ID, command("correct-2", 3));

        assertEquals("CORRECTED", result.getStatus());
        assertEquals(5, result.getSnapshotVersion());
        assertEquals(302L, result.getCurrentSnapshotId());
        assertEquals(4, result.getLockVersion());
        verify(resultSnapshotMapper).insert(any(CareerEvidenceUsageResultSnapshot.class));
    }

    @Test
    void voidResultAppendsVoidSnapshotWithReasonLimit() throws Exception {
        CareerEvidenceUsageResult root = resultRoot(2, "CONFIRMED", 300L, 3);
        CareerEvidenceUsageResultSnapshot current = resultSnapshot(
                300L, 3, "REPLIED", "old-content");
        current.setLimitsJson("[\"existing limit\"]");
        stubMutationResolution(root, current);
        when(resultSnapshotMapper.selectByIdempotencyKey(eq(RESULT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);
        when(resultSnapshotMapper.insert(any(CareerEvidenceUsageResultSnapshot.class)))
                .thenAnswer(invocation -> {
                    CareerEvidenceUsageResultSnapshot snapshot = invocation.getArgument(0);
                    snapshot.setId(302L);
                    return 1;
                });
        when(resultMapper.updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(302L), eq(4), eq("VOID"), eq(2)))
                .thenReturn(1);

        CareerEvidenceUsageResultVO result =
                service.voidResult(RESULT_ID, commandWithReason("void-1", 2, "duplicate event"));

        assertEquals("VOID", result.getStatus());
        assertEquals(302L, result.getCurrentSnapshotId());
        assertTrue(result.getLimits().contains("结果已作废：duplicate event"));
        verify(resultMapper).updateCurrentSnapshot(
                eq(RESULT_ID), eq(USER_ID), eq(302L), eq(4), eq("VOID"), eq(2));
        verify(profileFeedbackOutboxService).enqueue(RESULT_ID, USER_ID, 4);
    }

    @Test
    void listUsagesAppliesCampaignAndDataCutoffFilters() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 0, 0);
        CareerEvidenceUsage row = usageRow();
        Page<CareerEvidenceUsage> page = Page.of(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(usageMapper.selectPage(any(Page.class), any())).thenReturn(page);
        CareerEvidenceUsageQueryDTO query = new CareerEvidenceUsageQueryDTO();
        query.setUsageId(USAGE_ID);
        query.setCampaignId(501L);
        query.setTargetJobId(88L);
        query.setAssetId(31L);
        query.setPackageSnapshotId(41L);
        query.setHypothesisId(801L);
        query.setStatus("CAPTURED");
        query.setDataCutoffAt(cutoff);

        var envelope = service.listUsages(query);

        assertEquals(cutoff, envelope.getDataCutoffAt());
        assertEquals(1, envelope.getItems().size());
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsage>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(usageMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("id"), sql);
        assertTrue(sql.contains("campaign_id"), sql);
        assertTrue(sql.contains("target_job_id"), sql);
        assertTrue(sql.contains("asset_id"), sql);
        assertTrue(sql.contains("package_snapshot_id"), sql);
        assertTrue(sql.contains("hypothesis_id"), sql);
        assertTrue(sql.contains("used_at"), sql);
    }

    @Test
    void listUsagesFiltersLegacyExperimentByOwnedHypothesis() {
        ExperimentHypothesis hypothesis = new ExperimentHypothesis();
        hypothesis.setId(801L);
        hypothesis.setUserId(USER_ID);
        hypothesis.setLegacyExperimentId(7L);
        when(hypothesisMapper.selectList(any())).thenReturn(List.of(hypothesis));
        Page<CareerEvidenceUsage> page = Page.of(1, 20);
        page.setRecords(List.of(usageRow()));
        page.setTotal(1);
        when(usageMapper.selectPage(any(Page.class), any())).thenReturn(page);
        CareerEvidenceUsageQueryDTO query = new CareerEvidenceUsageQueryDTO();
        query.setExperimentId(7L);

        var envelope = service.listUsages(query);

        assertEquals(1, envelope.getItems().size());
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsage>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(usageMapper).selectPage(any(Page.class), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().toLowerCase().contains("hypothesis_id"));
    }

    @Test
    void listResultsAppliesCampaignAndCutoffAndReadsHistoricalSnapshot() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 0, 0);
        CareerEvidenceUsage usage = usageRow();
        CareerEvidenceUsageResult root = resultRoot(1, "CONFIRMED", 202L, 2);
        CareerEvidenceUsageResultSnapshot historical = resultSnapshot(
                202L, 2, "REPLIED", "historical-content");
        when(usageMapper.selectList(any())).thenReturn(List.of(usage));
        Page<CareerEvidenceUsageResult> page = Page.of(1, 20);
        page.setRecords(List.of(root));
        page.setTotal(1);
        when(resultMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(resultSnapshotMapper.selectLatestAtCutoff(RESULT_ID, USER_ID, cutoff))
                .thenReturn(historical);
        CareerEvidenceUsageResultQueryDTO query = new CareerEvidenceUsageResultQueryDTO();
        query.setResultId(RESULT_ID);
        query.setCampaignId(501L);
        query.setTargetJobId(88L);
        query.setAssetType("PROJECT_EVIDENCE");
        query.setAssetId(31L);
        query.setPackageSnapshotId(41L);
        query.setHypothesisId(801L);
        query.setStatus("CONFIRMED");
        query.setOutcomeCode("REPLIED");
        query.setDataCutoffAt(cutoff);

        var envelope = service.listResults(query);

        assertEquals(1, envelope.getItems().size());
        assertEquals(2, envelope.getItems().get(0).getSnapshotVersion());
        assertEquals("historical-content", envelope.getItems().get(0).getContentHash());
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsageResult>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(resultMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("id"), sql);
        assertTrue(sql.contains("usage_id"), sql);
        assertTrue(sql.contains("created_at"), sql);
        assertFalse(sql.contains("updated_at <="), sql);
        assertTrue(sql.contains("exists"), sql);
        assertTrue(sql.contains("outcome_code"), sql);
        assertTrue(sql.contains("s.status"), sql);
        verify(resultSnapshotMapper).selectLatestAtCutoff(RESULT_ID, USER_ID, cutoff);
        verify(resultSnapshotMapper, never())
                .selectResultIdsByOutcome(any(), any(), any(), any());
        verify(resultSnapshotMapper, never()).selectOwned(any(), any(), any());
    }

    @Test
    void listResultsAppliesOutcomeBeforePaginationAndKeepsFilteredTotal() {
        CareerEvidenceUsageResult root = resultRoot(1, "CONFIRMED", 202L, 2);
        CareerEvidenceUsageResultSnapshot current =
                resultSnapshot(202L, 2, "REPLIED", "matching-content");
        Page<CareerEvidenceUsageResult> page = Page.of(2, 1);
        page.setRecords(List.of(root));
        page.setTotal(3);
        when(resultMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(resultSnapshotMapper.selectOwned(202L, RESULT_ID, USER_ID)).thenReturn(current);
        CareerEvidenceUsageResultQueryDTO query = new CareerEvidenceUsageResultQueryDTO();
        query.setPageNo(2L);
        query.setPageSize(1L);
        query.setOutcomeCode("REPLIED");

        var envelope = service.listResults(query);

        assertEquals(3L, envelope.getTotal());
        assertEquals(2L, envelope.getPageNo());
        assertEquals(1L, envelope.getPageSize());
        assertEquals(List.of(RESULT_ID),
                envelope.getItems().stream().map(CareerEvidenceUsageResultVO::getId).toList());
        ArgumentCaptor<Page<CareerEvidenceUsageResult>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsageResult>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(resultMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals(2L, pageCaptor.getValue().getCurrent());
        assertEquals(1L, pageCaptor.getValue().getSize());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("exists"), sql);
        assertTrue(sql.contains("current_snapshot_id"), sql);
        assertTrue(sql.contains("outcome_code"), sql);
        verify(resultSnapshotMapper, never())
                .selectResultIdsByOutcome(any(), any(), any(), any());
    }

    @Test
    void overviewScopesUsageResultAndReadinessByCampaignAndApplication() {
        when(usageMapper.selectCount(any()))
                .thenReturn(1L, 1L, 0L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 501L, APPLICATION_ID, null)).thenReturn(1L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 501L, APPLICATION_ID, "PROJECT_EVIDENCE")).thenReturn(1L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 501L, APPLICATION_ID, "APPLICATION_PACKAGE_SNAPSHOT")).thenReturn(0L);
        when(projectEvidenceMapper.selectCount(any())).thenReturn(1L);
        when(projectEvidenceVersionMapper.selectCount(any())).thenReturn(1L);

        var envelope = service.overview(501L, APPLICATION_ID);

        assertEquals(1L, envelope.getOverview().getUsageCount());
        assertEquals(1L, envelope.getOverview().getOutcomeSampleCount());
        assertEquals(1L, envelope.getOverview().getReadiness().get(0).getResultCount());
        assertEquals(0L, envelope.getOverview().getReadiness().get(1).getResultCount());
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsage>> usageCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(usageMapper, org.mockito.Mockito.times(3)).selectCount(usageCaptor.capture());
        for (LambdaQueryWrapper<CareerEvidenceUsage> wrapper : usageCaptor.getAllValues()) {
            String sql = wrapper.getSqlSegment().toLowerCase();
            assertTrue(sql.contains("campaign_id"), sql);
            assertTrue(sql.contains("application_id"), sql);
        }
    }

    @Test
    void overviewKeepsTwoCampaignsIsolated() {
        when(usageMapper.selectCount(any()))
                .thenReturn(4L, 3L, 1L, 2L, 2L, 0L);
        when(resultMapper.selectCountByUsageScope(USER_ID, 501L, null, null))
                .thenReturn(3L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 501L, null, "PROJECT_EVIDENCE")).thenReturn(2L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 501L, null, "APPLICATION_PACKAGE_SNAPSHOT")).thenReturn(1L);
        when(resultMapper.selectCountByUsageScope(USER_ID, 502L, null, null))
                .thenReturn(1L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 502L, null, "PROJECT_EVIDENCE")).thenReturn(1L);
        when(resultMapper.selectCountByUsageScope(
                USER_ID, 502L, null, "APPLICATION_PACKAGE_SNAPSHOT")).thenReturn(0L);
        when(projectEvidenceMapper.selectCount(any())).thenReturn(5L);
        when(projectEvidenceVersionMapper.selectCount(any())).thenReturn(4L);

        var first = service.overview(501L, null);
        var second = service.overview(502L, null);

        assertEquals(4L, first.getOverview().getUsageCount());
        assertEquals(3L, first.getOverview().getOutcomeSampleCount());
        assertEquals(3L, first.getOverview().getReadiness().get(0).getUsedCount());
        assertEquals(2L, first.getOverview().getReadiness().get(0).getResultCount());
        assertEquals(1L, first.getOverview().getReadiness().get(1).getUsedCount());
        assertEquals(1L, first.getOverview().getReadiness().get(1).getResultCount());

        assertEquals(2L, second.getOverview().getUsageCount());
        assertEquals(1L, second.getOverview().getOutcomeSampleCount());
        assertEquals(2L, second.getOverview().getReadiness().get(0).getUsedCount());
        assertEquals(1L, second.getOverview().getReadiness().get(0).getResultCount());
        assertEquals(0L, second.getOverview().getReadiness().get(1).getUsedCount());
        assertEquals(0L, second.getOverview().getReadiness().get(1).getResultCount());
        assertFalse(first.getSourceSetHash().equals(second.getSourceSetHash()));
    }

    @Test
    void overviewResultCountMapperJoinsUsageScope() throws Exception {
        Select select = CareerEvidenceUsageResultMapper.class
                .getMethod("selectCountByUsageScope",
                        Long.class, Long.class, Long.class, String.class)
                .getAnnotation(Select.class);

        assertNotNull(select);
        String sql = String.join("\n", select.value()).toLowerCase();
        assertTrue(sql.contains("join career_evidence_usage"), sql);
        assertTrue(sql.contains("u.campaign_id"), sql);
        assertTrue(sql.contains("u.application_id"), sql);
        assertTrue(sql.contains("u.asset_type"), sql);
    }

    @Test
    void trustedOutcomeCountDeduplicatesUsageIds() throws Exception {
        Select select = CareerEvidenceUsageResultMapper.class
                .getMethod(
                        "countTrustedOutcomeByAsset",
                        Long.class,
                        Long.class,
                        String.class,
                        Long.class,
                        String.class)
                .getAnnotation(Select.class);

        assertNotNull(select);
        String sql = String.join("\n", select.value()).toLowerCase();
        assertTrue(sql.contains("count(distinct r.usage_id)"), sql);
    }

    @Test
    void innerFactsIncludesLatestAttributionAndHashesTheCompleteEnvelope() {
        CareerEvidenceUsage usage = usageRow();
        usage.setHypothesisId(801L);
        usage.setVariantId(802L);
        usage.setAssignmentId(803L);
        Page<CareerEvidenceUsage> usagePage = Page.of(1, 500);
        usagePage.setRecords(List.of(usage));
        usagePage.setTotal(1);
        when(usageMapper.selectPage(any(Page.class), any())).thenReturn(usagePage);
        Page<CareerEvidenceUsageResult> resultPage = Page.of(1, 100);
        resultPage.setRecords(List.of());
        resultPage.setTotal(0);
        when(resultMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);
        ExperimentAttribution attribution = new ExperimentAttribution();
        attribution.setId(901L);
        attribution.setUserId(USER_ID);
        attribution.setHypothesisId(801L);
        attribution.setComparableFlag(1);
        attribution.setFallback(0);
        attribution.setResultJson("{\"confidenceLevel\":\"MEDIUM\"}");
        when(attributionMapper.selectList(any())).thenReturn(List.of(attribution));

        var facts = service.innerFacts(USER_ID, 501L, null, USAGE_ID);

        assertEquals(1, facts.getUsageSnapshots().size());
        assertEquals(1, facts.getExperimentAttributions().size());
        var attributionFact = facts.getExperimentAttributions().get(0);
        assertEquals(901L, attributionFact.getAttributionId());
        assertEquals(802L, attributionFact.getVariantId());
        assertEquals(803L, attributionFact.getAssignmentId());
        assertEquals("COMPARABLE", attributionFact.getStatus());
        assertEquals("MEDIUM", attributionFact.getConfidenceLevel());
        assertNotNull(facts.getSourceSetHash());
        assertEquals(64, facts.getSourceSetHash().length());
        assertEquals(1, facts.getCoverage().get("attributionCount"));
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsage>> usageCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(usageMapper).selectPage(any(Page.class), usageCaptor.capture());
        assertTrue(usageCaptor.getValue().getSqlSegment().toLowerCase().contains("id"));
        assertTrue(usageCaptor.getValue().getParamNameValuePairs().containsValue(USAGE_ID));
    }

    @Test
    void innerFactsUsageIdNarrowingIsNotAffectedByFiveHundredRowLimit() {
        CareerEvidenceUsage requested = usageRow();
        CareerEvidenceUsage other = usageRow();
        other.setId(USAGE_ID + 1);
        Page<CareerEvidenceUsage> narrowedPage = Page.of(1, 500);
        narrowedPage.setRecords(List.of(requested));
        narrowedPage.setTotal(1);
        Page<CareerEvidenceUsage> unscopedPage = Page.of(1, 500);
        unscopedPage.setRecords(List.of(other));
        unscopedPage.setTotal(501);
        when(usageMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<CareerEvidenceUsage> wrapper = invocation.getArgument(1);
            wrapper.getCustomSqlSegment();
            return wrapper.getParamNameValuePairs().containsValue(USAGE_ID)
                    ? narrowedPage : unscopedPage;
        });
        Page<CareerEvidenceUsageResult> resultPage = Page.of(1, 100);
        resultPage.setRecords(List.of());
        resultPage.setTotal(0);
        when(resultMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        var facts = service.innerFacts(USER_ID, 501L, null, USAGE_ID);

        assertEquals(List.of(USAGE_ID),
                facts.getUsageSnapshots().stream()
                        .map(item -> item.getUsageId())
                        .toList());
        ArgumentCaptor<Page<CareerEvidenceUsage>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<CareerEvidenceUsage>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(usageMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals(500L, pageCaptor.getValue().getSize());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().toLowerCase().contains("id"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(USAGE_ID));
    }

    private void stubUsageResolution() {
        JobApplication application = application();
        AssetResolution asset = asset();
        when(sourceResolver.ownedApplication(USER_ID, APPLICATION_ID)).thenReturn(application);
        when(sourceResolver.resolveAsset(eq(USER_ID), eq(application), any()))
                .thenReturn(asset);
        CareerEvidenceUsageVO.SourceRef sourceRef = new CareerEvidenceUsageVO.SourceRef();
        sourceRef.setSourceType(asset.assetType());
        sourceRef.setSourceId(asset.assetId());
        sourceRef.setSourceVersion(asset.assetVersion());
        when(sourceResolver.sourceRef(asset)).thenReturn(sourceRef);
    }

    private void stubCreateResultResolution(
            CareerEvidenceUsageResult root, CareerEvidenceUsageResultSnapshot current) {
        CareerEvidenceUsage usage = usageRow();
        JobApplication application = application();
        EventResolution event = new EventResolution(
                "APPLICATION_EVENT", 701L, "v1", "event-hash",
                LocalDateTime.of(2026, 7, 21, 10, 0), "application event");
        when(usageMapper.selectOwned(USAGE_ID, USER_ID)).thenReturn(usage);
        when(sourceResolver.ownedApplication(USER_ID, APPLICATION_ID)).thenReturn(application);
        when(sourceResolver.resolveEvent(eq(USER_ID), eq(application), any())).thenReturn(event);
        when(resultMapper.selectByEventKey(eq(USER_ID), eq(USAGE_ID), anyString())).thenReturn(root);
        when(resultSnapshotMapper.selectOwned(
                eq(current.getId()), eq(RESULT_ID), eq(USER_ID))).thenReturn(current);
    }

    private void stubMutationResolution(
            CareerEvidenceUsageResult root, CareerEvidenceUsageResultSnapshot current) {
        when(resultMapper.selectForUpdate(RESULT_ID, USER_ID)).thenReturn(root);
        when(resultSnapshotMapper.selectOwned(
                eq(current.getId()), eq(RESULT_ID), eq(USER_ID))).thenReturn(current);
    }

    private CareerEvidenceUsageCreateDTO usageRequest(String key) {
        CareerEvidenceUsageCreateDTO request = new CareerEvidenceUsageCreateDTO();
        request.setAssetType("PROJECT_EVIDENCE");
        request.setAssetId(31L);
        request.setAssetVersion("V2");
        request.setUsageScene("APPLICATION_PACKAGE");
        request.setUsedAt(LocalDateTime.of(2026, 7, 21, 9, 0));
        request.setIdempotencyKey(key);
        return request;
    }

    private CareerEvidenceUsageResultWriteDTO resultRequest(String key, String outcome) {
        CareerEvidenceUsageResultWriteDTO request = new CareerEvidenceUsageResultWriteDTO();
        request.setEventType("APPLICATION_EVENT");
        request.setEventId(701L);
        request.setOutcomeCode(outcome);
        request.setKnownFacts(List.of("event observed"));
        request.setUnknowns(List.of("external decision"));
        request.setLimits(List.of("single event"));
        request.setOccurredAt(LocalDateTime.of(2026, 7, 21, 10, 0));
        request.setIdempotencyKey(key);
        return request;
    }

    private CareerEvidenceUsageResultCommandDTO command(String key, int lockVersion) {
        CareerEvidenceUsageResultCommandDTO request = new CareerEvidenceUsageResultCommandDTO();
        request.setExpectedLockVersion(lockVersion);
        request.setOutcomeCode("INTERVIEW_ADVANCED");
        request.setKnownFacts(List.of("corrected fact"));
        request.setUnknowns(List.of("remaining unknown"));
        request.setLimits(List.of("corrected limit"));
        request.setIdempotencyKey(key);
        return request;
    }

    private CareerEvidenceUsageResultCommandDTO commandWithReason(
            String key, int lockVersion, String reason) {
        CareerEvidenceUsageResultCommandDTO request = command(key, lockVersion);
        request.setReason(reason);
        return request;
    }

    private JobApplication application() {
        JobApplication application = new JobApplication();
        application.setId(APPLICATION_ID);
        application.setUserId(USER_ID);
        application.setCampaignId(501L);
        application.setTargetJobId(88L);
        return application;
    }

    private AssetResolution asset() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 7, 20, 8, 0);
        return new AssetResolution(
                "PROJECT_EVIDENCE", 31L, "2", "source-hash", "content-hash",
                88L, observedAt, observedAt, "Redis project");
    }

    private CareerEvidenceUsage usageRow() {
        CareerEvidenceUsage usage = new CareerEvidenceUsage();
        usage.setId(USAGE_ID);
        usage.setUserId(USER_ID);
        usage.setCampaignId(501L);
        usage.setApplicationId(APPLICATION_ID);
        usage.setTargetJobId(88L);
        usage.setAssetType("PROJECT_EVIDENCE");
        usage.setAssetId(31L);
        usage.setAssetVersion("2");
        usage.setPackageSnapshotId(41L);
        usage.setSourceHash("source-hash");
        usage.setContentHash("content-hash");
        usage.setUsageScene("APPLICATION_PACKAGE");
        usage.setUsedAt(LocalDateTime.of(2026, 7, 21, 9, 0));
        usage.setStatus("CAPTURED");
        usage.setStale(0);
        return usage;
    }

    private CareerEvidenceUsageResult resultRoot(
            int lockVersion, String status, Long currentSnapshotId, int snapshotVersion) {
        CareerEvidenceUsageResult root = new CareerEvidenceUsageResult();
        root.setId(RESULT_ID);
        root.setUserId(USER_ID);
        root.setUsageId(USAGE_ID);
        root.setApplicationId(APPLICATION_ID);
        root.setEventType("APPLICATION_EVENT");
        root.setEventId(701L);
        root.setCurrentSnapshotId(currentSnapshotId);
        root.setSnapshotVersion(snapshotVersion);
        root.setStatus(status);
        root.setLockVersion(lockVersion);
        return root;
    }

    private CareerEvidenceUsageResultSnapshot resultSnapshot(
            Long id, int version, String outcomeCode, String contentHash) {
        CareerEvidenceUsageResultSnapshot snapshot = new CareerEvidenceUsageResultSnapshot();
        snapshot.setId(id);
        snapshot.setResultId(RESULT_ID);
        snapshot.setUserId(USER_ID);
        snapshot.setSnapshotVersion(version);
        snapshot.setStatus("CONFIRMED");
        snapshot.setOutcomeCode(outcomeCode);
        snapshot.setKnownFactsJson("[\"old fact\"]");
        snapshot.setUnknownsJson("[\"old unknown\"]");
        snapshot.setLimitsJson("[\"old limit\"]");
        snapshot.setSourceType("APPLICATION_EVENT");
        snapshot.setSourceId(701L);
        snapshot.setSourceVersion("v1");
        snapshot.setSourceHash("event-hash");
        snapshot.setOccurredAt(LocalDateTime.of(2026, 7, 21, 10, 0));
        snapshot.setContentHash(contentHash);
        snapshot.setCreatedAt(LocalDateTime.of(2026, 7, 21, 11, 0));
        return snapshot;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
