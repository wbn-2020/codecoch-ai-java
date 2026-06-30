package com.codecoachai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.task.controller.NotificationController.NotificationVO;
import com.codecoachai.task.domain.vo.ReminderCandidateVO;
import com.codecoachai.task.domain.entity.Notification;
import com.codecoachai.task.domain.entity.NotificationRead;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.mapper.NotificationMapper;
import com.codecoachai.task.mapper.NotificationReadMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final Long BROADCAST_USER_ID = 0L;

    private final NotificationMapper notificationMapper;
    private final NotificationReadMapper notificationReadMapper;
    private final NotificationCommandService notificationCommandService;
    private final AiFeignClient aiFeignClient;
    private final ResumeFeignClient resumeFeignClient;

    public PageResult<NotificationVO> pageMyNotifications(Long userId, Long pageNo, Long pageSize, Integer readStatus,
                                                          String type) {
        List<ReminderCandidateVO> reminderCandidates = reminderCandidates(userId);
        ensureDailyReminders(userId, reminderCandidates);
        Page<Notification> page = notificationMapper.selectUserNotificationPage(Page.of(pageNo, pageSize), userId,
                readStatus, type);
        Set<Long> readBroadcastIds = readBroadcastIds(userId, page.getRecords());
        Map<String, ReminderCandidateVO> reminderContractMap = buildReminderContractMap(reminderCandidates);
        List<NotificationVO> records = page.getRecords().stream()
                .map(notification -> toVO(notification,
                        readBroadcastIds.contains(notification.getId()),
                        reminderContractMap.get(reminderKey(notification.getType(), notification.getBizType(), notification.getBizId()))))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Long countUnread(Long userId) {
        ensureDailyReminders(userId, reminderCandidates(userId));
        return safeLong(notificationMapper.countUnreadForUser(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            return;
        }
        if (userId.equals(notification.getUserId())) {
            notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId)
                    .set(Notification::getReadStatus, 1)
                    .set(Notification::getReadAt, LocalDateTime.now()));
            return;
        }
        if (BROADCAST_USER_ID.equals(notification.getUserId())) {
            insertBroadcastRead(userId, id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadStatus, 0)
                .set(Notification::getReadStatus, 1)
                .set(Notification::getReadAt, LocalDateTime.now()));
        notificationMapper.insertMissingBroadcastReads(userId);
    }

    private Set<Long> readBroadcastIds(Long userId, List<Notification> notifications) {
        List<Long> broadcastIds = notifications.stream()
                .filter(notification -> BROADCAST_USER_ID.equals(notification.getUserId()))
                .map(Notification::getId)
                .toList();
        if (broadcastIds.isEmpty()) {
            return Set.of();
        }
        List<NotificationRead> reads = notificationReadMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                .eq(NotificationRead::getUserId, userId)
                .in(NotificationRead::getNotificationId, broadcastIds));
        Set<Long> ids = new HashSet<>();
        reads.forEach(read -> ids.add(read.getNotificationId()));
        return ids;
    }

    private void insertBroadcastRead(Long userId, Long notificationId) {
        notificationMapper.upsertBroadcastRead(userId, notificationId);
    }

    private NotificationVO toVO(Notification notification, boolean broadcastRead, ReminderCandidateVO reminderCandidate) {
        NotificationVO vo = NotificationVO.from(notification);
        if (reminderCandidate != null) {
            vo.setActionUrl(reminderCandidate.getActionUrl());
            vo.setFallbackPath(reminderCandidate.getFallbackPath());
            vo.setFallbackLabel(reminderCandidate.getFallbackLabel());
            vo.setPlanDate(reminderCandidate.getPlanDate());
        }
        if (BROADCAST_USER_ID.equals(notification.getUserId())) {
            int effectiveReadStatus = broadcastRead ? 1 : 0;
            vo.setReadStatus(effectiveReadStatus);
            vo.setIsRead(effectiveReadStatus);
            if (!broadcastRead) {
                vo.setReadAt(null);
            }
        }
        return vo;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private void ensureDailyReminders(Long userId, List<ReminderCandidateVO> candidates) {
        if (userId == null) {
            return;
        }
        for (ReminderCandidateVO candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getType())
                    || !StringUtils.hasText(candidate.getBizType())
                    || !StringUtils.hasText(candidate.getBizId())) {
                continue;
            }
            notificationCommandService.ensureDailyReminder(userId,
                    candidate.getType(),
                    candidate.getTitle(),
                    candidate.getContent(),
                    candidate.getBizType(),
                    candidate.getBizId());
        }
    }

    private List<ReminderCandidateVO> reminderCandidates(Long userId) {
        LocalDate today = LocalDate.now();
        List<ReminderCandidateVO> candidates = new java.util.ArrayList<>();
        candidates.addAll(agentReminderCandidates(userId, today.minusDays(1)));
        candidates.addAll(applicationReminderCandidates(userId, today));
        return candidates;
    }

    private List<ReminderCandidateVO> agentReminderCandidates(Long userId, LocalDate agentPlanDate) {
        try {
            Result<List<ReminderCandidateVO>> result =
                    aiFeignClient.listReminderCandidates(userId, agentPlanDate);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return Collections.emptyList();
            }
            return result.getData();
        } catch (Exception ex) {
            log.warn("Load agent reminder candidates failed, userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private List<ReminderCandidateVO> applicationReminderCandidates(Long userId, LocalDate reminderDate) {
        try {
            Result<List<ReminderCandidateVO>> result =
                    resumeFeignClient.listApplicationReminderCandidates(userId, reminderDate);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return Collections.emptyList();
            }
            return result.getData();
        } catch (Exception ex) {
            log.warn("Load application reminder candidates failed, userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    private Map<String, ReminderCandidateVO> buildReminderContractMap(List<ReminderCandidateVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, ReminderCandidateVO> contractMap = new LinkedHashMap<>();
        for (ReminderCandidateVO candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getType())
                    || !StringUtils.hasText(candidate.getBizType())
                    || !StringUtils.hasText(candidate.getBizId())) {
                continue;
            }
            contractMap.putIfAbsent(reminderKey(candidate.getType(), candidate.getBizType(), candidate.getBizId()),
                    candidate);
        }
        return contractMap;
    }

    private String reminderKey(String type, String bizType, String bizId) {
        return normalizeKeyToken(type) + "|" + normalizeKeyToken(bizType) + "|" + normalizeKeyToken(bizId);
    }

    private String normalizeKeyToken(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
