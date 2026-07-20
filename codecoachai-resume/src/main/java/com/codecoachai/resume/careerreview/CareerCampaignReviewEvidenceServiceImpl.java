package com.codecoachai.resume.careerreview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.careercampaign.CareerCampaign;
import com.codecoachai.resume.careercampaign.CareerCampaignMapper;
import com.codecoachai.resume.careercontact.entity.CareerActivity;
import com.codecoachai.resume.careercontact.mapper.CareerActivityMapper;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewProcess;
import com.codecoachai.resume.careerinterview.entity.CareerInterviewRound;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewProcessMapper;
import com.codecoachai.resume.careerinterview.mapper.CareerInterviewRoundMapper;
import com.codecoachai.resume.careeroffer.entity.CareerOffer;
import com.codecoachai.resume.careeroffer.mapper.CareerOfferMapper;
import com.codecoachai.resume.careerresearch.entity.CareerResearchSnapshot;
import com.codecoachai.resume.careerresearch.mapper.CareerResearchSnapshotMapper;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CareerCampaignReviewEvidenceServiceImpl implements CareerCampaignReviewEvidenceService {

    private static final Set<String> FINAL_APPLICATION_STATUSES =
            Set.of("ACCEPTED", "REJECTED", "CLOSED", "WITHDRAWN");

    private final CareerCampaignMapper campaignMapper;
    private final JobApplicationMapper applicationMapper;
    private final CareerInterviewProcessMapper interviewProcessMapper;
    private final CareerInterviewRoundMapper interviewRoundMapper;
    private final CareerOfferMapper offerMapper;
    private final CareerActivityMapper activityMapper;
    private final CareerResearchSnapshotMapper researchSnapshotMapper;

    @Override
    public CareerCampaignReviewEvidenceVO get(Long userId, Long campaignId, LocalDateTime dataCutoffAt) {
        if (userId == null || campaignId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 和 campaignId 不能为空");
        }
        LocalDateTime cutoff = dataCutoffAt == null ? LocalDateTime.now() : dataCutoffAt;
        CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期不存在");
        }

        List<JobApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getCampaignId, campaignId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .le(JobApplication::getUpdatedAt, cutoff)
                .orderByAsc(JobApplication::getId));
        List<Long> applicationIds = applications.stream()
                .map(JobApplication::getId)
                .filter(Objects::nonNull)
                .toList();

        List<CareerInterviewProcess> processes = applicationIds.isEmpty()
                ? List.of()
                : interviewProcessMapper.selectList(new LambdaQueryWrapper<CareerInterviewProcess>()
                        .eq(CareerInterviewProcess::getUserId, userId)
                        .in(CareerInterviewProcess::getApplicationId, applicationIds)
                        .eq(CareerInterviewProcess::getDeleted, CommonConstants.NO)
                        .le(CareerInterviewProcess::getUpdatedAt, cutoff));
        List<Long> processIds = processes.stream()
                .map(CareerInterviewProcess::getId)
                .filter(Objects::nonNull)
                .toList();
        List<CareerInterviewRound> rounds = processIds.isEmpty()
                ? List.of()
                : interviewRoundMapper.selectList(new LambdaQueryWrapper<CareerInterviewRound>()
                        .in(CareerInterviewRound::getProcessId, processIds)
                        .eq(CareerInterviewRound::getDeleted, CommonConstants.NO)
                        .le(CareerInterviewRound::getUpdatedAt, cutoff));

        List<CareerOffer> offers = offerMapper.selectList(new LambdaQueryWrapper<CareerOffer>()
                .eq(CareerOffer::getUserId, userId)
                .eq(CareerOffer::getDeleted, CommonConstants.NO)
                .in(!applicationIds.isEmpty(), CareerOffer::getApplicationId, applicationIds)
                .eq(applicationIds.isEmpty(), CareerOffer::getApplicationId, -1L)
                .le(CareerOffer::getUpdatedAt, cutoff));
        List<CareerActivity> activities = applicationIds.isEmpty()
                ? List.of()
                : activityMapper.selectList(new LambdaQueryWrapper<CareerActivity>()
                        .eq(CareerActivity::getUserId, userId)
                        .in(CareerActivity::getApplicationId, applicationIds)
                        .eq(CareerActivity::getDeleted, CommonConstants.NO)
                        .le(CareerActivity::getUpdatedAt, cutoff));
        List<CareerResearchSnapshot> researchSnapshots = applicationIds.isEmpty()
                ? List.of()
                : researchSnapshotMapper.selectList(new LambdaQueryWrapper<CareerResearchSnapshot>()
                        .eq(CareerResearchSnapshot::getUserId, userId)
                        .in(CareerResearchSnapshot::getApplicationId, applicationIds)
                        .eq(CareerResearchSnapshot::getDeleted, CommonConstants.NO)
                        .le(CareerResearchSnapshot::getUpdatedAt, cutoff));

        String sourceRef = "CAREER_CAMPAIGN:" + campaignId;
        CareerCampaignReviewEvidenceVO result = new CareerCampaignReviewEvidenceVO();
        result.setUserId(userId);
        result.setCampaignId(campaignId);
        result.setCampaignStatus(campaign.getStatus());
        result.setCampaignTitle(campaign.getName());
        result.setCompleted("COMPLETED".equalsIgnoreCase(campaign.getStatus()));
        result.setAllOpportunitiesClosed(applications.stream()
                .allMatch(application -> FINAL_APPLICATION_STATUSES.contains(normalize(application.getStatus()))));
        result.setSampleSize(applications.size());
        result.setDataCutoffAt(cutoff);

        addFact(result, "application.count", "机会数量", applications.size(), sourceRef);
        Map<String, Long> statusCounts = applications.stream()
                .collect(Collectors.groupingBy(
                        application -> normalize(application.getStatus()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        statusCounts.forEach((status, count) ->
                addFact(result, "application.status." + status, "机会状态：" + status, count, sourceRef));
        addFact(result, "interview.process.count", "真实面试流程数量", processes.size(), sourceRef);
        addFact(result, "interview.round.count", "真实面试轮次数量", rounds.size(), sourceRef);
        addFact(result, "offer.count", "Offer 数量", offers.size(), sourceRef);
        addFact(result, "activity.count", "联系人活动数量", activities.size(), sourceRef);
        addFact(result, "research.snapshot.count", "研究快照数量", researchSnapshots.size(), sourceRef);

        addSource(result, "CAREER_CAMPAIGN", campaignId, campaign.getCreatedAt(), campaign.getUpdatedAt(),
                hash(campaignId + "|" + campaign.getStatus() + "|" + cutoff));
        addSource(result, "JOB_APPLICATION", campaignId, null, cutoff,
                hash(applications.stream().map(JobApplication::getId).toList()));
        addSource(result, "CAREER_INTERVIEW", campaignId, null, cutoff,
                hash(processes.stream().map(CareerInterviewProcess::getId).toList()));
        addSource(result, "CAREER_OFFER", campaignId, null, cutoff,
                hash(offers.stream().map(CareerOffer::getId).toList()));
        addSource(result, "CAREER_ACTIVITY", campaignId, null, cutoff,
                hash(activities.stream().map(CareerActivity::getId).toList()));
        addSource(result, "CAREER_RESEARCH", campaignId, null, cutoff,
                hash(researchSnapshots.stream().map(CareerResearchSnapshot::getId).toList()));
        return result;
    }

    private void addFact(CareerCampaignReviewEvidenceVO result, String key, String label,
                         Object value, String sourceRef) {
        CareerCampaignReviewEvidenceVO.Fact fact = new CareerCampaignReviewEvidenceVO.Fact();
        fact.setKey(key);
        fact.setLabel(label);
        fact.setValue(value);
        fact.setSourceRef(sourceRef);
        result.getFacts().add(fact);
    }

    private void addSource(CareerCampaignReviewEvidenceVO result, String type, Long id,
                           LocalDateTime sourceTime, LocalDateTime updatedAt, String sourceHash) {
        CareerCampaignReviewEvidenceVO.Source source = new CareerCampaignReviewEvidenceVO.Source();
        source.setSourceType(type);
        source.setSourceId(id);
        source.setSourceTime(sourceTime);
        source.setSourceUpdatedAt(updatedAt);
        source.setSourceHash(sourceHash);
        result.getSources().add(source);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase();
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("周期复盘事实指纹生成失败", exception);
        }
    }
}
