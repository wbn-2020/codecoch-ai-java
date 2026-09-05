package com.codecoachai.task.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.QuestionRecommendationGeneratePayload;
import com.codecoachai.task.feign.QuestionFeignClient;
import com.codecoachai.task.feign.vo.QuestionRecommendationGenerateVO;
import com.codecoachai.task.service.AsyncTaskService;
import com.codecoachai.task.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRecommendationGenerateConsumerTest {

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private QuestionFeignClient questionFeignClient;
    @Mock
    private NotificationService notificationService;

    private QuestionRecommendationGenerateConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new QuestionRecommendationGenerateConsumer(
                asyncTaskService, questionFeignClient, notificationService);
    }

    @Test
    void untrustedResultBecomesTerminalFailureInsteadOfAsyncSuccess() {
        MqMessage<QuestionRecommendationGeneratePayload> envelope = envelope();
        when(asyncTaskService.acquire(envelope, 3)).thenReturn(true);
        when(questionFeignClient.executeRecommendation(eq(88L), any())).thenReturn(
                Result.success(result("SUCCESS", "FALLBACK", true, 3)));

        consumer.onMessage(envelope);

        verify(asyncTaskService).markTerminalFailed(eq("msg-question-recommendation-1"), any());
        verify(asyncTaskService, never()).markSuccess(eq("msg-question-recommendation-1"), any());
        verify(notificationService).notifyTaskFailed(
                eq(10L), eq("QUESTION_RECOMMENDATION_GENERATE"), eq("88"), any(), any());
        verify(notificationService, never()).notifyTaskDone(any(), any(), any(), any(), any());
    }

    @Test
    void verifiedPracticeReadyResultCompletesAsyncTask() {
        MqMessage<QuestionRecommendationGeneratePayload> envelope = envelope();
        when(asyncTaskService.acquire(envelope, 3)).thenReturn(true);
        QuestionRecommendationGenerateVO result = result("SUCCESS", "VERIFIED", false, 3);
        when(questionFeignClient.executeRecommendation(eq(88L), any())).thenReturn(Result.success(result));

        consumer.onMessage(envelope);

        verify(asyncTaskService).markSuccess("msg-question-recommendation-1", result);
        verify(asyncTaskService, never()).markTerminalFailed(eq("msg-question-recommendation-1"), any());
        verify(notificationService).notifyTaskDone(
                eq(10L), eq("QUESTION_RECOMMENDATION_GENERATE"), eq("88"), any(), any());
    }

    private QuestionRecommendationGenerateVO result(String status,
                                                    String trustStatus,
                                                    boolean fallback,
                                                    int questionCount) {
        QuestionRecommendationGenerateVO result = new QuestionRecommendationGenerateVO();
        result.setBatchId(88L);
        result.setStatus(status);
        result.setTrustStatus(trustStatus);
        result.setFallback(fallback);
        result.setQuestionCount(questionCount);
        result.setAiCallLogId(99L);
        return result;
    }

    private MqMessage<QuestionRecommendationGeneratePayload> envelope() {
        MqMessage<QuestionRecommendationGeneratePayload> message = new MqMessage<>();
        message.setMessageId("msg-question-recommendation-1");
        message.setTraceId("trace-question-recommendation-1");
        message.setBizType("question.recommendation.generate");
        message.setBizId("88");
        message.setUserId(10L);
        message.setPayload(QuestionRecommendationGeneratePayload.builder()
                .batchId(88L)
                .userId(10L)
                .build());
        return message;
    }
}
