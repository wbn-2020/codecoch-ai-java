package com.codecoachai.resume.careerreview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.careercampaign.CareerCampaign;
import com.codecoachai.resume.careercampaign.CareerCampaignEvent;
import com.codecoachai.resume.careercampaign.CareerCampaignEventMapper;
import com.codecoachai.resume.careercampaign.CareerCampaignMapper;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileModels;
import com.codecoachai.resume.careercampaign.CareerCampaignOperatingProfileService;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
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
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.service.support.JobApplicationLifecyclePolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class CareerCampaignReviewEvidenceServiceImpl implements CareerCampaignReviewEvidenceService {

    private final CareerCampaignMapper campaignMapper;
    private final CareerCampaignEventMapper campaignEventMapper;
    private final CareerCampaignOperatingProfileService operatingProfileService;
    private final JobApplicationMapper applicationMapper;
    private final JobApplicationEventMapper applicationEventMapper;
    private final CareerCalendarEventMapper calendarMapper;
    private final CareerInterviewProcessMapper interviewProcessMapper;
    private final CareerInterviewRoundMapper interviewRoundMapper;
    private final CareerOfferMapper offerMapper;
    private final CareerActivityMapper activityMapper;
    private final CareerResearchSnapshotMapper researchSnapshotMapper;
    private final JobApplicationPackageMapper packageMapper;

    @Override
    public CareerCampaignReviewEvidenceVO get(
            Long userId, Long campaignId, LocalDateTime ignoredClientCutoffAt,
            Integer requestedApplicationLimit, Integer requestedEventLimitPerSection) {
        if (userId == null || campaignId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 和 campaignId 不能为空");
        }
        int applicationLimit = limit("applicationLimit", requestedApplicationLimit);
        int eventLimitPerSection = limit("eventLimitPerSection", requestedEventLimitPerSection);
        CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "求职周期不存在");
        }
        LocalDateTime cutoff = authoritativeCutoff(campaign);

        CareerCampaignReviewEvidenceVO result = new CareerCampaignReviewEvidenceVO();
        result.setUserId(userId);
        result.setCampaignId(campaignId);
        result.setCampaignStatus(campaign.getStatus());
        result.setCampaignTitle(campaign.getName());
        result.setCompleted("COMPLETED".equalsIgnoreCase(campaign.getStatus()));
        result.setDataCutoffAt(cutoff);

        CareerCampaignReviewEvidenceVO.CampaignSummary campaignSummary =
                new CareerCampaignReviewEvidenceVO.CampaignSummary();
        campaignSummary.setId(campaign.getId());
        campaignSummary.setName(campaign.getName());
        campaignSummary.setStatus(campaign.getStatus());
        campaignSummary.setGoal(campaign.getGoal());
        result.setCampaign(campaignSummary);
        putCoverage(result, "campaign", true, 1, false, null);

        try {
            var profileView = operatingProfileService.getForUser(userId, campaignId);
            result.setOperatingProfile(profileView);
            putCoverage(result, "operatingProfile", true,
                    Boolean.TRUE.equals(profileView.getConfigured()) ? 1 : 0, false, null);
        } catch (RuntimeException exception) {
            result.setOperatingProfile(
                    CareerCampaignOperatingProfileModels.conservativeDefaults(userId, campaignId));
            putCoverage(result, "operatingProfile", false, 0, false,
                    "经营配置暂不可用");
            warn(result, "经营配置暂不可用，本次证据使用保守默认值。", exception, userId, campaignId);
        }

        SectionData<JobApplication> applicationSection = loadSection(
                result, "applications", "机会摘要暂不可用", applicationLimit,
                () -> applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(JobApplication::getCampaignId, campaignId)
                        .eq(JobApplication::getDeleted, CommonConstants.NO)
                        .le(JobApplication::getUpdatedAt, cutoff)
                        .orderByDesc(JobApplication::getUpdatedAt)
                        .orderByDesc(JobApplication::getId)
                        .last("LIMIT " + (applicationLimit + 1))),
                userId, campaignId);
        List<JobApplication> applications = applicationSection.items();
        List<Long> applicationIds = applications.stream()
                .map(JobApplication::getId)
                .filter(Objects::nonNull)
                .toList();

        SectionData<CareerCampaignReviewEvidenceVO.EventEvidence> eventSection =
                applicationSection.available()
                        ? loadSection(result, "recentEvents", "最近事件暂不可用", eventLimitPerSection,
                        () -> recentEvents(userId, campaignId, applicationIds, cutoff,
                                eventLimitPerSection), userId, campaignId)
                        : unavailable("机会范围不可用");

        SectionData<CareerCalendarEvent> calendarSection =
                applicationSection.available()
                        ? loadSection(result, "upcomingCalendar", "未来日历暂不可用",
                        eventLimitPerSection,
                        () -> calendarMapper.selectList(new LambdaQueryWrapper<CareerCalendarEvent>()
                                .eq(CareerCalendarEvent::getUserId, userId)
                                .in(!applicationIds.isEmpty(), CareerCalendarEvent::getApplicationId,
                                        applicationIds)
                                .eq(applicationIds.isEmpty(), CareerCalendarEvent::getApplicationId, -1L)
                                .eq(CareerCalendarEvent::getDeleted, CommonConstants.NO)
                                .ge(CareerCalendarEvent::getStartsAtUtc, cutoff)
                                .le(CareerCalendarEvent::getCreatedAt, cutoff)
                                .le(CareerCalendarEvent::getUpdatedAt, cutoff)
                                .orderByAsc(CareerCalendarEvent::getStartsAtUtc)
                                .orderByAsc(CareerCalendarEvent::getId)
                                .last("LIMIT " + (eventLimitPerSection + 1))),
                        userId, campaignId)
                        : unavailable("机会范围不可用");

        SectionData<CareerInterviewProcess> processSection =
                applicationSection.available()
                        ? loadSection(result, "interviews", "面试流程暂不可用", eventLimitPerSection,
                        () -> interviewProcessMapper.selectList(
                                new LambdaQueryWrapper<CareerInterviewProcess>()
                                        .eq(CareerInterviewProcess::getUserId, userId)
                                        .in(!applicationIds.isEmpty(),
                                                CareerInterviewProcess::getApplicationId, applicationIds)
                                        .eq(applicationIds.isEmpty(),
                                                CareerInterviewProcess::getApplicationId, -1L)
                                        .eq(CareerInterviewProcess::getDeleted, CommonConstants.NO)
                                        .le(CareerInterviewProcess::getUpdatedAt, cutoff)
                                        .orderByDesc(CareerInterviewProcess::getUpdatedAt)
                                        .orderByDesc(CareerInterviewProcess::getId)
                                        .last("LIMIT " + (eventLimitPerSection + 1))),
                        userId, campaignId)
                        : unavailable("机会范围不可用");
        List<CareerInterviewProcess> processes = processSection.items();
        List<Long> processIds = processes.stream()
                .map(CareerInterviewProcess::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Long> selectedProcessIds = processIds;
        SectionData<CareerInterviewRound> roundSection =
                processSection.available()
                        ? loadSection(result, "interviews", "面试轮次暂不可用", eventLimitPerSection,
                        () -> selectedProcessIds.isEmpty() ? List.of()
                                : interviewRoundMapper.selectList(
                                new LambdaQueryWrapper<CareerInterviewRound>()
                                        .in(CareerInterviewRound::getProcessId, selectedProcessIds)
                                        .eq(CareerInterviewRound::getDeleted, CommonConstants.NO)
                                        .le(CareerInterviewRound::getUpdatedAt, cutoff)
                                        .orderByAsc(CareerInterviewRound::getScheduledStartsAtUtc)
                                        .orderByAsc(CareerInterviewRound::getId)
                                        .last("LIMIT " + (eventLimitPerSection + 1))),
                        userId, campaignId)
                        : unavailable("机会范围不可用");
        List<CareerInterviewRound> rounds = roundSection.items();
        if (!processSection.available() || !roundSection.available()) {
            putCoverage(result, "interviews", false,
                    processes.size() + rounds.size(),
                    processSection.truncated() || roundSection.truncated(),
                    "面试数据部分不可用");
        } else {
            putCoverage(result, "interviews", true,
                    processes.size() + rounds.size(),
                    processSection.truncated() || roundSection.truncated(), null);
        }

        SectionData<CareerOffer> offerSection = applicationSection.available()
                ? loadSection(result, "offers", "Offer 事实暂不可用", eventLimitPerSection,
                () -> offerMapper.selectList(new LambdaQueryWrapper<CareerOffer>()
                        .eq(CareerOffer::getUserId, userId)
                        .eq(CareerOffer::getDeleted, CommonConstants.NO)
                        .in(!applicationIds.isEmpty(), CareerOffer::getApplicationId, applicationIds)
                        .eq(applicationIds.isEmpty(), CareerOffer::getApplicationId, -1L)
                        .le(CareerOffer::getUpdatedAt, cutoff)
                        .orderByAsc(CareerOffer::getDecisionDeadline)
                        .orderByAsc(CareerOffer::getId)
                        .last("LIMIT " + (eventLimitPerSection + 1))),
                userId, campaignId)
                : unavailable("机会范围不可用");
        SectionData<CareerActivity> activitySection = applicationSection.available()
                ? loadSection(result, "activities", "联系人活动暂不可用", eventLimitPerSection,
                () -> activityMapper.selectList(new LambdaQueryWrapper<CareerActivity>()
                        .eq(CareerActivity::getUserId, userId)
                        .in(!applicationIds.isEmpty(), CareerActivity::getApplicationId, applicationIds)
                        .eq(applicationIds.isEmpty(), CareerActivity::getApplicationId, -1L)
                        .eq(CareerActivity::getDeleted, CommonConstants.NO)
                        .le(CareerActivity::getOccurredAt, cutoff)
                        .le(CareerActivity::getUpdatedAt, cutoff)
                        .orderByDesc(CareerActivity::getOccurredAt)
                        .orderByDesc(CareerActivity::getId)
                        .last("LIMIT " + (eventLimitPerSection + 1))),
                userId, campaignId)
                : unavailable("机会范围不可用");
        SectionData<CareerResearchSnapshot> researchSection = applicationSection.available()
                ? loadSection(result, "research", "研究证据暂不可用", eventLimitPerSection,
                () -> researchSnapshotMapper.selectList(
                        new LambdaQueryWrapper<CareerResearchSnapshot>()
                                .eq(CareerResearchSnapshot::getUserId, userId)
                                .in(!applicationIds.isEmpty(),
                                        CareerResearchSnapshot::getApplicationId, applicationIds)
                                .eq(applicationIds.isEmpty(),
                                        CareerResearchSnapshot::getApplicationId, -1L)
                                .eq(CareerResearchSnapshot::getDeleted, CommonConstants.NO)
                                .le(CareerResearchSnapshot::getUpdatedAt, cutoff)
                                .orderByDesc(CareerResearchSnapshot::getUpdatedAt)
                                .orderByDesc(CareerResearchSnapshot::getId)
                                .last("LIMIT " + (eventLimitPerSection + 1))),
                userId, campaignId)
                : unavailable("机会范围不可用");
        SectionData<JobApplicationPackage> materialSection = applicationSection.available()
                ? loadSection(result, "materials", "投递材料暂不可用", eventLimitPerSection,
                () -> packageMapper.selectList(new LambdaQueryWrapper<JobApplicationPackage>()
                        .eq(JobApplicationPackage::getUserId, userId)
                        .in(!applicationIds.isEmpty(), JobApplicationPackage::getApplicationId,
                                applicationIds)
                        .eq(applicationIds.isEmpty(), JobApplicationPackage::getApplicationId, -1L)
                        .eq(JobApplicationPackage::getDeleted, CommonConstants.NO)
                        .le(JobApplicationPackage::getUpdatedAt, cutoff)
                        .orderByDesc(JobApplicationPackage::getUpdatedAt)
                        .orderByDesc(JobApplicationPackage::getId)
                        .last("LIMIT " + (eventLimitPerSection + 1))),
                userId, campaignId)
                : unavailable("机会范围不可用");

        List<CareerOffer> offers = offerSection.items();
        List<CareerActivity> activities = activitySection.items();
        List<CareerResearchSnapshot> researchSnapshots = researchSection.items();
        List<JobApplicationPackage> materials = materialSection.items();
        ensureCoverage(result, "recentEvents", eventSection);
        ensureCoverage(result, "upcomingCalendar", calendarSection);
        ensureCoverage(result, "offers", offerSection);
        ensureCoverage(result, "activities", activitySection);
        ensureCoverage(result, "research", researchSection);
        ensureCoverage(result, "materials", materialSection);

        String sourceRef = "CAREER_CAMPAIGN:" + campaignId;
        result.setAllOpportunitiesClosed(applicationSection.available()
                ? applications.stream().allMatch(application ->
                JobApplicationLifecyclePolicy.isTerminal(application.getStatus())) : null);
        result.setSampleSize(applications.size());

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

        result.setRecentEvents(eventSection.items());
        result.setUpcomingCalendar(calendarSection.items().stream()
                .map(this::toCalendarEvidence).toList());
        result.setInterviews(toInterviewEvidence(processes, rounds, applicationIds));
        result.setOffers(offers.stream().map(this::toOfferEvidence).toList());
        result.setActivities(activities.stream().map(this::toActivityEvidence).toList());
        result.setResearch(researchSnapshots.stream().map(this::toResearchEvidence).toList());
        result.setMaterials(materials.stream().map(this::toMaterialEvidence).toList());
        result.setApplications(toApplicationEvidence(
                applications, processes, rounds, offers, activities, researchSnapshots, materials,
                result, cutoff, campaignId));

        addSource(result, "CAREER_CAMPAIGN", campaignId, campaign.getCreatedAt(), campaign.getUpdatedAt(),
                1, hash(campaignId + "|" + campaign.getStatus() + "|" + cutoff),
                null, campaignId, cutoff, "careerCampaign.status", "周期状态事实");
        addSource(result, "JOB_APPLICATION", campaignId, null, cutoff,
                1, hash(applications.stream().map(JobApplication::getId).toList()),
                null, campaignId, cutoff, "jobApplication", "周期机会摘要");
        addSource(result, "CAREER_INTERVIEW", campaignId, null, cutoff,
                1, hash(processes.stream().map(CareerInterviewProcess::getId).toList()),
                null, campaignId, cutoff, "careerInterview", "周期面试摘要");
        addSource(result, "CAREER_OFFER", campaignId, null, cutoff,
                1, hash(offers.stream().map(CareerOffer::getId).toList()),
                null, campaignId, cutoff, "careerOffer", "周期 Offer 摘要");
        addSource(result, "CAREER_ACTIVITY", campaignId, null, cutoff,
                1, hash(activities.stream().map(CareerActivity::getId).toList()),
                null, campaignId, cutoff, "careerActivity", "周期联系人活动摘要");
        addSource(result, "CAREER_RESEARCH", campaignId, null, cutoff,
                1, hash(researchSnapshots.stream().map(CareerResearchSnapshot::getId).toList()),
                null, campaignId, cutoff, "careerResearch", "周期研究摘要");
        addSource(result, "CAREER_CALENDAR", campaignId, null, cutoff, 1,
                hash(calendarSection.items().stream().map(CareerCalendarEvent::getId).toList()),
                null, campaignId, cutoff, "careerCalendar", "未来日历摘要");
        addSource(result, "APPLICATION_MATERIAL", campaignId, null, cutoff, 1,
                hash(materials.stream().map(JobApplicationPackage::getId).toList()),
                null, campaignId, cutoff, "applicationMaterial", "投递材料覆盖摘要");
        result.setEvidenceHash(evidenceHash(result));
        return result;
    }

    private List<CareerCampaignReviewEvidenceVO.EventEvidence> recentEvents(
            Long userId, Long campaignId, List<Long> applicationIds,
            LocalDateTime cutoff, int limit) {
        List<CareerCampaignEvent> campaignEvents = safeList(campaignEventMapper.selectList(
                new LambdaQueryWrapper<CareerCampaignEvent>()
                        .eq(CareerCampaignEvent::getUserId, userId)
                        .eq(CareerCampaignEvent::getCampaignId, campaignId)
                        .eq(CareerCampaignEvent::getDeleted, CommonConstants.NO)
                        .le(CareerCampaignEvent::getOccurredAt, cutoff)
                        .orderByDesc(CareerCampaignEvent::getOccurredAt)
                        .orderByDesc(CareerCampaignEvent::getId)
                        .last("LIMIT " + (limit + 1))));
        List<JobApplicationEvent> applicationEvents = applicationIds.isEmpty()
                ? List.of()
                : safeList(applicationEventMapper.selectList(
                new LambdaQueryWrapper<JobApplicationEvent>()
                        .eq(JobApplicationEvent::getUserId, userId)
                        .in(JobApplicationEvent::getApplicationId, applicationIds)
                        .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                        .le(JobApplicationEvent::getEventTime, cutoff)
                        .le(JobApplicationEvent::getCreatedAt, cutoff)
                        .le(JobApplicationEvent::getUpdatedAt, cutoff)
                        .orderByDesc(JobApplicationEvent::getEventTime)
                        .orderByDesc(JobApplicationEvent::getId)
                        .last("LIMIT " + (limit + 1))));
        List<CareerCampaignReviewEvidenceVO.EventEvidence> result = new ArrayList<>();
        for (CareerCampaignEvent event : campaignEvents) {
            CareerCampaignReviewEvidenceVO.EventEvidence item =
                    new CareerCampaignReviewEvidenceVO.EventEvidence();
            item.setId(event.getId());
            item.setSourceType("CAREER_CAMPAIGN_EVENT");
            item.setEventType(event.getEventType());
            item.setEventTime(event.getOccurredAt());
            item.setSummary(safeEventSummary(event.getEventType()));
            item.setSourceHash(hash(event.getId() + "|" + event.getEventType()
                    + "|" + event.getOccurredAt() + "|" + event.getResultLockVersion()));
            result.add(item);
        }
        for (JobApplicationEvent event : applicationEvents) {
            CareerCampaignReviewEvidenceVO.EventEvidence item =
                    new CareerCampaignReviewEvidenceVO.EventEvidence();
            item.setId(event.getId());
            item.setApplicationId(event.getApplicationId());
            item.setSourceType("JOB_APPLICATION_EVENT");
            item.setEventType(event.getEventType());
            item.setEventTime(event.getEventTime());
            item.setSummary(safeEventSummary(event.getEventType()));
            item.setSourceHash(hash(event.getId() + "|" + event.getApplicationId()
                    + "|" + event.getEventType() + "|" + event.getEventTime()
                    + "|" + event.getResultLockVersion()));
            result.add(item);
        }
        return result.stream()
                .sorted(Comparator.comparing(CareerCampaignReviewEvidenceVO.EventEvidence::getEventTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CareerCampaignReviewEvidenceVO.EventEvidence::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<CareerCampaignReviewEvidenceVO.ApplicationEvidence> toApplicationEvidence(
            List<JobApplication> applications,
            List<CareerInterviewProcess> processes,
            List<CareerInterviewRound> rounds,
            List<CareerOffer> offers,
            List<CareerActivity> activities,
            List<CareerResearchSnapshot> research,
            List<JobApplicationPackage> materials,
            CareerCampaignReviewEvidenceVO result,
            LocalDateTime cutoff,
            Long campaignId) {
        Map<Long, List<CareerInterviewRound>> roundsByProcess = rounds.stream()
                .filter(value -> value.getProcessId() != null)
                .collect(Collectors.groupingBy(CareerInterviewRound::getProcessId));
        Map<Long, CareerOffer> offersByApplication = new HashMap<>();
        for (CareerOffer offer : offers) {
            if (offer.getApplicationId() != null) {
                offersByApplication.merge(offer.getApplicationId(), offer,
                        (first, second) -> first.getDecisionDeadline() == null
                                ? second : second.getDecisionDeadline() == null
                                || first.getDecisionDeadline().isBefore(second.getDecisionDeadline())
                                ? first : second);
            }
        }
        Map<Long, LocalDateTime> contactFollowUps = new HashMap<>();
        for (CareerActivity activity : activities) {
            if (activity.getApplicationId() != null && activity.getNextFollowUpAt() != null) {
                contactFollowUps.merge(activity.getApplicationId(), activity.getNextFollowUpAt(),
                        (first, second) -> first.isBefore(second) ? first : second);
            }
        }
        Map<Long, Boolean> materialCoverage = new HashMap<>();
        for (JobApplicationPackage material : materials) {
            if (material.getApplicationId() != null) {
                materialCoverage.merge(material.getApplicationId(),
                        "READY".equalsIgnoreCase(material.getReadinessLevel()),
                        Boolean::logicalOr);
            }
        }
        Set<Long> researchedApplications = research.stream()
                .map(CareerResearchSnapshot::getApplicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<CareerCampaignReviewEvidenceVO.ApplicationEvidence> resultItems = new ArrayList<>();
        for (JobApplication application : applications) {
            Long applicationId = application.getId();
            List<CareerInterviewRound> applicationRounds = new ArrayList<>();
            for (CareerInterviewProcess process : processes) {
                if (Objects.equals(process.getApplicationId(), applicationId)) {
                    applicationRounds.addAll(roundsByProcess.getOrDefault(
                            process.getId(), List.of()));
                }
            }
            LocalDateTime interviewAt = applicationRounds.stream()
                    .map(CareerInterviewRound::getScheduledStartsAtUtc)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            CareerInterviewRound upcomingRound = applicationRounds.stream()
                    .filter(round -> round.getScheduledStartsAtUtc() != null
                            && round.getScheduledStartsAtUtc().isAfter(cutoff))
                    .min(Comparator.comparing(CareerInterviewRound::getScheduledStartsAtUtc))
                    .orElse(null);
            boolean reviewMissing = applicationRounds.stream()
                    .anyMatch(round -> round.getScheduledStartsAtUtc() != null
                            && !round.getScheduledStartsAtUtc().isAfter(cutoff)
                            && Set.of("COMPLETED", "FINISHED", "DONE", "PASSED", "FAILED")
                            .contains(normalize(round.getStatus()))
                            && !StringUtils.hasText(round.getResultSummary()));
            CareerOffer offer = offersByApplication.get(applicationId);
            CareerCampaignReviewEvidenceVO.ApplicationEvidence item =
                    new CareerCampaignReviewEvidenceVO.ApplicationEvidence();
            item.setId(applicationId);
            item.setApplicationId(applicationId);
            item.setCompanyName(application.getCompanyName());
            item.setJobTitle(application.getJobTitle());
            item.setStatus(application.getStatus());
            item.setStage(normalize(application.getStatus()));
            item.setPriorityLevel(application.getPriorityLevel());
            item.setCreatedAt(application.getCreatedAt());
            item.setUpdatedAt(application.getUpdatedAt());
            item.setNextFollowUpAt(application.getNextFollowUpAt());
            item.setInterviewAt(interviewAt);
            item.setOfferDeadlineAt(offer == null ? null : offer.getDecisionDeadline());
            item.setInterviewPrepMissing(upcomingRound != null
                    && !StringUtils.hasText(upcomingRound.getPreparationSourceHash()));
            item.setInterviewReviewMissing(reviewMissing);
            item.setMaterialCoverageLow(result.getCoverage().get("materials") != null
                    && Boolean.TRUE.equals(result.getCoverage().get("materials").getAvailable())
                    && !Boolean.TRUE.equals(materialCoverage.get(applicationId)));
            item.setResearchCoverageLow(result.getCoverage().get("research") != null
                    && Boolean.TRUE.equals(result.getCoverage().get("research").getAvailable())
                    && !researchedApplications.contains(applicationId));
            item.setContactFollowUpAt(contactFollowUps.get(applicationId));
            String sourceHash = hash(application.getId() + "|" + application.getCompanyName()
                    + "|" + application.getJobTitle() + "|" + application.getStatus()
                    + "|" + application.getUpdatedAt() + "|" + interviewAt + "|"
                    + (offer == null ? null : offer.getId()) + "|"
                    + contactFollowUps.get(applicationId) + "|"
                    + materialCoverage.get(applicationId) + "|"
                    + researchedApplications.contains(applicationId));
            item.setSourceHash(sourceHash);
            CareerCampaignReviewEvidenceVO.Source source = new CareerCampaignReviewEvidenceVO.Source();
            source.setSourceType("JOB_APPLICATION");
            source.setSourceId(applicationId);
            source.setSourceVersion(application.getLockVersion() == null
                    ? 1 : application.getLockVersion());
            source.setSourceTime(application.getCreatedAt());
            source.setSourceUpdatedAt(application.getUpdatedAt());
            source.setSourceHash(sourceHash);
            source.setApplicationId(applicationId);
            source.setCampaignId(campaignId);
            source.setObservedAt(cutoff);
            source.setFieldPath("jobApplication.status");
            source.setSummary("机会状态：" + normalize(application.getStatus()));
            item.getSources().add(source);
            resultItems.add(item);
        }
        return resultItems;
    }

    private CareerCampaignReviewEvidenceVO.CalendarEvidence toCalendarEvidence(
            CareerCalendarEvent value) {
        CareerCampaignReviewEvidenceVO.CalendarEvidence result =
                new CareerCampaignReviewEvidenceVO.CalendarEvidence();
        result.setId(value.getId());
        result.setApplicationId(value.getApplicationId());
        result.setTitle(value.getTitle());
        result.setEventType(value.getEventType());
        result.setStartsAtUtc(value.getStartsAtUtc());
        result.setEndsAtUtc(value.getEndsAtUtc());
        result.setTimezone(value.getTimezone());
        result.setStatus(value.getStatus());
        result.setSourceHash(hash(value.getId() + "|" + value.getApplicationId() + "|"
                + value.getStartsAtUtc() + "|" + value.getEndsAtUtc() + "|" + value.getStatus()));
        return result;
    }

    private List<CareerCampaignReviewEvidenceVO.InterviewEvidence> toInterviewEvidence(
            List<CareerInterviewProcess> processes, List<CareerInterviewRound> rounds,
            List<Long> applicationIds) {
        Map<Long, List<CareerInterviewRound>> roundsByProcess = rounds.stream()
                .filter(value -> value.getProcessId() != null)
                .collect(Collectors.groupingBy(CareerInterviewRound::getProcessId));
        return processes.stream().map(process -> {
            CareerCampaignReviewEvidenceVO.InterviewEvidence result =
                    new CareerCampaignReviewEvidenceVO.InterviewEvidence();
            List<CareerInterviewRound> processRounds = roundsByProcess.getOrDefault(
                    process.getId(), List.of());
            result.setId(process.getId());
            result.setApplicationId(process.getApplicationId());
            result.setStatus(process.getStatus());
            result.setCurrentRoundNo(process.getCurrentRoundNo());
            result.setOutcome(process.getOutcome());
            result.setRoundCount(processRounds.size());
            result.setNextInterviewAt(processRounds.stream()
                    .map(CareerInterviewRound::getScheduledStartsAtUtc)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null));
            result.setSourceHash(hash(process.getId() + "|" + process.getApplicationId()
                    + "|" + process.getStatus() + "|" + process.getLockVersion()
                    + "|" + processRounds.stream().map(CareerInterviewRound::getId).toList()
                    + "|" + applicationIds.size()));
            return result;
        }).toList();
    }

    private CareerCampaignReviewEvidenceVO.OfferEvidence toOfferEvidence(CareerOffer value) {
        CareerCampaignReviewEvidenceVO.OfferEvidence result =
                new CareerCampaignReviewEvidenceVO.OfferEvidence();
        result.setId(value.getId());
        result.setApplicationId(value.getApplicationId());
        result.setStatus(value.getStatus());
        result.setDecisionDeadline(value.getDecisionDeadline());
        result.setFinalizedAt(value.getFinalizedAt());
        result.setSourceHash(hash(value.getId() + "|" + value.getApplicationId() + "|"
                + value.getStatus() + "|" + value.getDecisionDeadline() + "|"
                + value.getFinalizedAt() + "|" + value.getLockVersion()));
        return result;
    }

    private CareerCampaignReviewEvidenceVO.ActivityEvidence toActivityEvidence(
            CareerActivity value) {
        CareerCampaignReviewEvidenceVO.ActivityEvidence result =
                new CareerCampaignReviewEvidenceVO.ActivityEvidence();
        result.setId(value.getId());
        result.setApplicationId(value.getApplicationId());
        result.setContactId(value.getContactId());
        result.setActivityType(value.getActivityType());
        result.setChannelType(value.getChannelType());
        result.setStatus(value.getStatus());
        result.setOccurredAt(value.getOccurredAt());
        result.setNextFollowUpAt(value.getNextFollowUpAt());
        result.setSourceHash(hash(value.getId() + "|" + value.getApplicationId() + "|"
                + value.getActivityType() + "|" + value.getOccurredAt() + "|"
                + value.getNextFollowUpAt() + "|" + value.getRequestHash()));
        return result;
    }

    private CareerCampaignReviewEvidenceVO.ResearchEvidence toResearchEvidence(
            CareerResearchSnapshot value) {
        CareerCampaignReviewEvidenceVO.ResearchEvidence result =
                new CareerCampaignReviewEvidenceVO.ResearchEvidence();
        result.setId(value.getId());
        result.setApplicationId(value.getApplicationId());
        result.setConfidenceLevel(value.getConfidenceLevel());
        result.setSourceSetHash(value.getSourceSetHash());
        result.setUpdatedAt(value.getUpdatedAt());
        result.setSourceHash(hash(value.getId() + "|" + value.getApplicationId() + "|"
                + value.getConfidenceLevel() + "|" + value.getSourceSetHash() + "|"
                + value.getUpdatedAt()));
        return result;
    }

    private CareerCampaignReviewEvidenceVO.MaterialEvidence toMaterialEvidence(
            JobApplicationPackage value) {
        CareerCampaignReviewEvidenceVO.MaterialEvidence result =
                new CareerCampaignReviewEvidenceVO.MaterialEvidence();
        result.setId(value.getId());
        result.setApplicationId(value.getApplicationId());
        result.setPackageStatus(value.getPackageStatus());
        result.setReadinessLevel(value.getReadinessLevel());
        result.setReadinessScore(value.getReadinessScore());
        result.setUpdatedAt(value.getUpdatedAt());
        result.setSourceHash(hash(value.getId() + "|" + value.getApplicationId() + "|"
                + value.getPackageStatus() + "|" + value.getReadinessLevel() + "|"
                + value.getReadinessScore() + "|" + value.getUpdatedAt()));
        return result;
    }

    private int limit(String name, Integer requested) {
        int value = requested == null ? 100 : requested;
        if (value < 1 || value > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    name + " 必须在 1 到 100 之间");
        }
        return value;
    }

    private <T> SectionData<T> loadSection(
            CareerCampaignReviewEvidenceVO result,
            String key,
            String warning,
            int limit,
            Supplier<List<T>> loader,
            Long userId,
            Long campaignId) {
        try {
            List<T> values = safeList(loader.get());
            boolean truncated = values.size() > limit;
            List<T> included = truncated
                    ? new ArrayList<>(values.subList(0, limit))
                    : new ArrayList<>(values);
            putCoverage(result, key, true, included.size(), truncated, null);
            return new SectionData<>(included, true, truncated, null);
        } catch (RuntimeException exception) {
            putCoverage(result, key, false, 0, false, warning);
            warn(result, warning + "。", exception, userId, campaignId);
            return unavailable(warning);
        }
    }

    private void ensureCoverage(
            CareerCampaignReviewEvidenceVO result,
            String key,
            SectionData<?> section) {
        if (!result.getCoverage().containsKey(key)) {
            putCoverage(result, key, section.available(), section.items().size(),
                    section.truncated(), section.reason());
        }
    }

    private static <T> SectionData<T> unavailable(String reason) {
        return new SectionData<>(List.of(), false, false, reason);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void putCoverage(
            CareerCampaignReviewEvidenceVO result,
            String key,
            boolean available,
            int itemCount,
            boolean truncated,
            String reason) {
        CareerCampaignReviewEvidenceVO.Coverage coverage =
                new CareerCampaignReviewEvidenceVO.Coverage();
        coverage.setAvailable(available);
        coverage.setItemCount(itemCount);
        coverage.setTruncated(truncated);
        coverage.setReason(reason);
        result.getCoverage().put(key, coverage);
    }

    private void warn(
            CareerCampaignReviewEvidenceVO result,
            String message,
            RuntimeException exception,
            Long userId,
            Long campaignId) {
        result.getWarnings().add(message);
        log.warn("Campaign evidence section failed: userId={}, campaignId={}, message={}",
                userId, campaignId, message, exception);
    }

    private String safeEventSummary(String eventType) {
        return "事件类型：" + normalize(eventType);
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

    private void addSource(
            CareerCampaignReviewEvidenceVO result,
            String type,
            Long id,
            LocalDateTime sourceTime,
            LocalDateTime updatedAt,
            Integer sourceVersion,
            String sourceHash,
            Long applicationId,
            Long campaignId,
            LocalDateTime observedAt,
            String fieldPath,
            String summary) {
        CareerCampaignReviewEvidenceVO.Source source = new CareerCampaignReviewEvidenceVO.Source();
        source.setSourceType(type);
        source.setSourceId(id);
        source.setSourceVersion(sourceVersion);
        source.setSourceTime(sourceTime);
        source.setSourceUpdatedAt(updatedAt);
        source.setSourceHash(sourceHash);
        source.setApplicationId(applicationId);
        source.setCampaignId(campaignId);
        source.setObservedAt(observedAt);
        source.setFieldPath(fieldPath);
        source.setSummary(summary);
        result.getSources().add(source);
    }

    private String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime authoritativeCutoff(CareerCampaign campaign) {
        if (Set.of("COMPLETED", "ARCHIVED").contains(normalize(campaign.getStatus()))
                && campaign.getCompletedAt() != null) {
            return campaign.getCompletedAt();
        }
        return LocalDateTime.now().withNano(0);
    }

    private String evidenceHash(CareerCampaignReviewEvidenceVO evidence) {
        List<String> values = new ArrayList<>();
        values.add("evidenceSchemaVersion="
                + CareerCampaignReviewEvidenceVO.EVIDENCE_SCHEMA_VERSION);
        values.add("userId=" + Objects.toString(evidence.getUserId(), ""));
        values.add("campaignId=" + Objects.toString(evidence.getCampaignId(), ""));
        values.add("campaignStatus=" + Objects.toString(evidence.getCampaignStatus(), ""));
        values.add("campaignTitle=" + Objects.toString(evidence.getCampaignTitle(), ""));
        values.add("completed=" + Objects.toString(evidence.getCompleted(), ""));
        values.add("allOpportunitiesClosed="
                + Objects.toString(evidence.getAllOpportunitiesClosed(), ""));
        values.add("sampleSize=" + Objects.toString(evidence.getSampleSize(), ""));
        values.add("dataCutoffAt=" + Objects.toString(evidence.getDataCutoffAt(), ""));
        evidence.getFacts().stream()
                .map(this::canonicalFact)
                .sorted()
                .forEach(value -> values.add("fact=" + value));
        evidence.getSources().stream()
                .map(this::canonicalSource)
                .sorted()
                .forEach(value -> values.add("source=" + value));
        return hash(String.join("\n", values));
    }

    private String canonicalFact(CareerCampaignReviewEvidenceVO.Fact value) {
        return String.join("|",
                Objects.toString(value.getKey(), ""),
                Objects.toString(value.getLabel(), ""),
                Objects.toString(value.getValue(), ""),
                Objects.toString(value.getSourceRef(), ""));
    }

    private String canonicalSource(CareerCampaignReviewEvidenceVO.Source value) {
        return String.join("|",
                Objects.toString(value.getSourceType(), ""),
                Objects.toString(value.getSourceId(), ""),
                Objects.toString(value.getSourceVersion(), ""),
                Objects.toString(value.getSourceTime(), ""),
                Objects.toString(value.getSourceUpdatedAt(), ""),
                Objects.toString(value.getSourceHash(), ""));
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("周期复盘事实指纹生成失败", exception);
        }
    }

    private record SectionData<T>(
            List<T> items,
            boolean available,
            boolean truncated,
            String reason) {
    }
}
