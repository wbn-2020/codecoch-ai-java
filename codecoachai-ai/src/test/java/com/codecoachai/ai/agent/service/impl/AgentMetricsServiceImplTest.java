package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.AgentMetricEventDTO;
import com.codecoachai.ai.agent.domain.entity.AgentMetricEventRecord;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.AgentMetricAckVO;
import com.codecoachai.ai.agent.mapper.AgentMetricEventRecordMapper;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AgentMetricsServiceImplTest {

    @Mock
    private AgentMetricEventRecordMapper eventRecordMapper;

    private AgentMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentMetricsServiceImpl(new ObjectMapper(), eventRecordMapper);
    }

    @Test
    void acceptEventPersistsSanitizedMetricRecord() {
        AgentMetricEventDTO event = new AgentMetricEventDTO();
        event.setEventCode("FEEDBACK_CTA_CLICKED");
        event.setTaskId(99L);
        event.setRunId(77L);
        event.setRequestId("req-r3-metric");
        event.setSourcePage("agent_today");
        event.setTargetPath("/agent/tasks/99");
        event.setIdempotencyKey("metric-feedback-001");
        event.setMetadata(Map.of(
                "actionType", "open_feedback_drawer",
                "latencyMs", 321L,
                "targetJobId", 88,
                "password", "should-not-store",
                "token", "should-not-store",
                "nested", Map.of("secret", "hidden")));

        AgentMetricAckVO ack = service.acceptEvent(10L, event);

        assertEquals("feedback_cta_clicked", ack.getEventCode());
        assertFalse(ack.getDuplicate());
        assertEquals("metric-feedback-001", ack.getIdempotencyKey());

        ArgumentCaptor<AgentMetricEventRecord> captor = ArgumentCaptor.forClass(AgentMetricEventRecord.class);
        verify(eventRecordMapper).insert(captor.capture());
        AgentMetricEventRecord record = captor.getValue();
        assertEquals(10L, record.getUserId());
        assertEquals("feedback_cta_clicked", record.getEventCode());
        assertEquals("metric-feedback-001", record.getIdempotencyKey());
        assertEquals("api", record.getIngestSource());
        assertEquals("/agent/tasks/99", record.getTargetPath());
        assertTrue(record.getMetadataJson().contains("open_feedback_drawer"));
        assertTrue(record.getMetadataJson().contains("targetJobId"));
        assertFalse(record.getMetadataJson().contains("password"));
        assertFalse(record.getMetadataJson().contains("token"));
        assertFalse(record.getMetadataJson().contains("secret"));
    }

    @Test
    void acceptEventReturnsExistingAckWhenIdempotencyKeyAlreadyExists() {
        AgentMetricEventRecord existing = new AgentMetricEventRecord();
        existing.setId(5L);
        existing.setEventId("evt-existing");
        existing.setEventCode("reminder_shown");
        existing.setUserId(10L);
        existing.setIdempotencyKey("reminder-001");
        existing.setAcceptedAt(LocalDateTime.of(2026, 6, 27, 10, 0));
        when(eventRecordMapper.selectOne(any())).thenReturn(existing);

        AgentMetricEventDTO event = new AgentMetricEventDTO();
        event.setEventCode("REMINDER_SHOWN");
        event.setNotificationId("99");
        event.setIdempotencyKey("reminder-001");

        AgentMetricAckVO ack = service.acceptEvent(10L, event);

        assertEquals("evt-existing", ack.getEventId());
        assertEquals("reminder_shown", ack.getEventCode());
        assertTrue(ack.getDuplicate());
        verify(eventRecordMapper, never()).insert(any(AgentMetricEventRecord.class));
    }

    @Test
    void acceptEventReturnsDuplicateAckWhenInsertHitsUniqueConstraint() {
        AgentMetricEventRecord existing = new AgentMetricEventRecord();
        existing.setId(6L);
        existing.setEventId("evt-race");
        existing.setEventCode("reminder_clicked");
        existing.setUserId(10L);
        existing.setIdempotencyKey("req-race-001");
        existing.setAcceptedAt(LocalDateTime.of(2026, 6, 27, 10, 30));
        when(eventRecordMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(existing);
        when(eventRecordMapper.insert(any(AgentMetricEventRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        AgentMetricEventDTO event = new AgentMetricEventDTO();
        event.setEventCode("REMINDER_CLICKED");
        event.setRequestId("req-race-001");

        AgentMetricAckVO ack = service.acceptEvent(10L, event);

        assertEquals("evt-race", ack.getEventId());
        assertEquals("reminder_clicked", ack.getEventCode());
        assertTrue(ack.getDuplicate());
        assertEquals("req-race-001", ack.getIdempotencyKey());
        verify(eventRecordMapper).insert(any(AgentMetricEventRecord.class));
    }

    @Test
    void acceptEventTreatsDifferentStagesAsDifferentIdempotencyKeys() {
        AgentMetricEventDTO started = new AgentMetricEventDTO();
        started.setEventCode("AI_COACH_ACTION_STARTED");
        started.setTaskId(99L);
        started.setRunId(77L);
        started.setRequestId("req-stage-001");
        started.setIdempotencyKey("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-stage-001|99");

        AgentMetricEventDTO succeeded = new AgentMetricEventDTO();
        succeeded.setEventCode("AI_COACH_ACTION_SUCCEEDED");
        succeeded.setTaskId(99L);
        succeeded.setRunId(77L);
        succeeded.setRequestId("req-stage-001");
        succeeded.setIdempotencyKey("ai-coach|ai_coach_action_succeeded|EXPLAIN_RECOMMENDATION|req-stage-001|99");

        when(eventRecordMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        when(eventRecordMapper.insert(any(AgentMetricEventRecord.class))).thenReturn(1);

        AgentMetricAckVO startedAck = service.acceptEvent(10L, started);
        AgentMetricAckVO succeededAck = service.acceptEvent(10L, succeeded);

        assertFalse(startedAck.getDuplicate());
        assertFalse(succeededAck.getDuplicate());
        assertEquals("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-stage-001|99",
                startedAck.getIdempotencyKey());
        assertEquals("ai-coach|ai_coach_action_succeeded|EXPLAIN_RECOMMENDATION|req-stage-001|99",
                succeededAck.getIdempotencyKey());
        verify(eventRecordMapper, times(2)).insert(any(AgentMetricEventRecord.class));
    }

    @Test
    void acceptEventDerivesCoachMetricKeyFromEventActionAndRequestMetadata() {
        AgentMetricEventDTO first = new AgentMetricEventDTO();
        first.setEventCode("AI_COACH_ACTION_STARTED");
        first.setTaskId(99L);
        first.setRunId(77L);
        first.setMetadata(Map.of(
                "actionType", "EXPLAIN_RECOMMENDATION",
                "requestId", "req-derived-001"));

        AgentMetricEventDTO second = new AgentMetricEventDTO();
        second.setEventCode("AI_COACH_ACTION_STARTED");
        second.setTaskId(99L);
        second.setRunId(77L);
        second.setMetadata(Map.of(
                "actionType", "REVIEW_COMPLETED_TASK",
                "requestId", "req-derived-002"));

        when(eventRecordMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        when(eventRecordMapper.insert(any(AgentMetricEventRecord.class))).thenReturn(1);

        AgentMetricAckVO firstAck = service.acceptEvent(10L, first);
        AgentMetricAckVO secondAck = service.acceptEvent(10L, second);

        assertFalse(firstAck.getDuplicate());
        assertFalse(secondAck.getDuplicate());
        assertEquals("ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-derived-001|99|77",
                firstAck.getIdempotencyKey());
        assertEquals("ai_coach_action_started|REVIEW_COMPLETED_TASK|req-derived-002|99|77",
                secondAck.getIdempotencyKey());
        verify(eventRecordMapper, times(2)).insert(any(AgentMetricEventRecord.class));
    }

    @Test
    void acceptEventReturnsDuplicateForRepeatedSameStageMetric() {
        AgentMetricEventRecord existing = new AgentMetricEventRecord();
        existing.setId(7L);
        existing.setEventId("evt-stage");
        existing.setEventCode("ai_coach_action_started");
        existing.setUserId(10L);
        existing.setIdempotencyKey("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-stage-dup|99");
        existing.setAcceptedAt(LocalDateTime.of(2026, 6, 27, 11, 0));
        when(eventRecordMapper.selectOne(any())).thenReturn(existing);

        AgentMetricEventDTO event = new AgentMetricEventDTO();
        event.setEventCode("AI_COACH_ACTION_STARTED");
        event.setTaskId(99L);
        event.setRequestId("req-stage-dup");
        event.setIdempotencyKey("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-stage-dup|99");

        AgentMetricAckVO ack = service.acceptEvent(10L, event);

        assertTrue(ack.getDuplicate());
        assertEquals("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-stage-dup|99",
                ack.getIdempotencyKey());
        verify(eventRecordMapper, never()).insert(any(AgentMetricEventRecord.class));
    }

    @Test
    void acceptEventRejectsUnsupportedMetricCodes() {
        AgentMetricEventDTO event = new AgentMetricEventDTO();
        event.setEventCode("global_chat_opened");

        assertThrows(BusinessException.class, () -> service.acceptEvent(10L, event));
        verify(eventRecordMapper, never()).insert(any(AgentMetricEventRecord.class));
    }

    @Test
    void recordTaskCompletedPersistsBackendMetricWithSanitizedMetadata() {
        AgentTask task = new AgentTask();
        task.setId(101L);
        task.setUserId(10L);
        task.setAgentRunId(202L);
        task.setDueDate(LocalDate.of(2026, 6, 27));
        task.setTargetJobId(303L);
        task.setActionUrl("/agent/tasks/101");
        task.setRelatedBizType("interview_report");
        task.setRelatedBizId(404L);
        task.setTaskType("INTERVIEW");
        task.setStatus("DONE");
        task.setRelatedSkillCode("JAVA");
        task.setRelatedSkillName("Java");
        task.setCompletedAt(LocalDateTime.of(2026, 6, 27, 9, 30));

        service.recordTaskCompleted(task, "req-task-001", List.of(), true);

        ArgumentCaptor<AgentMetricEventRecord> captor = ArgumentCaptor.forClass(AgentMetricEventRecord.class);
        verify(eventRecordMapper).insert(captor.capture());
        AgentMetricEventRecord record = captor.getValue();
        assertEquals("task_completed", record.getEventCode());
        assertEquals("backend_auto", record.getIngestSource());
        assertTrue(record.getMetadataJson().contains("verifiedBusinessAction"));
        assertFalse(record.getMetadataJson().contains("requestId"));
    }
}
