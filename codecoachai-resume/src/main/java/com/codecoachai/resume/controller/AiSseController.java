package com.codecoachai.resume.controller;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.domain.dto.ResumeJobMatchCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.vo.JobDescriptionAnalysisVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchReportDetailVO;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.service.ResumeJobMatchService;
import com.codecoachai.resume.service.ResumeService;
import com.codecoachai.resume.service.TargetJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ai/sse")
@Tag(name = "AI SSE")
public class AiSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;

    private final ResumeService resumeService;
    private final TargetJobService targetJobService;
    private final ResumeJobMatchService resumeJobMatchService;
    private final Executor resumeSseStreamExecutor;

    public AiSseController(ResumeService resumeService,
                           TargetJobService targetJobService,
                           ResumeJobMatchService resumeJobMatchService,
                           @Qualifier("resumeSseStreamExecutor") Executor resumeSseStreamExecutor) {
        this.resumeService = resumeService;
        this.targetJobService = targetJobService;
        this.resumeJobMatchService = resumeJobMatchService;
        this.resumeSseStreamExecutor = resumeSseStreamExecutor;
    }

    @Operation(summary = "Stream job target description analysis progress",
            description = "Compatibility SSE endpoint for V3 job target analysis. The synchronous /job-targets/{id}/parse API remains available.")
    @GetMapping(value = "/job-targets/{id}/parse", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter jobTargetParse(@PathVariable Long id,
                                     @RequestParam(required = false) Boolean forceRefresh,
                                     @RequestParam(required = false) String userTargetDirection) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        submitSseTask(emitter, active, requestId, "Job target parse",
                genericErrorEvent(requestId, "job-target-parse", id, null), () -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                if (!send(emitter, active, "start", genericEvent(requestId, "job-target-parse", id, null,
                        "岗位分析已开始"))) {
                    return;
                }
                if (!sendStageProgress(emitter, active, requestId, "job-target-parse", id, "LOAD_TARGET",
                        "正在读取目标岗位")) {
                    return;
                }
                JobDescriptionParseDTO dto = new JobDescriptionParseDTO();
                dto.setForceRefresh(forceRefresh);
                dto.setUserTargetDirection(userTargetDirection);
                if (!sendStageProgress(emitter, active, requestId, "job-target-parse", id, "CALL_AI",
                        "正在生成岗位分析")) {
                    return;
                }
                JobDescriptionAnalysisVO result = targetJobService.parseJobDescription(id, dto);
                if (result == null || "FAILED".equalsIgnoreCase(result.getParseStatus())) {
                    send(emitter, active, "error", genericErrorEvent(requestId, "job-target-parse", id, null));
                    complete(emitter, active);
                    return;
                }
                send(emitter, active, "result", genericResultEvent(requestId, "job-target-parse", id, result));
                send(emitter, active, "done", genericEvent(requestId, "job-target-parse", id,
                        result.getAiCallLogId(), "岗位分析已完成"));
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Job target parse SSE failed, requestId={}, targetJobId={}", requestId, id, ex);
                send(emitter, active, "error", genericErrorEvent(requestId, "job-target-parse", id, null));
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        });
        return emitter;
    }

    @Operation(summary = "Stream resume-job match report generation progress",
            description = "Compatibility SSE endpoint for creating a match report. The synchronous POST /resume-job-match/reports API remains the fallback.")
    @GetMapping(value = "/resume-job-match/reports", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter resumeJobMatchCreate(@RequestParam Long resumeId,
                                           @RequestParam Long targetJobId,
                                           @RequestParam(required = false) Long resumeVersionId,
                                           @RequestParam(required = false) Boolean forceRefresh) {
        ResumeJobMatchCreateDTO dto = new ResumeJobMatchCreateDTO();
        dto.setResumeId(resumeId);
        dto.setTargetJobId(targetJobId);
        dto.setResumeVersionId(resumeVersionId);
        dto.setForceRefresh(forceRefresh);
        return streamResumeJobMatchCreate(dto);
    }

    @Operation(summary = "Stream resume-job match report refresh progress",
            description = "Compatibility SSE endpoint for reading or regenerating an existing match report.")
    @GetMapping(value = "/resume-job-match/reports/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter resumeJobMatchReport(@PathVariable Long id,
                                           @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        submitSseTask(emitter, active, requestId, "Resume job match",
                genericErrorEvent(requestId, "resume-job-match", id, null), () -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                if (!send(emitter, active, "start", genericEvent(requestId, "resume-job-match", id, null,
                        "Match report stream started"))) {
                    return;
                }
                ResumeJobMatchReportDetailVO detail;
                if (Boolean.TRUE.equals(forceRefresh)) {
                    if (!sendStageProgress(emitter, active, requestId, "resume-job-match", id, "REGENERATE",
                            "Regenerating match report")) {
                        return;
                    }
                    ResumeJobMatchSubmitVO refreshed = resumeJobMatchService.regenerate(id);
                    Long reportId = refreshed == null ? id : refreshed.getReportId();
                    detail = resumeJobMatchService.getReport(reportId);
                } else {
                    if (!sendStageProgress(emitter, active, requestId, "resume-job-match", id, "LOAD_REPORT",
                            "Loading match report")) {
                        return;
                    }
                    detail = resumeJobMatchService.getReport(id);
                }
                send(emitter, active, "result", genericResultEvent(requestId, "resume-job-match",
                        detail == null ? id : detail.getReportId(), detail));
                send(emitter, active, "done", genericEvent(requestId, "resume-job-match",
                        detail == null ? id : detail.getReportId(),
                        detail == null ? null : detail.getAiCallLogId(), "Match report completed"));
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Resume job match SSE failed, requestId={}, reportId={}", requestId, id, ex);
                send(emitter, active, "error", genericErrorEvent(requestId, "resume-job-match", id, null));
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        });
        return emitter;
    }

    private SseEmitter streamResumeJobMatchCreate(ResumeJobMatchCreateDTO dto) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        submitSseTask(emitter, active, requestId, "Resume job match create",
                genericErrorEvent(requestId, "resume-job-match",
                        dto == null ? null : dto.getTargetJobId(), null), () -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                Long bizId = dto == null ? null : dto.getTargetJobId();
                if (!send(emitter, active, "start", genericEvent(requestId, "resume-job-match", bizId, null,
                        "岗位匹配报告已开始生成"))) {
                    return;
                }
                if (!sendStageProgress(emitter, active, requestId, "resume-job-match", bizId, "LOAD_CONTEXT",
                        "正在整理简历和岗位描述")) {
                    return;
                }
                if (!sendStageProgress(emitter, active, requestId, "resume-job-match", bizId, "SUBMIT_TASK",
                        "正在提交匹配报告生成任务")) {
                    return;
                }
                ResumeJobMatchSubmitVO submitted = resumeJobMatchService.createReport(dto);
                if (submitted == null || "FAILED".equalsIgnoreCase(submitted.getStatus())) {
                    send(emitter, active, "error", genericErrorEvent(requestId, "resume-job-match", bizId, null));
                    complete(emitter, active);
                    return;
                }
                if (!sendStageProgress(emitter, active, requestId, "resume-job-match", submitted.getReportId(),
                        "LOAD_REPORT", "Loading match report status")) {
                    return;
                }
                ResumeJobMatchReportDetailVO detail = resumeJobMatchService.getReport(submitted.getReportId());
                send(emitter, active, "result", genericResultEvent(requestId, "resume-job-match",
                        submitted.getReportId(), detail));
                send(emitter, active, "done", genericEvent(requestId, "resume-job-match",
                        submitted.getReportId(), submitted.getAiCallLogId(), "Match report task accepted"));
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Resume job match create SSE failed, requestId={}", requestId, ex);
                send(emitter, active, "error", genericErrorEvent(requestId, "resume-job-match",
                        dto == null ? null : dto.getTargetJobId(), null));
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        });
        return emitter;
    }

    @Operation(summary = "Stream resume optimization progress",
            description = "User SSE endpoint. Emits start/progress/result/done/error events for resume optimization. POST /resumes/{id}/optimize remains the synchronous fallback.")
    @GetMapping(value = "/resume-optimize", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter resumeOptimize(@RequestParam Long resumeId,
                                     @RequestParam(required = false) String targetPosition,
                                     @RequestParam(required = false) String targetCompany,
                                     @RequestParam(required = false) String extraRequirements,
                                     @RequestParam(required = false) String optimizeFocus,
                                     @RequestParam(required = false) Integer experienceYears,
                                     @RequestParam(required = false) Long targetJobId,
                                     @RequestParam(required = false) String industryDirection) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        submitSseTask(emitter, active, requestId, "Resume optimize",
                errorEvent(requestId, resumeId, null), () -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                if (!send(emitter, active, "start", event(requestId, "start", "简历优化开始", resumeId, null, null, null))) {
                    return;
                }
                if (!sendProgress(emitter, active, requestId, resumeId, "LOAD_RESUME", "正在校验简历归属")) {
                    return;
                }
                if (!sendProgress(emitter, active, requestId, resumeId, "BUILD_PROMPT", "正在准备简历优化上下文")) {
                    return;
                }
                if (!sendProgress(emitter, active, requestId, resumeId, "CALL_AI", "正在调用 AI 生成优化建议")) {
                    return;
                }
                ResumeOptimizeRequestDTO dto = new ResumeOptimizeRequestDTO();
                dto.setTargetJobId(targetJobId);
                dto.setTargetPosition(targetPosition);
                dto.setTargetCompany(targetCompany);
                dto.setExtraRequirements(extraRequirements);
                dto.setOptimizeFocus(optimizeFocus);
                dto.setExperienceYears(experienceYears);
                dto.setIndustryDirection(industryDirection);
                ResumeOptimizeSubmitVO result = resumeService.optimizeResume(resumeId, dto);
                if (!sendProgress(emitter, active, requestId, resumeId, "SAVE_RECORD", "正在保存简历优化记录")) {
                    return;
                }
                if (result == null || ResumeOptimizeStatus.FAILED.getCode().equals(result.getOptimizeStatus())) {
                    send(emitter, active, "error", errorEvent(requestId, resumeId, result));
                    complete(emitter, active);
                    return;
                }
                if (!send(emitter, active, "metadata", metadataEvent(requestId, result))) {
                    return;
                }
                if (!send(emitter, active, "result", resultEvent(requestId, result))) {
                    return;
                }
                send(emitter, active, "done", doneEvent(requestId, result));
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Resume SSE stream task failed, requestId={}", requestId, ex);
                send(emitter, active, "error", errorEvent(requestId, resumeId, null));
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        });
        return emitter;
    }

    CompletableFuture<Void> submitSseTask(SseEmitter emitter, AtomicBoolean active, String requestId,
                                          String workflow, Object rejectionErrorEvent, Runnable task) {
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(task, resumeSseStreamExecutor);
            bindCancellation(emitter, active, future);
            return future;
        } catch (RejectedExecutionException ex) {
            log.warn("{} SSE task rejected, requestId={}", workflow, requestId, ex);
            send(emitter, active, "error", rejectionErrorEvent);
            complete(emitter, active);
            return null;
        }
    }

    private void bindCancellation(SseEmitter emitter, AtomicBoolean active, CompletableFuture<?> task) {
        if (task == null) {
            return;
        }
        Runnable cancel = () -> cancelStream(active, task);
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(ex -> cancel.run());
        if (!active.get()) {
            cancel.run();
        }
    }

    private void cancelStream(AtomicBoolean active, CompletableFuture<?> task) {
        active.set(false);
        task.cancel(true);
    }

    private boolean sendStageProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                      String workflow, Long bizId, String stage, String message) {
        Map<String, Object> delta = genericEvent(requestId, workflow, bizId, null, message);
        delta.put("stage", stage);
        delta.put("content", message);
        delta.put("metadata", stageMetadata(stage, "PROCESSING"));
        if (!send(emitter, active, "delta", delta)) {
            return false;
        }
        Map<String, Object> metadata = genericEvent(requestId, workflow, bizId, null, message);
        metadata.put("stage", stage);
        metadata.put("metadata", stageMetadata(stage, "PROCESSING"));
        if (!send(emitter, active, "metadata", metadata)) {
            return false;
        }
        Map<String, Object> progress = genericEvent(requestId, workflow, bizId, null, message);
        progress.put("stage", stage);
        return send(emitter, active, "progress", progress);
    }

    private Map<String, Object> genericResultEvent(String requestId, String workflow, Long bizId, Object result) {
        Map<String, Object> data = genericEvent(requestId, workflow, bizId, null, "Task completed");
        data.put("result", result);
        return data;
    }

    private Map<String, Object> genericErrorEvent(String requestId, String workflow, Long bizId, String ignoredMessage) {
        Map<String, Object> data = genericEvent(requestId, workflow, bizId, null, safeWorkflowErrorMessage(workflow));
        data.put("code", "V3_SSE_TASK_FAILED");
        data.put("metadata", stageMetadata("ERROR", "FAILED"));
        return data;
    }

    private Map<String, Object> genericEvent(String requestId, String workflow, Long bizId, Long aiCallLogId,
                                             String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("workflow", workflow);
        data.put("bizId", bizId);
        data.put("aiCallLogId", aiCallLogId);
        data.put("message", message);
        return data;
    }

    private boolean sendProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                 Long resumeId, String stage, String message) {
        if (!send(emitter, active, "delta", deltaEvent(requestId, resumeId, stage, message))) {
            return false;
        }
        if (!send(emitter, active, "metadata", stageMetadataEvent(requestId, resumeId, stage, message))) {
            return false;
        }
        Map<String, Object> data = event(requestId, "progress", message, resumeId, null, null, null);
        data.put("stage", stage);
        return send(emitter, active, "progress", data);
    }

    private Map<String, Object> deltaEvent(String requestId, Long resumeId, String stage, String message) {
        Map<String, Object> data = event(requestId, "delta", message, resumeId, null, null, null);
        data.put("stage", stage);
        data.put("content", message);
        data.put("metadata", stageMetadata(stage, "PROCESSING"));
        return data;
    }

    private Map<String, Object> stageMetadataEvent(String requestId, Long resumeId, String stage, String message) {
        Map<String, Object> data = event(requestId, "metadata", message, resumeId, null, null, null);
        data.put("stage", stage);
        data.put("metadata", stageMetadata(stage, "PROCESSING"));
        return data;
    }

    private Map<String, Object> resultEvent(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> data = event(requestId, "result", "简历优化结果已生成",
                result.getResumeId(), result.getOptimizeRecordId(), result.getAiCallLogId(), resultPayload(result));
        data.put("optimizeStatus", result.getOptimizeStatus());
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> doneEvent(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> data = event(requestId, "done", "简历优化完成",
                result.getResumeId(), result.getOptimizeRecordId(), result.getAiCallLogId(), null);
        data.put("result", resultPayload(result));
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> metadataEvent(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> data = event(requestId, "metadata", "简历优化结果元数据",
                result.getResumeId(), result.getOptimizeRecordId(), result.getAiCallLogId(), null);
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> errorEvent(String requestId, Long resumeId, ResumeOptimizeSubmitVO result) {
        Long resolvedResumeId = result == null ? resumeId : result.getResumeId();
        Long recordId = result == null ? null : result.getOptimizeRecordId();
        Long aiCallLogId = result == null ? null : result.getAiCallLogId();
        Map<String, Object> data = event(requestId, "error", safeResumeOptimizeErrorMessage(),
                resolvedResumeId, recordId, aiCallLogId, null);
        data.put("code", "RESUME_OPTIMIZE_FAILED");
        data.put("metadata", result == null ? stageMetadata("ERROR", "FAILED") : resultMetadata(result));
        return data;
    }

    private Map<String, Object> resultPayload(ResumeOptimizeSubmitVO result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result == null) {
            return payload;
        }
        payload.put("optimizeRecordId", result.getOptimizeRecordId());
        payload.put("resumeId", result.getResumeId());
        payload.put("aiCallLogId", result.getAiCallLogId());
        payload.put("optimizeStatus", result.getOptimizeStatus());
        payload.put("overallScore", result.getOverallScore());
        payload.put("overallComment", result.getOverallComment());
        payload.put("rewriteSuggestions", result.getRewriteSuggestions());
        payload.put("riskWarnings", result.getRiskWarnings());
        payload.put("possibleInterviewQuestions", result.getPossibleInterviewQuestions());
        payload.put("nextActions", result.getNextActions());
        if (ResumeOptimizeStatus.FAILED.getCode().equals(result.getOptimizeStatus())) {
            payload.put("errorMessage", safeResumeOptimizeErrorMessage());
        }
        return payload;
    }

    private String safeWorkflowErrorMessage(String workflow) {
        if ("job-target-parse".equals(workflow)) {
            return "岗位分析生成失败，请稍后重试";
        }
        if ("resume-job-match".equals(workflow)) {
            return "匹配报告生成失败，请稍后重试";
        }
        return "任务执行失败，请稍后重试";
    }

    private String safeResumeOptimizeErrorMessage() {
        return "简历优化失败，请稍后重试";
    }

    private Map<String, Object> resultMetadata(ResumeOptimizeSubmitVO result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result == null) {
            return metadata;
        }
        metadata.put("resumeId", result.getResumeId());
        metadata.put("recordId", result.getOptimizeRecordId());
        metadata.put("aiCallLogId", result.getAiCallLogId());
        metadata.put("status", result.getOptimizeStatus());
        metadata.put("optimizeStatus", result.getOptimizeStatus());
        return metadata;
    }

    private Map<String, Object> stageMetadata(String stage, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", stage);
        metadata.put("status", status);
        return metadata;
    }

    private Map<String, Object> event(String requestId, String type, String message, Long resumeId,
                                      Long recordId, Long aiCallLogId, Object result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("type", type);
        data.put("message", message);
        data.put("resumeId", resumeId);
        data.put("recordId", recordId);
        data.put("aiCallLogId", aiCallLogId);
        if (result != null) {
            data.put("result", result);
        }
        return data;
    }

    private SseEmitter createEmitter(String requestId, AtomicBoolean active) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onCompletion(() -> active.set(false));
        emitter.onTimeout(() -> {
            active.set(false);
            completeQuietly(emitter, requestId, "timeout");
        });
        emitter.onError(ex -> {
            active.set(false);
            log.debug("Resume SSE connection error, requestId={}", requestId, ex);
        });
        return emitter;
    }

    private boolean send(SseEmitter emitter, AtomicBoolean active, String event, Object data) {
        if (!active.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException | IllegalStateException ex) {
            active.set(false);
            log.debug("Resume SSE send failed, event={}", event, ex);
            return false;
        }
    }

    private void complete(SseEmitter emitter, AtomicBoolean active) {
        if (active.getAndSet(false)) {
            emitter.complete();
        }
    }

    private void completeQuietly(SseEmitter emitter, String requestId, String reason) {
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("Resume SSE complete ignored, requestId={}, reason={}", requestId, reason, ex);
        }
    }
}
