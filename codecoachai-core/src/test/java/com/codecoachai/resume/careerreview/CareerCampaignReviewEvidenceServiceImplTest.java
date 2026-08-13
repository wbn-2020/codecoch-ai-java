package com.codecoachai.resume.careerreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.careercontact.mapper.CareerActivityMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewProcessMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundMapper;
import com.codecoachai.resume.careercampaign.CareerCampaign;
import com.codecoachai.resume.careercampaign.CareerCampaignEventMapper;
import com.codecoachai.resume.careercampaign.CareerCampaignMapper;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileService;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferMapper;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotMapper;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerCampaignReviewEvidenceServiceImplTest {

    @Mock
    private CareerCampaignMapper campaignMapper;
    @Mock
    private CareerCampaignEventMapper campaignEventMapper;
    @Mock
    private CareerCampaignOperatingProfileService operatingProfileService;
    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private JobApplicationEventMapper applicationEventMapper;
    @Mock
    private CareerCalendarEventMapper calendarMapper;
    @Mock
    private CareerInterviewProcessMapper interviewProcessMapper;
    @Mock
    private CareerInterviewRoundMapper interviewRoundMapper;
    @Mock
    private CareerOfferMapper offerMapper;
    @Mock
    private CareerActivityMapper activityMapper;
    @Mock
    private CareerResearchSnapshotMapper researchSnapshotMapper;
    @Mock
    private JobApplicationPackageMapper packageMapper;

    @BeforeEach
    void setUpOperatingProfile() {
        when(operatingProfileService.getForUser(any(), any()))
                .thenAnswer(invocation -> CareerCampaignOperatingProfileModels.conservativeDefaults(
                        invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void completedAtIsAuthoritativeAndClientCutoffIsIgnored() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        CareerCampaign campaign = new CareerCampaign();
        campaign.setStatus("COMPLETED");
        campaign.setCompletedAt(completedAt);
        campaign.setUpdatedAt(completedAt.plusHours(5));
        campaign.setName("Campaign");
        when(campaignMapper.selectOne(any())).thenReturn(campaign);
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(offerMapper.selectList(any())).thenReturn(List.of());

        CareerCampaignReviewEvidenceServiceImpl service = service();
        CareerCampaignReviewEvidenceVO result = service.get(
                9L, 20L, LocalDateTime.of(1999, 1, 1, 0, 0));

        assertEquals(completedAt, result.getDataCutoffAt());
        assertEquals(CareerCampaignReviewEvidenceVO.EVIDENCE_SCHEMA_VERSION,
                result.getEvidenceSchemaVersion());
        assertNotNull(result.getEvidenceHash());
        assertEquals(8, result.getSources().size());
        assertTrue(result.getSources().stream()
                .allMatch(source -> Integer.valueOf(1).equals(source.getSourceVersion())));
    }

    @Test
    void sameServerEvidenceHasStableHashAcrossDifferentClientCutoffs() {
        CareerCampaign campaign = new CareerCampaign();
        campaign.setStatus("COMPLETED");
        campaign.setCompletedAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        campaign.setUpdatedAt(LocalDateTime.of(2026, 7, 20, 13, 0));
        when(campaignMapper.selectOne(any())).thenReturn(campaign);
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(offerMapper.selectList(any())).thenReturn(List.of());

        CareerCampaignReviewEvidenceServiceImpl service = service();
        CareerCampaignReviewEvidenceVO first =
                service.get(9L, 20L, LocalDateTime.of(2026, 7, 20, 1, 0));
        CareerCampaignReviewEvidenceVO second =
                service.get(9L, 20L, LocalDateTime.of(2026, 7, 21, 1, 0));

        assertEquals(first.getEvidenceHash(), second.getEvidenceHash());
        assertEquals(first.getDataCutoffAt(), second.getDataCutoffAt());
    }

    @Test
    void declinedApplicationCountsAsClosedForCampaignEvidence() {
        CareerCampaign campaign = new CareerCampaign();
        campaign.setStatus("COMPLETED");
        campaign.setCompletedAt(LocalDateTime.of(2026, 7, 20, 12, 0));
        campaign.setUpdatedAt(LocalDateTime.of(2026, 7, 20, 13, 0));
        JobApplication application = new JobApplication();
        application.setId(31L);
        application.setStatus("DECLINED");
        application.setUpdatedAt(LocalDateTime.of(2026, 7, 20, 11, 0));
        when(campaignMapper.selectOne(any())).thenReturn(campaign);
        when(applicationMapper.selectList(any())).thenReturn(List.of(application));
        when(interviewProcessMapper.selectList(any())).thenReturn(List.of());
        when(offerMapper.selectList(any())).thenReturn(List.of());
        when(activityMapper.selectList(any())).thenReturn(List.of());
        when(researchSnapshotMapper.selectList(any())).thenReturn(List.of());

        CareerCampaignReviewEvidenceVO result = service().get(9L, 20L, null);

        assertTrue(Boolean.TRUE.equals(result.getAllOpportunitiesClosed()));
    }

    @Test
    void activeCampaignUsesCurrentServerCutoffInsteadOfCampaignUpdatedAt() {
        LocalDateTime oldCampaignUpdate = LocalDateTime.of(2026, 7, 1, 9, 0);
        CareerCampaign campaign = new CareerCampaign();
        campaign.setStatus("ACTIVE");
        campaign.setUpdatedAt(oldCampaignUpdate);
        when(campaignMapper.selectOne(any())).thenReturn(campaign);
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(offerMapper.selectList(any())).thenReturn(List.of());

        CareerCampaignReviewEvidenceVO result = service().get(
                9L, 20L, LocalDateTime.of(1999, 1, 1, 0, 0));

        assertTrue(result.getDataCutoffAt().isAfter(oldCampaignUpdate));
    }

    @Test
    void archivedApplicationsAreExcludedFromFreshCampaignEvidence() {
        CareerCampaign campaign = new CareerCampaign();
        campaign.setStatus("COMPLETED");
        campaign.setCompletedAt(LocalDateTime.of(2026, 8, 12, 12, 0));
        when(campaignMapper.selectOne(any())).thenReturn(campaign);
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(offerMapper.selectList(any())).thenReturn(List.of());

        service().get(9L, 20L, null);

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<JobApplication>> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(applicationMapper).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("archived_at IS NULL"));
    }

    private CareerCampaignReviewEvidenceServiceImpl service() {
        return new CareerCampaignReviewEvidenceServiceImpl(
                campaignMapper, campaignEventMapper, operatingProfileService,
                applicationMapper, applicationEventMapper, calendarMapper,
                interviewProcessMapper, interviewRoundMapper, offerMapper,
                activityMapper, researchSnapshotMapper, packageMapper);
    }
}
