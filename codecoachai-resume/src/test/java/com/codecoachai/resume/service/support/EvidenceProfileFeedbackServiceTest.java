package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.config.V12FeatureGate;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsage;
import com.codecoachai.resume.domain.entity.CareerEvidenceUsageResult;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.mapper.CareerEvidenceUsageMapper;
import com.codecoachai.resume.mapper.CareerEvidenceUsageResultMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.service.SkillProfileService;
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

@ExtendWith(MockitoExtension.class)
class EvidenceProfileFeedbackServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long USAGE_ID = 91L;
    private static final Long RESULT_ID = 101L;
    private static final Long TARGET_JOB_ID = 88L;
    private static final Long ASSET_ID = 31L;
    private static final Long PROFILE_ID = 555L;

    @Mock
    private V12FeatureGate featureGate;
    @Mock
    private CareerEvidenceUsageMapper usageMapper;
    @Mock
    private CareerEvidenceUsageResultMapper resultMapper;
    @Mock
    private SkillGapItemMapper gapItemMapper;
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
    }

    @BeforeEach
    void setUp() {
        service = new EvidenceProfileFeedbackService(
                featureGate, usageMapper, resultMapper, gapItemMapper,
                projectEvidenceMapper, projectSkillEvidenceMapper,
                skillProfileService, new ObjectMapper());
    }

    @Test
    void skipsEntirelyWhenGateDisabled() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(false);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verifyNoInteractions(usageMapper, gapItemMapper, skillProfileService);
    }

    @Test
    void missingUsageIsNoop() {
        gateOn();
        when(usageMapper.selectOwned(USAGE_ID, USER_ID)).thenReturn(null);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verifyNoInteractions(gapItemMapper, skillProfileService);
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
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);
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
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verify(gapItemMapper).updateById(existing);
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
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
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

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
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

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
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(2L);

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
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(3L);
        when(resultMapper.selectTrustedOutcomeUsageIds(
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE"))
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
    void noResponseDroppedBelowThresholdDeletesPatternGap() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "APPLICATION_SUBMISSION"));
        SkillGapItem patternGap = new SkillGapItem();
        patternGap.setId(88L);
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, patternGap);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(2L);

        service.afterResultTransition(root("VOID"), "NO_RESPONSE", null);

        verify(gapItemMapper).deleteById(88L);
        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
    }

    @Test
    void interviewNotAdvancedWithoutTargetJobWritesNothing() {
        gateOn();
        stubUsage(usage(null, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void patternGapWithoutTargetJobIsNotCreated() {
        gateOn();
        stubUsage(usage(null, "PROJECT_EVIDENCE", ASSET_ID, "APPLICATION_SUBMISSION"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ProjectEvidence evidence = new ProjectEvidence();
        evidence.setTitle("网关限流改造");
        when(projectEvidenceMapper.selectById(ASSET_ID)).thenReturn(evidence);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(3L);
        when(resultMapper.selectTrustedOutcomeUsageIds(
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE"))
                .thenReturn(List.of(91L, 92L, 93L));

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
                USER_ID, "PROJECT_SKILL_EVIDENCE", 41L, "NO_RESPONSE")).thenReturn(0L);
        when(skillProfileService.resolveEvidenceFeedbackProfile(USER_ID, TARGET_JOB_ID))
                .thenReturn(profile());
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null);

        ArgumentCaptor<SkillGapItem> captor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper).insert(captor.capture());
        assertEquals("Redis 分布式锁", captor.getValue().getSkillName());
    }

    @Test
    void positiveOutcomesWriteNothing() {
        gateOn();
        stubUsage(usage(TARGET_JOB_ID, "PROJECT_EVIDENCE", ASSET_ID, "INTERVIEW"));
        when(gapItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resultMapper.countTrustedOutcomeByAsset(
                USER_ID, "PROJECT_EVIDENCE", ASSET_ID, "NO_RESPONSE")).thenReturn(0L);

        service.afterResultTransition(root("CONFIRMED"), "OFFER_RECEIVED", null);

        verify(gapItemMapper, never()).insert(any(SkillGapItem.class));
        verifyNoInteractions(skillProfileService);
    }

    @Test
    void neverPropagatesMapperFailures() {
        gateOn();
        when(usageMapper.selectOwned(USAGE_ID, USER_ID))
                .thenThrow(new RuntimeException("db unavailable"));

        assertDoesNotThrow(() -> service.afterResultTransition(
                root("CONFIRMED"), "INTERVIEW_NOT_ADVANCED", null));
    }

    private void gateOn() {
        when(featureGate.isEvidenceProfileFeedback()).thenReturn(true);
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
