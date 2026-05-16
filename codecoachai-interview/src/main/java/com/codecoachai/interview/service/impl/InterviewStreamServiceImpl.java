package com.codecoachai.interview.service.impl;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.CurrentQuestionVO;
import com.codecoachai.interview.domain.vo.SseEventVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.InterviewStreamService;
import com.codecoachai.interview.util.SseEmitterUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewStreamServiceImpl implements InterviewStreamService {

    private static final long CHUNK_DELAY_MILLIS = 30L;

    private final InterviewService interviewService;
    @Qualifier("sseStreamExecutor")
    private final Executor sseStreamExecutor;

    @Override
    public SseEmitter streamCurrentQuestion(Long sessionId) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = SseEmitterUtils.createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();

        CompletableFuture.runAsync(() -> executeStream(emitter, active, requestId, loginUser, () -> {
            if (!sendStart(emitter, active, requestId, sessionId, "question stream started") || !active.get()) {
                return;
            }
            CurrentQuestionVO question = interviewService.currentQuestion(sessionId);
            String fullContent = question == null ? null : question.getQuestionContent();
            sendDeltas(emitter, active, requestId, sessionId, fullContent);
            sendMetadata(emitter, active, requestId, sessionId,
                    question == null ? null : question.getMessageId(),
                    questionMetadata(question));
            sendDone(emitter, active, requestId, sessionId,
                    question == null ? null : question.getMessageId(),
                    fullContent);
        }), sseStreamExecutor);
        return emitter;
    }

    @Override
    public SseEmitter streamAnswer(Long sessionId, SubmitInterviewAnswerDTO dto) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = SseEmitterUtils.createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();

        CompletableFuture.runAsync(() -> executeStream(emitter, active, requestId, loginUser, () -> {
            if (!sendStart(emitter, active, requestId, sessionId, "answer evaluation stream started") || !active.get()) {
                return;
            }
            SubmitInterviewAnswerVO answer = interviewService.answer(sessionId, dto);
            CurrentQuestionVO nextQuestion = answer == null ? null : answer.getNextQuestion();
            String fullContent = answerVisibleContent(answer);
            sendDeltas(emitter, active, requestId, sessionId, fullContent);
            sendMetadata(emitter, active, requestId, sessionId,
                    nextQuestion == null ? null : nextQuestion.getMessageId(),
                    answerMetadata(answer));
            sendDone(emitter, active, requestId, sessionId,
                    nextQuestion == null ? null : nextQuestion.getMessageId(),
                    fullContent);
        }), sseStreamExecutor);
        return emitter;
    }

    private void executeStream(SseEmitter emitter, AtomicBoolean active, String requestId,
                               LoginUser loginUser, Runnable runnable) {
        try {
            LoginUserContext.setLoginUser(loginUser);
            runnable.run();
        } catch (RuntimeException ex) {
            log.warn("SSE stream task failed, requestId={}", requestId, ex);
            sendError(emitter, active, requestId, "SSE_STREAM_ERROR");
        } finally {
            LoginUserContext.clear();
        }
    }

    private boolean sendStart(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId, String message) {
        return SseEmitterUtils.send(emitter, active, "start", SseEventVO.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    private void sendDeltas(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId, String fullContent) {
        int index = 1;
        for (String chunk : SseEmitterUtils.splitContent(fullContent)) {
            if (!SseEmitterUtils.send(emitter, active, "delta", SseEventVO.builder()
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .content(chunk)
                    .index(index++)
                    .build())) {
                return;
            }
            sleepBetweenChunks();
        }
    }

    private void sendMetadata(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                              Long messageId, Map<String, Object> metadata) {
        SseEmitterUtils.send(emitter, active, "metadata", SseEventVO.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .messageId(messageId)
                .aiCallLogId(null)
                .metadata(metadata)
                .build());
    }

    private void sendDone(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                          Long messageId, String fullContent) {
        if (SseEmitterUtils.send(emitter, active, "done", SseEventVO.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .messageId(messageId)
                .aiCallLogId(null)
                .fullContent(fullContent)
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean active, String requestId, String code) {
        if (SseEmitterUtils.send(emitter, active, "error", SseEventVO.builder()
                .requestId(requestId)
                .code(code)
                .message("流式输出失败，请稍后重试")
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private Map<String, Object> questionMetadata(CurrentQuestionVO question) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (question == null) {
            return metadata;
        }
        metadata.put("stageId", question.getStageId());
        metadata.put("stageName", question.getStageName());
        metadata.put("questionId", question.getQuestionId());
        metadata.put("questionGroupId", question.getQuestionGroupId());
        metadata.put("isFollowUp", question.getIsFollowUp());
        metadata.put("parentMessageId", question.getParentMessageId());
        metadata.put("followUpCount", question.getFollowUpCount());
        metadata.put("stageProgress", question.getStageProgress());
        metadata.put("interviewStatus", question.getInterviewStatus());
        return metadata;
    }

    private Map<String, Object> answerMetadata(SubmitInterviewAnswerVO answer) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (answer == null) {
            return metadata;
        }
        metadata.put("score", answer.getScore());
        metadata.put("nextAction", answer.getNextAction());
        metadata.put("knowledgePoints", answer.getKnowledgePoints());
        metadata.put("followUpReason", answer.getFollowUpReason());
        metadata.put("followUpValid", answer.getFollowUpValid());
        if (answer.getNextQuestion() != null) {
            metadata.put("nextQuestion", questionMetadata(answer.getNextQuestion()));
        }
        return metadata;
    }

    private String answerVisibleContent(SubmitInterviewAnswerVO answer) {
        if (answer == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendBlock(builder, answer.getComment());
        appendBlock(builder, answer.getFollowUpQuestion());
        if (answer.getNextQuestion() != null) {
            appendBlock(builder, answer.getNextQuestion().getQuestionContent());
        }
        if (builder.length() == 0 && StringUtils.hasText(answer.getNextAction())) {
            builder.append(answer.getNextAction());
        }
        return builder.toString();
    }

    private void appendBlock(StringBuilder builder, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(content.trim());
    }

    private void sleepBetweenChunks() {
        try {
            Thread.sleep(CHUNK_DELAY_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
