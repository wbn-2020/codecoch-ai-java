package com.codecoachai.resume.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
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

    private static final class RejectingExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("resume sse queue full");
        }
    }
}
