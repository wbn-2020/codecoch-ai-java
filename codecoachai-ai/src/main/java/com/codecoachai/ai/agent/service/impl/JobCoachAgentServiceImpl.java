package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.convert.AgentConvert;
import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult.FocusSkill;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult.PlanTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentCoachActionDTO;
import com.codecoachai.ai.agent.domain.dto.AgentMetricEventDTO;
import com.codecoachai.ai.agent.domain.dto.AgentRunFailureDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskSkipDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.ActivationHandoffVO;
import com.codecoachai.ai.agent.domain.vo.AgentCoachActionVO;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentRunUserDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.ai.agent.domain.vo.SuggestionEvidenceSourceVO;
import com.codecoachai.ai.agent.feign.InterviewReportEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.QuestionPracticeEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.ResumeJobApplicationEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.ResumeOptimizeRecordEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.vo.InterviewReportEvidenceVO;
import com.codecoachai.ai.agent.feign.vo.JobApplicationEventEvidenceVO;
import com.codecoachai.ai.agent.feign.vo.PracticeRecordEvidenceVO;
import com.codecoachai.ai.agent.feign.vo.ResumeOptimizeRecordEvidenceVO;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mq.AgentMqDispatcher;
import com.codecoachai.ai.agent.service.AgentContextBuilder;
import com.codecoachai.ai.agent.service.AgentMetricsService;
import com.codecoachai.ai.agent.service.AgentOutputParser;
import com.codecoachai.ai.agent.service.AgentOutputValidator;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.security.AiPiiMasker;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobCoachAgentServiceImpl implements JobCoachAgentService {

    private static final String AGENT_TYPE = "JOB_COACH";
    private static final String REVIEW_SOURCE_RULE = "RULE";
    private static final String REVIEW_SOURCE_RULE_LABEL = "规则复盘";
    private static final String TRIGGER_MANUAL = "MANUAL";
    private static final String REVIEW_SOURCE_LLM_LABEL = "AI复盘";
    private static final String REVIEW_SOURCE_FALLBACK_LABEL = "规则兜底";
    private static final String REVIEW_PROMPT_SCENE = "agent.task.review";
    private static final String REVIEW_PROMPT_VERSION = "agent-task-review-v1";
    private static final String COACH_PROMPT_SCENE = "agent.coach.contextual_action";
    private static final String COACH_PROMPT_VERSION = "agent-coach-contextual-action-v1";
    private static final String ACTION_EXPLAIN_RECOMMENDATION = "EXPLAIN_RECOMMENDATION";
    private static final String ACTION_REVIEW_COMPLETED_TASK = "REVIEW_COMPLETED_TASK";
    private static final String TASK_TYPE_QUESTION_PRACTICE = "QUESTION_PRACTICE";
    private static final String TASK_TYPE_INTERVIEW = "INTERVIEW";
    private static final String TASK_TYPE_APPLICATION_FOLLOW_UP = "APPLICATION_FOLLOW_UP";
    private static final String TASK_TYPE_RESUME_OPTIMIZE = "RESUME_OPTIMIZE";
    private static final String EVIDENCE_TYPE_PRACTICE_RECORD = "PRACTICE_RECORD";
    private static final String EVIDENCE_TYPE_INTERVIEW_REPORT = "INTERVIEW_REPORT";
    private static final String EVIDENCE_TYPE_JOB_APPLICATION_EVENT = "JOB_APPLICATION_EVENT";
    private static final String EVIDENCE_TYPE_RESUME_OPTIMIZE_RECORD = "RESUME_OPTIMIZE_RECORD";
    private static final String EVIDENCE_TYPE_PROJECT_EVIDENCE = "PROJECT_EVIDENCE";
    private static final String BIZ_TYPE_TARGET_JOB = "TARGET_JOB";
    private static final String BIZ_TYPE_JOB_APPLICATION = "JOB_APPLICATION";
    private static final String BIZ_TYPE_PROJECT_EVIDENCE = "PROJECT_EVIDENCE";
    private static final String REPORT_STATUS_GENERATED = "GENERATED";
    private static final String RESUME_OPTIMIZE_STATUS_SUCCESS = "SUCCESS";
    private static final int DEFAULT_TASK_COUNT = 3;
    private static final int DEFAULT_MAX_TOTAL_MINUTES = 120;
    private static final String RUN_FORCE_REGENERATED = "AGENT_RUN_FORCE_REGENERATED";
    private static final String HANDOFF_CODE_TARGET_DIRECTION_ESTABLISHED = "ACT-001-TARGET-DIRECTION-ESTABLISHED";
    private static final String HANDOFF_CODE_FIRST_PLAN_GENERATED = "ACT-001-FIRST-PLAN-GENERATED";
    private static final String HANDOFF_CODE_FIRST_TASK_COMPLETED = "ACT-001-FIRST-TASK-COMPLETED";
    private static final String HANDOFF_STAGE_TARGET_DIRECTION = "target_direction_established";
    private static final String HANDOFF_STAGE_FIRST_PLAN = "first_plan_generated";
    private static final String HANDOFF_STAGE_FIRST_TASK_COMPLETED = "first_task_completed";
    private static final List<String> ACTIVE_PLAN_STATUSES = List.of(
            AgentRunStatusEnum.RUNNING.name(),
            AgentRunStatusEnum.SUCCESS.name());
    private static final List<String> VISIBLE_PLAN_STATUSES = List.of(
            AgentRunStatusEnum.RUNNING.name(),
            AgentRunStatusEnum.SUCCESS.name(),
            AgentRunStatusEnum.FAILED.name());

    private final AgentRunMapper agentRunMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentReviewMapper agentReviewMapper;
    private final AgentContextBuilder agentContextBuilder;
    private final CandidateTaskBuilder candidateTaskBuilder;
    private final AgentPromptBuilder agentPromptBuilder;
    private final AgentOutputParser agentOutputParser;
    private final AgentOutputValidator agentOutputValidator;
    private final AgentMetricsService agentMetricsService;
    private final AiCallLogService aiCallLogService;
    private final QuestionPracticeEvidenceFeignClient questionPracticeEvidenceFeignClient;
    private final ResumeJobApplicationEvidenceFeignClient resumeJobApplicationEvidenceFeignClient;
    private final InterviewReportEvidenceFeignClient interviewReportEvidenceFeignClient;
    private final ResumeOptimizeRecordEvidenceFeignClient resumeOptimizeRecordEvidenceFeignClient;
    private final ObjectMapper objectMapper;
    private final AgentMqDispatcher agentMqDispatcher;
    private final TransactionTemplate transactionTemplate;

    @Value("${codecoachai.agent.daily-plan.timeout-recovery.stale-minutes:15}")
    private long dailyPlanStaleMinutes;

    @Override
    public DailyPlanVO generateDailyPlan(Long userId, DailyPlanGenerateDTO dto) {
        DailyPlanGenerateDTO request = normalizeDailyPlanRequest(dto);
        LocalDate planDate = request.getDate();
        Long scopeTargetJobId = resolveTargetJobIdForScope(userId, request.getTargetJobId(), planDate);
        request.setTargetJobId(scopeTargetJobId);
        AgentRun existing = latestVisibleRun(userId, scopeTargetJobId, planDate);
        if (!Boolean.TRUE.equals(request.getForceRegenerate())) {
            if (existing != null && AgentRunStatusEnum.SUCCESS.name().equals(existing.getStatus())) {
                return toDailyPlan(existing);
            }
            if (existing != null && AgentRunStatusEnum.RUNNING.name().equals(existing.getStatus())) {
                if (!isStaleRunning(existing)) {
                    return toDailyPlan(existing);
                }
                markFailed(existing, AgentErrorCode.RUN_TIMEOUT, "计划生成超时，请重新生成今日计划。",
                        durationFromStart(existing));
                AgentRun active = latestActiveRun(userId, scopeTargetJobId, planDate);
                if (active != null && AgentRunStatusEnum.SUCCESS.name().equals(active.getStatus())) {
                    return toDailyPlan(active);
                }
            }
        } else {
            cancelActiveRuns(userId, scopeTargetJobId, planDate);
        }

        RunCreateResult createResult = createRun(userId, scopeTargetJobId, planDate);
        AgentRun run = createResult.run();
        if (!createResult.created()) {
            return toDailyPlan(run);
        }
        request.setExecutionToken(run.getExecutionToken());
        if (deferDailyPlanDispatchAfterCommit(run.getId(), userId, request)) {
            return toDailyPlan(agentRunMapper.selectById(run.getId()));
        }
        try {
            MqDispatchReceipt receipt = agentMqDispatcher.dispatchDailyPlanWithReceipt(run.getId(), userId, request);
            if (receipt != null) {
                return withAsyncReceipt(toDailyPlan(agentRunMapper.selectById(run.getId())), receipt);
            }
        } catch (RuntimeException ex) {
            log.warn("Agent daily plan dispatch failed before local fallback runId={} userId={}", run.getId(), userId, ex);
        }
        return executeDailyPlanRun(userId, run, request);
    }

    @Override
    public DailyPlanVO executeDailyPlan(Long userId, Long runId, DailyPlanGenerateDTO dto) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null || !userId.equals(run.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.RUN_NOT_FOUND);
        }
        if (!AgentRunStatusEnum.RUNNING.name().equals(run.getStatus())) {
            return toDailyPlan(run);
        }
        DailyPlanGenerateDTO request = normalizeDailyPlanRequest(dto);
        request.setDate(run.getPlanDate());
        if (request.getTargetJobId() == null) {
            request.setTargetJobId(run.getTargetJobId());
        }
        return executeDailyPlanRun(userId, run, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyPlanVO failDailyPlanRun(Long userId, Long runId, AgentRunFailureDTO dto) {
        if (userId == null || runId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId and runId are required");
        }
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null || !userId.equals(run.getUserId()) || Integer.valueOf(1).equals(run.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.RUN_NOT_FOUND);
        }
        if (!AgentRunStatusEnum.RUNNING.name().equals(run.getStatus())) {
            return toDailyPlan(run);
        }
        if (!executionTokenMatches(run, dto == null ? null : dto.getExecutionToken())) {
            return currentDailyPlan(userId, runId);
        }
        String errorCode = agentErrorCode(dto == null ? null : dto.getErrorCode());
        if (!AgentErrorCode.ASYNC_TASK_FAILED.equals(errorCode)) {
            errorCode = AgentErrorCode.ASYNC_TASK_FAILED;
        }
        String errorMessage = dto == null ? null : dto.getErrorMessage();
        markFailed(run, errorCode, friendlyAgentErrorMessage(errorCode, errorMessage), durationFromStart(run));
        AgentRun latest = agentRunMapper.selectById(runId);
        return toDailyPlan(latest == null ? run : latest);
    }

    private DailyPlanVO executeDailyPlanRun(Long userId, AgentRun run, DailyPlanGenerateDTO request) {
        long start = System.currentTimeMillis();
        if (!prepareRunForExecution(userId, run, request.getExecutionToken())) {
            return currentDailyPlan(userId, run.getId());
        }
        try {
            int taskCount = valueOrDefault(request.getTaskCount(), DEFAULT_TASK_COUNT);
            int maxTotalMinutes = valueOrDefault(request.getMaxTotalMinutes(), DEFAULT_MAX_TOTAL_MINUTES);
            JobCoachAgentContext context = agentContextBuilder.build(userId, request.getTargetJobId(), request.getDate());
            run.setTargetJobId(context.getTargetJobId());
            if (!updateRunningRunFields(run, update -> update.set(AgentRun::getTargetJobId, run.getTargetJobId()))) {
                return currentDailyPlan(userId, run.getId());
            }

            List<CandidateTask> candidates = candidateTaskBuilder.build(context, taskCount);
            PromptRenderResult prompt = agentPromptBuilder.buildDailyPlanPrompt(context, candidates, taskCount, maxTotalMinutes);
            run.setInputSnapshotJson(maskAgentRunDiagnosticText(toJson(buildRunInputSnapshot(context, request))));
            run.setPromptType(AgentPromptBuilderImpl.PROMPT_TYPE);
            run.setPromptVersionId(prompt.getPromptTemplateVersionId());
            if (!updateRunningRunFields(run, update -> update
                    .set(AgentRun::getInputSnapshotJson, run.getInputSnapshotJson())
                    .set(AgentRun::getPromptType, run.getPromptType())
                    .set(AgentRun::getPromptVersionId, run.getPromptVersionId()))) {
                return currentDailyPlan(userId, run.getId());
            }

            RouteResult routeResult = callAi(userId, run, prompt);
            DailyPlanResult planResult = agentOutputParser.parseDailyPlan(routeResult.getContent());
            agentOutputValidator.validateDailyPlan(planResult, candidates, taskCount, maxTotalMinutes);
            return transactionTemplate.execute(status -> {
                if (!isRunStillRunning(userId, run.getId(), run.getExecutionToken())) {
                    return currentDailyPlan(userId, run.getId());
                }
                if (!markSuccess(run, planResult, routeResult, System.currentTimeMillis() - start)) {
                    return currentDailyPlan(userId, run.getId());
                }
                clearRunTasks(run);
                saveTasks(userId, run, planResult, candidates);
                return toDailyPlan(agentRunMapper.selectById(run.getId()));
            });
        } catch (BusinessException ex) {
            String errorCode = agentErrorCode(ex.getMessage());
            markFailed(run, errorCode, friendlyAgentErrorMessage(errorCode, ex.getMessage()), System.currentTimeMillis() - start);
            throw new BusinessException(ex.getCode(), friendlyAgentErrorMessage(errorCode, ex.getMessage()));
        } catch (RuntimeException ex) {
            markFailed(run, AgentErrorCode.AI_CALL_FAILED, friendlyAgentErrorMessage(AgentErrorCode.AI_CALL_FAILED, ex.getMessage()),
                    System.currentTimeMillis() - start);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    friendlyAgentErrorMessage(AgentErrorCode.AI_CALL_FAILED, ex.getMessage()));
        }
    }

    @Override
    public DailyPlanVO latestDailyPlan(Long userId, Long targetJobId, LocalDate date) {
        LocalDate planDate = date == null ? LocalDate.now() : date;
        Long scopeTargetJobId = resolveTargetJobIdForScope(userId, targetJobId, planDate);
        AgentRun run = latestVisibleRun(userId, scopeTargetJobId, planDate);
        if (run != null && AgentRunStatusEnum.RUNNING.name().equals(run.getStatus()) && isStaleRunning(run)) {
            markFailed(run, AgentErrorCode.RUN_TIMEOUT, "计划生成超时，请重新生成今日计划。",
                    durationFromStart(run));
            run = agentRunMapper.selectById(run.getId());
        }
        DailyPlanVO recovered = recoverableMissingTargetFailure(run, userId, scopeTargetJobId, planDate);
        if (recovered != null) {
            return recovered;
        }
        if (run == null) {
            return emptyDailyPlan(scopeTargetJobId, planDate,
                    "今天还没有 Agent 计划。先确认目标岗位和默认简历；如果缺少弱点证据，可以先完成一次题目练习或模拟面试，再生成今日计划。");
        }
        return toDailyPlan(run);
    }

    @Override
    public List<AgentTaskVO> todayTasks(Long userId, Long targetJobId, LocalDate date, String status) {
        LocalDate dueDate = date == null ? LocalDate.now() : date;
        Long scopeTargetJobId = resolveTargetJobIdForScope(userId, targetJobId, dueDate);
        AgentRun run = latestActiveRun(userId, scopeTargetJobId, dueDate);
        if (run == null) {
            return List.of();
        }
        if (AgentRunStatusEnum.RUNNING.name().equals(run.getStatus()) && isStaleRunning(run)) {
            markFailed(run, AgentErrorCode.RUN_TIMEOUT, "计划生成超时，请重新生成今日计划。",
                    durationFromStart(run));
            return List.of();
        }
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getUserId, userId)
                        .eq(AgentTask::getAgentRunId, run.getId())
                        .eq(AgentTask::getDeleted, 0)
                        .eq(AgentTask::getDueDate, dueDate)
                        .eq(StringUtils.hasText(status), AgentTask::getStatus, status)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByAsc(AgentTask::getId))
                .stream().map(task -> toReviewedTaskVO(task, run)).toList();
    }

    @Override
    public PageResult<AgentTaskVO> pageTasks(Long userId, AgentTaskQueryDTO query) {
        AgentTaskQueryDTO actual = query == null ? new AgentTaskQueryDTO() : query;
        long pageNo = pageNo(actual.getPageNo());
        long pageSize = pageSize(actual.getPageSize());
        Page<AgentTask> page = agentTaskMapper.selectPage(Page.of(pageNo, pageSize),
                taskQuery()
                        .eq(AgentTask::getUserId, userId)
                        .eq(actual.getTargetJobId() != null, AgentTask::getTargetJobId, actual.getTargetJobId())
                        .eq(actual.getDate() != null, AgentTask::getDueDate, actual.getDate())
                        .ge(actual.getStartDate() != null, AgentTask::getDueDate, actual.getStartDate())
                        .le(actual.getEndDate() != null, AgentTask::getDueDate, actual.getEndDate())
                        .eq(StringUtils.hasText(actual.getTaskType()), AgentTask::getTaskType, actual.getTaskType())
                        .eq(StringUtils.hasText(actual.getStatus()), AgentTask::getStatus, actual.getStatus())
                        .eq(StringUtils.hasText(actual.getPriority()), AgentTask::getPriority, actual.getPriority())
                        .orderByDesc(AgentTask::getDueDate)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByDesc(AgentTask::getId));
        return PageResult.of(page.getRecords().stream().map(this::toReviewedTaskVOWithRunTrace).toList(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO startTask(Long userId, Long taskId) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.DOING.name().equals(task.getStatus())) {
            return toReviewedTaskVOWithRunTrace(task);
        }
        transitionTask(userId, taskId, AgentTaskStatusEnum.DOING.name(),
                List.of(AgentTaskStatusEnum.TODO.name()),
                wrapper -> wrapper.set(AgentTask::getStartedAt, LocalDateTime.now()));
        return taskAfterTransition(userId, taskId, AgentTaskStatusEnum.DOING.name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO completeTask(Long userId, Long taskId, AgentTaskCompleteDTO dto) {
        return completeTaskInternal(userId, taskId, dto, false);
    }

    @Override
    public AgentCoachActionVO performCoachAction(Long userId, AgentCoachActionDTO dto) {
        AgentCoachActionRequest request = normalizeCoachActionRequest(dto);
        AgentTask task = requireUserTask(userId, request.taskId());
        if (ACTION_REVIEW_COMPLETED_TASK.equals(request.actionType())
                && !AgentTaskStatusEnum.DONE.name().equals(task.getStatus())) {
            emitAiCoachMetric(task, request, "ai_coach_action_failed", null,
                    Map.of("failureReason", "TASK_NOT_COMPLETED"));
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only completed Agent tasks can be reviewed");
        }
        emitAiCoachMetric(task, request, "ai_coach_action_started", null, Map.of());
        long startedAt = System.nanoTime();
        try {
            AgentCoachActionVO vo = ACTION_EXPLAIN_RECOMMENDATION.equals(request.actionType())
                    ? explainRecommendation(task, request)
                    : reviewCompletedTask(task, request);
            long measuredLatency = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            if (vo.getLatencyMs() == null) {
                vo.setLatencyMs(measuredLatency);
            }
            emitAiCoachMetric(task, request, "ai_coach_action_succeeded", vo,
                    Map.of("latencyMs", vo.getLatencyMs()));
            return vo;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            emitAiCoachMetric(task, request, "ai_coach_action_failed", null,
                    Map.of("failureReason", ex.getClass().getSimpleName()));
            throw ex;
        }
    }

    private AgentTaskVO completeTaskInternal(Long userId, Long taskId, AgentTaskCompleteDTO dto,
                                             boolean verifiedBusinessAction) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.DONE.name().equals(task.getStatus())) {
            return toReviewedTaskVOWithRunTrace(task);
        }
        if (!verifiedBusinessAction && isEvidenceBoundTaskType(task.getTaskType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "This Agent task must be completed by a verified business action");
        }
        LocalDateTime now = LocalDateTime.now();
        transitionTask(userId, taskId, AgentTaskStatusEnum.DONE.name(),
                List.of(AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name()),
                wrapper -> wrapper
                        .set(AgentTask::getStartedAt, task.getStartedAt() == null ? now : task.getStartedAt())
                        .set(AgentTask::getCompletedAt, now)
                        .set(AgentTask::getSkippedAt, null)
                        .set(AgentTask::getSkipReason, null));
        AgentTask latest = taskAfterTransitionEntity(userId, taskId, AgentTaskStatusEnum.DONE.name());
        AgentReview review = upsertTaskReview(latest, dto == null ? null : dto.getNote());
        List<ActivationHandoffVO> activationHandoffs = taskActivationHandoffs(latest);
        String requestId = activationHandoffs.isEmpty() ? null : activationHandoffs.get(0).getRequestId();
        emitTaskCompletedMetricAfterCommit(latest, requestId, activationHandoffs, verifiedBusinessAction);
        return applyTaskRunTrace(enrichTaskReview(AgentConvert.toTaskVO(latest), review), latest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO completeBusinessAction(AgentBusinessActionCompleteDTO dto) {
        if (dto == null || dto.getUserId() == null || !StringUtils.hasText(dto.getTaskType())
                || !StringUtils.hasText(dto.getRelatedBizType()) || dto.getRelatedBizId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "userId, taskType, relatedBizType and relatedBizId are required");
        }
        BusinessActionEvidence evidence = validateBusinessActionEvidence(dto);
        AgentTask task = findBusinessActionTask(dto, evidence.evidenceDate());
        if (task == null) {
            return null;
        }
        if (AgentTaskStatusEnum.DONE.name().equals(task.getStatus())) {
            return toReviewedTaskVOWithRunTrace(task);
        }
        return completeTaskInternal(dto.getUserId(), task.getId(), businessActionCompleteNote(dto), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO skipTask(Long userId, Long taskId, AgentTaskSkipDTO dto) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus())) {
            return toReviewedTaskVOWithRunTrace(task);
        }
        transitionTask(userId, taskId, AgentTaskStatusEnum.SKIPPED.name(),
                List.of(AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name()),
                wrapper -> wrapper
                        .set(AgentTask::getSkippedAt, LocalDateTime.now())
                        .set(AgentTask::getSkipReason, dto == null ? null : dto.getSkipReason()));
        AgentTask latest = taskAfterTransitionEntity(userId, taskId, AgentTaskStatusEnum.SKIPPED.name());
        AgentReview review = upsertTaskReview(latest, dto == null ? null : dto.getSkipReason());
        return applyTaskRunTrace(enrichTaskReview(AgentConvert.toTaskVO(latest), review), latest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO restoreTask(Long userId, Long taskId) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.TODO.name().equals(task.getStatus())) {
            return toReviewedTaskVOWithRunTrace(task);
        }
        transitionTask(userId, taskId, AgentTaskStatusEnum.TODO.name(),
                List.of(AgentTaskStatusEnum.SKIPPED.name()),
                wrapper -> wrapper
                        .set(AgentTask::getSkippedAt, null)
                        .set(AgentTask::getSkipReason, null));
        return taskAfterTransition(userId, taskId, AgentTaskStatusEnum.TODO.name());
    }

    @Override
    public AgentRunUserDetailVO getRunDetail(Long userId, Long runId) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null || !userId.equals(run.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.RUN_NOT_FOUND);
        }
        return toUserRunDetail(run);
    }

    @Override
    public AgentRunDetailVO adminGetRunDetail(Long runId) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.RUN_NOT_FOUND);
        }
        return toRunDetail(run);
    }

    @Override
    public PageResult<AgentRunDetailVO> adminPageRuns(AdminAgentRunQueryDTO query) {
        AdminAgentRunQueryDTO actual = query == null ? new AdminAgentRunQueryDTO() : query;
        long pageNo = pageNo(actual.getPageNo());
        long pageSize = pageSize(actual.getPageSize());
        Page<AgentRun> page = agentRunMapper.selectPage(Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<AgentRun>()
                        .eq(actual.getUserId() != null, AgentRun::getUserId, actual.getUserId())
                        .eq(actual.getTargetJobId() != null, AgentRun::getTargetJobId, actual.getTargetJobId())
                        .eq(actual.getDate() != null, AgentRun::getPlanDate, actual.getDate())
                        .ge(actual.getStartDate() != null, AgentRun::getPlanDate, actual.getStartDate())
                        .le(actual.getEndDate() != null, AgentRun::getPlanDate, actual.getEndDate())
                        .eq(StringUtils.hasText(actual.getAgentType()), AgentRun::getAgentType, actual.getAgentType())
                        .eq(StringUtils.hasText(actual.getTriggerType()), AgentRun::getTriggerType, actual.getTriggerType())
                        .eq(StringUtils.hasText(actual.getStatus()), AgentRun::getStatus, actual.getStatus())
                        .eq(StringUtils.hasText(actual.getTriggerType()), AgentRun::getTriggerType, actual.getTriggerType())
                        .eq(StringUtils.hasText(actual.getPromptType()), AgentRun::getPromptType, actual.getPromptType())
                        .orderByDesc(AgentRun::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toRunDetail).toList(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public PageResult<AgentTaskVO> adminPageTasks(AdminAgentTaskQueryDTO query) {
        AdminAgentTaskQueryDTO actual = query == null ? new AdminAgentTaskQueryDTO() : query;
        long pageNo = pageNo(actual.getPageNo());
        long pageSize = pageSize(actual.getPageSize());
        Page<AgentTask> page = agentTaskMapper.selectPage(Page.of(pageNo, pageSize),
                taskQuery()
                        .eq(actual.getUserId() != null, AgentTask::getUserId, actual.getUserId())
                        .eq(actual.getTargetJobId() != null, AgentTask::getTargetJobId, actual.getTargetJobId())
                        .eq(actual.getDate() != null, AgentTask::getDueDate, actual.getDate())
                        .ge(actual.getStartDate() != null, AgentTask::getDueDate, actual.getStartDate())
                        .le(actual.getEndDate() != null, AgentTask::getDueDate, actual.getEndDate())
                        .eq(StringUtils.hasText(actual.getTaskType()), AgentTask::getTaskType, actual.getTaskType())
                        .eq(StringUtils.hasText(actual.getStatus()), AgentTask::getStatus, actual.getStatus())
                        .eq(StringUtils.hasText(actual.getPriority()), AgentTask::getPriority, actual.getPriority())
                        .orderByDesc(AgentTask::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toReviewedTaskVOWithRunTrace).toList(), page.getTotal(), pageNo, pageSize);
    }

    private RunCreateResult createRun(Long userId, Long targetJobId, LocalDate planDate) {
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setAgentType(AGENT_TYPE);
        run.setTargetJobId(targetJobId);
        run.setPlanDate(planDate);
        run.setTriggerType(TRIGGER_MANUAL);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setExecutionToken(newExecutionToken());
        run.setStartedAt(LocalDateTime.now());
        try {
            agentRunMapper.insert(run);
            return new RunCreateResult(run, true);
        } catch (DuplicateKeyException ex) {
            AgentRun active = latestActiveRun(userId, targetJobId, planDate);
            if (active != null) {
                return new RunCreateResult(active, false);
            }
            throw ex;
        }
    }

    private DailyPlanGenerateDTO normalizeDailyPlanRequest(DailyPlanGenerateDTO dto) {
        DailyPlanGenerateDTO request = dto == null ? new DailyPlanGenerateDTO() : dto;
        if (request.getDate() == null) {
            request.setDate(LocalDate.now());
        }
        if (request.getTaskCount() == null) {
            request.setTaskCount(DEFAULT_TASK_COUNT);
        }
        if (request.getMaxTotalMinutes() == null) {
            request.setMaxTotalMinutes(DEFAULT_MAX_TOTAL_MINUTES);
        }
        if (!StringUtils.hasText(request.getRequestId()) && StringUtils.hasText(request.getIdempotencyKey())) {
            request.setRequestId(request.getIdempotencyKey().trim());
        } else if (!StringUtils.hasText(request.getRequestId()) && StringUtils.hasText(request.getExecutionToken())) {
            request.setRequestId(request.getExecutionToken().trim());
        } else if (StringUtils.hasText(request.getRequestId())) {
            request.setRequestId(request.getRequestId().trim());
        }
        if (!StringUtils.hasText(request.getIdempotencyKey()) && StringUtils.hasText(request.getRequestId())) {
            request.setIdempotencyKey(request.getRequestId());
        }
        return request;
    }

    private boolean prepareRunForExecution(Long userId, AgentRun run, String executionToken) {
        if (run == null || run.getId() == null || !AgentRunStatusEnum.RUNNING.name().equals(run.getStatus())) {
            return false;
        }
        if (!executionTokenMatches(run, executionToken)) {
            return false;
        }
        String expectedToken = executionToken;
        String claimToken = newExecutionToken();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AgentRun> update = applyRunExecutionTokenScope(new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name()), expectedToken)
                .set(AgentRun::getExecutionToken, claimToken)
                .set(AgentRun::getStartedAt, now)
                .set(AgentRun::getFinishedAt, null)
                .set(AgentRun::getDurationMs, null)
                .set(AgentRun::getErrorCode, null)
                .set(AgentRun::getErrorMessage, null)
                .set(AgentRun::getUpdatedAt, now);
        int rows = agentRunMapper.update(null, update);
        if (rows <= 0) {
            return false;
        }
        run.setExecutionToken(claimToken);
        run.setStartedAt(now);
        run.setFinishedAt(null);
        run.setDurationMs(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        return true;
    }

    private boolean updateRunningRunFields(AgentRun run, Consumer<LambdaUpdateWrapper<AgentRun>> fieldUpdater) {
        if (run == null || run.getId() == null || fieldUpdater == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AgentRun> update = applyRunExecutionTokenScope(new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, run.getUserId())
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name()), run.getExecutionToken());
        fieldUpdater.accept(update);
        update.set(AgentRun::getUpdatedAt, now);
        return agentRunMapper.update(null, update) > 0;
    }

    private LambdaUpdateWrapper<AgentRun> applyRunExecutionTokenScope(LambdaUpdateWrapper<AgentRun> update,
                                                                      String executionToken) {
        if (StringUtils.hasText(executionToken)) {
            return update.eq(AgentRun::getExecutionToken, executionToken);
        }
        return update.isNull(AgentRun::getExecutionToken);
    }

    private boolean executionTokenMatches(AgentRun run, String executionToken) {
        if (run == null) {
            return false;
        }
        if (StringUtils.hasText(run.getExecutionToken())) {
            return run.getExecutionToken().equals(executionToken);
        }
        return !StringUtils.hasText(executionToken);
    }

    private String newExecutionToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private RouteResult callAi(Long userId, AgentRun run, PromptRenderResult prompt) {
        AiCallContext ctx = new AiCallContext();
        ctx.setScene(AgentPromptBuilderImpl.PROMPT_TYPE);
        ctx.setPrompt(prompt.getRenderedPrompt());
        ctx.setUserId(userId);
        ctx.setBusinessId(String.valueOf(run.getId()));
        ctx.setPromptTemplateId(prompt.getPromptTemplateId());
        ctx.setPromptTemplateVersionId(prompt.getPromptTemplateVersionId());
        ctx.setPromptVersion(prompt.getPromptVersion());
        ctx.setInputVariablesJson(prompt.getInputVariablesJson());
        ctx.setModelParamsJson(prompt.getModelParamsJson());
        ctx.setPromptHash(prompt.getPromptHash());
        ctx.setResponseFormat("JSON");
        ctx.setRequestBody(run.getInputSnapshotJson());
        return aiCallLogService.callAndLog(ctx);
    }

    private void clearRunTasks(AgentRun run) {
        agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getAgentRunId, run.getId())
                .eq(AgentTask::getUserId, run.getUserId())
                .eq(AgentTask::getDeleted, 0)
                .set(AgentTask::getDeleted, 1)
                .set(AgentTask::getUpdatedAt, LocalDateTime.now()));
    }

    private void saveTasks(Long userId, AgentRun run, DailyPlanResult planResult, List<CandidateTask> candidates) {
        List<PlanTask> tasks = planResult == null || planResult.getTasks() == null
                ? Collections.emptyList() : planResult.getTasks();
        int order = 0;
        for (PlanTask item : tasks) {
            CandidateTask candidate = matchCandidate(item.getCandidateId(), candidates);
            AgentTask task = new AgentTask();
            task.setUserId(userId);
            task.setAgentRunId(run.getId());
            task.setTargetJobId(run.getTargetJobId());
            task.setCandidateId(item.getCandidateId());
            task.setTaskType(firstText(candidate == null ? null : candidate.getType(), item.getType()));
            task.setTitle(item.getTitle());
            task.setDescription(item.getDescription());
            task.setReason(item.getReason());
            task.setPriority(item.getPriority());
            task.setEstimatedMinutes(item.getEstimatedMinutes());
            task.setRelatedSkillCode(firstText(candidate == null ? null : candidate.getRelatedSkillCode(), item.getRelatedSkillCode()));
            task.setRelatedSkillName(firstText(candidate == null ? null : candidate.getRelatedSkillName(), item.getRelatedSkillName()));
            task.setRelatedBizType(firstText(candidate == null ? null : candidate.getRelatedBizType(), item.getRelatedBizType()));
            task.setRelatedBizId(candidate == null ? item.getRelatedBizId() : candidate.getRelatedBizId());
            task.setActionUrl(firstText(candidate == null ? null : candidate.getActionUrl(), item.getActionUrl()));
            task.setStatus(AgentTaskStatusEnum.TODO.name());
            task.setDueDate(run.getPlanDate());
            task.setSortOrder(++order);
            agentTaskMapper.insert(task);
        }
    }

    private DailyPlanVO withAsyncReceipt(DailyPlanVO vo, MqDispatchReceipt receipt) {
        if (vo == null || receipt == null) {
            return vo;
        }
        vo.setAsyncMessageId(receipt.getMessageId());
        vo.setAsyncTraceId(receipt.getTraceId());
        vo.setAsyncBizType(receipt.getBizType());
        vo.setAsyncBizId(receipt.getBizId());
        return vo;
    }

    private boolean deferDailyPlanDispatchAfterCommit(Long runId, Long userId, DailyPlanGenerateDTO request) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        DailyPlanGenerateDTO dispatchRequest = copyDailyPlanGenerateRequest(request);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    MqDispatchReceipt receipt = agentMqDispatcher.dispatchDailyPlanWithReceipt(runId, userId, dispatchRequest);
                    if (receipt != null) {
                        return;
                    }
                    fallbackDailyPlanAfterCommit(runId, userId, dispatchRequest, null);
                } catch (RuntimeException ex) {
                    fallbackDailyPlanAfterCommit(runId, userId, dispatchRequest, ex);
                }
            }
        });
        return true;
    }

    private void fallbackDailyPlanAfterCommit(Long runId,
                                              Long userId,
                                              DailyPlanGenerateDTO dispatchRequest,
                                              RuntimeException dispatchError) {
        try {
            DailyPlanVO fallback = executeDailyPlan(userId, runId, dispatchRequest);
            if (dispatchError == null) {
                log.warn("Agent daily plan dispatch returned no receipt after commit, fallback to local execution runId={} userId={} status={}",
                        runId, userId, fallback == null ? null : fallback.getStatus());
            } else {
                log.warn("Agent daily plan dispatch failed after commit, fallback to local execution runId={} userId={} status={}",
                        runId, userId, fallback == null ? null : fallback.getStatus(), dispatchError);
            }
        } catch (RuntimeException fallbackEx) {
            if (dispatchError == null) {
                log.error("Agent daily plan local fallback after dispatch failure failed runId={} userId={}",
                        runId, userId, fallbackEx);
            } else {
                fallbackEx.addSuppressed(dispatchError);
                log.error("Agent daily plan dispatch and local fallback both failed after commit runId={} userId={}",
                        runId, userId, fallbackEx);
            }
        }
    }

    private DailyPlanGenerateDTO copyDailyPlanGenerateRequest(DailyPlanGenerateDTO source) {
        DailyPlanGenerateDTO target = new DailyPlanGenerateDTO();
        if (source == null) {
            return target;
        }
        target.setUserId(source.getUserId());
        target.setRequestId(source.getRequestId());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setExecutionToken(source.getExecutionToken());
        target.setTargetJobId(source.getTargetJobId());
        target.setDate(source.getDate());
        target.setMaxTotalMinutes(source.getMaxTotalMinutes());
        target.setTaskCount(source.getTaskCount());
        target.setForceRegenerate(source.getForceRegenerate());
        return target;
    }

    private boolean markSuccess(AgentRun run, DailyPlanResult planResult, RouteResult routeResult, long durationMs) {
        LocalDateTime finishedAt = LocalDateTime.now();
        String outputJson = maskAgentRunDiagnosticText(toJson(planResult));
        String rawOutputText = maskAgentRunDiagnosticText(routeResult.getContent());
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        run.setOutputJson(outputJson);
        run.setRawOutputText(rawOutputText);
        run.setModelName(routeResult.getModel());
        run.setTraceId(routeResult.getRouteTrace());
        run.setAiCallLogId(routeResult.getAiCallLogId());
        run.setResultSource(routeResult.getResultSource());
        run.setTokenInput(routeResult.getPromptTokens());
        run.setTokenOutput(routeResult.getCompletionTokens());
        run.setDurationMs(durationMs);
        run.setFinishedAt(finishedAt);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        int rows = agentRunMapper.update(null, applyRunExecutionTokenScope(new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, run.getUserId())
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name()), run.getExecutionToken())
                .set(AgentRun::getStatus, AgentRunStatusEnum.SUCCESS.name())
                .set(AgentRun::getOutputJson, outputJson)
                .set(AgentRun::getRawOutputText, rawOutputText)
                .set(AgentRun::getModelName, routeResult.getModel())
                .set(AgentRun::getTraceId, routeResult.getRouteTrace())
                .set(AgentRun::getAiCallLogId, routeResult.getAiCallLogId())
                .set(AgentRun::getResultSource, routeResult.getResultSource())
                .set(AgentRun::getTokenInput, routeResult.getPromptTokens())
                .set(AgentRun::getTokenOutput, routeResult.getCompletionTokens())
                .set(AgentRun::getDurationMs, durationMs)
                .set(AgentRun::getFinishedAt, finishedAt)
                .set(AgentRun::getErrorCode, null)
                .set(AgentRun::getErrorMessage, null)
                .set(AgentRun::getUpdatedAt, finishedAt));
        return rows > 0;
    }

    private String maskAgentRunDiagnosticText(String text) {
        return AiPiiMasker.maskResumeJson(text);
    }

    private void markFailed(AgentRun run, String errorCode, String errorMessage, long durationMs) {
        if (run == null || run.getId() == null) {
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        run.setStatus(AgentRunStatusEnum.FAILED.name());
        run.setErrorCode(truncate(errorCode, 128));
        run.setErrorMessage(truncate(errorMessage, 1024));
        run.setDurationMs(durationMs);
        run.setFinishedAt(finishedAt);
        agentRunMapper.update(null, applyRunExecutionTokenScope(new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, run.getUserId())
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name()), run.getExecutionToken())
                .set(AgentRun::getStatus, AgentRunStatusEnum.FAILED.name())
                .set(AgentRun::getErrorCode, truncate(errorCode, 128))
                .set(AgentRun::getErrorMessage, truncate(errorMessage, 1024))
                .set(AgentRun::getDurationMs, durationMs)
                .set(AgentRun::getFinishedAt, finishedAt)
                .set(AgentRun::getUpdatedAt, finishedAt));
    }

    private void markCanceled(AgentRun run, String errorCode, String errorMessage, long durationMs) {
        if (run == null || run.getId() == null) {
            return;
        }
        run.setStatus(AgentRunStatusEnum.CANCELED.name());
        run.setErrorCode(truncate(errorCode, 128));
        run.setErrorMessage(truncate(errorMessage, 1024));
        run.setDurationMs(durationMs);
        run.setFinishedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
    }

    private void cancelActiveRuns(Long userId, Long targetJobId, LocalDate planDate) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .select(AgentRun::getId)
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getAgentType, AGENT_TYPE)
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getPlanDate, planDate);
        List<AgentRun> canceledRuns = agentRunMapper.selectList(applyRunTargetScope(query, targetJobId)
                .in(AgentRun::getStatus, ACTIVE_PLAN_STATUSES));
        List<Long> canceledRunIds = canceledRuns == null
                ? List.of()
                : canceledRuns.stream()
                        .map(AgentRun::getId)
                        .filter(id -> id != null)
                        .toList();
        expireOpenTasksForRuns(userId, canceledRunIds);

        LambdaUpdateWrapper<AgentRun> update = new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getAgentType, AGENT_TYPE)
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getPlanDate, planDate)
                ;
        applyRunTargetScope(update, targetJobId)
                .in(AgentRun::getStatus, ACTIVE_PLAN_STATUSES)
                .set(AgentRun::getStatus, AgentRunStatusEnum.CANCELED.name())
                .set(AgentRun::getErrorCode, RUN_FORCE_REGENERATED)
                .set(AgentRun::getErrorMessage, "用户已强制重新生成今日计划。")
                .set(AgentRun::getFinishedAt, LocalDateTime.now())
                .set(AgentRun::getUpdatedAt, LocalDateTime.now());
        agentRunMapper.update(null, update);
    }

    private void expireOpenTasksForRuns(Long userId, List<Long> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDeleted, 0)
                .in(AgentTask::getAgentRunId, runIds)
                .in(AgentTask::getStatus, AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name())
                .set(AgentTask::getStatus, AgentTaskStatusEnum.EXPIRED.name())
                .set(AgentTask::getDeleted, 1)
                .set(AgentTask::getUpdatedAt, now));
    }

    private DailyPlanVO toDailyPlan(AgentRun run) {
        DailyPlanVO vo = new DailyPlanVO();
        vo.setRunId(run.getId());
        AgentConvert.applyRunTrace(vo, run);
        vo.setTargetJobId(run.getTargetJobId());
        vo.setDate(run.getPlanDate());
        vo.setPlanDate(run.getPlanDate());
        vo.setCreatedAt(run.getCreatedAt());
        vo.setStatus(run.getStatus());
        vo.setErrorCode(run.getErrorCode());
        vo.setErrorMessage(friendlyAgentErrorMessage(run.getErrorCode(), run.getErrorMessage()));
        applyFailureDiagnosis(vo, run.getErrorCode(), run.getErrorMessage());
        vo.setDurationMs(run.getDurationMs());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setRequestId(readRequestId(run.getInputSnapshotJson()));
        DailyPlanResult result = readPlanResult(run.getOutputJson());
        if (result != null) {
            vo.setSummary(result.getSummary());
            List<FocusSkill> focusSkills = result.getFocusSkills() == null ? Collections.emptyList() : result.getFocusSkills();
            vo.setFocusSkills(focusSkills.stream().map(AgentConvert::toSkillTagVO).toList());
        }
        vo.setTasks(agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getAgentRunId, run.getId())
                        .eq(AgentTask::getUserId, run.getUserId())
                        .eq(AgentTask::getDeleted, 0)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByAsc(AgentTask::getId))
                .stream().map(task -> toReviewedTaskVO(task, run)).toList());
        vo.setActivationHandoffs(planActivationHandoffs(run));
        return vo;
    }

    private AgentRunDetailVO toRunDetail(AgentRun run) {
        AgentRunDetailVO vo = AgentConvert.toRunDetailVO(run);
        vo.setTasks(agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getAgentRunId, run.getId())
                        .eq(AgentTask::getUserId, run.getUserId())
                        .eq(AgentTask::getDeleted, 0)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByAsc(AgentTask::getId))
                .stream().map(task -> toReviewedTaskVO(task, run)).toList());
        return vo;
    }

    private AgentRunUserDetailVO toUserRunDetail(AgentRun run) {
        AgentRunUserDetailVO vo = new AgentRunUserDetailVO();
        vo.setId(run.getId());
        vo.setAgentType(run.getAgentType());
        vo.setTargetJobId(run.getTargetJobId());
        vo.setPlanDate(run.getPlanDate());
        vo.setTriggerType(run.getTriggerType());
        vo.setStatus(run.getStatus());
        vo.setPromptVersionId(run.getPromptVersionId());
        vo.setTraceId(run.getTraceId());
        vo.setAiCallLogId(run.getAiCallLogId());
        vo.setResultSource(run.getResultSource());
        vo.setResultSourceLabel(AgentConvert.aiResultSourceLabel(run.getResultSource()));
        vo.setFallback(AgentConvert.isFallbackAiResultSource(run.getResultSource()));
        vo.setMock(AgentConvert.isMockAiResultSource(run.getResultSource()));
        vo.setDurationMs(run.getDurationMs());
        vo.setErrorCode(run.getErrorCode());
        vo.setErrorMessage(friendlyAgentErrorMessage(run.getErrorCode(), run.getErrorMessage()));
        AgentFailureDiagnosis diagnosis = diagnoseAgentFailure(run.getErrorCode(), run.getErrorMessage());
        vo.setFailureAction(diagnosis.action());
        vo.setFailureActionLabel(diagnosis.actionLabel());
        vo.setFailureSuggestion(diagnosis.suggestion());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setCreatedAt(run.getCreatedAt());

        DailyPlanResult result = readPlanResult(run.getOutputJson());
        if (result != null) {
            vo.setSummary(result.getSummary());
            List<FocusSkill> focusSkills = result.getFocusSkills() == null ? Collections.emptyList() : result.getFocusSkills();
            vo.setFocusSkills(focusSkills.stream().map(AgentConvert::toSkillTagVO).toList());
        }
        vo.setTasks(agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getAgentRunId, run.getId())
                        .eq(AgentTask::getUserId, run.getUserId())
                        .eq(AgentTask::getDeleted, 0)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByAsc(AgentTask::getId))
                .stream().map(task -> toReviewedTaskVO(task, run)).toList());
        return vo;
    }

    private AgentRun latestVisibleRun(Long userId, Long targetJobId, LocalDate planDate) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getAgentType, AGENT_TYPE)
                .eq(AgentRun::getPlanDate, planDate);
        return agentRunMapper.selectOne(applyRunTargetScope(query, targetJobId)
                .in(AgentRun::getStatus, VISIBLE_PLAN_STATUSES)
                .orderByDesc(AgentRun::getCreatedAt)
                .last("limit 1"));
    }

    private DailyPlanVO currentDailyPlan(Long userId, Long runId) {
        AgentRun latest = agentRunMapper.selectById(runId);
        if (latest == null || !userId.equals(latest.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.RUN_NOT_FOUND);
        }
        return toDailyPlan(latest);
    }

    private boolean isRunStillRunning(Long userId, Long runId, String executionToken) {
        AgentRun latest = agentRunMapper.selectById(runId);
        return latest != null
                && userId.equals(latest.getUserId())
                && AgentRunStatusEnum.RUNNING.name().equals(latest.getStatus())
                && executionTokenMatches(latest, executionToken);
    }

    private AgentRun latestActiveRun(Long userId, Long targetJobId, LocalDate planDate) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getAgentType, AGENT_TYPE)
                .eq(AgentRun::getPlanDate, planDate);
        return agentRunMapper.selectOne(applyRunTargetScope(query, targetJobId)
                .in(AgentRun::getStatus, ACTIVE_PLAN_STATUSES)
                .orderByDesc(AgentRun::getCreatedAt)
                .last("limit 1"));
    }

    private Long resolveTargetJobIdForScope(Long userId, Long requestedTargetJobId, LocalDate planDate) {
        if (requestedTargetJobId != null) {
            return requestedTargetJobId;
        }
        try {
            JobCoachAgentContext context = agentContextBuilder.build(userId, null, planDate);
            return context == null ? null : context.getTargetJobId();
        } catch (BusinessException ex) {
            if (AgentErrorCode.TARGET_JOB_REQUIRED.equals(ex.getMessage())) {
                return null;
            }
            throw ex;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LambdaQueryWrapper<AgentRun> applyRunTargetScope(LambdaQueryWrapper<AgentRun> query, Long targetJobId) {
        if (targetJobId == null) {
            return query.isNull(AgentRun::getTargetJobId);
        }
        return query.eq(AgentRun::getTargetJobId, targetJobId);
    }

    private LambdaUpdateWrapper<AgentRun> applyRunTargetScope(LambdaUpdateWrapper<AgentRun> update, Long targetJobId) {
        if (targetJobId == null) {
            return update.isNull(AgentRun::getTargetJobId);
        }
        return update.eq(AgentRun::getTargetJobId, targetJobId);
    }

    private DailyPlanVO recoverableMissingTargetFailure(AgentRun run, Long userId, Long requestedTargetJobId, LocalDate planDate) {
        if (run == null
                || !AgentRunStatusEnum.FAILED.name().equals(run.getStatus())
                || !AgentErrorCode.TARGET_JOB_REQUIRED.equals(run.getErrorCode())) {
            return null;
        }
        try {
            JobCoachAgentContext context = agentContextBuilder.build(userId, requestedTargetJobId, planDate);
            if (context == null || context.getTargetJobId() == null) {
                return null;
            }
            DailyPlanVO vo = emptyDailyPlan(context.getTargetJobId(), planDate,
                    "目标岗位资料已补齐。之前的失败记录已保留为历史，请重新生成今日计划。");
            vo.setErrorCode(run.getErrorCode());
            vo.setErrorMessage("目标岗位资料已补齐，请重新生成今日计划。");
            applyFailureDiagnosis(vo, run.getErrorCode(), run.getErrorMessage());
            return vo;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private DailyPlanVO emptyDailyPlan(Long targetJobId, LocalDate planDate, String message) {
        DailyPlanVO vo = new DailyPlanVO();
        vo.setTargetJobId(targetJobId);
        vo.setDate(planDate);
        vo.setPlanDate(planDate);
        vo.setEmpty(true);
        vo.setStatus(AgentRunStatusEnum.PENDING.name());
        vo.setEmptyMessage(message);
        return vo;
    }

    private void applyFailureDiagnosis(DailyPlanVO vo, String errorCode, String rawMessage) {
        AgentFailureDiagnosis diagnosis = diagnoseAgentFailure(errorCode, rawMessage);
        vo.setFailureAction(diagnosis.action());
        vo.setFailureActionLabel(diagnosis.actionLabel());
        vo.setFailureSuggestion(diagnosis.suggestion());
    }

    private boolean isStaleRunning(AgentRun run) {
        if (run == null || !AgentRunStatusEnum.RUNNING.name().equals(run.getStatus()) || run.getStartedAt() == null) {
            return false;
        }
        return Duration.between(run.getStartedAt(), LocalDateTime.now()).toMinutes() >= Math.max(dailyPlanStaleMinutes, 1L);
    }

    private long durationFromStart(AgentRun run) {
        if (run == null || run.getStartedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(run.getStartedAt(), LocalDateTime.now()).toMillis());
    }

    private record RunCreateResult(AgentRun run, boolean created) {
    }

    private record BusinessActionEvidence(LocalDate evidenceDate) {
    }

    private AgentTask requireUserTask(Long userId, Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId()) || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private AgentTask findBusinessActionTask(AgentBusinessActionCompleteDTO dto, LocalDate evidenceDate) {
        String taskType = normalizeCode(dto.getTaskType());
        String relatedBizType = normalizeCode(dto.getRelatedBizType());
        List<AgentTask> tasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, dto.getUserId())
                .eq(AgentTask::getDeleted, 0)
                .eq(AgentTask::getTaskType, taskType)
                .eq(AgentTask::getRelatedBizType, relatedBizType)
                .eq(AgentTask::getRelatedBizId, dto.getRelatedBizId())
                .eq(evidenceDate != null, AgentTask::getDueDate, evidenceDate)
                .in(AgentTask::getStatus, AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name(),
                        AgentTaskStatusEnum.DONE.name())
                .orderByDesc(AgentTask::getDueDate)
                .orderByAsc(AgentTask::getSortOrder)
                .orderByDesc(AgentTask::getId));
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        if (evidenceDate != null) {
            tasks = tasks.stream()
                    .filter(task -> evidenceDate.equals(task.getDueDate()))
                    .toList();
            if (tasks.isEmpty()) {
                return null;
            }
        }
        return tasks.stream()
                .filter(task -> AgentTaskStatusEnum.TODO.name().equals(task.getStatus())
                        || AgentTaskStatusEnum.DOING.name().equals(task.getStatus()))
                .findFirst()
                .orElse(tasks.get(0));
    }

    private BusinessActionEvidence validateBusinessActionEvidence(AgentBusinessActionCompleteDTO dto) {
        String taskType = normalizeCode(dto.getTaskType());
        if (TASK_TYPE_QUESTION_PRACTICE.equals(taskType)) {
            return validateQuestionPracticeEvidence(dto);
        }
        if (TASK_TYPE_INTERVIEW.equals(taskType)) {
            return validateInterviewReportEvidence(dto);
        }
        if (TASK_TYPE_APPLICATION_FOLLOW_UP.equals(taskType)) {
            return validateApplicationFollowUpEvidence(dto);
        }
        if (TASK_TYPE_RESUME_OPTIMIZE.equals(taskType)) {
            return validateResumeOptimizeEvidence(dto);
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported business action task type");
    }

    private boolean isEvidenceBoundTaskType(String taskType) {
        String normalized = normalizeCode(taskType);
        return TASK_TYPE_QUESTION_PRACTICE.equals(normalized)
                || TASK_TYPE_INTERVIEW.equals(normalized)
                || TASK_TYPE_APPLICATION_FOLLOW_UP.equals(normalized)
                || TASK_TYPE_RESUME_OPTIMIZE.equals(normalized);
    }

    private BusinessActionEvidence validateQuestionPracticeEvidence(AgentBusinessActionCompleteDTO dto) {
        String evidenceBizType = normalizeCode(dto.getEvidenceBizType());
        if (!EVIDENCE_TYPE_PRACTICE_RECORD.equals(evidenceBizType) || dto.getEvidenceBizId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "QUESTION_PRACTICE completion requires PRACTICE_RECORD evidence");
        }
        PracticeRecordEvidenceVO evidence = FeignResultUtils.unwrap(
                questionPracticeEvidenceFeignClient.getPracticeRecordEvidence(dto.getUserId(), dto.getEvidenceBizId()));
        if (evidence == null || !dto.getUserId().equals(evidence.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "practice record evidence does not belong to user");
        }
        String relatedBizType = normalizeCode(dto.getRelatedBizType());
        if (BIZ_TYPE_TARGET_JOB.equals(relatedBizType)
                && (!BIZ_TYPE_TARGET_JOB.equals(normalizeCode(evidence.getSourceType()))
                || !dto.getRelatedBizId().equals(evidence.getSourceId()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "practice record evidence does not match target job");
        }
        return businessActionEvidence(evidence.getCreatedAt());
    }

    private BusinessActionEvidence validateInterviewReportEvidence(AgentBusinessActionCompleteDTO dto) {
        String evidenceBizType = normalizeCode(dto.getEvidenceBizType());
        if (!EVIDENCE_TYPE_INTERVIEW_REPORT.equals(evidenceBizType) || dto.getEvidenceBizId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "INTERVIEW completion requires INTERVIEW_REPORT evidence");
        }
        InterviewReportEvidenceVO evidence = FeignResultUtils.unwrap(
                interviewReportEvidenceFeignClient.getReportEvidence(dto.getUserId(), dto.getEvidenceBizId()));
        if (evidence == null || !dto.getUserId().equals(evidence.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview report evidence does not belong to user");
        }
        if (!REPORT_STATUS_GENERATED.equals(normalizeCode(evidence.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview report evidence is not generated");
        }
        String relatedBizType = normalizeCode(dto.getRelatedBizType());
        if (!BIZ_TYPE_TARGET_JOB.equals(relatedBizType)
                || !dto.getRelatedBizId().equals(evidence.getTargetJobId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "interview report evidence does not match target job");
        }
        return businessActionEvidence(firstTimestamp(evidence.getGeneratedAt(), evidence.getCreatedAt()));
    }

    private BusinessActionEvidence validateApplicationFollowUpEvidence(AgentBusinessActionCompleteDTO dto) {
        String evidenceBizType = normalizeCode(dto.getEvidenceBizType());
        if (!EVIDENCE_TYPE_JOB_APPLICATION_EVENT.equals(evidenceBizType) || dto.getEvidenceBizId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "APPLICATION_FOLLOW_UP completion requires JOB_APPLICATION_EVENT evidence");
        }
        JobApplicationEventEvidenceVO evidence = FeignResultUtils.unwrap(
                resumeJobApplicationEvidenceFeignClient.getApplicationEventEvidence(
                        dto.getUserId(), dto.getEvidenceBizId()));
        if (evidence == null || !dto.getUserId().equals(evidence.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "job application event evidence does not belong to user");
        }
        String relatedBizType = normalizeCode(dto.getRelatedBizType());
        if (!BIZ_TYPE_JOB_APPLICATION.equals(relatedBizType)
                || !dto.getRelatedBizId().equals(evidence.getApplicationId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "job application event evidence does not match application");
        }
        if (!isApplicationFollowUpCompletionEvent(evidence.getEventType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "job application event evidence is not a follow-up completion event");
        }
        return businessActionEvidence(evidence.getEventTime());
    }

    private BusinessActionEvidence validateResumeOptimizeEvidence(AgentBusinessActionCompleteDTO dto) {
        if (BIZ_TYPE_PROJECT_EVIDENCE.equals(normalizeCode(dto.getRelatedBizType()))) {
            return validateProjectEvidenceCompletion(dto);
        }
        String evidenceBizType = normalizeCode(dto.getEvidenceBizType());
        if (!EVIDENCE_TYPE_RESUME_OPTIMIZE_RECORD.equals(evidenceBizType) || dto.getEvidenceBizId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "RESUME_OPTIMIZE completion requires RESUME_OPTIMIZE_RECORD evidence");
        }
        ResumeOptimizeRecordEvidenceVO evidence = FeignResultUtils.unwrap(
                resumeOptimizeRecordEvidenceFeignClient.getOptimizeRecordEvidence(
                        dto.getUserId(), dto.getEvidenceBizId()));
        if (evidence == null || !dto.getUserId().equals(evidence.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume optimize evidence does not belong to user");
        }
        String relatedBizType = normalizeCode(dto.getRelatedBizType());
        if (!BIZ_TYPE_TARGET_JOB.equals(relatedBizType)
                || dto.getRelatedBizId() == null
                || !dto.getRelatedBizId().equals(evidence.getTargetJobId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "resume optimize evidence does not match target job");
        }
        if (!RESUME_OPTIMIZE_STATUS_SUCCESS.equals(normalizeCode(evidence.getStatus()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume optimize evidence is not successful");
        }
        return businessActionEvidence(firstTimestamp(evidence.getOptimizedAt(), evidence.getCreatedAt()));
    }

    private BusinessActionEvidence validateProjectEvidenceCompletion(AgentBusinessActionCompleteDTO dto) {
        String evidenceBizType = normalizeCode(dto.getEvidenceBizType());
        if (!EVIDENCE_TYPE_PROJECT_EVIDENCE.equals(evidenceBizType)
                || dto.getEvidenceBizId() == null
                || dto.getRelatedBizId() == null
                || !dto.getRelatedBizId().equals(dto.getEvidenceBizId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "PROJECT_EVIDENCE completion requires matching PROJECT_EVIDENCE evidence");
        }
        return new BusinessActionEvidence(null);
    }

    private BusinessActionEvidence businessActionEvidence(LocalDateTime occurredAt) {
        return new BusinessActionEvidence(occurredAt == null ? null : occurredAt.toLocalDate());
    }

    private LocalDateTime firstTimestamp(LocalDateTime... values) {
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean isApplicationFollowUpCompletionEvent(String eventType) {
        String normalized = normalizeCode(eventType);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return "APPLIED".equals(normalized)
                || "SUBMITTED".equals(normalized)
                || "APPLICATION_SUBMITTED".equals(normalized)
                || "INTERVIEW".equals(normalized)
                || normalized.startsWith("INTERVIEW_")
                || "OFFER".equals(normalized)
                || "OFFER_RECEIVED".equals(normalized)
                || "REJECTION".equals(normalized)
                || "REJECTED".equals(normalized)
                || "CLOSED".equals(normalized)
                || "FOLLOW_UP".equals(normalized)
                || normalized.startsWith("FOLLOW_UP_");
    }

    private AgentTaskCompleteDTO businessActionCompleteNote(AgentBusinessActionCompleteDTO dto) {
        AgentTaskCompleteDTO completeDTO = new AgentTaskCompleteDTO();
        completeDTO.setNote(firstText(dto.getNote(), businessActionDefaultNote(dto)));
        return completeDTO;
    }

    private String businessActionDefaultNote(AgentBusinessActionCompleteDTO dto) {
        String evidence = StringUtils.hasText(dto.getEvidenceBizType()) && dto.getEvidenceBizId() != null
                ? dto.getEvidenceBizType() + "#" + dto.getEvidenceBizId()
                : "business action";
        return "Completed through " + evidence + ".";
    }

    private void transitionTask(Long userId, Long taskId, String nextStatus, Collection<String> allowedStatuses,
                                Consumer<LambdaUpdateWrapper<AgentTask>> customizer) {
        LambdaUpdateWrapper<AgentTask> wrapper = new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDeleted, 0)
                .in(AgentTask::getStatus, allowedStatuses)
                .set(AgentTask::getStatus, nextStatus)
                .set(AgentTask::getUpdatedAt, LocalDateTime.now());
        customizer.accept(wrapper);
        int rows = agentTaskMapper.update(null, wrapper);
        if (rows <= 0) {
            AgentTask latest = requireUserTask(userId, taskId);
            if (!nextStatus.equals(latest.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TASK_STATUS_INVALID);
            }
        }
    }

    private AgentTaskVO taskAfterTransition(Long userId, Long taskId, String expectedStatus) {
        return toReviewedTaskVOWithRunTrace(taskAfterTransitionEntity(userId, taskId, expectedStatus));
    }

    private AgentTask taskAfterTransitionEntity(Long userId, Long taskId, String expectedStatus) {
        AgentTask latest = requireUserTask(userId, taskId);
        if (!expectedStatus.equals(latest.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TASK_STATUS_INVALID);
        }
        return latest;
    }

    private AgentTaskVO toReviewedTaskVO(AgentTask task) {
        return enrichTaskReview(AgentConvert.toTaskVO(task), task);
    }

    private AgentTaskVO toReviewedTaskVO(AgentTask task, AgentRun run) {
        return AgentConvert.applyRunTrace(toReviewedTaskVO(task), run);
    }

    private AgentTaskVO toReviewedTaskVOWithRunTrace(AgentTask task) {
        return applyTaskRunTrace(toReviewedTaskVO(task), task);
    }

    private AgentTaskVO applyTaskRunTrace(AgentTaskVO vo, AgentTask task) {
        if (vo == null || task == null || task.getAgentRunId() == null) {
            return vo;
        }
        return AgentConvert.applyRunTrace(vo, agentRunMapper.selectById(task.getAgentRunId()));
    }

    private AgentTaskVO enrichTaskReview(AgentTaskVO vo, AgentTask task) {
        if (task == null
                || (!AgentTaskStatusEnum.DONE.name().equals(task.getStatus())
                && !AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus()))) {
            return vo;
        }
        return enrichTaskReview(vo, findTaskReview(task));
    }

    private AgentTaskVO enrichTaskReview(AgentTaskVO vo, AgentReview review) {
        if (vo == null || review == null) {
            return vo;
        }
        vo.setReviewId(review.getId());
        vo.setReviewSummary(review.getSummary());
        vo.setReviewNextActions(readStringList(review.getNextActionsJson()));
        String source = readReviewSource(review.getReviewJson());
        vo.setReviewSource(source);
        vo.setReviewSourceLabel(reviewSourceLabel(source));
        vo.setReviewNote(readReviewNote(review.getReviewJson()));
        vo.setActivationHandoffs(readActivationHandoffs(review.getReviewJson()));
        return vo;
    }

    private AgentReview upsertTaskReview(AgentTask task, String note) {
        AgentReview review = findTaskReview(task);
        if (review == null) {
            review = new AgentReview();
            review.setUserId(task.getUserId());
            review.setTargetJobId(task.getTargetJobId());
            review.setReviewDate(task.getDueDate() == null ? LocalDate.now() : task.getDueDate());
            review.setAgentRunId(task.getAgentRunId());
            applyTaskReviewPayload(review, task, note, REVIEW_SOURCE_RULE, null, null);
            tryEnhanceReviewWithAi(review, task, note);
            agentReviewMapper.insert(review);
            return review;
        }
        applyTaskReviewPayload(review, task, note, REVIEW_SOURCE_RULE, null, null);
        tryEnhanceReviewWithAi(review, task, note);
        agentReviewMapper.updateById(review);
        return review;
    }

    private void applyTaskReviewPayload(AgentReview review, AgentTask task, String note,
                                        String generatedSource, String aiFailureReason, String promptVersion) {
        String status = task.getStatus();
        boolean done = AgentTaskStatusEnum.DONE.name().equals(status);
        boolean skipped = AgentTaskStatusEnum.SKIPPED.name().equals(status);
        review.setDoneCount(done ? 1 : 0);
        review.setSkippedCount(skipped ? 1 : 0);
        review.setTodoCount(done || skipped ? 0 : 1);
        review.setCompletionRate(done ? java.math.BigDecimal.valueOf(100) : java.math.BigDecimal.ZERO);
        review.setReadinessScore(done ? 70 : 45);
        review.setSummary(taskReviewSummary(task, note));
        List<String> nextActions = taskReviewNextActions(task, skipped);
        review.setNextActionsJson(toJson(nextActions));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("status", status);
        payload.put("taskType", task.getTaskType());
        payload.put("title", task.getTitle());
        payload.put("relatedBizType", task.getRelatedBizType());
        payload.put("relatedBizId", task.getRelatedBizId());
        payload.put("note", firstText(note, task.getSkipReason()));
        payload.put("nextActions", nextActions);
        payload.put("generatedSource", generatedSource);
        payload.put("aiFailureReason", aiFailureReason);
        payload.put("promptVersion", promptVersion);
        payload.put("generatedAt", LocalDateTime.now().toString());
        payload.put("activationHandoffs", taskActivationHandoffs(task));
        review.setReviewJson(toJson(payload));
    }

    private void tryEnhanceReviewWithAi(AgentReview review, AgentTask task, String note) {
        try {
            AiCallContext ctx = new AiCallContext();
            ctx.setScene(REVIEW_PROMPT_SCENE);
            ctx.setPrompt(buildTaskReviewPrompt(task, note, review));
            ctx.setUserId(task.getUserId());
            ctx.setBusinessId(String.valueOf(task.getId()));
            ctx.setPromptVersion(REVIEW_PROMPT_VERSION);
            ctx.setResponseFormat("JSON");
            ctx.setRequestBody(toJson(taskReviewRequestSnapshot(task, note)));
            RouteResult result = aiCallLogService.callAndLog(ctx);
            AiReviewResult aiReview = parseAiReviewResult(result == null ? null : result.getContent());
            if (result == null || aiReview == null || !StringUtils.hasText(aiReview.summary())
                    || !AiResultSourceEnum.LLM.name().equals(result.getResultSource())) {
                return;
            }
            review.setSummary(truncate(aiReview.summary(), 1000));
            review.setNextActionsJson(toJson(aiReview.nextActions().isEmpty()
                    ? taskReviewNextActions(task, AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus()))
                    : aiReview.nextActions()));
            review.setAiCallLogId(result.getAiCallLogId());
            rewriteReviewMetadata(review, AiResultSourceEnum.LLM.name(), null, REVIEW_PROMPT_VERSION);
        } catch (Exception ex) {
            rewriteReviewMetadata(review, AiResultSourceEnum.FALLBACK.name(), ex.getClass().getSimpleName(), REVIEW_PROMPT_VERSION);
        }
    }

    private void emitTaskCompletedMetricAfterCommit(AgentTask task, String requestId,
                                                    List<ActivationHandoffVO> activationHandoffs,
                                                    boolean verifiedBusinessAction) {
        Runnable action = () -> {
            try {
                agentMetricsService.recordTaskCompleted(task, requestId, activationHandoffs, verifiedBusinessAction);
            } catch (Exception ex) {
                log.warn("Agent task completed metric capture failed taskId={} runId={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getAgentRunId(),
                        ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String buildTaskReviewPrompt(AgentTask task, String note, AgentReview ruleReview) {
        return """
                You are CodeCoachAI, a Java job-search coach. Summarize one finished or skipped daily action.
                Return strict JSON only: {"summary":"short user-safe summary","nextActions":["one next action","optional second next action"]}.
                Do not include raw prompts, private resume text, secrets, phone, email, or hidden input snapshots.
                Task title: %s
                Task type: %s
                Status: %s
                Skill: %s
                Related business: %s/%s
                User note: %s
                Rule summary: %s
                """.formatted(
                firstText(task.getTitle(), ""),
                firstText(task.getTaskType(), ""),
                firstText(task.getStatus(), ""),
                firstText(task.getRelatedSkillName(), ""),
                firstText(task.getRelatedBizType(), ""),
                task.getRelatedBizId() == null ? "" : task.getRelatedBizId(),
                firstText(note, task.getSkipReason(), ""),
                firstText(ruleReview.getSummary(), ""));
    }

    private Map<String, Object> taskReviewRequestSnapshot(AgentTask task, String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("status", task.getStatus());
        payload.put("taskType", task.getTaskType());
        payload.put("title", task.getTitle());
        payload.put("relatedSkillName", task.getRelatedSkillName());
        payload.put("relatedBizType", task.getRelatedBizType());
        payload.put("relatedBizId", task.getRelatedBizId());
        payload.put("note", firstText(note, task.getSkipReason()));
        return payload;
    }

    private AiReviewResult parseAiReviewResult(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(content, Map.class);
            Object summary = payload.get("summary");
            Object nextActions = payload.get("nextActions");
            List<String> actions = nextActions instanceof List<?> list
                    ? list.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .filter(StringUtils::hasText)
                            .limit(3)
                            .toList()
                    : List.of();
            return summary instanceof String text ? new AiReviewResult(text, actions) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void rewriteReviewMetadata(AgentReview review, String generatedSource, String aiFailureReason, String promptVersion) {
        if (!StringUtils.hasText(review.getReviewJson())) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(review.getReviewJson(), Map.class);
            payload.put("generatedSource", generatedSource);
            payload.put("aiFailureReason", aiFailureReason);
            payload.put("promptVersion", promptVersion);
            payload.put("generatedAt", LocalDateTime.now().toString());
            review.setReviewJson(toJson(payload));
        } catch (Exception ex) {
            // Keep the already safe rule payload when metadata rewrite fails.
        }
    }

    private AgentReview findTaskReview(AgentTask task) {
        if (task == null || task.getId() == null || task.getUserId() == null) {
            return null;
        }
        LocalDate reviewDate = task.getDueDate() == null ? LocalDate.now() : task.getDueDate();
        List<AgentReview> reviews = agentReviewMapper.selectList(new LambdaQueryWrapper<AgentReview>()
                .eq(AgentReview::getUserId, task.getUserId())
                .eq(AgentReview::getReviewDate, reviewDate)
                .eq(task.getAgentRunId() != null, AgentReview::getAgentRunId, task.getAgentRunId())
                .eq(task.getAgentRunId() == null && task.getTargetJobId() != null, AgentReview::getTargetJobId, task.getTargetJobId())
                .eq(AgentReview::getDeleted, 0)
                .like(AgentReview::getReviewJson, "\"taskId\":" + task.getId())
                .last("limit 1"));
        return reviews == null || reviews.isEmpty() ? null : reviews.get(0);
    }

    private String taskReviewSummary(AgentTask task, String note) {
        String title = firstText(task.getTitle(), task.getTaskType(), "Agent task");
        if (AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus())) {
            return "Skipped task: " + title + ". " + firstText(note, task.getSkipReason(), "Review why this action was deferred before the next plan.");
        }
        return "Completed task: " + title + ". " + firstText(note, "Keep the evidence and move to the next job-search action.");
    }

    private List<String> taskReviewNextActions(AgentTask task, boolean skipped) {
        if (skipped) {
            return List.of("Confirm whether this task still matters for the target job.", "Regenerate or adjust the daily plan after the blocker is resolved.");
        }
        String type = StringUtils.hasText(task.getTaskType()) ? task.getTaskType().toUpperCase(Locale.ROOT) : "";
        if (type.contains("INTERVIEW") || type.contains("REPORT")) {
            return List.of("Review low-score interview points.", "Turn one weak point into the next practice task.");
        }
        if (type.contains("QUESTION") || type.contains("SKILL")) {
            return List.of("Save reusable answer points.", "Practice one adjacent question to check retention.");
        }
        if (type.contains("RESUME")) {
            return List.of("Rerun resume-job match after edits.", "Check whether the updated evidence supports the target JD.");
        }
        return List.of("Record the concrete outcome.", "Continue the next daily-plan task.");
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String readReviewNote(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(json, Map.class).get("note");
            return value instanceof String text && StringUtils.hasText(text) ? text : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String readReviewSource(String json) {
        if (!StringUtils.hasText(json)) {
            return REVIEW_SOURCE_RULE;
        }
        try {
            Object value = objectMapper.readValue(json, Map.class).get("generatedSource");
            return value instanceof String text && StringUtils.hasText(text) ? text : REVIEW_SOURCE_RULE;
        } catch (Exception ex) {
            return REVIEW_SOURCE_RULE;
        }
    }

    private String reviewSourceLabel(String source) {
        if (AiResultSourceEnum.LLM.name().equals(source)) {
            return REVIEW_SOURCE_LLM_LABEL;
        }
        if (AiResultSourceEnum.FALLBACK.name().equals(source)) {
            return REVIEW_SOURCE_FALLBACK_LABEL;
        }
        return REVIEW_SOURCE_RULE_LABEL;
    }

    private String readRequestId(String inputSnapshotJson) {
        if (!StringUtils.hasText(inputSnapshotJson)) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(inputSnapshotJson, Map.class).get("requestId");
            return value instanceof String text && StringUtils.hasText(text) ? text : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> buildRunInputSnapshot(JobCoachAgentContext context, DailyPlanGenerateDTO request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestId", request == null ? null : request.getRequestId());
        snapshot.put("idempotencyKey", request == null ? null : request.getIdempotencyKey());
        snapshot.put("context", context);
        return snapshot;
    }

    private List<ActivationHandoffVO> planActivationHandoffs(AgentRun run) {
        if (run == null || run.getId() == null || !AgentRunStatusEnum.SUCCESS.name().equals(run.getStatus())) {
            return List.of();
        }
        List<ActivationHandoffVO> handoffs = new java.util.ArrayList<>();
        handoffs.add(buildPlanHandoff(run, HANDOFF_CODE_TARGET_DIRECTION_ESTABLISHED, HANDOFF_STAGE_TARGET_DIRECTION, true));
        handoffs.add(buildPlanHandoff(run, HANDOFF_CODE_FIRST_PLAN_GENERATED, HANDOFF_STAGE_FIRST_PLAN,
                isFirstSuccessfulPlan(run)));
        return handoffs;
    }

    private List<ActivationHandoffVO> taskActivationHandoffs(AgentTask task) {
        if (task == null || !AgentTaskStatusEnum.DONE.name().equals(task.getStatus())) {
            return List.of();
        }
        String requestId = resolveTaskRequestId(task);
        ActivationHandoffVO handoff = new ActivationHandoffVO();
        handoff.setCode(HANDOFF_CODE_FIRST_TASK_COMPLETED);
        handoff.setStage(HANDOFF_STAGE_FIRST_TASK_COMPLETED);
        handoff.setFirstOccurrence(isFirstCompletedTask(task));
        handoff.setRunId(task.getAgentRunId());
        handoff.setTaskId(task.getId());
        handoff.setTargetJobId(task.getTargetJobId());
        handoff.setPlanDate(task.getDueDate());
        handoff.setOccurredAt(firstTimestamp(task.getCompletedAt(), task.getUpdatedAt(), task.getCreatedAt()));
        handoff.setRequestId(requestId);
        return List.of(handoff);
    }

    private String resolveTaskRequestId(AgentTask task) {
        if (task == null || task.getAgentRunId() == null) {
            return null;
        }
        AgentRun run = agentRunMapper.selectById(task.getAgentRunId());
        return run == null ? null : readRequestId(run.getInputSnapshotJson());
    }

    private ActivationHandoffVO buildPlanHandoff(AgentRun run, String code, String stage, boolean firstOccurrence) {
        ActivationHandoffVO handoff = new ActivationHandoffVO();
        handoff.setCode(code);
        handoff.setStage(stage);
        handoff.setFirstOccurrence(firstOccurrence);
        handoff.setRunId(run.getId());
        handoff.setTargetJobId(run.getTargetJobId());
        handoff.setPlanDate(run.getPlanDate());
        handoff.setOccurredAt(firstTimestamp(run.getFinishedAt(), run.getStartedAt(), run.getCreatedAt()));
        handoff.setRequestId(readRequestId(run.getInputSnapshotJson()));
        return handoff;
    }

    private boolean isFirstSuccessfulPlan(AgentRun run) {
        if (run == null || run.getUserId() == null || run.getId() == null) {
            return false;
        }
        List<AgentRun> priorRuns = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .select(AgentRun::getId)
                .eq(AgentRun::getUserId, run.getUserId())
                .eq(AgentRun::getAgentType, AGENT_TYPE)
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.SUCCESS.name())
                .lt(AgentRun::getId, run.getId())
                .last("limit 1"));
        return priorRuns == null || priorRuns.isEmpty();
    }

    private boolean isFirstCompletedTask(AgentTask task) {
        if (task == null || task.getUserId() == null || task.getId() == null) {
            return false;
        }
        List<AgentTask> priorTasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .select(AgentTask::getId)
                .eq(AgentTask::getUserId, task.getUserId())
                .eq(AgentTask::getDeleted, 0)
                .eq(AgentTask::getStatus, AgentTaskStatusEnum.DONE.name())
                .lt(AgentTask::getId, task.getId())
                .last("limit 1"));
        return priorTasks == null || priorTasks.isEmpty();
    }

    private List<ActivationHandoffVO> readActivationHandoffs(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            Object value = objectMapper.readValue(json, Map.class).get("activationHandoffs");
            if (!(value instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            return list.stream()
                    .map(item -> objectMapper.convertValue(item, ActivationHandoffVO.class))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private AgentCoachActionRequest normalizeCoachActionRequest(AgentCoachActionDTO dto) {
        if (dto == null || dto.getTaskId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "taskId is required");
        }
        String actionType = normalizeCode(dto.getActionType());
        if (!ACTION_EXPLAIN_RECOMMENDATION.equals(actionType) && !ACTION_REVIEW_COMPLETED_TASK.equals(actionType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported Agent coach action type");
        }
        String requestId = StringUtils.hasText(dto.getRequestId()) ? dto.getRequestId().trim() : UUID.randomUUID().toString();
        String idempotencyKey = StringUtils.hasText(dto.getIdempotencyKey()) ? dto.getIdempotencyKey().trim() : null;
        return new AgentCoachActionRequest(dto.getTaskId(), actionType, requestId, idempotencyKey, UUID.randomUUID().toString());
    }

    private AgentCoachActionVO explainRecommendation(AgentTask task, AgentCoachActionRequest request) {
        try {
            AiCallContext ctx = new AiCallContext();
            ctx.setScene(COACH_PROMPT_SCENE);
            ctx.setPrompt(buildRecommendationExplainPrompt(task));
            ctx.setUserId(task.getUserId());
            ctx.setBusinessId(String.valueOf(task.getId()));
            ctx.setRequestId(request.requestId());
            ctx.setPromptVersion(COACH_PROMPT_VERSION);
            ctx.setResponseFormat("JSON");
            ctx.setRequestBody(toJson(coachActionRequestSnapshot(task, request)));
            RouteResult result = aiCallLogService.callAndLog(ctx);
            CoachActionResult parsed = parseCoachActionResult(result == null ? null : result.getContent());
            if (result != null && parsed != null && StringUtils.hasText(parsed.summary())) {
                return toCoachActionVO(task, request, parsed, result);
            }
        } catch (Exception ex) {
            emitAiCoachMetric(task, request, "ai_coach_action_failed", null,
                    Map.of("failureReason", ex.getClass().getSimpleName()));
        }
        return fallbackExplainRecommendation(task, request);
    }

    private AgentCoachActionVO reviewCompletedTask(AgentTask task, AgentCoachActionRequest request) {
        AgentReview review = findTaskReview(task);
        if (review == null) {
            review = upsertTaskReview(task, null);
        }
        List<String> nextActions = readStringList(review.getNextActionsJson()).stream().limit(3).toList();
        CoachActionResult result = new CoachActionResult(
                firstText(review.getSummary(), taskReviewSummary(task, null)),
                nextActions,
                List.of("agent_review", "task.status"),
                nextActions.isEmpty() ? "Continue the next daily-plan task." : nextActions.get(0));
        AgentCoachActionVO vo = toCoachActionVO(task, request, result, null);
        vo.setResultSource(readReviewSource(review.getReviewJson()));
        vo.setFallback(AiResultSourceEnum.FALLBACK.name().equals(vo.getResultSource()));
        vo.setAiCallLogId(review.getAiCallLogId());
        return vo;
    }

    private AgentCoachActionVO fallbackExplainRecommendation(AgentTask task, AgentCoachActionRequest request) {
        List<String> reasons = new ArrayList<>();
        if (StringUtils.hasText(task.getReason())) {
            reasons.add(task.getReason());
        }
        if (StringUtils.hasText(task.getRelatedSkillName())) {
            reasons.add("Focus skill: " + task.getRelatedSkillName());
        }
        if (StringUtils.hasText(task.getActionUrl())) {
            reasons.add("It has a concrete next action entry.");
        }
        if (reasons.isEmpty()) {
            reasons.add("This task is part of today's JobCoachAI plan.");
        }
        CoachActionResult result = new CoachActionResult(
                "This task is recommended because it connects today's plan to a concrete job-search action.",
                reasons.stream().limit(3).toList(),
                List.of("task.reason", "task.relatedSkillName", "task.actionUrl").stream()
                        .filter(ref -> hasEvidenceRef(task, ref))
                        .toList(),
                StringUtils.hasText(task.getActionUrl()) ? "Open the task entry and complete one focused action." : "Continue this task from the Agent task list.");
        AgentCoachActionVO vo = toCoachActionVO(task, request, result, null);
        vo.setResultSource(AiResultSourceEnum.FALLBACK.name());
        vo.setFallback(true);
        return vo;
    }

    private boolean hasEvidenceRef(AgentTask task, String ref) {
        return switch (ref) {
            case "task.reason" -> StringUtils.hasText(task.getReason());
            case "task.relatedSkillName" -> StringUtils.hasText(task.getRelatedSkillName());
            case "task.actionUrl" -> StringUtils.hasText(task.getActionUrl());
            default -> false;
        };
    }

    private AgentCoachActionVO toCoachActionVO(AgentTask task, AgentCoachActionRequest request,
                                               CoachActionResult result, RouteResult routeResult) {
        AgentCoachActionVO vo = new AgentCoachActionVO();
        vo.setActionType(request.actionType());
        vo.setTaskId(task.getId());
        vo.setSummary(truncate(result.summary(), 1000));
        vo.setReasons(result.reasons().stream().filter(StringUtils::hasText).map(item -> truncate(item, 300)).limit(3).toList());
        vo.setEvidenceRefs(result.evidenceRefs().stream().filter(StringUtils::hasText).limit(5).toList());
        vo.setEvidenceSources(coachActionEvidenceSources(task, vo.getEvidenceRefs()));
        vo.setNextAction(truncate(firstText(result.nextAction(), vo.getReasons().isEmpty() ? null : vo.getReasons().get(0)), 300));
        vo.setRequestId(request.requestId());
        vo.setTraceId(request.traceId());
        vo.setIdempotencyKey(request.idempotencyKey());
        vo.setResultSource(routeResult == null ? AiResultSourceEnum.FALLBACK.name() : routeResult.getResultSource());
        vo.setFallback(AiResultSourceEnum.FALLBACK.name().equals(vo.getResultSource()));
        vo.setAiCallLogId(routeResult == null ? null : routeResult.getAiCallLogId());
        vo.setLatencyMs(routeResult == null ? null : routeResult.getElapsedMs());
        vo.setEstimatedCost(routeResult == null ? null : routeResult.getEstimatedCost());
        return vo;
    }

    private List<SuggestionEvidenceSourceVO> coachActionEvidenceSources(AgentTask task, List<String> evidenceRefs) {
        if (task == null || evidenceRefs == null || evidenceRefs.isEmpty()) {
            return List.of();
        }
        SuggestionEvidenceSourceVO source = new SuggestionEvidenceSourceVO();
        source.setId("agent-task:" + task.getId());
        source.setSourceType(firstText(task.getRelatedBizType(), task.getTaskType(), "JOB_COACH_AGENT_TASK"));
        source.setSourceId(task.getRelatedBizId() == null ? task.getId() : task.getRelatedBizId());
        source.setSourceTitle(task.getTitle());
        source.setSourceLabel("Agent 任务");
        source.setEvidenceSummary(truncate(String.join(", ", evidenceRefs), 200));
        source.setSourceSummary(truncate(firstText(task.getReason(), task.getRelatedSkillName(), "Agent 任务上下文摘要"), 300));
        source.setTrustStatus(task.getAgentRunId() == null ? "PARTIAL" : "VERIFIED");
        source.setSourceUpdatedAt(task.getUpdatedAt());
        source.setActionUrl(task.getActionUrl());
        return List.of(source);
    }

    private CoachActionResult parseCoachActionResult(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(content, Map.class);
            Object summary = payload.get("summary");
            if (!(summary instanceof String summaryText) || !StringUtils.hasText(summaryText)) {
                return null;
            }
            return new CoachActionResult(
                    summaryText,
                    readStringList(payload.get("reasons")),
                    readStringList(payload.get("evidenceRefs")),
                    payload.get("nextAction") instanceof String nextAction ? nextAction : null);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
    }

    private String buildRecommendationExplainPrompt(AgentTask task) {
        return """
                You are CodeCoachAI, a Java job-search coach. Explain why this one task is recommended.
                Return strict JSON only: {"summary":"short user-safe explanation","reasons":["up to 3 concise reasons"],"evidenceRefs":["stable field names only"],"nextAction":"one concrete next action"}.
                Do not expose raw prompts, raw model output, private resume/JD text, secrets, phone, email, or hidden input snapshots.
                Task title: %s
                Task type: %s
                Status: %s
                Recommendation reason: %s
                Skill: %s
                Evidence summary: %s/%s
                Action URL present: %s
                """.formatted(
                firstText(task.getTitle(), ""),
                firstText(task.getTaskType(), ""),
                firstText(task.getStatus(), ""),
                firstText(task.getReason(), ""),
                firstText(task.getRelatedSkillName(), ""),
                firstText(task.getRelatedBizType(), ""),
                task.getRelatedBizId() == null ? "" : task.getRelatedBizId(),
                StringUtils.hasText(task.getActionUrl()) ? "yes" : "no");
    }

    private Map<String, Object> coachActionRequestSnapshot(AgentTask task, AgentCoachActionRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("actionType", request.actionType());
        payload.put("requestId", request.requestId());
        payload.put("idempotencyKey", request.idempotencyKey());
        payload.put("status", task.getStatus());
        payload.put("taskType", task.getTaskType());
        payload.put("title", task.getTitle());
        payload.put("reason", task.getReason());
        payload.put("relatedSkillName", task.getRelatedSkillName());
        payload.put("relatedBizType", task.getRelatedBizType());
        payload.put("relatedBizId", task.getRelatedBizId());
        return payload;
    }

    private void emitAiCoachMetric(AgentTask task, AgentCoachActionRequest request, String eventCode,
                                   AgentCoachActionVO result, Map<String, Object> extraMetadata) {
        try {
            AgentMetricEventDTO event = new AgentMetricEventDTO();
            event.setEventCode(eventCode);
            event.setIdempotencyKey(buildCoachMetricIdempotencyKey(task, request, eventCode));
            event.setUserId(task.getUserId());
            event.setTaskId(task.getId());
            event.setRunId(task.getAgentRunId());
            event.setPlanDate(task.getDueDate());
            event.setTargetJobId(task.getTargetJobId());
            event.setRequestId(request.requestId());
            event.setSourcePage("agent_coach_contextual_action");
            event.setTargetPath(task.getActionUrl());
            event.setBizType(task.getRelatedBizType());
            event.setBizId(task.getRelatedBizId() == null ? null : String.valueOf(task.getRelatedBizId()));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("actionType", request.actionType());
            metadata.put("traceId", request.traceId());
            metadata.put("idempotencyKey", request.idempotencyKey());
            if (result != null) {
                metadata.put("resultSource", result.getResultSource());
                metadata.put("aiCallLogId", result.getAiCallLogId());
                metadata.put("latencyMs", result.getLatencyMs());
                metadata.put("estimatedCost", result.getEstimatedCost());
            }
            if (extraMetadata != null) {
                metadata.putAll(extraMetadata);
            }
            event.setMetadata(metadata);
            agentMetricsService.acceptEvent(task.getUserId(), event);
        } catch (Exception ex) {
            log.warn("Agent AI coach metric capture failed taskId={} eventCode={}",
                    task == null ? null : task.getId(),
                    eventCode,
                    ex);
        }
    }

    private String buildCoachMetricIdempotencyKey(AgentTask task, AgentCoachActionRequest request, String eventCode) {
        StringBuilder builder = new StringBuilder("ai-coach");
        appendCoachMetricKeyPart(builder, eventCode);
        appendCoachMetricKeyPart(builder, request.actionType());
        appendCoachMetricKeyPart(builder, request.requestId());
        appendCoachMetricKeyPart(builder, task == null ? null : task.getId());
        return truncate(builder.toString(), 128);
    }

    private void appendCoachMetricKeyPart(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('|');
        }
        builder.append(truncate(text, 64));
    }

    private record AgentCoachActionRequest(Long taskId, String actionType, String requestId,
                                           String idempotencyKey, String traceId) {
    }

    private record CoachActionResult(String summary, List<String> reasons, List<String> evidenceRefs,
                                     String nextAction) {
    }

    private record AiReviewResult(String summary, List<String> nextActions) {
    }

    private CandidateTask matchCandidate(String candidateId, List<CandidateTask> candidates) {
        if (!StringUtils.hasText(candidateId) || candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> candidateId.equals(candidate.getCandidateId()))
                .findFirst()
                .orElse(null);
    }

    private DailyPlanResult readPlanResult(String outputJson) {
        if (!StringUtils.hasText(outputJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(outputJson, DailyPlanResult.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private LambdaQueryWrapper<AgentTask> taskQuery() {
        return new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getDeleted, 0);
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private long pageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private long pageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, AgentErrorCode.OUTPUT_PARSE_FAILED);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String agentErrorCode(String message) {
        if (!StringUtils.hasText(message)) {
            return AgentErrorCode.AI_CALL_FAILED;
        }
        String value = message.trim();
        if (AgentErrorCode.TARGET_JOB_REQUIRED.equals(value)
                || AgentErrorCode.AI_CALL_FAILED.equals(value)
                || AgentErrorCode.OUTPUT_PARSE_FAILED.equals(value)
                || AgentErrorCode.OUTPUT_VALIDATE_FAILED.equals(value)
                || AgentErrorCode.ASYNC_TASK_FAILED.equals(value)
                || AgentErrorCode.RUN_NOT_FOUND.equals(value)
                || AgentErrorCode.RUN_TIMEOUT.equals(value)
                || AgentErrorCode.TASK_NOT_FOUND.equals(value)
                || AgentErrorCode.TASK_STATUS_INVALID.equals(value)) {
            return value;
        }
        return AgentErrorCode.AI_CALL_FAILED;
    }

    private String friendlyAgentErrorMessage(String errorCode, String rawMessage) {
        if (AgentErrorCode.TARGET_JOB_REQUIRED.equals(errorCode)) {
            return "还没有当前目标岗位。请先创建或设置一个目标岗位，再生成今日计划。";
        }
        if (AgentErrorCode.OUTPUT_PARSE_FAILED.equals(errorCode)
                || AgentErrorCode.OUTPUT_VALIDATE_FAILED.equals(errorCode)) {
            return "今日计划内容暂时不可用，请重新生成。";
        }
        if (AgentErrorCode.RUN_TIMEOUT.equals(errorCode)) {
            return "计划生成超时，请重新生成今日计划。";
        }
        if (AgentErrorCode.RUN_NOT_FOUND.equals(errorCode)) {
            return "没有找到这次计划记录，请刷新后重试。";
        }
        if (AgentErrorCode.TASK_NOT_FOUND.equals(errorCode)) {
            return "没有找到这条训练任务，请刷新后重试。";
        }
        if (AgentErrorCode.TASK_STATUS_INVALID.equals(errorCode)) {
            return "任务状态已经变化，请刷新后重试。";
        }
        if (StringUtils.hasText(rawMessage)) {
            String value = rawMessage.trim();
            if (!isInternalAgentFailureMessage(value)) {
                return value;
            }
        }
        return "AI 生成暂时失败，请稍后重试。";
    }

    private AgentFailureDiagnosis diagnoseAgentFailure(String errorCode, String rawMessage) {
        String value = ((errorCode == null ? "" : errorCode) + " " + (rawMessage == null ? "" : rawMessage))
                .toUpperCase(Locale.ROOT);
        if (AgentErrorCode.TARGET_JOB_REQUIRED.equals(errorCode) || value.contains("TARGET_JOB")) {
            return new AgentFailureDiagnosis(
                    "FIX_TARGET_JOB",
                    "去创建目标岗位",
                    "请先补充目标岗位，再重新生成今日计划。");
        }
        if (value.contains("RESUME")) {
            return new AgentFailureDiagnosis(
                    "FIX_RESUME",
                    "去完善简历",
                    "请先补充或选择默认简历，再重新生成今日计划。");
        }
        if (value.contains("SKILL_PROFILE")) {
            return new AgentFailureDiagnosis(
                    "FIX_SKILL_PROFILE",
                    "去生成能力画像",
                    "请先生成能力画像或完成一次简历匹配，再重新生成今日计划。");
        }
        if (AgentErrorCode.OUTPUT_PARSE_FAILED.equals(errorCode)
                || AgentErrorCode.OUTPUT_VALIDATE_FAILED.equals(errorCode)
                || AgentErrorCode.ASYNC_TASK_FAILED.equals(errorCode)
                || AgentErrorCode.RUN_TIMEOUT.equals(errorCode)) {
            return new AgentFailureDiagnosis(
                    "RETRY",
                    "重新生成",
                    "本次生成结果不可用，请重新生成今日计划。");
        }
        return new AgentFailureDiagnosis(
                "WAIT_AND_RETRY",
                "稍后重试",
                "AI 服务暂时不可用，请稍后重试；如果持续失败，请联系管理员查看智能生成监控。");
    }

    private boolean isInternalAgentFailureMessage(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("agent_")
                || (normalized.contains("ai") && normalized.contains("call") && normalized.contains("failed"))
                || (normalized.contains("json") && normalized.contains("serialize") && normalized.contains("failed"));
    }

    private record AgentFailureDiagnosis(String action, String actionLabel, String suggestion) {
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
