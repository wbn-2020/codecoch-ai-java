package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.dto.AgentMetricEventDTO;
import com.codecoachai.ai.agent.domain.entity.AgentMetricEventRecord;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.ActivationHandoffVO;
import com.codecoachai.ai.agent.domain.vo.AgentMetricAckVO;
import com.codecoachai.ai.agent.mapper.AgentMetricEventRecordMapper;
import com.codecoachai.ai.agent.service.AgentMetricsService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMetricsServiceImpl implements AgentMetricsService {

    private static final String LOG_PREFIX = "agent_metric_event";
    private static final String EVENT_TASK_COMPLETED = "task_completed";
    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_COLLECTION_ITEMS = 20;
    private static final int MAX_MAP_ENTRIES = 20;
    private static final Set<String> BLOCKED_FIELD_NAMES = Set.of(
            "password", "passwd", "token", "accessToken", "refreshToken", "authorization",
            "secret", "apiKey", "cookie", "credential", "session", "idempotencyKey", "requestBody",
            "responseBody", "comment", "note", "content", "rawOutput", "rawInput", "message", "failureReason");
    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of(
            "actionType", "actionSource", "actionCount", "backendActionCount", "priority", "title",
            "latencyMs", "estimatedCost", "traceId", "resultSource", "aiCallLogId", "sessionId",
            "taskType", "taskStatus", "relatedSkillCode", "relatedSkillName", "estimatedMinutes",
            "durationMinutes", "readStatus", "relatedType", "reason", "fallbackPath", "targetPath",
            "interviewId", "verifiedBusinessAction", "activationHandoffs", "targetJobId", "code",
            "stage", "firstOccurrence", "occurredAt", "requestId");
    private static final Set<String> SUPPORTED_EVENT_CODES = Set.of(
            EVENT_TASK_COMPLETED,
            "feedback_cta_clicked",
            "reminder_shown",
            "reminder_clicked",
            "reminder_target_invalid",
            "ai_coach_action_started",
            "ai_coach_action_succeeded",
            "ai_coach_action_failed",
            "ai_coach_action_canceled",
            "ai_coach_next_action_clicked",
            "interview_report_next_action_shown",
            "interview_report_next_action_clicked",
            "focus_session_started",
            "focus_session_finished",
            "focus_session_canceled");

    private final ObjectMapper objectMapper;
    private final AgentMetricEventRecordMapper eventRecordMapper;

    @Override
    public AgentMetricAckVO acceptEvent(Long actorUserId, AgentMetricEventDTO event) {
        if (actorUserId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "actorUserId is required");
        }
        AgentMetricEventDTO normalized = normalize(event);
        normalized.setUserId(actorUserId);
        return record(normalized, "api");
    }

    @Override
    public void recordTaskCompleted(AgentTask task, String requestId, List<ActivationHandoffVO> activationHandoffs,
                                    boolean verifiedBusinessAction) {
        if (task == null || task.getUserId() == null || task.getId() == null) {
            return;
        }
        AgentMetricEventDTO event = new AgentMetricEventDTO();
        event.setEventCode(EVENT_TASK_COMPLETED);
        event.setUserId(task.getUserId());
        event.setTaskId(task.getId());
        event.setRunId(task.getAgentRunId());
        event.setPlanDate(task.getDueDate());
        event.setTargetJobId(task.getTargetJobId());
        event.setRequestId(trimToNull(requestId));
        event.setSourcePage("agent_task_backend");
        event.setTargetPath(trimToNull(task.getActionUrl()));
        event.setBizType(trimToNull(task.getRelatedBizType()));
        event.setBizId(task.getRelatedBizId() == null ? null : String.valueOf(task.getRelatedBizId()));
        event.setOccurredAt(firstTimestamp(task.getCompletedAt(), task.getUpdatedAt(), task.getCreatedAt()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskType", task.getTaskType());
        metadata.put("taskStatus", task.getStatus());
        metadata.put("relatedSkillCode", task.getRelatedSkillCode());
        metadata.put("relatedSkillName", task.getRelatedSkillName());
        metadata.put("verifiedBusinessAction", verifiedBusinessAction);
        metadata.put("activationHandoffs", activationHandoffs == null ? List.of() : activationHandoffs);
        event.setMetadata(metadata);
        record(normalize(event), "backend_auto");
    }

    private AgentMetricAckVO record(AgentMetricEventDTO event, String ingestSource) {
        AgentMetricEventRecord existing = findExisting(event.getUserId(), event.getIdempotencyKey());
        if (existing != null) {
            return toAck(existing, true);
        }

        String eventId = UUID.randomUUID().toString();
        LocalDateTime acceptedAt = LocalDateTime.now();
        Map<String, Object> payload = buildLogPayload(eventId, acceptedAt, ingestSource, event);
        log.info("{} {}", LOG_PREFIX, toJson(payload));

        AgentMetricEventRecord record = new AgentMetricEventRecord();
        record.setEventId(eventId);
        record.setEventCode(event.getEventCode());
        record.setIdempotencyKey(event.getIdempotencyKey());
        record.setUserId(event.getUserId());
        record.setTaskId(event.getTaskId());
        record.setRunId(event.getRunId());
        record.setPlanDate(event.getPlanDate());
        record.setTargetJobId(event.getTargetJobId());
        record.setRequestId(event.getRequestId());
        record.setSourcePage(event.getSourcePage());
        record.setTargetPath(event.getTargetPath());
        record.setNotificationId(event.getNotificationId());
        record.setBizType(event.getBizType());
        record.setBizId(event.getBizId());
        record.setOccurredAt(event.getOccurredAt());
        record.setAcceptedAt(acceptedAt);
        record.setIngestSource(ingestSource);
        record.setMetadataJson(toJson(event.getMetadata()));
        try {
            eventRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            AgentMetricEventRecord duplicate = findExisting(event.getUserId(), event.getIdempotencyKey());
            if (duplicate != null) {
                return toAck(duplicate, true);
            }
            throw ex;
        }

        return toAck(record, false);
    }

    private Map<String, Object> buildLogPayload(String eventId, LocalDateTime acceptedAt, String ingestSource,
                                                AgentMetricEventDTO event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventCode", event.getEventCode());
        payload.put("userId", event.getUserId());
        payload.put("taskId", event.getTaskId());
        payload.put("runId", event.getRunId());
        payload.put("planDate", event.getPlanDate());
        payload.put("targetJobId", event.getTargetJobId());
        payload.put("requestId", event.getRequestId());
        payload.put("sourcePage", event.getSourcePage());
        payload.put("targetPath", event.getTargetPath());
        payload.put("notificationId", event.getNotificationId());
        payload.put("bizType", event.getBizType());
        payload.put("bizId", event.getBizId());
        payload.put("occurredAt", event.getOccurredAt());
        payload.put("acceptedAt", acceptedAt);
        payload.put("ingestSource", ingestSource);
        payload.put("metadata", event.getMetadata());
        return payload;
    }

    private AgentMetricEventDTO normalize(AgentMetricEventDTO source) {
        if (source == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "agent metric event is required");
        }
        AgentMetricEventDTO normalized = new AgentMetricEventDTO();
        normalized.setEventCode(normalizeEventCode(source.getEventCode()));
        normalized.setIdempotencyKey(normalizeIdempotencyKey(source));
        normalized.setUserId(source.getUserId());
        normalized.setTaskId(source.getTaskId());
        normalized.setRunId(source.getRunId());
        normalized.setPlanDate(source.getPlanDate());
        normalized.setTargetJobId(source.getTargetJobId());
        normalized.setRequestId(trimToNull(source.getRequestId()));
        normalized.setSourcePage(trimToNull(source.getSourcePage()));
        normalized.setTargetPath(trimToNull(source.getTargetPath()));
        normalized.setNotificationId(trimToNull(source.getNotificationId()));
        normalized.setBizType(trimToNull(source.getBizType()));
        normalized.setBizId(trimToNull(source.getBizId()));
        normalized.setOccurredAt(source.getOccurredAt() == null ? LocalDateTime.now() : source.getOccurredAt());
        normalized.setMetadata(sanitizeMetadata(source.getMetadata()));
        return normalized;
    }

    private String normalizeEventCode(String eventCode) {
        String normalized = trimToNull(eventCode);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "eventCode is required");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EVENT_CODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported agent metric event: " + normalized);
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(AgentMetricEventDTO source) {
        String explicit = trimToNull(source.getIdempotencyKey());
        if (explicit != null) {
            return truncate(explicit, 128);
        }
        return buildDerivedIdempotencyKey(source);
    }

    private String buildDerivedIdempotencyKey(AgentMetricEventDTO source) {
        StringBuilder builder = new StringBuilder();
        appendKeyPart(builder, normalizeEventCode(source.getEventCode()));
        appendKeyPart(builder, metadataText(source.getMetadata(), "actionType"));
        appendKeyPart(builder, firstText(source.getRequestId(), metadataText(source.getMetadata(), "requestId")));
        appendKeyPart(builder, metadataText(source.getMetadata(), "stage"));
        appendKeyPart(builder, source.getTaskId());
        appendKeyPart(builder, source.getRunId());
        appendKeyPart(builder, source.getNotificationId());
        appendKeyPart(builder, source.getBizType());
        appendKeyPart(builder, source.getBizId());
        appendKeyPart(builder, source.getTargetPath());
        appendKeyPart(builder, normalizeOccurredAt(source.getOccurredAt()));
        return truncate(builder.toString(), 128);
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof CharSequence sequence) {
            return trimToNull(sequence.toString());
        }
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String normalizeOccurredAt(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            return null;
        }
        return occurredAt.withNano(0).toString();
    }

    private void appendKeyPart(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('|');
        }
        builder.append(truncate(text, 64));
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (count >= MAX_MAP_ENTRIES) {
                break;
            }
            String key = trimToNull(entry.getKey());
            if (key == null || isBlockedField(key) || !ALLOWED_METADATA_KEYS.contains(key)) {
                continue;
            }
            Object sanitizedValue = sanitizeValue(key, entry.getValue(), 0);
            if (sanitizedValue == null) {
                continue;
            }
            sanitized.put(key, sanitizedValue);
            count++;
        }
        return sanitized;
    }

    private Object sanitizeValue(String key, Object value, int depth) {
        if (value == null || depth > 2 || isBlockedField(key)) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            return truncate(sequence.toString().trim(), MAX_STRING_LENGTH);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return sanitizeNestedMap(mapValue, depth + 1);
        }
        if (value instanceof Collection<?> collection) {
            return sanitizeCollection(collection, depth + 1);
        }
        if (value instanceof Object[] array) {
            return sanitizeCollection(List.of(array), depth + 1);
        }
        if (value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof ActivationHandoffVO handoff) {
            return sanitizeNestedMap(objectMapper.convertValue(handoff, Map.class), depth + 1);
        }
        return truncate(String.valueOf(value), MAX_STRING_LENGTH);
    }

    private Map<String, Object> sanitizeNestedMap(Map<?, ?> value, int depth) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (count >= MAX_MAP_ENTRIES) {
                break;
            }
            String key = trimToNull(Objects.toString(entry.getKey(), null));
            if (key == null || isBlockedField(key) || !ALLOWED_METADATA_KEYS.contains(key)) {
                continue;
            }
            Object sanitizedValue = sanitizeValue(key, entry.getValue(), depth);
            if (sanitizedValue == null) {
                continue;
            }
            sanitized.put(key, sanitizedValue);
            count++;
        }
        return sanitized.isEmpty() ? null : sanitized;
    }

    private List<Object> sanitizeCollection(Collection<?> value, int depth) {
        List<Object> sanitized = new java.util.ArrayList<>();
        int count = 0;
        for (Object item : value) {
            if (count >= MAX_COLLECTION_ITEMS) {
                break;
            }
            Object sanitizedItem = sanitizeValue("collectionItem", item, depth);
            if (sanitizedItem == null) {
                continue;
            }
            sanitized.add(sanitizedItem);
            count++;
        }
        return sanitized.isEmpty() ? null : sanitized;
    }

    private boolean isBlockedField(String key) {
        return BLOCKED_FIELD_NAMES.contains(key) || BLOCKED_FIELD_NAMES.contains(key.toLowerCase(Locale.ROOT));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private AgentMetricEventRecord findExisting(Long userId, String idempotencyKey) {
        if (userId == null || !StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return eventRecordMapper.selectOne(new LambdaQueryWrapper<AgentMetricEventRecord>()
                .eq(AgentMetricEventRecord::getUserId, userId)
                .eq(AgentMetricEventRecord::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private AgentMetricAckVO toAck(AgentMetricEventRecord record, boolean duplicate) {
        AgentMetricAckVO ack = new AgentMetricAckVO();
        ack.setEventId(record.getEventId());
        ack.setEventCode(record.getEventCode());
        ack.setIdempotencyKey(record.getIdempotencyKey());
        ack.setDuplicate(duplicate);
        ack.setAcceptedAt(record.getAcceptedAt());
        return ack;
    }

    private LocalDateTime firstTimestamp(LocalDateTime... values) {
        if (values == null) {
            return LocalDateTime.now();
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return LocalDateTime.now();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return payload.toString();
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            if (payload instanceof Map<?, ?> map) {
                return new HashMap<>(map).toString();
            }
            return String.valueOf(payload);
        }
    }
}
