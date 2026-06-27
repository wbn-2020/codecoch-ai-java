package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.StudyPlanService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InterviewStreamServiceImplTest {

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void streamCompletionCancelsInFlightReportWork() throws Exception {
        assertCancellationCallbackInterruptsInFlightReportWork(this::triggerCompletionCallback);
    }

    @Test
    void streamTimeoutCancelsInFlightReportWork() throws Exception {
        assertCancellationCallbackInterruptsInFlightReportWork(this::triggerTimeoutCallback);
    }

    @Test
    void streamErrorCancelsInFlightReportWork() throws Exception {
        assertCancellationCallbackInterruptsInFlightReportWork(this::triggerErrorCallback);
    }

    private void assertCancellationCallbackInterruptsInFlightReportWork(EmitterCallbackTrigger callbackTrigger)
            throws Exception {
        InterviewService interviewService = mock(InterviewService.class);
        StudyPlanService studyPlanService = mock(StudyPlanService.class);
        CountDownLatch enteredReportCall = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        when(interviewService.report(1L)).thenAnswer(invocation -> {
            enteredReportCall.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return new InterviewReportVO();
        });
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("sse-cancel-test-");
        executor.initialize();
        InterviewStreamServiceImpl service = new InterviewStreamServiceImpl(interviewService, studyPlanService, executor);

        try {
            SseEmitter emitter = service.streamReport(1L);
            assertTrue(enteredReportCall.await(3, TimeUnit.SECONDS), "report generation should start in background");

            callbackTrigger.trigger(emitter);

            assertTrue(await(interrupted), "SSE cancellation callback should interrupt in-flight report work");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamAnswerUsesTokenAwareAnswerReviewPath() throws Exception {
        InterviewService interviewService = mock(InterviewService.class);
        StudyPlanService studyPlanService = mock(StudyPlanService.class);
        CountDownLatch tokenConsumerCalled = new CountDownLatch(1);
        when(interviewService.answerForSse(eq(1L), any(SubmitInterviewAnswerDTO.class),
                any(Consumer.class), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<String> tokenConsumer = invocation.getArgument(3);
            tokenConsumer.accept("streamed-token");
            tokenConsumerCalled.countDown();
            SubmitInterviewAnswerVO vo = new SubmitInterviewAnswerVO();
            vo.setInterviewId(1L);
            vo.setAnswerId(2001L);
            vo.setEvaluationMessageId(2002L);
            vo.setScore(80);
            vo.setComment("Good");
            vo.setNextAction("FINISH");
            return vo;
        });
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("sse-answer-test-");
        executor.initialize();
        InterviewStreamServiceImpl service = new InterviewStreamServiceImpl(interviewService, studyPlanService, executor);

        try {
            SubmitInterviewAnswerDTO dto = new SubmitInterviewAnswerDTO();
            dto.setAnswerContent("answer");
            service.streamAnswer(1L, dto);

            assertTrue(tokenConsumerCalled.await(3, TimeUnit.SECONDS),
                    "answer review SSE should use token-aware service path");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void streamReportDegradesWhenSseExecutorRejectsSubmission() {
        InterviewService interviewService = mock(InterviewService.class);
        StudyPlanService studyPlanService = mock(StudyPlanService.class);
        ThreadPoolTaskExecutor executor = new RejectingThreadPoolTaskExecutor();
        InterviewStreamServiceImpl service = new InterviewStreamServiceImpl(interviewService, studyPlanService, executor);

        SseEmitter emitter = assertDoesNotThrow(() -> service.streamReport(1L),
                "SSE queue rejection should be degraded on the emitter instead of escaping to HTTP thread");

        assertNotNull(emitter);
        verify(interviewService, never()).report(1L);
    }

    @Test
    void ensureStreamingActiveStopsWhenEmitterAlreadyCancelled() {
        InterviewService interviewService = mock(InterviewService.class);
        StudyPlanService studyPlanService = mock(StudyPlanService.class);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        InterviewStreamServiceImpl service = new InterviewStreamServiceImpl(interviewService, studyPlanService, executor);
        AtomicBoolean active = new AtomicBoolean(false);

        assertFalse(service.isStreamingActive(active));
        assertThrows(CancellationException.class, () -> service.ensureStreamingActive(active));
    }

    private boolean await(AtomicBoolean value) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (value.get()) {
                return true;
            }
            Thread.sleep(25L);
        }
        return value.get();
    }

    private void triggerCompletionCallback(SseEmitter emitter) throws Exception {
        invokeEmitterRunnableCallback(emitter, "completionCallback");
    }

    private void triggerTimeoutCallback(SseEmitter emitter) throws Exception {
        invokeEmitterRunnableCallback(emitter, "timeoutCallback");
    }

    @SuppressWarnings("unchecked")
    private void triggerErrorCallback(SseEmitter emitter) throws Exception {
        if (emitter == null) {
            return;
        }
        Field field = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
                .getDeclaredField("errorCallback");
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method accept = callback.getClass().getDeclaredMethod("accept", Throwable.class);
        accept.setAccessible(true);
        accept.invoke(callback, new IllegalStateException("client disconnected"));
    }

    private void invokeEmitterRunnableCallback(SseEmitter emitter, String fieldName) throws Exception {
        if (emitter == null) {
            return;
        }
        Field field = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method run = callback.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(callback);
    }

    @FunctionalInterface
    private interface EmitterCallbackTrigger {
        void trigger(SseEmitter emitter) throws Exception;
    }

    private static final class RejectingThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
        @Override
        public Future<?> submit(Runnable task) {
            throw new RejectedExecutionException("sse queue full");
        }
    }
}
