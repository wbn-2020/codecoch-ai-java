package com.codecoachai.resume.careercontact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.careercontact.dto.CareerActivityRecordDTO;
import com.codecoachai.resume.careercontact.dto.CareerActivitySaveDTO;
import com.codecoachai.resume.careercontact.dto.CareerCommunicationDraftDTO;
import com.codecoachai.resume.careercontact.dto.CareerContactSaveDTO;
import com.codecoachai.resume.careercontact.dto.CareerInterviewRoundContactSaveDTO;
import com.codecoachai.resume.careercontact.entity.CareerActivity;
import com.codecoachai.resume.careercontact.entity.CareerActivityEvent;
import com.codecoachai.resume.careercontact.entity.CareerContact;
import com.codecoachai.resume.careercontact.entity.CareerContactApplication;
import com.codecoachai.resume.careercontact.entity.CareerInterviewRoundContact;
import com.codecoachai.resume.careercontact.mapper.CareerActivityEventMapper;
import com.codecoachai.resume.careercontact.mapper.CareerActivityMapper;
import com.codecoachai.resume.careercontact.mapper.CareerContactApplicationMapper;
import com.codecoachai.resume.careercontact.mapper.CareerContactMapper;
import com.codecoachai.resume.careercontact.mapper.CareerInterviewRoundContactMapper;
import com.codecoachai.resume.careercontact.service.CareerCommunicationDraftGenerator;
import com.codecoachai.resume.careercontact.service.CareerContactReminderCandidateService;
import com.codecoachai.resume.careercontact.service.CareerContactService;
import com.codecoachai.resume.careercontact.vo.CareerActivityVO;
import com.codecoachai.resume.careercontact.vo.CareerCommunicationDraftVO;
import com.codecoachai.resume.careercontact.vo.CareerContactReminderCandidateVO;
import com.codecoachai.resume.careercontact.vo.CareerContactVO;
import com.codecoachai.resume.careercontact.vo.CareerInterviewRoundContactVO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CareerContactServiceImpl
        implements CareerContactService, CareerContactReminderCandidateService {

    private static final int MAX_LIST = 100;
    private final CareerContactMapper contactMapper;
    private final CareerContactApplicationMapper contactApplicationMapper;
    private final CareerActivityMapper activityMapper;
    private final CareerActivityEventMapper activityEventMapper;
    private final CareerInterviewRoundContactMapper roundContactMapper;
    private final JobApplicationMapper applicationMapper;
    private final ObjectProvider<CareerCommunicationDraftGenerator> draftGeneratorProvider;

    @Override
    public List<CareerContactVO> listContacts(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        List<CareerContactApplication> relations = contactApplicationMapper.selectList(
                new LambdaQueryWrapper<CareerContactApplication>()
                        .eq(CareerContactApplication::getUserId, userId)
                        .eq(CareerContactApplication::getApplicationId, applicationId)
                        .eq(CareerContactApplication::getDeleted, CommonConstants.NO)
                        .orderByAsc(CareerContactApplication::getId)
                        .last("LIMIT " + MAX_LIST));
        List<CareerContactVO> views = new ArrayList<>();
        for (CareerContactApplication relation : relations) {
            CareerContact contact = ownedContact(userId, relation.getContactId());
            CareerContactVO view = toContactView(contact);
            view.setApplicationId(applicationId);
            view.setRelationshipType(relation.getRelationshipType());
            views.add(view);
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerContactVO createContact(CareerContactSaveDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, request.getApplicationId());
        CareerContact contact = new CareerContact();
        contact.setUserId(userId);
        fillContact(contact, request);
        contactMapper.insert(contact);
        CareerContactApplication relation = new CareerContactApplication();
        relation.setUserId(userId);
        relation.setContactId(contact.getId());
        relation.setApplicationId(request.getApplicationId());
        relation.setRelationshipType(truncate(request.getRelationshipType(), 80));
        contactApplicationMapper.insert(relation);
        CareerContactVO view = toContactView(contact);
        view.setApplicationId(request.getApplicationId());
        view.setRelationshipType(relation.getRelationshipType());
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerContactVO updateContact(Long contactId, CareerContactSaveDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerContact contact = ownedContact(userId, contactId);
        if (request.getApplicationId() != null) {
            ownedApplication(userId, request.getApplicationId());
        }
        fillContact(contact, request);
        contactMapper.updateById(contact);
        return toContactView(contact);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContact(Long contactId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedContact(userId, contactId);
        contactMapper.delete(new LambdaQueryWrapper<CareerContact>()
                .eq(CareerContact::getId, contactId)
                .eq(CareerContact::getUserId, userId));
        contactApplicationMapper.delete(new LambdaQueryWrapper<CareerContactApplication>()
                .eq(CareerContactApplication::getContactId, contactId)
                .eq(CareerContactApplication::getUserId, userId));
    }

    @Override
    public List<CareerActivityVO> listActivities(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        return activityMapper.selectList(new LambdaQueryWrapper<CareerActivity>()
                        .eq(CareerActivity::getUserId, userId)
                        .eq(CareerActivity::getApplicationId, applicationId)
                        .eq(CareerActivity::getDeleted, CommonConstants.NO)
                        .orderByDesc(CareerActivity::getOccurredAt)
                        .orderByDesc(CareerActivity::getId)
                        .last("LIMIT " + MAX_LIST))
                .stream().map(this::toActivityView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerActivityVO createActivity(Long applicationId, CareerActivitySaveDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerContact contact = validateContactRelation(userId, applicationId, request.getContactId());
        String requestHash = hash(normalizedActivityPayload(request));
        String keyHash = hash(userId + "|CREATE_ACTIVITY|" + applicationId + "|"
                + request.getIdempotencyKey().trim());
        CareerActivity existing = activityMapper.selectByIdempotency(userId, keyHash);
        if (existing != null) {
            assertSameRequest(existing.getRequestHash(), requestHash);
            return toActivityView(existing);
        }
        CareerActivity activity = new CareerActivity();
        activity.setUserId(userId);
        activity.setApplicationId(applicationId);
        activity.setContactId(contact == null ? null : contact.getId());
        activity.setActivityType(normalize(request.getActivityType(), "GENERAL"));
        activity.setChannelType(truncate(request.getChannelType(), 40));
        activity.setSubject(requireText(request.getSubject(), "活动主题不能为空", 200));
        activity.setSummary(requireText(request.getSummary(), "活动摘要不能为空", 2000));
        activity.setOccurredAt(request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt());
        activity.setNextFollowUpAt(request.getNextFollowUpAt());
        activity.setStatus("READY");
        activity.setIdempotencyKeyHash(keyHash);
        activity.setRequestHash(requestHash);
        try {
            activityMapper.insert(activity);
        } catch (DuplicateKeyException ex) {
            CareerActivity winner = activityMapper.selectByIdempotency(userId, keyHash);
            if (winner == null) {
                throw ex;
            }
            assertSameRequest(winner.getRequestHash(), requestHash);
            return toActivityView(winner);
        }
        return toActivityView(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerActivityVO recordActivity(Long activityId, CareerActivityRecordDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerActivity activity = ownedActivity(userId, activityId);
        String requestHash = hash(activityId + "|" + request.getIdempotencyKey().trim());
        String keyHash = hash(userId + "|RECORD_ACTIVITY|" + activityId + "|"
                + request.getIdempotencyKey().trim());
        CareerActivityEvent existing = activityEventMapper.selectByIdempotency(userId, activityId, keyHash);
        if (existing != null) {
            assertSameRequest(existing.getRequestHash(), requestHash);
            return toActivityView(activity);
        }
        if ("CANCELLED".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已取消的活动不能标记为已发生");
        }
        if (!"RECORDED".equals(activity.getStatus())) {
            activity.setStatus("RECORDED");
            if (activity.getOccurredAt() == null) {
                activity.setOccurredAt(LocalDateTime.now());
            }
            activityMapper.updateById(activity);
        }
        CareerActivityEvent event = new CareerActivityEvent();
        event.setUserId(userId);
        event.setActivityId(activityId);
        event.setEventType("RECORDED");
        event.setEventTime(LocalDateTime.now());
        event.setIdempotencyKeyHash(keyHash);
        event.setRequestHash(requestHash);
        try {
            activityEventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            CareerActivityEvent winner =
                    activityEventMapper.selectByIdempotency(userId, activityId, keyHash);
            if (winner == null) {
                throw ex;
            }
            assertSameRequest(winner.getRequestHash(), requestHash);
        }
        return toActivityView(activity);
    }

    @Override
    public CareerCommunicationDraftVO createCommunicationDraft(Long applicationId,
                                                                CareerCommunicationDraftDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(userId, applicationId);
        CareerContact contact = validateContactRelation(userId, applicationId, request.getContactId());
        CareerCommunicationDraftGenerator generator = draftGeneratorProvider.getIfAvailable();
        if (generator != null) {
            return generator.generate(userId, applicationId,
                    contact == null ? null : contact.getDisplayName(), request);
        }
        CareerCommunicationDraftVO fallback = new CareerCommunicationDraftVO();
        fallback.setSubject("跟进：" + truncate(request.getPurpose(), 120));
        fallback.setBody("你好，" + (contact == null ? "招聘流程联系人" : contact.getDisplayName())
                + ",\n\n" + truncate(request.getPurpose(), 500)
                + "\n\n此致");
        fallback.setFactsUsed(StringUtils.hasText(request.getFacts())
                ? List.of(truncate(request.getFacts().trim(), 500)) : List.of());
        fallback.setWarnings(List.of("这是规则模板生成的草稿，复制前请核对事实和措辞。"));
        fallback.setConfidence("LOW");
        fallback.setFallback("RULE_TEMPLATE");
        return fallback;
    }

    @Override
    public List<CareerInterviewRoundContactVO> listRoundContacts(Long roundId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedRound(userId, roundId);
        return roundContactMapper.selectViews(userId, roundId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerInterviewRoundContactVO addRoundContact(Long roundId,
                                                         CareerInterviewRoundContactSaveDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedRound(userId, roundId);
        CareerContact contact = ownedContact(userId, request.getContactId());
        CareerInterviewRoundContact existing = roundContactMapper.selectOne(
                new LambdaQueryWrapper<CareerInterviewRoundContact>()
                        .eq(CareerInterviewRoundContact::getUserId, userId)
                        .eq(CareerInterviewRoundContact::getInterviewRoundId, roundId)
                        .eq(CareerInterviewRoundContact::getContactId, contact.getId())
                        .eq(CareerInterviewRoundContact::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (existing != null) {
            return roundContactMapper.selectViews(userId, roundId).stream()
                    .filter(view -> Objects.equals(view.getId(), existing.getId()))
                    .findFirst().orElseThrow();
        }
        CareerInterviewRoundContact relation = new CareerInterviewRoundContact();
        relation.setUserId(userId);
        relation.setInterviewRoundId(roundId);
        relation.setContactId(contact.getId());
        relation.setRelationshipType(truncate(request.getRelationshipType(), 80));
        roundContactMapper.insert(relation);
        CareerInterviewRoundContactVO view = new CareerInterviewRoundContactVO();
        view.setId(relation.getId());
        view.setInterviewRoundId(roundId);
        view.setContactId(contact.getId());
        view.setDisplayName(contact.getDisplayName());
        view.setRoleType(contact.getRoleType());
        view.setRelationshipType(relation.getRelationshipType());
        view.setCreatedAt(relation.getCreatedAt());
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRoundContact(Long roundContactId) {
        Long userId = SecurityAssert.requireLoginUserId();
        CareerInterviewRoundContact relation = roundContactMapper.selectOne(
                new LambdaQueryWrapper<CareerInterviewRoundContact>()
                        .eq(CareerInterviewRoundContact::getId, roundContactId)
                        .eq(CareerInterviewRoundContact::getUserId, userId)
                        .eq(CareerInterviewRoundContact::getDeleted, CommonConstants.NO)
                        .last("LIMIT 1"));
        if (relation == null) {
            throw notFound("面试轮次联系人关系不存在");
        }
        roundContactMapper.deleteById(relation.getId());
    }

    @Override
    public List<CareerContactReminderCandidateVO> listReminderCandidates(Long userId,
                                                                           LocalDate date,
                                                                           LocalDateTime now) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDateTime until = targetDate.plusDays(1).atStartOfDay();
        Map<Long, CareerContactReminderCandidateVO> candidates = new LinkedHashMap<>();
        for (CareerActivity activity : activityMapper.selectDueFollowUps(userId, until, MAX_LIST)) {
            if (activity.getNextFollowUpAt() == null
                    || activity.getNextFollowUpAt().toLocalDate().isAfter(targetDate)) {
                continue;
            }
            CareerContact contact = activity.getContactId() == null ? null
                    : contactMapper.selectById(activity.getContactId());
            if (contact == null || !Objects.equals(contact.getUserId(), userId)) {
                continue;
            }
            CareerContactReminderCandidateVO candidate = new CareerContactReminderCandidateVO();
            candidate.setBizId(String.valueOf(contact.getId()));
            candidate.setTitle("跟进联系人：" + contact.getDisplayName());
            candidate.setContent(truncate(activity.getSubject() + ": " + activity.getSummary(), 500));
            candidate.setActionUrl("/applications/" + activity.getApplicationId() + "?tab=contacts");
            candidate.setPlanDate(targetDate);
            candidates.putIfAbsent(contact.getId(), candidate);
        }
        return List.copyOf(candidates.values());
    }

    private CareerContact validateContactRelation(Long userId, Long applicationId, Long contactId) {
        if (contactId == null) {
            return null;
        }
        CareerContact contact = ownedContact(userId, contactId);
        Long count = contactApplicationMapper.selectCount(new LambdaQueryWrapper<CareerContactApplication>()
                .eq(CareerContactApplication::getUserId, userId)
                .eq(CareerContactApplication::getApplicationId, applicationId)
                .eq(CareerContactApplication::getContactId, contactId)
                .eq(CareerContactApplication::getDeleted, CommonConstants.NO));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "联系人未关联到当前机会");
        }
        return contact;
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (application == null) {
            throw notFound("机会不存在");
        }
        return application;
    }

    private CareerContact ownedContact(Long userId, Long contactId) {
        CareerContact contact = contactMapper.selectOne(new LambdaQueryWrapper<CareerContact>()
                .eq(CareerContact::getId, contactId)
                .eq(CareerContact::getUserId, userId)
                .eq(CareerContact::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (contact == null) {
            throw notFound("联系人不存在");
        }
        return contact;
    }

    private CareerActivity ownedActivity(Long userId, Long activityId) {
        CareerActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<CareerActivity>()
                .eq(CareerActivity::getId, activityId)
                .eq(CareerActivity::getUserId, userId)
                .eq(CareerActivity::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (activity == null) {
            throw notFound("活动不存在");
        }
        return activity;
    }

    private void ownedRound(Long userId, Long roundId) {
        if (roundContactMapper.selectOwnedRound(userId, roundId) == null) {
            throw notFound("面试轮次不存在");
        }
    }

    private void fillContact(CareerContact contact, CareerContactSaveDTO request) {
        contact.setDisplayName(requireText(request.getDisplayName(), "联系人显示名称不能为空", 160));
        contact.setRoleType(StringUtils.hasText(request.getRoleType())
                ? truncate(request.getRoleType().trim(), 32) : "OTHER");
        contact.setChannelType(truncate(request.getChannelType(), 40));
        contact.setMaskedContactHint(validateMaskedHint(request.getMaskedContactHint()));
        contact.setRelationshipSummary(truncate(request.getRelationshipSummary(), 1000));
    }

    private CareerContactVO toContactView(CareerContact contact) {
        CareerContactVO view = new CareerContactVO();
        view.setId(contact.getId());
        view.setDisplayName(contact.getDisplayName());
        view.setRoleType(contact.getRoleType());
        view.setChannelType(contact.getChannelType());
        view.setMaskedContactHint(contact.getMaskedContactHint());
        view.setRelationshipSummary(contact.getRelationshipSummary());
        view.setCreatedAt(contact.getCreatedAt());
        view.setUpdatedAt(contact.getUpdatedAt());
        return view;
    }

    private CareerActivityVO toActivityView(CareerActivity activity) {
        CareerActivityVO view = new CareerActivityVO();
        view.setId(activity.getId());
        view.setApplicationId(activity.getApplicationId());
        view.setContactId(activity.getContactId());
        view.setActivityType(activity.getActivityType());
        view.setChannelType(activity.getChannelType());
        view.setSubject(activity.getSubject());
        view.setSummary(activity.getSummary());
        view.setOccurredAt(activity.getOccurredAt());
        view.setNextFollowUpAt(activity.getNextFollowUpAt());
        view.setStatus(activity.getStatus());
        view.setCreatedAt(activity.getCreatedAt());
        return view;
    }

    private String validateMaskedHint(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        long digits = normalized.chars().filter(Character::isDigit).count();
        if (normalized.contains("@") && !normalized.contains("*")
                || digits > 4 && !normalized.contains("*")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "只允许保存已遮罩的联系方式提示");
        }
        return truncate(normalized, 160);
    }

    private String normalizedActivityPayload(CareerActivitySaveDTO request) {
        return String.join("|", safe(request.getContactId()), safe(request.getActivityType()),
                safe(request.getChannelType()), safe(request.getSubject()), safe(request.getSummary()),
                safe(request.getOccurredAt()), safe(request.getNextFollowUpAt()));
    }

    private void assertSameRequest(String existingHash, String requestHash) {
        if (!Objects.equals(existingHash, requestHash)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该幂等键已用于不同请求内容");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return truncate(value.trim(), maxLength);
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? truncate(value.trim().toUpperCase(Locale.ROOT), 40) : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }
}
