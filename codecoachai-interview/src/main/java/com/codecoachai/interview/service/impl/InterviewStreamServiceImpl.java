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
            sendChunks(emitter, active, requestId, sessionId, fullContent);
            sendMetadata(emitter, active, requestId, sessionId,
                    question == null ? null : question.getMessageId(), questionMetadata(question));
            sendDone(emitter, active, requestId, sessionId,
                    question == null ? null : question.getMessageId(), fullContent);
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
            sendChunks(emitter, active, requestId, sessionId, fullContent);
            sendMetadata(emitter, active, requestId, sessionId,
                    nextQuestion == null ? null : nextQuestion.getMessageId(), answerMetadata(answer));
            sendDone(emitter, active, requestId, sessionId,
                    nextQuestion == null ? null : nextQuestion.getMessageId(), fullContent);
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
                .requestId(requestId)
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    private void sendChunks(SseEmitter emitter, AtomicBoolean active, String requestId, Long sessionId, String fullContent) {
        int index = 1;
        for (String chunk : SseEmitterUtils.splitContent(fullContent)) {
            SseEventVO event = SseEventVO.builder()
                    .requestId(requestId)
                    .sessionId(sessionId)
                    .content(chunk)
                    .index(index++)
                    .build();
            if (!SseEmitterUtils.send(emitter, active, "chunk", event)) {
                return;
            }
            if (!SseEmitterUtils.send(emitter, active, "delta", event)) {
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
                .message("Streaming failed. Please retry later.")
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
        SseEmitterUtils.send(emitter, active, "progress", SseEventVO.builder()
                .type("progress")
                .requestId(requestId)
                .interviewId(interviewId)
                .sessionId(interviewId)
                .stage(stage)
                .message(interviewReportStageMessage(stage))
                .build());
    }

    private void sendInterviewReportResult(SseEmitter emitter, AtomicBoolean active, String requestId,
                                           InterviewReportGenerateResultVO result) {
        if (result == null) {
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
                .message("面试报告生成失败，请稍后重试")
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
