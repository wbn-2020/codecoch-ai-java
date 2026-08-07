package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.resume.domain.dto.SkillProfileRefreshDTO;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.SkillGapItem;
import com.codecoachai.resume.domain.entity.SkillProfile;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.enums.SkillProfileStatus;
import com.codecoachai.resume.domain.vo.InnerSkillGapAgentContextVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.SkillGapItemMapper;
import com.codecoachai.resume.mapper.SkillProfileMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.support.ResumeJobMatchTrustPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SkillProfileServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long TARGET_JOB_ID = 88L;
    private static final Long INTERVIEW_ID = 501L;
    private static final Long REPORT_ID = 601L;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper projectMapper;
    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private ResumeJobMatchReportMapper reportMapper;
    @Mock
    private ResumeJobMatchDetailMapper detailMapper;
    @Mock
    private SkillProfileMapper profileMapper;
    @Mock
    private SkillGapItemMapper gapItemMapper;
    @Mock
    private UserAbilityProfileMapper abilityProfileMapper;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SkillProfileServiceImpl service;

    @BeforeAll
    static void initTables() {
        initTableInfo(SkillProfile.class);
        initTableInfo(SkillGapItem.class);
        initTableInfo(UserAbilityProfile.class);
        initTableInfo(ResumeJobMatchReport.class);
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("skill-profile-test")
                .build());
        ObjectMapper objectMapper = new ObjectMapper();
        service = new SkillProfileServiceImpl(
                resumeMapper, projectMapper, analysisRecordMapper, targetJobMapper,
                jobDescriptionAnalysisMapper, reportMapper, detailMapper, profileMapper,
                gapItemMapper, abilityProfileMapper, aiFeignClient, objectMapper,
                transactionTemplate, new ResumeJobMatchTrustPolicy(objectMapper));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void unanchoredFeedbackCreatesProfileWithNullableMatchReportId() {
        stubOwnedTargetJob();
        when(profileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(profileMapper.insert(any(SkillProfile.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SkillProfile.class).setId(999L);
            return 1;
        });
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.feedbackInterviewWeakPoints(feedback(null, null, List.of("系统设计表达不清")));

        ArgumentCaptor<SkillProfile> captor = ArgumentCaptor.forClass(SkillProfile.class);
        verify(profileMapper).insert(captor.capture());
        SkillProfile created = captor.getValue();
        assertEquals(null, created.getMatchReportId());
        assertEquals(USER_ID, created.getUserId());
        assertEquals("INTERVIEW_REPORT", created.getSourceType());

        ArgumentCaptor<SkillGapItem> gapCaptor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper).insert(gapCaptor.capture());
        SkillGapItem gap = gapCaptor.getValue();
        assertEquals(999L, gap.getProfileId());
        assertEquals("INTERVIEW_FEEDBACK", gap.getCategory());
        assertEquals("INTERVIEW_REPORT", gap.getSourceType());
        assertEquals(INTERVIEW_ID, gap.getSourceBizId());
    }

    @Test
    void providedMatchReportIdIsKeptOnCreatedProfile() {
        stubOwnedTargetJob();
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(profileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(profileMapper.insert(any(SkillProfile.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SkillProfile.class).setId(999L);
            return 1;
        });
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.feedbackInterviewWeakPoints(feedback(null, 777L, List.of("弱项")));

        ArgumentCaptor<SkillProfile> captor = ArgumentCaptor.forClass(SkillProfile.class);
        verify(profileMapper).insert(captor.capture());
        assertEquals(777L, captor.getValue().getMatchReportId());
    }

    @Test
    void boundProfileIdResolvesWithoutCreating() {
        stubOwnedTargetJob();
        SkillProfile bound = new SkillProfile();
        bound.setId(70L);
        bound.setUserId(USER_ID);
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bound);
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        service.feedbackInterviewWeakPoints(feedback(70L, null, List.of("弱项A", "弱项B")));

        verify(profileMapper, never()).insert(any(SkillProfile.class));
        ArgumentCaptor<SkillGapItem> gapCaptor = ArgumentCaptor.forClass(SkillGapItem.class);
        verify(gapItemMapper, times(2)).insert(gapCaptor.capture());
        assertEquals(70L, gapCaptor.getAllValues().get(0).getProfileId());
        assertEquals(3, gapCaptor.getAllValues().get(0).getPriority());
        assertEquals(4, gapCaptor.getAllValues().get(1).getPriority());
    }

    @Test
    void abilityUpdatesFallBackToSkillNameAsSkillCode() {
        stubOwnedTargetJob();
        SkillProfile bound = new SkillProfile();
        bound.setId(70L);
        bound.setUserId(USER_ID);
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bound);
        when(gapItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(abilityProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        InterviewWeakPointFeedbackDTO dto = feedback(70L, null, List.of("弱项"));
        dto.setAbilityProfileUpdatesJson("[{\"skillName\":\"Redis 缓存\",\"status\":\"WEAK\"}]");
        service.feedbackInterviewWeakPoints(dto);

        ArgumentCaptor<UserAbilityProfile> captor = ArgumentCaptor.forClass(UserAbilityProfile.class);
        verify(abilityProfileMapper).insert(captor.capture());
        assertEquals("Redis 缓存", captor.getValue().getSkillCode());
        assertEquals("WEAK", captor.getValue().getStatus());
        assertEquals("INTERVIEW_REPORT", captor.getValue().getSourceType());
    }

    @Test
    void incompleteFeedbackIsRejected() {
        assertThrows(BusinessException.class, () -> service.feedbackInterviewWeakPoints(null));
        assertThrows(BusinessException.class,
                () -> service.feedbackInterviewWeakPoints(feedback(null, null, List.of())));
    }

    @Test
    void partialMatchReportsDoNotPassInnerProfileGate() {
        ResumeJobMatchReport warningReport = trustedMatchReport("""
                {
                  "trustStatus": "PARTIAL",
                  "fallback": false,
                  "schemaWarnings": [
                    {"field":"evidenceBoundary","message":"unsupported evidence removed"}
                  ]
                }
                """);
        ResumeJobMatchReport rawPartialReport = trustedMatchReport("""
                {"trustStatus":"PARTIAL","fallback":false,"schemaWarnings":[]}
                """);
        when(reportMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(warningReport, rawPartialReport);

        assertNull(service.getInnerSuccessProfileByMatchReport(REPORT_ID));
        assertNull(service.getInnerSuccessProfileByMatchReport(REPORT_ID));
        verify(profileMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void verifiedMatchReportPassesInnerProfileGate() {
        ResumeJobMatchReport report = trustedMatchReport("""
                {"trustStatus":"VERIFIED","fallback":false,"schemaWarnings":[]}
                """);
        SkillProfile profile = resumeMatchProfile();
        when(reportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(report);
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(profile)
                .thenReturn(null);
        when(gapItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = service.getInnerSuccessProfileByMatchReport(REPORT_ID);

        assertNotNull(result);
        assertEquals(profile.getId(), result.getProfileId());
        assertEquals(REPORT_ID, result.getMatchReportId());
    }

    @Test
    void agentContextGapsAreEmptyWithoutTrustedProfile() {
        when(profileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertEquals(List.of(), service.listAgentContextGaps(USER_ID, TARGET_JOB_ID));
        assertEquals(List.of(), service.listAgentContextGaps(null, TARGET_JOB_ID));
        assertEquals(List.of(), service.listAgentContextGaps(USER_ID, null));
    }

    @Test
    void agentContextGapsSortBeforeLimitBySeverityPriorityAndUpdateTime() {
        when(profileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(trustedProfile()));
        List<SkillGapItem> items = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2026, 7, 27, 12, 0);
        for (long id = 1; id <= 40; id++) {
            SkillGapItem low = gapItem(id, "LOW", "低危缺口" + id);
            low.setPriority(1);
            low.setUpdatedAt(now.plusMinutes(id));
            items.add(low);
        }
        SkillGapItem priorityTwo = gapItem(100L, "HIGH", "高危优先级二");
        priorityTwo.setPriority(2);
        priorityTwo.setUpdatedAt(now.minusDays(3));
        items.add(priorityTwo);
        SkillGapItem olderPriorityOne = gapItem(101L, "HIGH", "高危较早");
        olderPriorityOne.setPriority(1);
        olderPriorityOne.setUpdatedAt(now.minusDays(2));
        items.add(olderPriorityOne);
        SkillGapItem newerPriorityOne = gapItem(102L, "HIGH", "高危较新");
        newerPriorityOne.setPriority(1);
        newerPriorityOne.setUpdatedAt(now.minusDays(1));
        items.add(newerPriorityOne);
        when(gapItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(items);

        List<InnerSkillGapAgentContextVO> gaps = service.listAgentContextGaps(USER_ID, TARGET_JOB_ID);

        assertEquals(8, gaps.size());
        assertEquals(List.of(102L, 101L, 100L),
                gaps.subList(0, 3).stream()
                        .map(InnerSkillGapAgentContextVO::getId)
                        .toList());
    }

    @Test
    void agentContextGapMapsFieldsAndParsesRecommendedActions() {
        when(profileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(trustedProfile()));
        SkillGapItem item = gapItem(5L, "MEDIUM", "Redis 分布式锁");
        item.setRecommendedActionsJson("[\"复盘讲述结构\",\"表达训练\"]");
        item.setGapDescription("长描述".repeat(120));
        when(gapItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        List<InnerSkillGapAgentContextVO> gaps = service.listAgentContextGaps(USER_ID, TARGET_JOB_ID);

        assertEquals(1, gaps.size());
        InnerSkillGapAgentContextVO vo = gaps.get(0);
        assertEquals(5L, vo.getId());
        assertEquals("Redis 分布式锁", vo.getSkillName());
        assertEquals("EVIDENCE_USAGE_FEEDBACK", vo.getCategory());
        assertEquals("MEDIUM", vo.getSeverity());
        assertEquals("EVIDENCE_USAGE_RESULT", vo.getSourceType());
        assertEquals(List.of("复盘讲述结构", "表达训练"), vo.getRecommendedActions());
        assertEquals(300, vo.getGapDescription().length());
    }

    @Test
    void refreshRejectsEvidenceOverlayWithoutMatchReport() {
        SkillProfile overlay = trustedProfile();
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(overlay);
        SkillProfileRefreshDTO request = new SkillProfileRefreshDTO();
        request.setProfileId(overlay.getId());

        assertThrows(BusinessException.class, () -> service.refresh(request));
    }

    @Test
    void detailMergesOverlayGapsAndMapsThemToTheReturnedProfile() {
        TargetJob job = new TargetJob();
        job.setId(TARGET_JOB_ID);
        job.setUserId(USER_ID);
        when(targetJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        SkillProfile base = trustedProfile();
        base.setSourceType("INTERVIEW_REPORT");
        base.setId(70L);
        SkillProfile overlay = trustedProfile();
        overlay.setId(80L);
        when(profileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(base));
        when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(overlay);
        SkillGapItem baseGap = gapItem(1L, "MEDIUM", "基础短板");
        baseGap.setProfileId(70L);
        SkillGapItem overlayGap = gapItem(2L, "HIGH", "实战反馈短板");
        overlayGap.setProfileId(80L);
        when(gapItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(baseGap), List.of(overlayGap));

        var detail = service.getByTargetJob(TARGET_JOB_ID);

        assertEquals(2, detail.getGapItems().size());
        assertTrue(detail.getGapItems().stream()
                .allMatch(gap -> base.getId().equals(gap.getProfileId())));
        assertTrue(detail.getGapItems().stream()
                .anyMatch(gap -> "实战反馈短板".equals(gap.getSkillName())));
    }

    private SkillProfile trustedProfile() {
        SkillProfile profile = new SkillProfile();
        profile.setId(70L);
        profile.setUserId(USER_ID);
        profile.setTargetJobId(TARGET_JOB_ID);
        profile.setSourceType("EVIDENCE_USAGE");
        return profile;
    }

    private SkillProfile resumeMatchProfile() {
        SkillProfile profile = new SkillProfile();
        profile.setId(71L);
        profile.setUserId(USER_ID);
        profile.setTargetJobId(TARGET_JOB_ID);
        profile.setMatchReportId(REPORT_ID);
        profile.setProfileName("Trusted match profile");
        profile.setSourceType("RESUME_JOB_MATCH");
        profile.setStatus(SkillProfileStatus.SUCCESS.getCode());
        return profile;
    }

    private ResumeJobMatchReport trustedMatchReport(String rawResultJson) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setId(REPORT_ID);
        report.setUserId(USER_ID);
        report.setResumeId(33L);
        report.setTargetJobId(TARGET_JOB_ID);
        report.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        report.setOverallScore(82);
        report.setAiCallLogId(44L);
        report.setSummary("Grounded match result");
        report.setRawResultJson(rawResultJson);
        return report;
    }

    private SkillGapItem gapItem(Long id, String severity, String skillName) {
        SkillGapItem item = new SkillGapItem();
        item.setId(id);
        item.setProfileId(70L);
        item.setUserId(USER_ID);
        item.setTargetJobId(TARGET_JOB_ID);
        item.setSkillName(skillName);
        item.setCategory("EVIDENCE_USAGE_FEEDBACK");
        item.setSeverity(severity);
        item.setGapLevel(2);
        item.setConfidence(new BigDecimal("0.60"));
        item.setGapDescription("描述");
        item.setSourceType("EVIDENCE_USAGE_RESULT");
        return item;
    }

    private void stubOwnedTargetJob() {
        TargetJob job = new TargetJob();
        job.setId(TARGET_JOB_ID);
        job.setUserId(USER_ID);
        when(targetJobMapper.selectById(TARGET_JOB_ID)).thenReturn(job);
    }

    private InterviewWeakPointFeedbackDTO feedback(Long skillProfileId, Long matchReportId,
                                                   List<String> weakPoints) {
        InterviewWeakPointFeedbackDTO dto = new InterviewWeakPointFeedbackDTO();
        dto.setUserId(USER_ID);
        dto.setTargetJobId(TARGET_JOB_ID);
        dto.setSkillProfileId(skillProfileId);
        dto.setMatchReportId(matchReportId);
        dto.setInterviewId(INTERVIEW_ID);
        dto.setReportId(REPORT_ID);
        dto.setWeakPoints(weakPoints);
        return dto;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
