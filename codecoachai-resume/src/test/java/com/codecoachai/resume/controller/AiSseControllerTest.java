package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.TargetJobService;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AiSseControllerTest {

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void jobTargetParseDegradesWhenSseExecutorRejectsSubmission() {
        ResumeService resumeService = mock(ResumeService.class);
        TargetJobService targetJobService = mock(TargetJobService.class);
        ResumeJobMatchService resumeJobMatchService = mock(ResumeJobMatchService.class);
        AiSseController controller = new AiSseController(
                resumeService,
                targetJobService,
                resumeJobMatchService,
                new RejectingExecutor());
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(1L)
                .username("user")
                .roles(List.of("USER"))
                .build());

        SseEmitter emitter = assertDoesNotThrow(() -> controller.jobTargetParse(1L, false, "Java backend"),
                "SSE queue rejection should be degraded on the emitter instead of escaping to HTTP thread");

        assertNotNull(emitter);
        verify(targetJobService, never()).parseJobDescription(eq(1L), any(JobDescriptionParseDTO.class));
    }

    @Test
    void completionCancelsSubmittedSseFuture() throws Exception {
        assertCallbackCancelsFuture("completionCallback");
    }

    @Test
    void timeoutCancelsSubmittedSseFuture() throws Exception {
        assertCallbackCancelsFuture("timeoutCallback");
    }

    @Test
    void errorCancelsSubmittedSseFuture() throws Exception {
        ResumeService resumeService = mock(ResumeService.class);
        TargetJobService targetJobService = mock(TargetJobService.class);
        ResumeJobMatchService resumeJobMatchService = mock(ResumeJobMatchService.class);
        QueuedExecutor executor = new QueuedExecutor();
        AiSseController controller = new AiSseController(
                resumeService, targetJobService, resumeJobMatchService, executor);
        SseEmitter emitter = new SseEmitter();
        AtomicBoolean active = new AtomicBoolean(true);

        CompletableFuture<Void> future = controller.submitSseTask(
                emitter, active, "request", "test", new Object(), () -> { });

        triggerErrorCallback(emitter);

        assertTrue(future.isCancelled());
    }

    private void assertCallbackCancelsFuture(String callbackField) throws Exception {
        ResumeService resumeService = mock(ResumeService.class);
        TargetJobService targetJobService = mock(TargetJobService.class);
        ResumeJobMatchService resumeJobMatchService = mock(ResumeJobMatchService.class);
        QueuedExecutor executor = new QueuedExecutor();
        AiSseController controller = new AiSseController(
                resumeService, targetJobService, resumeJobMatchService, executor);
        SseEmitter emitter = new SseEmitter();
        AtomicBoolean active = new AtomicBoolean(true);

        CompletableFuture<Void> future = controller.submitSseTask(
                emitter, active, "request", "test", new Object(), () -> { });

        triggerRunnableCallback(emitter, callbackField);

        assertTrue(future.isCancelled());
    }

    private void triggerRunnableCallback(SseEmitter emitter, String fieldName) throws Exception {
        Field field = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method run = callback.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(callback);
    }

    private void triggerErrorCallback(SseEmitter emitter) throws Exception {
        Field field = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
                .getDeclaredField("errorCallback");
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method accept = callback.getClass().getDeclaredMethod("accept", Throwable.class);
        accept.setAccessible(true);
        accept.invoke(callback, new IllegalStateException("client disconnected"));
    }

    private static final class RejectingExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("resume sse queue full");
        }
    }

    private static final class QueuedExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            // Keep the task pending so emitter callbacks can cancel its CompletableFuture deterministically.
        }
    }
}
