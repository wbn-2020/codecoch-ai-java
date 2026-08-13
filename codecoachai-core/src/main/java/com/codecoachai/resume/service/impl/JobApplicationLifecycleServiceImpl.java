package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.service.JobApplicationLifecycleService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobApplicationLifecycleServiceImpl implements JobApplicationLifecycleService {

    private static final Map<String, Set<String>> TRANSITIONS = transitions();

    private final JobApplicationMapper applicationMapper;
    private final JobApplicationEventMapper eventMapper;

    @Override
    public Set<String> allowedTransitions(String status) {
        return TRANSITIONS.getOrDefault(normalize(status), Set.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplication transition(Long applicationId, String targetStatus,
                                     Integer expectedLockVersion, String idempotencyKey) {
        return transitionForUser(SecurityAssert.requireLoginUserId(), applicationId,
                targetStatus, expectedLockVersion, idempotencyKey, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplication transitionForUser(Long userId, Long applicationId, String targetStatus,
                                            Integer expectedLockVersion, String idempotencyKey) {
        return transitionForUser(userId, applicationId, targetStatus,
                expectedLockVersion, idempotencyKey, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplication transition(Long applicationId, String targetStatus,
                                     Integer expectedLockVersion, String idempotencyKey,
                                     String note) {
        return transitionForUser(SecurityAssert.requireLoginUserId(), applicationId,
                targetStatus, expectedLockVersion, idempotencyKey, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplication transitionForUser(Long userId, Long applicationId, String targetStatus,
                                            Integer expectedLockVersion, String idempotencyKey,
                                            String note) {
        JobApplication current = ownedForUpdate(userId, applicationId);
        String next = requiredStatus(targetStatus);
        String normalizedNote = trimNote(note);
        int expected = requiredVersion(expectedLockVersion);
        String cleanKey = requiredKey(idempotencyKey);
        String requestHash = hash("STATUS_CHANGED|" + next + "|" + normalizedNote);
        String keyHash = hash(userId + "|STATUS_CHANGED|" + applicationId + "|" + cleanKey);
        if (keyHash != null) {
            JobApplicationEvent existing = eventMapper.selectByIdempotencyKey(userId, applicationId, keyHash);
            if (existing != null) {
                if (sameRequest(existing, requestHash, next)) {
                    return current;
                }
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Idempotency key was already used with a different application transition");
            }
        }
        String previous = normalize(current.getStatus());
        if (current.getLockVersion() != null && !current.getLockVersion().equals(expected)) {
            throw concurrent();
        }
        if (previous.equals(next)) {
            return current;
        }
        if (!allowedTransitions(previous).contains(next)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Application status cannot transition from " + previous + " to " + next);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = applicationMapper.transitionStatus(applicationId, userId, previous, next, now,
                outcomeFor(next), expected);
        if (updated != 1) {
            throw concurrent();
        }
        JobApplicationEvent event = new JobApplicationEvent();
        event.setUserId(userId);
        event.setApplicationId(applicationId);
        event.setEventType("STATUS_CHANGED");
        event.setEventTime(now);
        event.setSummary(summary(previous, next, normalizedNote));
        event.setIdempotencyKeyHash(keyHash);
        event.setRequestHash(requestHash);
        event.setResultLockVersion(expected + 1);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            JobApplicationEvent existing = eventMapper.selectByIdempotencyKey(userId, applicationId, keyHash);
            if (existing == null) {
                throw duplicate;
            }
            if (!sameRequest(existing, requestHash, next)) {
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Idempotency key was already used for a different application transition");
            }
        }
        return owned(userId, applicationId);
    }

    private JobApplication owned(Long userId, Long id) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found");
        }
        return application;
    }

    private JobApplication ownedForUpdate(Long userId, Long id) {
        JobApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDeleted, CommonConstants.NO)
                .isNull(JobApplication::getArchivedAt)
                .last("FOR UPDATE"));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found");
        }
        return application;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requiredStatus(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target application status is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static int requiredVersion(Integer value) {
        if (value == null || value < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Expected application lock version is required");
        }
        return value;
    }

    private static String requiredKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Idempotency key is required");
        }
        return value.trim();
    }

    private static String trimNote(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value.trim();
        return result.length() <= 300 ? result : result.substring(0, 300);
    }

    private static String summary(String previous, String next, String note) {
        String transition = previous + " -> " + next;
        return StringUtils.hasText(note) ? transition + " | " + note : transition;
    }

    private static boolean sameRequest(JobApplicationEvent existing, String requestHash, String targetStatus) {
        if (existing.getRequestHash() != null) {
            return existing.getRequestHash().equals(requestHash);
        }
        return transitionTarget(existing.getSummary()).equals(targetStatus);
    }

    private static String transitionTarget(String summary) {
        if (!StringUtils.hasText(summary)) {
            return "";
        }
        int separator = summary.indexOf(" -> ");
        if (separator < 0) {
            return "";
        }
        String target = summary.substring(separator + 4);
        int noteSeparator = target.indexOf(" | ");
        return (noteSeparator < 0 ? target : target.substring(0, noteSeparator))
                .trim().toUpperCase(Locale.ROOT);
    }

    private static BusinessException concurrent() {
        return new BusinessException(ErrorCode.PARAM_ERROR,
                "Application was changed by another request");
    }

    private static String outcomeFor(String status) {
        return switch (status) {
            case "ACCEPTED" -> "ACCEPTED";
            case "DECLINED" -> "DECLINED";
            case "REJECTED" -> "RECRUITER_REJECTED";
            case "WITHDRAWN" -> "WITHDRAWN";
            case "CLOSED" -> "CLOSED";
            default -> null;
        };
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static Map<String, Set<String>> transitions() {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        values.put("DRAFT", orderedSet("SAVED", "APPLIED", "WITHDRAWN", "CLOSED"));
        values.put("SAVED", orderedSet("PREPARING", "APPLIED", "WITHDRAWN", "CLOSED"));
        values.put("PREPARING", orderedSet("APPLIED", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("APPLIED", orderedSet("PREPARING", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("SCREENING", orderedSet("INTERVIEW", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("INTERVIEW", orderedSet("OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("INTERVIEWING", orderedSet("OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("OFFER", orderedSet("ACCEPTED", "DECLINED", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("ACCEPTED", orderedSet("CLOSED"));
        values.put("DECLINED", orderedSet("CLOSED"));
        values.put("REJECTED", orderedSet("CLOSED"));
        values.put("WITHDRAWN", orderedSet("CLOSED"));
        values.put("CLOSED", orderedSet("REOPENED"));
        values.put("REOPENED", orderedSet("SAVED", "PREPARING", "APPLIED", "INTERVIEWING", "OFFER", "CLOSED"));
        return Map.copyOf(values);
    }

    private static Set<String> orderedSet(String... values) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(java.util.List.of(values)));
    }
}
