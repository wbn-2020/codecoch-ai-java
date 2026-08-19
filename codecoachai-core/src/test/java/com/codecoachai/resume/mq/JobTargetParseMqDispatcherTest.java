package com.codecoachai.resume.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.JobTargetParsePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.task.service.AsyncTaskService;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class JobTargetParseMqDispatcherTest {

    @Mock
    private ObjectProvider<MqProducer> mqProducerProvider;
    @Mock
    private MqProducer mqProducer;
    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private SendResult sendResult;

    private JobTargetParseMqDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(mqProducerProvider.getIfAvailable()).thenReturn(mqProducer);
        dispatcher = new JobTargetParseMqDispatcher(mqProducerProvider, asyncTaskService);
    }

    @Test
    void registersExecutionBeforeDispatchAndReturnsSameCorrelationId() {
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(mqProducer.sendEnvelopeSync(any(), any())).thenReturn(sendResult);

        MqDispatchReceipt receipt = dispatcher.dispatchParseWithReceipt(
                88L, 10L, true, "Backend Engineer");

        assertNotNull(receipt);
        assertEquals(JobTargetParseMqDispatcher.BIZ_TYPE, receipt.getBizType());
        assertEquals("88", receipt.getBizId());
        ArgumentCaptor<MqMessage<JobTargetParsePayload>> envelopeCaptor = messageCaptor();
        verify(mqProducer).sendEnvelopeSync(any(), envelopeCaptor.capture());
        MqMessage<JobTargetParsePayload> envelope = envelopeCaptor.getValue();
        assertEquals(receipt.getMessageId(), envelope.getMessageId());
        assertEquals(receipt.getTraceId(), envelope.getTraceId());
        verify(asyncTaskService).registerPending(
                eq(receipt.getMessageId()),
                eq(JobTargetParseMqDispatcher.BIZ_TYPE),
                eq("88"),
                eq(10L),
                eq(receipt.getTraceId()),
                eq(receipt.getMessageId()),
                eq(envelope.getPayload()),
                eq(3));
    }

    @Test
    void retainsRegisteredTaskForSynchronousFallbackWhenDispatchFails() {
        when(mqProducer.sendEnvelopeSync(any(), any())).thenThrow(new IllegalStateException("broker unavailable"));

        MqDispatchReceipt receipt = dispatcher.dispatchParseWithReceipt(88L, 10L, false, null);

        assertNotNull(receipt);
        assertEquals("FALLBACK_REQUIRED", receipt.getSendStatus());
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> executionIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncTaskService).registerPending(
                messageIdCaptor.capture(),
                eq(JobTargetParseMqDispatcher.BIZ_TYPE),
                eq("88"),
                eq(10L),
                any(),
                executionIdCaptor.capture(),
                any(JobTargetParsePayload.class),
                eq(3));
        assertEquals(messageIdCaptor.getValue(), executionIdCaptor.getValue());
        assertEquals(messageIdCaptor.getValue(), receipt.getMessageId());
        verify(asyncTaskService, never()).failPendingIfUnclaimed(any(), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<MqMessage<JobTargetParsePayload>> messageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(MqMessage.class);
    }
}
