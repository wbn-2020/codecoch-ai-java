package com.codecoachai.task.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.AgentDailyPlanPayload;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.dto.AgentRunFailureDTO;
import com.codecoachai.task.feign.vo.AgentDailyPlanVO;
import com.codecoachai.task.service.AsyncTaskService;
import com.codecoachai.task.service.NotificationService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentDailyPlanConsumerTest {

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private NotificationService notificationService;

    private AgentDailyPlanConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AgentDailyPlanConsumer(asyncTaskService, aiFeignClient, notificationService);
    }

    @Test
    void canceledRunDoesNotMarkAsyncTaskSuccessOrSendDoneNotification() {
        MqMessage<AgentDailyPlanPayload> envelope = envelope();
        when(asyncTaskService.acquire(envelope, 3)).thenReturn(true);
        AgentDailyPlanVO plan = new AgentDailyPlanVO();
        plan.setRunId(77L);
        plan.setStatus("CANCELED");
        plan.setErrorMessage("run was canceled by force regeneration");
        when(aiFeignClient.executeAgentDailyPlan(eq(77L), any())).thenReturn(Result.success(plan));

        consumer.onMessage(envelope);

        verify(asyncTaskService).markTerminalFailed(eq("msg-agent-daily-1"), any());
        verify(asyncTaskService, never()).markSuccess(eq("msg-agent-daily-1"), any());
        verify(notificationService, never()).notifyTaskDone(any(), any(), any(), any(), any());
    }

    @Test
    void terminalBusinessFailureMarksAgentRunFailed() {
        MqMessage<AgentDailyPlanPayload> envelope = envelope();
        when(asyncTaskService.acquire(envelope, 3)).thenReturn(true);
        when(aiFeignClient.executeAgentDailyPlan(eq(77L), any()))
                .thenReturn(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "invalid target job"));

        consumer.onMessage(envelope);

        ArgumentCaptor<AgentRunFailureDTO> failureCaptor = ArgumentCaptor.forClass(AgentRunFailureDTO.class);
        verify(aiFeignClient).failAgentDailyPlan(eq(77L), failureCaptor.capture());
        AgentRunFailureDTO failure = failureCaptor.getValue();
        assertEquals(10L, failure.getUserId());
        assertEquals("AGENT_ASYNC_TASK_FAILED", failure.getErrorCode());
        verify(asyncTaskService).markTerminalFailed(eq("msg-agent-daily-1"), any());
        verify(notificationService).notifyTaskFailed(eq(10L), eq("AGENT_DAILY_PLAN"), eq("77"), any(), any());
    }

    private MqMessage<AgentDailyPlanPayload> envelope() {
        AgentDailyPlanPayload payload = AgentDailyPlanPayload.builder()
                .runId(77L)
                .userId(10L)
                .targetJobId(501L)
                .date(LocalDate.of(2026, 6, 18))
                .taskCount(3)
                .maxTotalMinutes(90)
                .forceRegenerate(false)
                .build();
        MqMessage<AgentDailyPlanPayload> message = new MqMessage<>();
        message.setMessageId("msg-agent-daily-1");
        message.setTraceId("trace-agent-daily-1");
        message.setBizType("agent.daily-plan");
        message.setBizId("77");
        message.setUserId(10L);
        message.setPayload(payload);
        message.setRetryCount(0);
        return message;
    }
}
