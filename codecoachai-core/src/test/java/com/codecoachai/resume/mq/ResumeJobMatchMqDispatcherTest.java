package com.codecoachai.resume.mq;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.producer.MqProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ResumeJobMatchMqDispatcherTest {

    @Mock
    private ObjectProvider<MqProducer> mqProducerProvider;
    @Mock
    private MqProducer mqProducer;
    @Mock
    private SendResult sendResult;

    private ResumeJobMatchMqDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(mqProducerProvider.getIfAvailable()).thenReturn(mqProducer);
        dispatcher = new ResumeJobMatchMqDispatcher(mqProducerProvider);
    }

    @Test
    void preservesPreRegisteredCorrelationIdWhenBrokerAcceptsTheMessage() {
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(mqProducer.sendEnvelopeSync(any(), any())).thenReturn(sendResult);

        MqDispatchReceipt result = dispatcher.dispatchAnalyzeWithReceipt(
                88L, 10L, "match-msg-1", "match-trace-1");

        assertNotNull(result);
        assertEquals("match-msg-1", result.getMessageId());
        assertEquals("match-trace-1", result.getTraceId());
        ArgumentCaptor<MqMessage<?>> envelopeCaptor = messageCaptor();
        verify(mqProducer).sendEnvelopeSync(any(), envelopeCaptor.capture());
        assertEquals("match-msg-1", envelopeCaptor.getValue().getMessageId());
        assertEquals("match-trace-1", envelopeCaptor.getValue().getTraceId());
    }

    @Test
    void rejectsNonSuccessfulBrokerResultSoCallerCanUseSynchronousFallback() {
        when(sendResult.getSendStatus()).thenReturn(SendStatus.FLUSH_DISK_TIMEOUT);
        when(mqProducer.sendEnvelopeSync(any(), any())).thenReturn(sendResult);

        assertNull(dispatcher.dispatchAnalyzeWithReceipt(88L, 10L));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<MqMessage<?>> messageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(MqMessage.class);
    }
}
