package com.codecoachai.resume.applicationworkspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.Coverage;
import com.codecoachai.resume.applicationworkspace.ApplicationWorkspaceModels.WorkspaceView;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.careercampaign.CareerCampaign;
import com.codecoachai.resume.careercampaign.CareerCampaignMapper;
import com.codecoachai.resume.config.V7FeatureGate;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationWorkspaceServiceImpl implements ApplicationWorkspaceService {

    private static final int TIMELINE_LIMIT = 100;
    private static final int CALENDAR_LIMIT = 50;
    private static final int MATERIAL_LIMIT = 20;

    private final JobApplicationMapper applicationMapper;
    private final CareerCampaignMapper campaignMapper;
    private final JobApplicationEventMapper eventMapper;
    private final CareerCalendarEventMapper calendarMapper;
    private final JobApplicationPackageMapper packageMapper;
    private final V7FeatureGate featureGate;

    @Override
    public WorkspaceView get(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "机会不存在");
        }
        WorkspaceView view = new WorkspaceView();
        view.setApplication(application);
        view.setCapabilities(featureGate.enabledCapabilities());
        if (application.getCampaignId() != null) {
            CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                    .eq(CareerCampaign::getId, application.getCampaignId())
                    .eq(CareerCampaign::getUserId, userId)
                    .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                    .last("LIMIT 1"));
            view.setCampaign(campaign);
        }
        view.getCoverage().put("application", new Coverage("codecoachai-resume", true, 1, false));
        loadTimeline(view, userId, applicationId);
        loadCalendar(view, userId, applicationId);
        loadMaterials(view, userId, applicationId);
        view.setNextSteps(nextSteps(application));
        view.getCoverage().put("nextSteps",
                new Coverage("codecoachai-resume", true, view.getNextSteps().size(), false));
        view.getCoverage().put("mockInterviewEvidence",
                new Coverage("codecoachai-interview", false, 0, false));
        view.getWarnings().add("模拟面试证据暂不可用，本次工作区仅返回简历服务已确认的事实。");
        return view;
    }

    private void loadTimeline(WorkspaceView view, Long userId, Long applicationId) {
        try {
            List<JobApplicationEvent> values = eventMapper.selectList(
                    new LambdaQueryWrapper<JobApplicationEvent>()
                            .eq(JobApplicationEvent::getUserId, userId)
                            .eq(JobApplicationEvent::getApplicationId, applicationId)
                            .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                            .orderByDesc(JobApplicationEvent::getEventTime)
                            .orderByDesc(JobApplicationEvent::getId)
                            .last("LIMIT " + (TIMELINE_LIMIT + 1)));
            boolean truncated = values.size() > TIMELINE_LIMIT;
            view.setTimeline(truncated ? values.subList(0, TIMELINE_LIMIT) : values);
            view.getCoverage().put("timeline",
                    new Coverage("codecoachai-resume", true, view.getTimeline().size(), truncated));
        } catch (RuntimeException ex) {
            view.getCoverage().put("timeline", new Coverage("codecoachai-resume", false, 0, false));
            view.getWarnings().add("机会时间线暂不可用。");
        }
    }

    private void loadCalendar(WorkspaceView view, Long userId, Long applicationId) {
        try {
            List<CareerCalendarEvent> values = calendarMapper.selectList(
                    new LambdaQueryWrapper<CareerCalendarEvent>()
                            .eq(CareerCalendarEvent::getUserId, userId)
                            .eq(CareerCalendarEvent::getApplicationId, applicationId)
                            .eq(CareerCalendarEvent::getDeleted, CommonConstants.NO)
                            .orderByDesc(CareerCalendarEvent::getStartsAtUtc)
                            .orderByDesc(CareerCalendarEvent::getId)
                            .last("LIMIT " + (CALENDAR_LIMIT + 1)));
            boolean truncated = values.size() > CALENDAR_LIMIT;
            view.setCalendar(truncated ? values.subList(0, CALENDAR_LIMIT) : values);
            view.getCoverage().put("calendar",
                    new Coverage("codecoachai-resume", true, view.getCalendar().size(), truncated));
        } catch (RuntimeException ex) {
            view.getCoverage().put("calendar", new Coverage("codecoachai-resume", false, 0, false));
            view.getWarnings().add("求职日历暂不可用。");
        }
    }

    private void loadMaterials(WorkspaceView view, Long userId, Long applicationId) {
        try {
            List<JobApplicationPackage> values = packageMapper.selectList(
                    new LambdaQueryWrapper<JobApplicationPackage>()
                            .eq(JobApplicationPackage::getUserId, userId)
                            .eq(JobApplicationPackage::getApplicationId, applicationId)
                            .eq(JobApplicationPackage::getDeleted, CommonConstants.NO)
                            .orderByDesc(JobApplicationPackage::getUpdatedAt)
                            .orderByDesc(JobApplicationPackage::getId)
                            .last("LIMIT " + (MATERIAL_LIMIT + 1)));
            boolean truncated = values.size() > MATERIAL_LIMIT;
            view.setMaterials(truncated ? values.subList(0, MATERIAL_LIMIT) : values);
            view.getCoverage().put("materials",
                    new Coverage("codecoachai-resume", true, view.getMaterials().size(), truncated));
        } catch (RuntimeException ex) {
            view.getCoverage().put("materials", new Coverage("codecoachai-resume", false, 0, false));
            view.getWarnings().add("关联材料暂不可用。");
        }
    }

    private static List<String> nextSteps(JobApplication application) {
        String status = application.getStatus() == null
                ? "DRAFT" : application.getStatus().trim().toUpperCase(Locale.ROOT);
        if (application.getNextFollowUpAt() != null) {
            return List.of("请在 " + application.getNextFollowUpAt() + " 前完成下一次跟进");
        }
        return switch (status) {
            case "DRAFT", "SAVED", "PREPARING" -> List.of("补齐并确认本次投递材料");
            case "APPLIED", "SCREENING" -> List.of("安排下一次跟进并记录结果");
            case "INTERVIEW", "INTERVIEWING" -> List.of("准备下一轮真实面试并确认日程");
            case "OFFER" -> List.of("核对 Offer 事实和截止时间，再由你确认接受或拒绝");
            default -> List.of();
        };
    }
}
