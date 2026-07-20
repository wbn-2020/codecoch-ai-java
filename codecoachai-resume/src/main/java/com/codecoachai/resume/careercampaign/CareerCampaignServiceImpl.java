package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.CampaignView;
import com.codecoachai.resume.careercampaign.CareerCampaignModels.SaveRequest;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerCampaignServiceImpl implements CareerCampaignService {

    private static final Set<String> OPEN_APPLICATION_STATUSES =
            Set.of("DRAFT", "SAVED", "PREPARING", "APPLIED", "SCREENING", "INTERVIEW", "INTERVIEWING", "OFFER", "REOPENED");

    private final CareerCampaignMapper campaignMapper;
    private final CareerCampaignEventMapper eventMapper;
    private final JobApplicationMapper applicationMapper;

    @Override
    public List<CampaignView> list() {
        Long userId = SecurityAssert.requireLoginUserId();
        return campaignMapper.selectList(new LambdaQueryWrapper<CareerCampaign>()
                        .eq(CareerCampaign::getUserId, userId)
                        .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                        .orderByDesc(CareerCampaign::getUpdatedAt)
                        .orderByDesc(CareerCampaign::getId))
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView create(SaveRequest request) {
        Long userId = SecurityAssert.requireLoginUserId();
        requireName(request);
        CareerCampaign campaign = new CareerCampaign();
        campaign.setUserId(userId);
        campaign.setName(request.getName().trim());
        campaign.setGoal(trim(request.getGoal(), 2000));
        campaign.setStatus("DRAFT");
        campaign.setLockVersion(1);
        campaignMapper.insert(campaign);
        appendEvent(campaign, "CREATED", "Campaign created");
        return toView(campaign);
    }

    @Override
    public CampaignView get(Long campaignId) {
        return toView(owned(SecurityAssert.requireLoginUserId(), campaignId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView update(Long campaignId, SaveRequest request) {
        CareerCampaign campaign = owned(SecurityAssert.requireLoginUserId(), campaignId);
        requireName(request);
        if ("ARCHIVED".equals(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Archived campaign cannot be edited");
        }
        campaign.setName(request.getName().trim());
        campaign.setGoal(trim(request.getGoal(), 2000));
        campaignMapper.updateById(campaign);
        appendEvent(campaign, "UPDATED", "Campaign updated");
        return toView(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView activate(Long campaignId) {
        CareerCampaign campaign = owned(SecurityAssert.requireLoginUserId(), campaignId);
        if (campaignMapper.countActive(campaign.getUserId()) > 0 && !"ACTIVE".equals(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only one active campaign is allowed");
        }
        return transition(campaign, Set.of("DRAFT", "PAUSED"), "ACTIVE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView complete(Long campaignId, boolean retainOpenApplications) {
        CareerCampaign campaign = owned(SecurityAssert.requireLoginUserId(), campaignId);
        long open = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getCampaignId, campaignId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .in(JobApplication::getStatus, OPEN_APPLICATION_STATUSES));
        if (open > 0 && !retainOpenApplications) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Campaign still contains open applications");
        }
        return transition(campaign, Set.of("ACTIVE", "PAUSED"), "COMPLETED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView archive(Long campaignId) {
        CareerCampaign campaign = owned(SecurityAssert.requireLoginUserId(), campaignId);
        return transition(campaign, Set.of("COMPLETED"), "ARCHIVED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView addApplication(Long campaignId, Long applicationId) {
        CareerCampaign campaign = owned(SecurityAssert.requireLoginUserId(), campaignId);
        if ("COMPLETED".equals(campaign.getStatus()) || "ARCHIVED".equals(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Closed campaign cannot accept applications");
        }
        JobApplication application = ownedApplication(campaign.getUserId(), applicationId);
        application.setCampaignId(campaignId);
        applicationMapper.updateById(application);
        appendEvent(campaign, "APPLICATION_ADDED", "Application " + applicationId + " added");
        return toView(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApplication(Long campaignId, Long applicationId) {
        CareerCampaign campaign = owned(SecurityAssert.requireLoginUserId(), campaignId);
        JobApplication application = ownedApplication(campaign.getUserId(), applicationId);
        if (!campaignId.equals(application.getCampaignId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application is not in this campaign");
        }
        applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getCampaignId, campaignId)
                .set(JobApplication::getCampaignId, null));
        appendEvent(campaign, "APPLICATION_REMOVED", "Application " + applicationId + " removed");
    }

    protected CampaignView transition(CareerCampaign campaign, Set<String> expected, String next) {
        if (!expected.contains(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Campaign status cannot transition from " + campaign.getStatus() + " to " + next);
        }
        int version = campaign.getLockVersion() == null ? 1 : campaign.getLockVersion();
        try {
            if (campaignMapper.transition(campaign.getId(), campaign.getUserId(),
                    campaign.getStatus(), next, version) != 1) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Campaign was changed by another request");
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only one active campaign is allowed");
        }
        CareerCampaign updated = owned(campaign.getUserId(), campaign.getId());
        appendEvent(updated, next, "Campaign status changed to " + next);
        return toView(updated);
    }

    private CareerCampaign owned(Long userId, Long campaignId) {
        CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO));
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign not found");
        }
        return campaign;
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found");
        }
        return application;
    }

    private void appendEvent(CareerCampaign campaign, String type, String summary) {
        CareerCampaignEvent event = new CareerCampaignEvent();
        event.setUserId(campaign.getUserId());
        event.setCampaignId(campaign.getId());
        event.setEventType(type);
        event.setSummary(summary);
        event.setOccurredAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private CampaignView toView(CareerCampaign campaign) {
        CampaignView view = new CampaignView();
        view.setId(campaign.getId());
        view.setUserId(campaign.getUserId());
        view.setName(campaign.getName());
        view.setGoal(campaign.getGoal());
        view.setStatus(campaign.getStatus());
        view.setStartedAt(campaign.getStartedAt());
        view.setCompletedAt(campaign.getCompletedAt());
        view.setArchivedAt(campaign.getArchivedAt());
        view.setLockVersion(campaign.getLockVersion());
        view.setCreatedAt(campaign.getCreatedAt());
        view.setUpdatedAt(campaign.getUpdatedAt());
        Long count = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getCampaignId, campaign.getId())
                .eq(JobApplication::getDeleted, CommonConstants.NO));
        view.setApplicationCount(count == null ? 0 : Math.toIntExact(count));
        return view;
    }

    private static void requireName(SaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Campaign name is required");
        }
    }

    private static String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }
}
