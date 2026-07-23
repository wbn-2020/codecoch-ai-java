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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                targetStatus, expectedLockVersion, idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplication transitionForUser(Long userId, Long applicationId, String targetStatus,
                                            Integer expectedLockVersion, String idempotencyKey) {
        JobApplication current = owned(userId, applicationId);
        String next = normalize(targetStatus);
        String keyHash = idempotencyKey == null || idempotencyKey.isBlank()
                ? null
                : hash(userId + "|STATUS_CHANGED|" + applicationId + "|" + idempotencyKey.trim());
        if (keyHash != null) {
            JobApplicationEvent existing = eventMapper.selectByIdempotencyKey(userId, applicationId, keyHash);
            if (existing != null) {
                if (existing.getSummary() != null && existing.getSummary().endsWith(" -> " + next)) {
                    return current;
                }
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "Idempotency key was already used with a different status");
            }
        }
        String previous = normalize(current.getStatus());
        if (previous.equals(next)) {
            return current;
        }
        if (!allowedTransitions(previous).contains(next)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Application status cannot transition from " + previous + " to " + next);
        }
        int expected = expectedLockVersion == null
                ? (current.getLockVersion() == null ? 1 : current.getLockVersion())
                : expectedLockVersion;
        if (current.getLockVersion() != null && !current.getLockVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Application was changed by another request");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = applicationMapper.transitionStatus(applicationId, userId, previous, next, now,
                outcomeFor(next), expected);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Application was changed by another request");
        }
        JobApplicationEvent event = new JobApplicationEvent();
        event.setUserId(userId);
        event.setApplicationId(applicationId);
        event.setEventType("STATUS_CHANGED");
        event.setEventTime(now);
        event.setSummary(previous + " -> " + next);
        event.setIdempotencyKeyHash(keyHash);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            JobApplicationEvent existing = eventMapper.selectByIdempotencyKey(userId, applicationId, keyHash);
            if (existing == null) {
                throw duplicate;
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

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
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
        values.put("DRAFT", Set.of("SAVED", "APPLIED", "WITHDRAWN", "CLOSED"));
        values.put("SAVED", Set.of("PREPARING", "APPLIED", "WITHDRAWN", "CLOSED"));
        values.put("PREPARING", Set.of("APPLIED", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("APPLIED", Set.of("PREPARING", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("SCREENING", Set.of("INTERVIEW", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("INTERVIEW", Set.of("OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("INTERVIEWING", Set.of("OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("OFFER", Set.of("ACCEPTED", "DECLINED", "REJECTED", "WITHDRAWN", "CLOSED"));
        values.put("ACCEPTED", Set.of("CLOSED"));
        values.put("DECLINED", Set.of("CLOSED"));
        values.put("REJECTED", Set.of("CLOSED"));
        values.put("WITHDRAWN", Set.of("CLOSED"));
        values.put("CLOSED", Set.of("REOPENED"));
        values.put("REOPENED", Set.of("SAVED", "PREPARING", "APPLIED", "INTERVIEWING", "OFFER", "CLOSED"));
        return Map.copyOf(values);
    }
}
