package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.config.V12FeatureGate;
import com.codecoachai.resume.config.V13FeatureGate;
import com.codecoachai.resume.domain.entity.AbilitySkillNode;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResultSnapshot;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.mapper.AbilitySkillNodeMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultSnapshotMapper;
import com.codecoachai.resume.mapper.EvidenceUsageAbilityProjectionMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.SkillProfileService;
import com.codecoachai.resume.service.support.EvidenceProfileFeedbackService.ProjectionDisposition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class EvidenceProfileFeedbackServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long USAGE_ID = 91L;
    private static final Long RESULT_ID = 101L;
    private static final Long TARGET_JOB_ID = 88L;
    private static final Long ASSET_ID = 31L;
    private static final Long PROFILE_ID = 555L;
    private static final Long SKILL_EVIDENCE_ID = 41L;

    @Mock
    private V12FeatureGate featureGate;
    @Mock
    private V13FeatureGate v13FeatureGate;
    @Mock
    private CareerEvidenceUsageMapper usageMapper;
    @Mock
    private CareerEvidenceUsageResultMapper resultMapper;
    @Mock
    private CareerEvidenceUsageResultSnapshotMapper resultSnapshotMapper;
    @Mock
    private AbilitySkillNodeMapper abilitySkillNodeMapper;
    @Mock
    private EvidenceUsageAbilityProjectionMapper abilityProjectionMapper;
    @Mock
    private SkillGapItemMapper gapItemMapper;
    @Mock
    private UserAbilityProfileMapper abilityProfileMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private ProjectSkillEvidenceMapper projectSkillEvidenceMapper;
    @Mock
    private SkillProfileService skillProfileService;

    private EvidenceProfileFeedbackService service;

    @BeforeAll
    static void initTables() {
        initTableInfo(SkillGapItem.class);
        initTableInfo(ProjectSkillEvidence.class);
        initTableInfo(UserAbilityProfile.class);
        initTableInfo(AbilitySkillNode.class);
    }

    @BeforeEach
    void setUp() {
        service = new EvidenceProfileFeedbackService(
                featureGate, v13FeatureGate, usageMapper, resultMapper, resultSnapshotMapper,
                abilitySkillNodeMapper, abilityProjectionMapper, gapItemMapper,
                abilityProfileMapper, projectEvidenceMapper, projectSkillEvidenceMapper,
                skillProfileService, new ObjectMapper());
    }

    @Test
    void skipsEntirelyWhenGateDisabled() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(false);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verifyNoInteractions(usageMapper, gapItemMapper, skillProfileService);
    }

    @Test
    void outboxProjectionIsDeferredWhenAllProjectionGatesAreDisabled() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(false);
        when(v13FeatureGate.isPositiveAbilityReinforcement()).thenReturn(false);

        ProjectionDisposition disposition = service.recomputeResult(RESULT_ID, USER_ID);

        assertEquals(ProjectionDisposition.DEFERRED_BOTH, disposition);
        verifyNoInteractions(resultMapper, resultSnapshotMapper, usageMapper, gapItemMapper,
                abilityProfileMapper, skillProfileService);
    }

    @Test
    void missingUsageIsNoop() {
        gateOn();
        when(usageMapper.selectOwned(USAGE_ID, USER_ID)).thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verifyNoInteractions(gapItemMapper, skillProfileService);
    }

    @Test
    void outboxProjectionReloadsTheCurrentPersistedSnapshot() {
        gateOn();
        reinforcementGateOn();
        CareerEvidenceUsageResult root = root("CONFIRMED");
        root.setCurrentSnapshotId(202L);
        CareerEvidenceUsageResultSnapshot snapshot = new CareerEvidenceUsageResultSnapshot();
        snapshot.setId(202L);
        snapshot.setResultId(RESULT_ID);
        snapshot.setUserId(USER_ID);
        snapshot.setOutcomeCode("INTERVIEW_NOT_ADVANCED");
        snapshot.setUserInterpretationText("需要突出量化结果");
        when(resultMapper.selectOwned(RESULT_ID, USER_ID)).thenReturn(root);
        when(resultSnapshotMapper.selectOwned(202L, RESULT_ID, USER_ID)).thenReturn(snapshot);
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE"))
                .thenReturn(0L);
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ProjectionDisposition disposition = service.recomputeResult(RESULT_ID, USER_ID);

        assertEquals(ProjectionDisposition.COMPLETED, disposition);
        ArgumentCaptor<SkillGapItem> captor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper).insert(captor.capture());
        assertTrue(captor.getValue().getGapDescription().contains("需要突出量化结果"));
    }

    @Test
    void completedEvidenceProjectionWaitsOnlyForPositiveAbilityReinforcement() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(false);
        when(v13FeatureGate.isPositiveAbilityReinforcement()).thenReturn(false);

        ProjectionDisposition disposition =
                service.recomputeResult(RESULT_ID, USER_ID, true, false);

        assertEquals(ProjectionDisposition.DEFERRED_ABILITY, disposition);
        verifyNoInteractions(resultMapper, resultSnapshotMapper, usageMapper, gapItemMapper,
                abilityProfileMapper, skillProfileService);
    }

    @Test
    void enabledEvidenceProjectionDefersDisabledAbilityProjection() {
        gateOn();
        when(v13FeatureGate.isPositiveAbilityReinforcement()).thenReturn(false);
        CareerEvidenceUsageResult root = root("CONFIRMED");
        root.setCurrentSnapshotId(202L);
        CareerEvidenceUsageResultSnapshot snapshot = new CareerEvidenceUsageResultSnapshot();
        snapshot.setId(202L);
        snapshot.setResultId(RESULT_ID);
        snapshot.setUserId(USER_ID);
        snapshot.setOutcomeCode("OFFER_RECEIVED");
        when(resultMapper.selectOwned(RESULT_ID, USER_ID)).thenReturn(root);
        when(resultSnapshotMapper.selectOwned(202L, RESULT_ID, USER_ID)).thenReturn(snapshot);
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE"))
                .thenReturn(0L);

        ProjectionDisposition disposition = service.recomputeResult(RESULT_ID, USER_ID);

        assertEquals(ProjectionDisposition.DEFERRED_ABILITY, disposition);
        verifyNoInteractions(abilityProfileMapper);
    }

    @Test
    void retryWithCompletedEvidenceProjectionRunsOnlyAbilityProjection() {
        gateOn();
        reinforcementGateOn();
        CareerEvidenceUsageResult root = root("CONFIRMED");
        root.setCurrentSnapshotId(202L);
        CareerEvidenceUsageResultSnapshot snapshot = new CareerEvidenceUsageResultSnapshot();
        snapshot.setId(202L);
        snapshot.setResultId(RESULT_ID);
        snapshot.setUserId(USER_ID);
        snapshot.setOutcomeCode("OFFER_RECEIVED");
        when(resultMapper.selectOwned(RESULT_ID, USER_ID)).thenReturn(root);
        when(resultSnapshotMapper.selectOwned(202L, RESULT_ID, USER_ID)).thenReturn(snapshot);
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(1L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ProjectionDisposition disposition =
                service.recomputeResult(RESULT_ID, USER_ID, true, false);

        assertEquals(ProjectionDisposition.COMPLETED, disposition);
        verify(abilityProfileMapper).insert(any(UserAbilityProfile.class));
        verifyNoInteractions(gapItemMapper, skillProfileService);
        verify(resultMapper, never()).countTrustedOutcomeByAsset(
                anyLong(), anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void confirmedInterviewNotAdvancedInsertsResultGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ProjectEvidence evidence = new ProjectEvidence();
        evidence.setTitle("分布式锁改造");
        when(projectEvidenceMapper.selectById(ASSET_ID)).thenReturn(evidence);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(4L);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", "讲得太散，未突出难点");

        ArgumentCaptor<SkillGapItem> captor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper).insert(captor.capture());
        SkillGapItem item = captor.getValue();
        assertEquals(PROFILE_ID, item.getProfileId());
        assertEquals(USER_ID, item.getUserId());
        assertEquals(TARGET_JOB_ID, item.getTargetJobId());
        assertEquals("EVIDENCE_USAGE_FEEDBACK", item.getCategory());
        assertEquals("EVIDENCE_USAGE_RESULT", item.getSourceType());
        assertEquals(RESULT_ID, item.getSourceBizId());
        assertEquals("MEDIUM", item.getSeverity());
        assertEquals("分布式锁改造", item.getSkillName());
        assertEquals(5, item.getPriority());
        assertTrue(item.getGapDescription().contains("面试"));
        assertTrue(item.getGapDescription().contains("面试未晋级"));
        assertTrue(item.getGapDescription().contains("讲得太散"));
        assertTrue(item.getEvidenceSourcesJson().contains("EVIDENCE_USAGE_RESULT:" + RESULT_ID));
    }

    @Test
    void repeatConfirmUpdatesExistingResultGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        SkillGapItem existing = new SkillGapItem();
        existing.setId(77L);
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing, (SkillGapItem) null);
        ProjectEvidence evidence = new ProjectEvidence();
        evidence.setTitle("分布式锁改造");
        when(projectEvidenceMapper.selectById(ASSET_ID)).thenReturn(evidence);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verify(gapItemMapper).updateById(existing);
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        assertEquals(PROFILE_ID, existing.getProfileId());
    }

    @Test
    void correctedAwayRemovesResultGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        SkillGapItem existing = new SkillGapItem();
        existing.setId(77L);
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing, (SkillGapItem) null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

        service.afterResultTransition(root("CORRECTED"), "REPLIED", null);

        verify(gapItemMapper).deleteById(77L);
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void voidRetractsResultGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        SkillGapItem existing = new SkillGapItem();
        existing.setId(77L);
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing, (SkillGapItem) null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

        service.afterResultTransition(root("VOID"), "INTERVIEW_NOT_ADVANCED", null);

        verify(gapItemMapper).deleteById(77L);
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void noResponseBelowThresholdWritesNothing() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "APPLICATION_SUBMISSION"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(2L);

        service.afterResultTransition(root("CONFIRMED"), "NO_RESPONSE", null);

        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verify(gapItemMapper, never()).deleteById(any(Long.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void noResponseAtThresholdInsertsPatternGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "APPLICATION_SUBMISSION"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ProjectEvidence evidence = new ProjectEvidence();
        evidence.setTitle("网关限流改造");
        when(projectEvidenceMapper.selectById(ASSET_ID)).thenReturn(evidence);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(3L);
        when(resultMapper.selectTrustedOutcomeUsageIds(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE"))
                .thenReturn(List.of(91L, 92L, 93L));
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "NO_RESPONSE", null);

        ArgumentCaptor<SkillGapItem> captor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper).insert(captor.capture());
        SkillGapItem item = captor.getValue();
        assertEquals("EVIDENCE_USAGE_PATTERN_PROJECT_EVIDENCE", item.getSourceType());
        assertEquals(ASSET_ID, item.getSourceBizId());
        assertEquals("LOW", item.getSeverity());
        assertEquals(1, item.getPriority());
        assertTrue(item.getGapDescription().contains("3 次"));
        assertTrue(item.getEvidenceSourcesJson().contains("EVIDENCE_USAGE:91"));
    }

    @Test
    void noResponseRefreshUpdatesPatternGapSkillName() {
        gateOn();
        stubUsage(usage(
                TARGET_JOB_ID,
                "PROJECT_EVIDENCE",
                ASSET_ID,
                "APPLICATION_SUBMISSION"));
        SkillGapItem existing = new SkillGapItem();
        existing.setId(88L);
        existing.setSkillName("旧证据标题");
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, existing);
        ProjectEvidence evidence = new ProjectEvidence();
        evidence.setTitle("新版网关限流改造");
        when(projectEvidenceMapper.selectById(ASSET_ID)).thenReturn(evidence);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID,
                TARGET_JOB_ID,
                "PROJECT_EVIDENCE",
                ASSET_ID,
                "NO_RESPONSE")).thenReturn(3L);
        when(resultMapper.selectTrustedOutcomeUsageIds(
                USER_ID,
                TARGET_JOB_ID,
                "PROJECT_EVIDENCE",
                ASSET_ID,
                "NO_RESPONSE")).thenReturn(List.of(91L, 92L, 93L));
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());

        service.afterResultTransition(root("CONFIRMED"), "NO_RESPONSE", null);

        verify(gapItemMapper).updateById(existing);
        assertEquals("新版网关限流改造", existing.getSkillName());
    }

    @Test
    void noResponseDroppedBelowThresholdDeletesPatternGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "APPLICATION_SUBMISSION"));
        SkillGapItem patternGap = new SkillGapItem();
        patternGap.setId(88L);
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, patternGap);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(2L);

        service.afterResultTransition(root("VOID"), "NO_RESPONSE", null);

        verify(gapItemMapper).deleteById(88L);
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
    }

    @Test
    void interviewNotAdvancedWithoutTargetJobWritesNothing() {
        gateOn();
        stubUsage(usage(null, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void patternGapWithoutTargetJobIsNotCreated() {
        gateOn();
        stubUsage(usage(null, "PROJECT_EVIDENCE", ASSET_ID, "APPLICATION_SUBMISSION"));

        service.afterResultTransition(root("CONFIRMED"), "NO_RESPONSE", null);

        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void skillEvidenceAssetUsesSkillNameAsGapSkillName() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", 41L, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ProjectSkillEvidence evidence = new ProjectSkillEvidence();
        evidence.setSkillName("Redis 分布式锁");
        when(projectSkillEvidenceMapper.selectById(41L)).thenReturn(evidence);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", 41L, "NO_RESPONSE")).thenReturn(0L);
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        ArgumentCaptor<SkillGapItem> captor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper).insert(captor.capture());
        assertEquals("Redis 分布式锁", captor.getValue().getSkillName());
    }

    @Test
    void positiveOutcomesWriteNothingWhenReinforcementGateOff() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService, abilityProfileMapper);
    }

    @Test
    void redisDistributedLockChoosesRedisFromProductionNodeSet() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence(" Redis 分布式锁 ", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(2L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_ADVANCED", null);

        ArgumentCaptor<UserAbilityProfile> captor = ArgumentCaptor.forClass(UserAbilityProfile.class);
        verify(abilityProfileMapper).insert(captor.capture());
        UserAbilityProfile created = captor.getValue();
        assertEquals(USER_ID, created.getUserId());
        assertEquals("REDIS_CACHE", created.getSkillCode());
        assertEquals("UNASSESSED", created.getStatus());
        assertEquals(2, created.getEvidenceCount());
        assertEquals("MEDIUM", created.getConfidence());
        assertEquals("EVIDENCE_USAGE", created.getSourceType());
        assertNotNull(created.getLastEvaluatedAt());
        assertTrue(created.getSummary().contains("2 次正向结果"));
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void equallySpecificProductionAliasesRemainUnmapped() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(
                TARGET_JOB_ID,
                "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID,
                "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID,
                TARGET_JOB_ID,
                "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID,
                "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis MySQL 双写", 1));

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProjectionMapper, never()).insertSkillCodes(
                anyLong(), anyLong(), anyLong(), anyList());
        verify(abilityProjectionMapper, never())
                .countDistinctUsageBySkillCode(anyLong(), anyString());
        verifyNoInteractions(abilityProfileMapper);
    }

    @Test
    void projectEvidenceAssetReinforcesEachConfirmedLinkedSkill() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        skillEvidence("Redis 分布式锁", 1),
                        skillEvidence("微服务网关限流", 1)));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(1L);
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "MICROSERVICE")).thenReturn(3L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_ACCEPTED", null);

        ArgumentCaptor<UserAbilityProfile> captor = ArgumentCaptor.forClass(UserAbilityProfile.class);
        verify(abilityProfileMapper, times(2)).insert(captor.capture());
        assertEquals("REDIS_CACHE", captor.getAllValues().get(0).getSkillCode());
        assertEquals(1, captor.getAllValues().get(0).getEvidenceCount());
        assertEquals("MICROSERVICE", captor.getAllValues().get(1).getSkillCode());
        assertEquals(3, captor.getAllValues().get(1).getEvidenceCount());
    }

    @Test
    void unmappedSkillNameDoesNotPolluteAbilityProjection() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(
                TARGET_JOB_ID,
                "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID,
                "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID,
                TARGET_JOB_ID,
                "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID,
                "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("自定义领域黑科技", 1));

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProjectionMapper, never()).insertSkillCodes(
                anyLong(), anyLong(), anyLong(), anyList());
        verify(abilityProjectionMapper, never())
                .countDistinctUsageBySkillCode(anyLong(), anyString());
        verifyNoInteractions(abilityProfileMapper);
    }

    @Test
    void unconfirmedSkillEvidenceIsNotReinforced() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 0));

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProjectionMapper, never())
                .countDistinctUsageBySkillCode(anyLong(), anyString());
        verifyNoInteractions(abilityProfileMapper);
    }

    @Test
    void skillRenameMovesPersistedContributionAndResetsOldAbilityRow() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Spring Boot 自动配置", 1));
        when(abilityProjectionMapper.selectSkillCodes(RESULT_ID, USER_ID))
                .thenReturn(List.of("REDIS_CACHE"));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE"))
                .thenReturn(0L);
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "SPRING_BOOT")).thenReturn(1L);
        UserAbilityProfile oldRow =
                abilityRow("EVIDENCE_USAGE", "MEDIUM", 1, "UNASSESSED", "正向验证");
        oldRow.setSkillCode("REDIS_CACHE");
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(oldRow)
                .thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProjectionMapper).deleteSkillCodes(
                RESULT_ID, USER_ID, List.of("REDIS_CACHE"));
        verify(abilityProjectionMapper).insertSkillCodes(
                RESULT_ID, USAGE_ID, USER_ID, List.of("SPRING_BOOT"));
        verify(abilityProfileMapper).updateById(oldRow);
        assertEquals(0, oldRow.getEvidenceCount());
        ArgumentCaptor<UserAbilityProfile> created =
                ArgumentCaptor.forClass(UserAbilityProfile.class);
        verify(abilityProfileMapper).insert(created.capture());
        assertEquals("SPRING_BOOT", created.getValue().getSkillCode());
    }

    @Test
    void unavailableSkillEvidenceRemovesPersistedContribution() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID)).thenReturn(null);
        when(abilityProjectionMapper.selectSkillCodes(RESULT_ID, USER_ID))
                .thenReturn(List.of("REDIS_CACHE"));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE"))
                .thenReturn(0L);
        UserAbilityProfile oldRow =
                abilityRow("EVIDENCE_USAGE", "MEDIUM", 1, "UNASSESSED", "正向验证");
        oldRow.setSkillCode("REDIS_CACHE");
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(oldRow);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProjectionMapper).deleteSkillCodes(
                RESULT_ID, USER_ID, List.of("REDIS_CACHE"));
        verify(abilityProfileMapper).updateById(oldRow);
        assertEquals(0, oldRow.getEvidenceCount());
    }

    @Test
    void deletedProjectRemovesPersistedProjectContribution() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(projectEvidenceMapper.selectById(ASSET_ID)).thenReturn(null);
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_EVIDENCE",
                ASSET_ID, "NO_RESPONSE")).thenReturn(0L);
        when(abilityProjectionMapper.selectSkillCodes(RESULT_ID, USER_ID))
                .thenReturn(List.of("REDIS_CACHE"));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE"))
                .thenReturn(0L);
        UserAbilityProfile oldRow =
                abilityRow("EVIDENCE_USAGE", "MEDIUM", 1, "UNASSESSED", "正向验证");
        oldRow.setSkillCode("REDIS_CACHE");
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(oldRow);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProjectionMapper).deleteSkillCodes(
                RESULT_ID, USER_ID, List.of("REDIS_CACHE"));
        verify(abilityProfileMapper).updateById(oldRow);
        assertEquals(0, oldRow.getEvidenceCount());
    }

    @Test
    void assetWithoutSkillIdentityIsNotReinforced() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "RESUME_VERSION", ASSET_ID, "APPLICATION_SUBMISSION"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "RESUME_VERSION", ASSET_ID, "NO_RESPONSE"))
                .thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verifyNoInteractions(abilityProfileMapper, projectSkillEvidenceMapper);
    }

    @Test
    void zeroPositiveCountWithoutRowIsNoop() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "FOLLOW_UP"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(abilityProjectionMapper.selectSkillCodes(RESULT_ID, USER_ID))
                .thenReturn(List.of("REDIS_CACHE"));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(0L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "REPLIED", null);

        verify(abilityProfileMapper, never()).insert(any(UserAbilityProfile.class));
        verify(abilityProfileMapper, never()).updateById(any(UserAbilityProfile.class));
    }

    @Test
    void interviewOwnedRowIsNeverMutated() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(1L);
        UserAbilityProfile interviewRow =
                abilityRow("INTERVIEW_REPORT", "LOW", 5, "WEAK", "面试反馈总结");
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(interviewRow);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_ADVANCED", null);

        verify(abilityProfileMapper, never()).updateById(any(UserAbilityProfile.class));
        verify(abilityProfileMapper, never()).insert(any(UserAbilityProfile.class));
        assertEquals("LOW", interviewRow.getConfidence());
        assertEquals("WEAK", interviewRow.getStatus());
        assertEquals(5, interviewRow.getEvidenceCount());
        assertEquals("面试反馈总结", interviewRow.getSummary());
        assertEquals("INTERVIEW_REPORT", interviewRow.getSourceType());
    }

    @Test
    void interviewOwnedRowWithHighConfidenceIsUntouched() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(2L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(abilityRow("INTERVIEW_REPORT", "HIGH", 7, "STRONG", "高置信"));

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(abilityProfileMapper, never()).updateById(any(UserAbilityProfile.class));
        verify(abilityProfileMapper, never()).insert(any(UserAbilityProfile.class));
    }

    @Test
    void retractionResetsOwnedRowInsteadOfDeleting() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(abilityProjectionMapper.selectSkillCodes(RESULT_ID, USER_ID))
                .thenReturn(List.of("REDIS_CACHE"));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(0L);
        UserAbilityProfile ownedRow =
                abilityRow("EVIDENCE_USAGE", "MEDIUM", 3, "UNASSESSED", "正向验证");
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ownedRow);

        service.afterResultTransition(root("VOID"), "INTERVIEW_ADVANCED", null);

        verify(abilityProfileMapper).updateById(ownedRow);
        assertEquals(0, ownedRow.getEvidenceCount());
        assertEquals("UNKNOWN", ownedRow.getConfidence());
        assertTrue(ownedRow.getSummary().contains("已撤回"));
        assertEquals("EVIDENCE_USAGE", ownedRow.getSourceType());
    }

    @Test
    void retractionLeavesInterviewOwnedRowAlone() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(abilityProjectionMapper.selectSkillCodes(RESULT_ID, USER_ID))
                .thenReturn(List.of("REDIS_CACHE"));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(0L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(abilityRow("INTERVIEW_REPORT", "MEDIUM", 5, "WEAK", "面试反馈总结"));

        service.afterResultTransition(root("VOID"), "OFFER_RECEIVED", null);

        verify(abilityProfileMapper, never()).updateById(any(UserAbilityProfile.class));
        verify(abilityProfileMapper, never()).insert(any(UserAbilityProfile.class));
    }

    @Test
    void duplicateKeyOnInsertFallsBackToUpdatingWinnerRow() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(2L);
        UserAbilityProfile winner =
                abilityRow("EVIDENCE_USAGE", "LOW", 1, "UNASSESSED", "正向验证");
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, winner);
        when(abilityProfileMapper.insert(any(UserAbilityProfile.class)))
                .thenThrow(new DuplicateKeyException("uk_user_ability_profile_user_skill"));

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_ADVANCED", null);

        verify(abilityProfileMapper).updateById(winner);
        assertEquals(2, winner.getEvidenceCount());
        assertEquals("MEDIUM", winner.getConfidence());
    }

    @Test
    void logicallyDeletedAbilityRowIsAtomicallyRestoredBeforeInsert() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(2L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(abilityProfileMapper.restoreDeletedEvidenceUsageProfile(
                any(UserAbilityProfile.class))).thenReturn(1);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_ADVANCED", null);

        ArgumentCaptor<UserAbilityProfile> restored =
                ArgumentCaptor.forClass(UserAbilityProfile.class);
        verify(abilityProfileMapper)
                .restoreDeletedEvidenceUsageProfile(restored.capture());
        verify(abilityProfileMapper, never()).insert(any(UserAbilityProfile.class));
        assertEquals(USER_ID, restored.getValue().getUserId());
        assertEquals("REDIS_CACHE", restored.getValue().getSkillCode());
        assertEquals("UNASSESSED", restored.getValue().getStatus());
        assertEquals(2, restored.getValue().getEvidenceCount());
        assertEquals("MEDIUM", restored.getValue().getConfidence());
        assertEquals("EVIDENCE_USAGE", restored.getValue().getSourceType());
    }

    @Test
    void duplicateKeyRaceRetriesRestoreWhenDeletedRowHasNoActiveWinner() {
        gateOn();
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE",
                SKILL_EVIDENCE_ID, "NO_RESPONSE")).thenReturn(0L);
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(2L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn((UserAbilityProfile) null)
                .thenReturn((UserAbilityProfile) null);
        when(abilityProfileMapper.restoreDeletedEvidenceUsageProfile(
                any(UserAbilityProfile.class))).thenReturn(0, 1);
        when(abilityProfileMapper.insert(any(UserAbilityProfile.class)))
                .thenThrow(new DuplicateKeyException("uk_user_ability_profile_user_skill"));

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_ADVANCED", null);

        verify(abilityProfileMapper, times(2))
                .restoreDeletedEvidenceUsageProfile(any(UserAbilityProfile.class));
        verify(abilityProfileMapper).insert(any(UserAbilityProfile.class));
        verify(abilityProfileMapper, never()).updateById(any(UserAbilityProfile.class));
    }

    @Test
    void reinforcementRunsIndependentlyOfV12Gate() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(false);
        reinforcementGateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_SKILL_EVIDENCE", SKILL_EVIDENCE_ID, "INTERVIEW"));
        when(projectSkillEvidenceMapper.selectById(SKILL_EVIDENCE_ID))
                .thenReturn(skillEvidence("Redis 分布式锁", 1));
        when(abilityProjectionMapper.countDistinctUsageBySkillCode(
                USER_ID, "REDIS_CACHE")).thenReturn(1L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_ADVANCED", null);

        verify(abilityProfileMapper).insert(any(UserAbilityProfile.class));
        verifyNoInteractions(gapItemMapper, skillProfileService);
    }

    @Test
    void propagatesMapperFailuresSoTheProjectionTransactionCanRollBack() {
        gateOn();
        when(usageMapper.selectOwned(USAGE_ID, USER_ID))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThrows(RuntimeException.class, () -> service.afterResultTransition(
                root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null));
    }

    private void gateOn() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(true);
    }

    private void reinforcementGateOn() {
        when(v13FeatureGate.isPositiveAbilityReinforcement()).thenReturn(true);
        lenient().when(projectEvidenceMapper.selectById(ASSET_ID))
                .thenReturn(activeProject());
        lenient().when(abilitySkillNodeMapper.selectEnabledForEvidenceMapping())
                .thenReturn(productionAbilityNodes());
    }

    private List<AbilitySkillNode> productionAbilityNodes() {
        return List.of(
                abilityNode("JAVA_CORE", "Java 基础", "JAVA_CORE", "Java 基础"),
                abilityNode("COLLECTION_HASHMAP", "集合", "COLLECTION", "集合"),
                abilityNode("JUC_THREAD_POOL", "并发", "CONCURRENCY", "并发"),
                abilityNode("JVM_MEMORY_GC", "JVM", "JVM", "JVM"),
                abilityNode("MYSQL_INDEX_TX", "MySQL", "MYSQL", "MySQL"),
                abilityNode("REDIS_CACHE", "Redis", "REDIS", "Redis"),
                abilityNode(
                        "SPRING_BOOT",
                        "Spring / Spring Boot",
                        "SPRING",
                        "Spring / Spring Boot"),
                abilityNode("MYBATIS_ORM", "MyBatis", "MYBATIS", "MyBatis"),
                abilityNode("MICROSERVICE", "微服务", "MICROSERVICE", "微服务"),
                abilityNode("MESSAGE_QUEUE", "消息队列", "MESSAGE_QUEUE", "消息队列"),
                abilityNode(
                        "DISTRIBUTED_SYSTEM",
                        "分布式",
                        "DISTRIBUTED",
                        "分布式"),
                abilityNode("SYSTEM_DESIGN", "系统设计", "SYSTEM_DESIGN", "系统设计"),
                abilityNode(
                        "PROJECT_EXPRESSION",
                        "项目表达",
                        "PROJECT_EXPRESSION",
                        "项目表达"),
                abilityNode(
                        "ENGINEERING_PRACTICE",
                        "工程实践",
                        "ENGINEERING",
                        "工程实践"));
    }

    private AbilitySkillNode abilityNode(
            String code, String name, String domainCode, String domainName) {
        AbilitySkillNode node = new AbilitySkillNode();
        node.setCode(code);
        node.setName(name);
        node.setDomainCode(domainCode);
        node.setDomainName(domainName);
        node.setEnabled(1);
        return node;
    }

    private ProjectSkillEvidence skillEvidence(String skillName, Integer confirmed) {
        ProjectSkillEvidence evidence = new ProjectSkillEvidence();
        evidence.setId(SKILL_EVIDENCE_ID);
        evidence.setUserId(USER_ID);
        evidence.setProjectEvidenceId(ASSET_ID);
        evidence.setSkillName(skillName);
        evidence.setConfirmed(confirmed);
        return evidence;
    }

    private ProjectEvidence activeProject() {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(ASSET_ID);
        project.setUserId(USER_ID);
        project.setDeleted(0);
        return project;
    }

    private UserAbilityProfile abilityRow(String sourceType, String confidence,
                                          Integer evidenceCount, String status, String summary) {
        UserAbilityProfile row = new UserAbilityProfile();
        row.setId(66L);
        row.setUserId(USER_ID);
        row.setSkillCode("REDIS_CACHE");
        row.setSourceType(sourceType);
        row.setConfidence(confidence);
        row.setEvidenceCount(evidenceCount);
        row.setStatus(status);
        row.setSummary(summary);
        return row;
    }

    private void stubUsage(CareerEvidenceUsage usage) {
        when(usageMapper.selectOwned(USAGE_ID, USER_ID)).thenReturn(usage);
    }

    private CareerEvidenceUsageResult root(String status) {
        CareerEvidenceUsageResult root = new CareerEvidenceUsageResult();
        root.setId(RESULT_ID);
        root.setUserId(USER_ID);
        root.setUsageId(USAGE_ID);
        root.setStatus(status);
        return root;
    }

    private CareerEvidenceUsage usage(Long targetJobId, String assetType, Long assetId,
                                      String scene) {
        CareerEvidenceUsage usage = new CareerEvidenceUsage();
        usage.setId(USAGE_ID);
        usage.setUserId(USER_ID);
        usage.setTargetJobId(targetJobId);
        usage.setAssetType(assetType);
        usage.setAssetId(assetId);
        usage.setUsageScene(scene);
        return usage;
    }

    private SkillProfile profile() {
        SkillProfile profile = new SkillProfile();
        profile.setId(PROFILE_ID);
        profile.setUserId(USER_ID);
        return profile;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
