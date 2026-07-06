package com.codecoachai.ai.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.service.AiService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InnerInterviewAiStreamControllerTest {

    @Test
    void evaluateStreamReturnsEmitterAndCompletes() throws Exception {
        AiService aiService = mock(AiService.class);
        when(aiService.evaluateStream(any(EvaluateAnswerDTO.class), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("token-one");
            EvaluateAnswerVO vo = new EvaluateAnswerVO();
            vo.setAiCallLogId(101L);
            vo.setScore(90);
            vo.setComment("good");
            vo.setNextAction("FINISH");
            return vo;
        });
        InnerInterviewAiStreamController controller = new InnerInterviewAiStreamController(aiService, Runnable::run);

        SseEmitter emitter = controller.evaluateStream(new EvaluateAnswerDTO());
        assertNotNull(emitter);
        triggerCompletionCallback(emitter);
    }

    private void triggerCompletionCallback(SseEmitter emitter) throws Exception {
        if (emitter == null) {
            return;
        }
        Field field = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
                .getDeclaredField("completionCallback");
        field.setAccessible(true);
        Object callback = field.get(emitter);
        if (callback == null) {
            return;
        }
        Method run = callback.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(callback);
    }
}
