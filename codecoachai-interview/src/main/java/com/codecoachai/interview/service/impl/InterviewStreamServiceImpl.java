package com.codecoachai.interview.service.impl;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.CurrentQuestionVO;
import com.codecoachai.interview.domain.vo.InterviewReportGenerateResultVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
import com.codecoachai.interview.domain.vo.SseEventVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.InterviewStreamService;
import com.codecoachai.interview.service.StudyPlanService;
import com.codecoachai.interview.util.SseEmitterUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class InterviewStreamServiceImpl implements InterviewStreamService {

    private static final long CHUNK_DELAY_MILLIS = 30L;

    private final InterviewService interviewService;
    private final StudyPlanService studyPlanService;
    private final Executor sseStreamExecutor;

    public InterviewStreamServiceImpl(InterviewService interviewService,
                                      StudyPlanService studyPlanService,
                                      @Qualifier("sseStreamExecutor") Executor sseStreamExecutor) {
        this.interviewService = interviewService;
        this.studyPlanService = studyPlanService;
        this.sseStreamExecutor = sseStreamExecutor;
    }

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
            sendQuestionProgress(emitter, active, requestId, sessionId, question);
            sendChunks(emitter, active, requestId, sessionId, fullContent);
            sendMetadata(emitter, active, requestId, sessionId,
                    question == null ? null : question.getMessageId(), questionMetadata(question));
            sendQuestionResult(emitter, active, requestId, sessionId, question);
            sendQuestionDone(emitter, active, requestId, sessionId, question, fullContent);
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
            try {
                if (!sendAnswerReviewStart(emitter, active, requestId, sessionId) || !active.get()) {
                    return;
                }
                SubmitInterviewAnswerVO answer = interviewService.answerForSse(sessionId, dto,
                        stage -> sendAnswerReviewProgress(emitter, active, requestId, sessionId, stage));
                if (!active.get()) {
                    return;
                }
                sendAnswerReviewResult(emitter, active, requestId, sessionId, answer);
                sendAnswerReviewDone(emitter, active, requestId, sessionId, answer);
            } catch (RuntimeException ex) {
                log.warn("Interview answer review SSE failed, requestId={}, interviewId={}", requestId, sessionId, ex);
                sendAnswerReviewError(emitter, active, requestId, sessionId);
            }
        }), sseStreamExecutor);
        return emitter;
    }

    @Override
    public SseEmitter streamReport(Long sessionId) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = SseEmitterUtils.createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        CompletableFuture.runAsync(() -> executeStream(emitter, active, requestId, loginUser, () -> {
            if (!sendStart(emitter, active, requestId, sessionId, "report stream started") || !active.get()) {
                return;
            }
            InterviewReportVO report = interviewService.report(sessionId);
            String fullContent = firstText(report == null ? null : report.getReportContent(),
                    report == null ? null : report.getSummary(),
                    "Interview report generated");
            sendChunks(emitter, active, requestId, sessionId, fullContent);
            sendMetadata(emitter, active, requestId, sessionId, null, reportMetadata(report));
            sendDone(emitter, active, requestId, sessionId, null, fullContent);
        }), sseStreamExecutor);
        return emitter;
    }

    @Override
    public SseEmitter streamInterviewReport(Long interviewId, Long reportId, Boolean forceRegenerate) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = SseEmitterUtils.createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        CompletableFuture.runAsync(() -> executeStream(emitter, active, requestId, loginUser, () -> {
            try {
                if (!sendInterviewReportStart(emitter, active, requestId, interviewId) || !active.get()) {
                    return;
                }
                InterviewReportGenerateResultVO result = interviewService.generateReportForSse(interviewId, reportId,
                        forceRegenerate, stage -> sendInterviewReportProgress(emitter, active, requestId, interviewId, stage));
                if (!active.get()) {
                    return;
                }
                sendInterviewReportResult(emitter, active, requestId, result);
                sendInterviewReportDone(emitter, active, requestId, result);
            } catch (RuntimeException ex) {
                log.warn("Interview report SSE failed, requestId={}, interviewId={}", requestId, interviewId, ex);
                sendInterviewReportError(emitter, active, requestId, interviewId, reportId);
            }
        }), sseStreamExecutor);
        return emitter;
    }

    @Override
    public SseEmitter streamStudyPlan(StudyPlanGenerateDTO dto) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = SseEmitterUtils.createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        CompletableFuture.runAsync(() -> executeStream(emitter, active, requestId, loginUser, () -> {
            if (!sendStart(emitter, active, requestId, null, "study plan stream started") || !active.get()) {
                return;
            }
            StudyPlanGenerateVO plan = studyPlanService.generate(dto);
            String fullContent = "Study plan generated: "
                    + firstText(plan == null ? null : plan.getPlanTitle(), "Untitled plan");
            sendChunks(emitter, active, requestId, null, fullContent);
            sendMetadata(emitter, active, requestId, null, null, studyPlanMetadata(plan));
            sendDone(emitter, active, requestId, null, null, fullContent);
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
                .type("start")
                .requestId(requestId)
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    private void sendChunks(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId, String fullContent) {
        int index = 1;
        for (String chunk : SseEmitterUtils.splitContent(fullContent)) {
            SseEventVO chunkEvent = SseEventVO.builder()
                    .type("chunk")
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .content(chunk)
                    .message(chunk)
                    .index(index)
                    .build();
            SseEventVO deltaEvent = SseEventVO.builder()
                    .type("delta")
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .content(chunk)
                    .message(chunk)
                    .index(index++)
                    .build();
            if (!SseEmitterUtils.send(emitter, active, "chunk", chunkEvent)) {
                return;
            }
            if (!SseEmitterUtils.send(emitter, active, "delta", deltaEvent)) {
                return;
            }
            sleepBetweenChunks();
        }
    }

    private void sendMetadata(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                              Long messageId, Map<String, Object> metadata) {
        SseEmitterUtils.send(emitter, active, "metadata", SseEventVO.builder()
                .type("metadata")
                .requestId(requestId)
                .sessionId(sessionId)
                .messageId(messageId)
                .aiCallLogId(null)
                .metadata(metadata)
                .build());
    }

    private void sendQuestionProgress(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                                      CurrentQuestionVO question) {
        SseEmitterUtils.send(emitter, active, "progress", SseEventVO.builder()
                .type("progress")
                .requestId(requestId)
                .interviewId(sessionId)
                .sessionId(sessionId)
                .messageId(question == null ? null : question.getMessageId())
                .stage("CURRENT_QUESTION")
                .message("current question loaded")
                .content(question == null ? null : question.getQuestionContent())
                .metadata(questionMetadata(question))
                .build());
    }

    private void sendQuestionResult(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                                    CurrentQuestionVO question) {
        if (question == null) {
            return;
        }
        SseEmitterUtils.send(emitter, active, "result", SseEventVO.builder()
                .type("result")
                .requestId(requestId)
                .interviewId(sessionId)
                .sessionId(sessionId)
                .messageId(question.getMessageId())
                .stage("CURRENT_QUESTION")
                .message("current question ready")
                .content(question.getQuestionContent())
                .result(questionResult(question))
                .metadata(questionMetadata(question))
                .build());
    }

    private void sendDone(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                          Long messageId, String fullContent) {
        if (SseEmitterUtils.send(emitter, active, "done", SseEventVO.builder()
                .type("done")
                .requestId(requestId)
                .sessionId(sessionId)
                .messageId(messageId)
                .aiCallLogId(null)
                .message("stream completed")
                .fullContent(fullContent)
                .metadata(Map.of("status", "SUCCESS"))
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private void sendQuestionDone(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId,
                                  CurrentQuestionVO question, String fullContent) {
        if (SseEmitterUtils.send(emitter, active, "done", SseEventVO.builder()
                .type("done")
                .requestId(requestId)
                .interviewId(sessionId)
                .sessionId(sessionId)
                .messageId(question == null ? null : question.getMessageId())
                .stage("CURRENT_QUESTION")
                .message("current question stream completed")
                .content(fullContent)
                .fullContent(fullContent)
                .result(question == null ? null : questionResult(question))
                .metadata(questionDoneMetadata(question))
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean active, String requestId, String code) {
        if (SseEmitterUtils.send(emitter, active, "error", SseEventVO.builder()
                .type("error")
                .requestId(requestId)
                .code(code)
                .message("流式生成失败，请稍后重试。")
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private boolean sendInterviewReportStart(SseEmitter emitter, AtomicBoolean active, String requestId, Long interviewId) {
        return SseEmitterUtils.send(emitter, active, "start", SseEventVO.builder()
                .type("start")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .message("面试报告生成开始")
                .build());
    }

    private void sendInterviewReportProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                             Long interviewId, String stage) {
        String message = interviewReportStageMessage(stage);
        SseEventVO standardEvent = SseEventVO.builder()
                .type("delta")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(message)
                .content(message)
                .metadata(Map.of("stage", stage, "status", "PROCESSING"))
                .build();
        if (!SseEmitterUtils.send(emitter, active, "delta", standardEvent)) {
            return;
        }
        SseEmitterUtils.send(emitter, active, "metadata", SseEventVO.builder()
                .type("metadata")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(message)
                .metadata(Map.of("stage", stage, "status", "PROCESSING"))
                .build());
        SseEmitterUtils.send(emitter, active, "progress", SseEventVO.builder()
                .type("progress")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(message)
                .build());
    }

    private void sendInterviewReportResult(SseEmitter emitter, AtomicBoolean active, String requestId,
                                           InterviewReportGenerateResultVO result) {
        if (result == null) {
            return;
        }
        Map<String, Object> metadata = interviewReportMetadata(result);
        if (!SseEmitterUtils.send(emitter, active, "metadata", SseEventVO.builder()
                .type("metadata")
                .requestId(requestId)
                .interviewId(result.getInterviewId())
                .sessionId(result.getInterviewId())
                .reportId(result.getReportId())
                .aiCallLogId(result.getAiCallLogId())
                .message("面试报告结果元数据")
                .metadata(metadata)
                .build())) {
            return;
        }
        SseEmitterUtils.send(emitter, active, "result", SseEventVO.builder()
                .type("result")
                .requestId(requestId)
                .interviewId(result.getInterviewId())
                .sessionId(result.getInterviewId())
                .reportId(result.getReportId())
                .aiCallLogId(result.getAiCallLogId())
                .result(result.getResult())
                .metadata(metadata)
                .build());
    }

    private void sendInterviewReportDone(SseEmitter emitter, AtomicBoolean active, String requestId,
                                         InterviewReportGenerateResultVO result) {
        if (result == null) {
            return;
        }
        if (SseEmitterUtils.send(emitter, active, "done", SseEventVO.builder()
                .type("done")
                .requestId(requestId)
                .interviewId(result.getInterviewId())
                .sessionId(result.getInterviewId())
                .reportId(result.getReportId())
                .message("面试报告生成完成")
                .result(result.getResult())
                .metadata(interviewReportMetadata(result))
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private void sendInterviewReportError(SseEmitter emitter, AtomicBoolean active, String requestId,
                                          Long interviewId, Long reportId) {
        if (SseEmitterUtils.send(emitter, active, "error", SseEventVO.builder()
                .type("error")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .reportId(reportId)
                .code("INTERVIEW_REPORT_FAILED")
                .message("面试报告生成失败，请稍后重试")
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private boolean sendAnswerReviewStart(SseEmitter emitter, AtomicBoolean active, String requestId, Long interviewId) {
        return SseEmitterUtils.send(emitter, active, "start", SseEventVO.builder()
                .type("start")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .message("面试回答点评开始生成")
                .build());
    }

    private void sendAnswerReviewProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                          Long interviewId, String stage) {
        String message = answerReviewStageMessage(stage);
        SseEventVO standardEvent = SseEventVO.builder()
                .type("delta")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(message)
                .content(message)
                .metadata(Map.of("stage", stage, "status", "PROCESSING"))
                .build();
        if (!SseEmitterUtils.send(emitter, active, "delta", standardEvent)) {
            return;
        }
        SseEmitterUtils.send(emitter, active, "metadata", SseEventVO.builder()
                .type("metadata")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(message)
                .metadata(Map.of("stage", stage, "status", "PROCESSING"))
                .build());
        SseEmitterUtils.send(emitter, active, "progress", SseEventVO.builder()
                .type("progress")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(message)
                .build());
    }

    private void sendAnswerReviewResult(SseEmitter emitter, AtomicBoolean active, String requestId,
                                        Long interviewId, SubmitInterviewAnswerVO answer) {
        if (answer == null) {
            return;
        }
        Map<String, Object> metadata = answerMetadata(answer);
        if (!SseEmitterUtils.send(emitter, active, "metadata", SseEventVO.builder()
                .type("metadata")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .messageId(answer.getEvaluationMessageId())
                .aiCallLogId(answer.getAiCallLogId())
                .message("AI 点评结果元数据")
                .metadata(metadata)
                .build())) {
            return;
        }
        SseEmitterUtils.send(emitter, active, "result", SseEventVO.builder()
                .type("result")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .messageId(answer.getEvaluationMessageId())
                .aiCallLogId(answer.getAiCallLogId())
                .result(answerResult(answer))
                .metadata(metadata)
                .build());
    }

    private void sendAnswerReviewDone(SseEmitter emitter, AtomicBoolean active, String requestId,
                                      Long interviewId, SubmitInterviewAnswerVO answer) {
        if (answer == null) {
            return;
        }
        if (SseEmitterUtils.send(emitter, active, "done", SseEventVO.builder()
                .type("done")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .messageId(answer.getEvaluationMessageId())
                .aiCallLogId(answer.getAiCallLogId())
                .message("AI 点评生成完成")
                .result(doneResult(answer))
                .metadata(answerMetadata(answer))
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private void sendAnswerReviewError(SseEmitter emitter, AtomicBoolean active, String requestId, Long interviewId) {
        if (SseEmitterUtils.send(emitter, active, "error", SseEventVO.builder()
                .type("error")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .code("INTERVIEW_ANSWER_REVIEW_FAILED")
                .message("AI 点评生成失败，请稍后重试。")
                .build())) {
            SseEmitterUtils.complete(emitter, active);
        }
    }

    private String interviewReportStageMessage(String stage) {
        return switch (stage) {
            case "LOAD_INTERVIEW" -> "加载面试信息";
            case "LOAD_ANSWERS" -> "加载面试问答";
            case "BUILD_PROMPT" -> "构建报告提示词";
            case "CALL_AI" -> "调用 AI 生成报告";
            case "SAVE_REPORT" -> "保存面试报告";
            default -> "处理面试报告";
        };
    }

    private String answerReviewStageMessage(String stage) {
        return switch (stage) {
            case "VALIDATE_REQUEST" -> "validate request";
            case "LOAD_INTERVIEW" -> "load interview";
            case "SAVE_ANSWER" -> "save answer";
            case "BUILD_PROMPT" -> "build prompt";
            case "CALL_AI_REVIEW" -> "call AI review";
            case "SAVE_REVIEW" -> "save review";
            case "GENERATE_FOLLOW_UP" -> "generate follow-up";
            case "SAVE_FOLLOW_UP" -> "save follow-up";
            default -> "process answer review";
        };
    }

    private Map<String, Object> questionMetadata(CurrentQuestionVO question) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (question == null) {
            return metadata;
        }
        metadata.put("interviewId", question.getSessionId());
        metadata.put("sessionId", question.getSessionId());
        metadata.put("messageId", question.getMessageId());
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

    private Map<String, Object> questionDoneMetadata(CurrentQuestionVO question) {
        Map<String, Object> metadata = questionMetadata(question);
        metadata.put("status", "SUCCESS");
        metadata.put("nextAction", "ANSWER_CURRENT_QUESTION");
        return metadata;
    }

    private Map<String, Object> questionResult(CurrentQuestionVO question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interviewId", question.getSessionId());
        result.put("sessionId", question.getSessionId());
        result.put("messageId", question.getMessageId());
        result.put("questionId", question.getQuestionId());
        result.put("questionGroupId", question.getQuestionGroupId());
        result.put("questionContent", question.getQuestionContent());
        result.put("stageId", question.getStageId());
        result.put("stageName", question.getStageName());
        result.put("stageProgress", question.getStageProgress());
        result.put("isFollowUp", question.getIsFollowUp());
        result.put("parentMessageId", question.getParentMessageId());
        result.put("followUpCount", question.getFollowUpCount());
        result.put("interviewStatus", question.getInterviewStatus());
        result.put("status", "SUCCESS");
        return result;
    }

    private Map<String, Object> answerMetadata(SubmitInterviewAnswerVO answer) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (answer == null) {
            return metadata;
        }
        metadata.put("interviewId", answer.getInterviewId());
        metadata.put("questionId", answer.getQuestionId());
        metadata.put("answerId", answer.getAnswerId());
        metadata.put("evaluationMessageId", answer.getEvaluationMessageId());
        metadata.put("followUpMessageId", answer.getFollowUpMessageId());
        metadata.put("aiCallLogId", answer.getAiCallLogId());
        metadata.put("followUpAiCallLogId", answer.getFollowUpAiCallLogId());
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

    private Map<String, Object> interviewReportMetadata(InterviewReportGenerateResultVO result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result == null) {
            return metadata;
        }
        metadata.put("interviewId", result.getInterviewId());
        metadata.put("reportId", result.getReportId());
        metadata.put("aiCallLogId", result.getAiCallLogId());
        metadata.put("status", "SUCCESS");
        return metadata;
    }

    private Map<String, Object> answerResult(SubmitInterviewAnswerVO answer) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "result");
        result.put("interviewId", answer.getInterviewId());
        result.put("questionId", answer.getQuestionId());
        result.put("answerId", answer.getAnswerId());
        result.put("score", answer.getScore());
        result.put("feedback", answer.getComment());
        result.put("strengths", java.util.List.of());
        result.put("weaknesses", java.util.List.of());
        result.put("suggestions", java.util.List.of());
        result.put("knowledgePoints", answer.getKnowledgePoints());
        result.put("followUpQuestion", answer.getFollowUpQuestion());
        result.put("followUpReason", answer.getFollowUpReason());
        result.put("nextAction", answer.getNextAction());
        result.put("nextQuestion", answer.getNextQuestion());
        result.put("aiCallLogId", answer.getAiCallLogId());
        result.put("followUpAiCallLogId", answer.getFollowUpAiCallLogId());
        return result;
    }

    private Map<String, Object> doneResult(SubmitInterviewAnswerVO answer) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "done");
        result.put("interviewId", answer.getInterviewId());
        result.put("questionId", answer.getQuestionId());
        result.put("answerId", answer.getAnswerId());
        result.put("message", "AI review completed");
        return result;
    }

    private Map<String, Object> reportMetadata(InterviewReportVO report) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (report == null) {
            return metadata;
        }
        metadata.put("reportId", report.getId());
        metadata.put("status", report.getStatus());
        metadata.put("totalScore", report.getTotalScore());
        metadata.put("weakPoints", report.getWeakPoints());
        metadata.put("recommendedQuestions", report.getRecommendedQuestions());
        return metadata;
    }

    private Map<String, Object> studyPlanMetadata(StudyPlanGenerateVO plan) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (plan == null) {
            return metadata;
        }
        metadata.put("planId", plan.getPlanId());
        metadata.put("planStatus", plan.getPlanStatus());
        metadata.put("planTitle", plan.getPlanTitle());
        metadata.put("failureReason", plan.getFailureReason());
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

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
