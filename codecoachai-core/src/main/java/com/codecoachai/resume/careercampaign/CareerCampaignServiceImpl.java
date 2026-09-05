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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerCampaignServiceImpl implements CareerCampaignService {

    private static final Set<String> OPEN_APPLICATION_STATUSES =
            Set.of("DRAFT", "SAVED", "PREPARING", "APPLIED", "SCREENING", "INTERVIEW",
                    "INTERVIEWING", "OFFER", "REOPENED");
    private static final Set<String> APPLICATION_MUTABLE_STATUSES =
            Set.of("DRAFT", "ACTIVE", "PAUSED");

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
        String name = request.getName().trim();
        String goal = trim(request.getGoal(), 2000);
        String requestHash = requestHash("CREATE", name, goal, trim(request.getNote(), 300));
        String keyHash = keyHash(userId, "CREATE", null, request.getIdempotencyKey());
        CareerCampaignEvent replay = findEvent(userId, null, keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return toView(owned(userId, replay.getCampaignId()));
        }
        CareerCampaign campaign = new CareerCampaign();
        campaign.setUserId(userId);
        campaign.setName(name);
        campaign.setGoal(goal);
        campaign.setStatus("DRAFT");
        campaign.setLockVersion(1);
        campaignMapper.insert(campaign);
        appendEvent(campaign, "CREATED", summary("Campaign created", request.getNote()),
                keyHash, requestHash, campaign.getLockVersion());
        return toView(campaign);
    }

    @Override
    public CampaignView get(Long campaignId) {
        return toView(owned(SecurityAssert.requireLoginUserId(), campaignId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView update(Long campaignId, SaveRequest request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerCampaign campaign = owned(userId, campaignId);
        requireName(request);
        if ("ARCHIVED".equals(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Archived campaign cannot be edited");
        }
        String name = request.getName().trim();
        String goal = trim(request.getGoal(), 2000);
        int requestVersion = request.getExpectedLockVersion() == null
                ? currentVersion(campaign) : request.getExpectedLockVersion();
        String requestHash = requestHash("UPDATE", name, goal, requestVersion, trim(request.getNote(), 300));
        String keyHash = keyHash(userId, "UPDATE", campaignId, request.getIdempotencyKey());
        CareerCampaignEvent replay = findEvent(userId, campaignId, keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return toView(owned(userId, campaignId));
        }
        int expectedVersion = expectedVersion(campaign, requestVersion);
        int updatedRows = campaignMapper.update(null, new LambdaUpdateWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                .eq(CareerCampaign::getLockVersion, expectedVersion)
                .set(CareerCampaign::getName, name)
                .set(CareerCampaign::getGoal, goal)
                .setSql("lock_version = lock_version + 1"));
        if (updatedRows != 1) {
            CareerCampaignEvent winner = findEvent(userId, campaignId, keyHash);
            if (winner != null) {
                assertSameRequest(winner, requestHash);
                return toView(owned(userId, campaignId));
            }
            throw concurrent();
        }
        CareerCampaign result = owned(userId, campaignId);
        appendEvent(result, "UPDATED", summary("Campaign updated", request.getNote()),
                keyHash, requestHash, result.getLockVersion());
        return toView(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView activate(Long campaignId) {
        return activate(campaignId, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView activate(Long campaignId, Integer expectedLockVersion,
                                 String idempotencyKey, String note) {
        CareerCampaign campaign = ownedForUpdate(SecurityAssert.requireLoginUserId(), campaignId);
        requireCommandMetadata(expectedLockVersion, idempotencyKey);
        if (campaignMapper.countActive(campaign.getUserId()) > 0 && !"ACTIVE".equals(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only one active campaign is allowed");
        }
        return transition(campaign, Set.of("DRAFT", "PAUSED"), "ACTIVE",
                expectedLockVersion, idempotencyKey, note, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView complete(Long campaignId, boolean retainOpenApplications) {
        return complete(campaignId, retainOpenApplications, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView complete(Long campaignId, boolean retainOpenApplications,
                                 Integer expectedLockVersion, String idempotencyKey, String note) {
        return completeInternal(SecurityAssert.requireLoginUserId(), campaignId, retainOpenApplications,
                expectedLockVersion, idempotencyKey, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView completeForUser(Long userId, Long campaignId, boolean retainOpenApplications,
                                        String idempotencyKey, String note) {
        return completeInternal(userId, campaignId, retainOpenApplications,
                currentVersion(ownedForUpdate(userId, campaignId)), idempotencyKey, note);
    }

    private CampaignView completeInternal(Long userId, Long campaignId, boolean retainOpenApplications,
                                          Integer expectedLockVersion, String idempotencyKey, String note) {
        CareerCampaign campaign = ownedForUpdate(userId, campaignId);
        requireCommandMetadata(expectedLockVersion, idempotencyKey);
        String requestHash = transitionRequestHash("COMPLETED", note, retainOpenApplications);
        String keyHash = keyHash(campaign.getUserId(), "STATUS_COMPLETED", campaignId, idempotencyKey);
        CareerCampaignEvent replay = findEvent(campaign.getUserId(), campaignId, keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return toView(owned(campaign.getUserId(), campaignId));
        }
        assertTransition(campaign, Set.of("ACTIVE", "PAUSED"), "COMPLETED");
        long open = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getCampaignId, campaignId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt)
                .in(JobApplication::getStatus, OPEN_APPLICATION_STATUSES));
        if (open > 0 && !retainOpenApplications) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Campaign still contains open applications");
        }
        return transition(campaign, Set.of("ACTIVE", "PAUSED"), "COMPLETED",
                expectedLockVersion, idempotencyKey, note, retainOpenApplications);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView archive(Long campaignId) {
        return archive(campaignId, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView archive(Long campaignId, Integer expectedLockVersion,
                                String idempotencyKey, String note) {
        CareerCampaign campaign = ownedForUpdate(SecurityAssert.requireLoginUserId(), campaignId);
        requireCommandMetadata(expectedLockVersion, idempotencyKey);
        return transition(campaign, Set.of("COMPLETED"), "ARCHIVED",
                expectedLockVersion, idempotencyKey, note, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView addApplication(Long campaignId, Long applicationId) {
        return addApplication(campaignId, applicationId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignView addApplication(Long campaignId, Long applicationId, String idempotencyKey) {
        CareerCampaign campaign = ownedForUpdate(SecurityAssert.requireLoginUserId(), campaignId);
        assertApplicationsMutable(campaign);
        String cleanKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = requestHash("APPLICATION_ADDED", applicationId);
        String keyHash = keyHash(campaign.getUserId(), "APPLICATION_ADDED", campaignId, cleanKey);
        CareerCampaignEvent replay = findEvent(campaign.getUserId(), campaignId, keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return toView(owned(campaign.getUserId(), campaignId));
        }
        JobApplication application = ownedApplication(campaign.getUserId(), applicationId);
        if (campaignId.equals(application.getCampaignId())) {
            appendEvent(campaign, "APPLICATION_ADDED", "Application " + applicationId + " already belonged",
                    keyHash, requestHash, campaign.getLockVersion());
            return toView(campaign);
        }
        if (application.getCampaignId() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "Application already belongs to another campaign");
        }
        int updatedRows = applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt)
                .isNull(JobApplication::getCampaignId)
                .set(JobApplication::getCampaignId, campaignId)
                .setSql("lock_version = lock_version + 1"));
        if (updatedRows != 1) {
            CareerCampaignEvent winner = findEvent(campaign.getUserId(), campaignId, keyHash);
            if (winner != null) {
                assertSameRequest(winner, requestHash);
                return toView(owned(campaign.getUserId(), campaignId));
            }
            throw concurrent();
        }
        appendEvent(campaign, "APPLICATION_ADDED", "Application " + applicationId + " added",
                keyHash, requestHash, campaign.getLockVersion());
        return toView(campaign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApplication(Long campaignId, Long applicationId) {
        removeApplication(campaignId, applicationId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApplication(Long campaignId, Long applicationId, String idempotencyKey) {
        CareerCampaign campaign = ownedForUpdate(SecurityAssert.requireLoginUserId(), campaignId);
        assertApplicationsMutable(campaign);
        String cleanKey = requireIdempotencyKey(idempotencyKey);
        String requestHash = requestHash("APPLICATION_REMOVED", applicationId);
        String keyHash = keyHash(campaign.getUserId(), "APPLICATION_REMOVED", campaignId, cleanKey);
        CareerCampaignEvent replay = findEvent(campaign.getUserId(), campaignId, keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return;
        }
        JobApplication application = ownedApplication(campaign.getUserId(), applicationId);
        if (!campaignId.equals(application.getCampaignId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application is not in this campaign");
        }
        int updatedRows = applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getCampaignId, campaignId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt)
                .set(JobApplication::getCampaignId, null)
                .setSql("lock_version = lock_version + 1"));
        if (updatedRows != 1) {
            CareerCampaignEvent winner = findEvent(campaign.getUserId(), campaignId, keyHash);
            if (winner != null) {
                assertSameRequest(winner, requestHash);
                return;
            }
            throw concurrent();
        }
        appendEvent(campaign, "APPLICATION_REMOVED", "Application " + applicationId + " removed",
                keyHash, requestHash, campaign.getLockVersion());
    }

    protected CampaignView transition(CareerCampaign campaign, Set<String> expected, String next,
                                      Integer expectedLockVersion, String idempotencyKey,
                                      String note, boolean retainOpenApplications) {
        requireCommandMetadata(expectedLockVersion, idempotencyKey);
        String requestHash = transitionRequestHash(next, note, retainOpenApplications);
        String keyHash = keyHash(campaign.getUserId(), "STATUS_" + next, campaign.getId(), idempotencyKey);
        CareerCampaignEvent replay = findEvent(campaign.getUserId(), campaign.getId(), keyHash);
        if (replay != null) {
            assertSameRequest(replay, requestHash);
            return toView(owned(campaign.getUserId(), campaign.getId()));
        }
        int version = expectedVersion(campaign, expectedLockVersion);
        assertTransition(campaign, expected, next);
        try {
            if (campaignMapper.transition(campaign.getId(), campaign.getUserId(),
                    campaign.getStatus(), next, version) != 1) {
                CareerCampaignEvent winner = findEvent(campaign.getUserId(), campaign.getId(), keyHash);
                if (winner != null) {
                    assertSameRequest(winner, requestHash);
                    return toView(owned(campaign.getUserId(), campaign.getId()));
                }
                throw concurrent();
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only one active campaign is allowed");
        }
        CareerCampaign updated = owned(campaign.getUserId(), campaign.getId());
        appendEvent(updated, next, summary("Campaign status changed to " + next, note),
                keyHash, requestHash, updated.getLockVersion());
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

    private CareerCampaign ownedForUpdate(Long userId, Long campaignId) {
        CareerCampaign campaign = campaignMapper.selectOne(new LambdaQueryWrapper<CareerCampaign>()
                .eq(CareerCampaign::getId, campaignId)
                .eq(CareerCampaign::getUserId, userId)
                .eq(CareerCampaign::getDeleted, CommonConstants.NO)
                .last("FOR UPDATE"));
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Campaign not found");
        }
        return campaign;
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found");
        }
        return application;
    }

    private void appendEvent(CareerCampaign campaign, String type, String summary,
                             String keyHash, String requestHash, Integer resultLockVersion) {
        CareerCampaignEvent event = new CareerCampaignEvent();
        event.setUserId(campaign.getUserId());
        event.setCampaignId(campaign.getId());
        event.setEventType(type);
        event.setSummary(summary);
        event.setIdempotencyKeyHash(keyHash);
        event.setRequestHash(requestHash);
        event.setResultLockVersion(resultLockVersion);
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
        view.setAllowedTransitions(allowedTransitions(campaign.getStatus()));
        view.setStartedAt(campaign.getStartedAt());
        view.setCompletedAt(campaign.getCompletedAt());
        view.setArchivedAt(campaign.getArchivedAt());
        view.setLockVersion(campaign.getLockVersion());
        view.setCreatedAt(campaign.getCreatedAt());
        view.setUpdatedAt(campaign.getUpdatedAt());
        Long count = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, campaign.getUserId())
                .eq(JobApplication::getCampaignId, campaign.getId())
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt));
        view.setApplicationCount(count == null ? 0 : Math.toIntExact(count));
        return view;
    }

    private CareerCampaignEvent findEvent(Long userId, Long campaignId, String keyHash) {
        if (!StringUtils.hasText(keyHash)) {
            return null;
        }
        return eventMapper.selectOne(new LambdaQueryWrapper<CareerCampaignEvent>()
                .eq(CareerCampaignEvent::getUserId, userId)
                .eq(campaignId != null, CareerCampaignEvent::getCampaignId, campaignId)
                .eq(CareerCampaignEvent::getIdempotencyKeyHash, keyHash)
                .eq(CareerCampaignEvent::getDeleted, CommonConstants.NO)
                .orderByAsc(CareerCampaignEvent::getId)
                .last("LIMIT 1"));
    }

    private static void assertSameRequest(CareerCampaignEvent event, String requestHash) {
        if (!Objects.equals(event.getRequestHash(), requestHash)) {
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "Idempotency key was already used for a different campaign request");
        }
    }

    private static void assertTransition(CareerCampaign campaign, Set<String> expected, String next) {
        if (!expected.contains(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Campaign status cannot transition from " + campaign.getStatus() + " to " + next);
        }
    }

    private static void assertApplicationsMutable(CareerCampaign campaign) {
        if (!APPLICATION_MUTABLE_STATUSES.contains(campaign.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Completed or archived campaign applications cannot be changed");
        }
    }

    private static int expectedVersion(CareerCampaign campaign, Integer requested) {
        int current = currentVersion(campaign);
        if (requested == null || requested < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Expected campaign lock version is invalid");
        }
        if (requested != current) {
            throw concurrent();
        }
        return requested;
    }

    private static int currentVersion(CareerCampaign campaign) {
        return campaign.getLockVersion() == null ? 1 : campaign.getLockVersion();
    }

    private static List<String> allowedTransitions(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase(Locale.ROOT)) {
            case "DRAFT" -> List.of("ACTIVE");
            case "ACTIVE" -> List.of("COMPLETED");
            case "PAUSED" -> List.of("ACTIVE", "COMPLETED");
            case "COMPLETED" -> List.of("ARCHIVED");
            default -> List.of();
        };
    }

    private static String transitionRequestHash(String next, String note,
                                                boolean retainOpenApplications) {
        return requestHash("STATUS", next, trim(note, 300), retainOpenApplications);
    }

    private static String keyHash(Long userId, String operation, Long campaignId, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return hash(userId + "|" + operation + "|" + (campaignId == null ? "" : campaignId)
                + "|" + key.trim());
    }

    private static void requireCommandMetadata(Integer expectedLockVersion, String idempotencyKey) {
        if (expectedLockVersion == null || expectedLockVersion < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Expected campaign lock version is required");
        }
        requireIdempotencyKey(idempotencyKey);
    }

    private static String requireIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Idempotency key is required");
        }
        return value.trim();
    }

    private static String requestHash(Object... values) {
        StringBuilder normalized = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value);
            normalized.append(text.length()).append(':').append(text).append('|');
        }
        return hash(normalized.toString());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String summary(String base, String note) {
        String cleanNote = trim(note, 300);
        return StringUtils.hasText(cleanNote) ? base + " | " + cleanNote : base;
    }

    private static BusinessException concurrent() {
        return new BusinessException(ErrorCode.PARAM_ERROR,
                "Campaign was changed by another request");
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
