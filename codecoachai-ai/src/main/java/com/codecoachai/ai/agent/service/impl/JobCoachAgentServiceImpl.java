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
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentRunUserDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
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
import com.codecoachai.ai.agent.service.AgentOutputParser;
import com.codecoachai.ai.agent.service.AgentOutputValidator;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

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
    private static final String TASK_TYPE_QUESTION_PRACTICE = "QUESTION_PRACTICE";
    private static final String TASK_TYPE_INTERVIEW = "INTERVIEW";
    private static final String TASK_TYPE_APPLICATION_FOLLOW_UP = "APPLICATION_FOLLOW_UP";
    private static final String TASK_TYPE_RESUME_OPTIMIZE = "RESUME_OPTIMIZE";
    private static final String EVIDENCE_TYPE_PRACTICE_RECORD = "PRACTICE_RECORD";
    private static final String EVIDENCE_TYPE_INTERVIEW_REPORT = "INTERVIEW_REPORT";
    private static final String EVIDENCE_TYPE_JOB_APPLICATION_EVENT = "JOB_APPLICATION_EVENT";
    private static final String EVIDENCE_TYPE_RESUME_OPTIMIZE_RECORD = "RESUME_OPTIMIZE_RECORD";
    private static final String BIZ_TYPE_TARGET_JOB = "TARGET_JOB";
    private static final String BIZ_TYPE_JOB_APPLICATION = "JOB_APPLICATION";
    private static final String REPORT_STATUS_GENERATED = "GENERATED";
    private static final String RESUME_OPTIMIZE_STATUS_SUCCESS = "SUCCESS";
    private static final int DEFAULT_TASK_COUNT = 3;
    private static final int DEFAULT_MAX_TOTAL_MINUTES = 120;
    private static final long RUNNING_REUSE_WINDOW_MINUTES = 15L;
    private static final String RUN_FORCE_REGENERATED = "AGENT_RUN_FORCE_REGENERATED";
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
    private final AiCallLogService aiCallLogService;
    private final QuestionPracticeEvidenceFeignClient questionPracticeEvidenceFeignClient;
    private final ResumeJobApplicationEvidenceFeignClient resumeJobApplicationEvidenceFeignClient;
    private final InterviewReportEvidenceFeignClient interviewReportEvidenceFeignClient;
    private final ResumeOptimizeRecordEvidenceFeignClient resumeOptimizeRecordEvidenceFeignClient;
    private final ObjectMapper objectMapper;
    private final AgentMqDispatcher agentMqDispatcher;
    private final TransactionTemplate transactionTemplate;

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
        MqDispatchReceipt receipt = agentMqDispatcher.dispatchDailyPlanWithReceipt(run.getId(), userId, request);
        if (receipt != null) {
            return withAsyncReceipt(toDailyPlan(agentRunMapper.selectById(run.getId())), receipt);
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
        if (!prepareRunForExecution(userId, run)) {
            return currentDailyPlan(userId, run.getId());
        }
        try {
            int taskCount = valueOrDefault(request.getTaskCount(), DEFAULT_TASK_COUNT);
            int maxTotalMinutes = valueOrDefault(request.getMaxTotalMinutes(), DEFAULT_MAX_TOTAL_MINUTES);
            JobCoachAgentContext context = agentContextBuilder.build(userId, request.getTargetJobId(), request.getDate());
            run.setTargetJobId(context.getTargetJobId());
            agentRunMapper.updateById(run);

            List<CandidateTask> candidates = candidateTaskBuilder.build(context, taskCount);
            PromptRenderResult prompt = agentPromptBuilder.buildDailyPlanPrompt(context, candidates, taskCount, maxTotalMinutes);
            run.setInputSnapshotJson(toJson(context));
            run.setPromptType(AgentPromptBuilderImpl.PROMPT_TYPE);
            run.setPromptVersionId(prompt.getPromptTemplateVersionId());
            agentRunMapper.updateById(run);

            RouteResult routeResult = callAi(userId, run, prompt);
            DailyPlanResult planResult = agentOutputParser.parseDailyPlan(routeResult.getContent());
            agentOutputValidator.validateDailyPlan(planResult, candidates, taskCount, maxTotalMinutes);
            return transactionTemplate.execute(status -> {
                if (!isRunStillRunning(userId, run.getId())) {
                    return currentDailyPlan(userId, run.getId());
                }
                clearRunTasks(run);
                saveTasks(userId, run, planResult, candidates);
                if (!markSuccess(run, planResult, routeResult, System.currentTimeMillis() - start)) {
                    clearRunTasks(run);
                    return currentDailyPlan(userId, run.getId());
                }
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
                .stream().map(this::toReviewedTaskVO).toList();
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
                        .eq(StringUtils.hasText(actual.getTaskType()), AgentTask::getTaskType, actual.getTaskType())
                        .eq(StringUtils.hasText(actual.getStatus()), AgentTask::getStatus, actual.getStatus())
                        .eq(StringUtils.hasText(actual.getPriority()), AgentTask::getPriority, actual.getPriority())
                        .orderByDesc(AgentTask::getDueDate)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByDesc(AgentTask::getId));
        return PageResult.of(page.getRecords().stream().map(this::toReviewedTaskVO).toList(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO startTask(Long userId, Long taskId) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.DOING.name().equals(task.getStatus())) {
            return AgentConvert.toTaskVO(task);
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

    private AgentTaskVO completeTaskInternal(Long userId, Long taskId, AgentTaskCompleteDTO dto,
                                             boolean verifiedBusinessAction) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.DONE.name().equals(task.getStatus())) {
            return enrichTaskReview(AgentConvert.toTaskVO(task), task);
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
        return enrichTaskReview(AgentConvert.toTaskVO(latest), review);
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
            return enrichTaskReview(AgentConvert.toTaskVO(task), task);
        }
        return completeTaskInternal(dto.getUserId(), task.getId(), businessActionCompleteNote(dto), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO skipTask(Long userId, Long taskId, AgentTaskSkipDTO dto) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus())) {
            return enrichTaskReview(AgentConvert.toTaskVO(task), task);
        }
        transitionTask(userId, taskId, AgentTaskStatusEnum.SKIPPED.name(),
                List.of(AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name()),
                wrapper -> wrapper
                        .set(AgentTask::getSkippedAt, LocalDateTime.now())
                        .set(AgentTask::getSkipReason, dto == null ? null : dto.getSkipReason()));
        AgentTask latest = taskAfterTransitionEntity(userId, taskId, AgentTaskStatusEnum.SKIPPED.name());
        AgentReview review = upsertTaskReview(latest, dto == null ? null : dto.getSkipReason());
        return enrichTaskReview(AgentConvert.toTaskVO(latest), review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO restoreTask(Long userId, Long taskId) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.TODO.name().equals(task.getStatus())) {
            return AgentConvert.toTaskVO(task);
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
                        .eq(StringUtils.hasText(actual.getAgentType()), AgentRun::getAgentType, actual.getAgentType())
                        .eq(StringUtils.hasText(actual.getStatus()), AgentRun::getStatus, actual.getStatus())
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
                        .eq(StringUtils.hasText(actual.getTaskType()), AgentTask::getTaskType, actual.getTaskType())
                        .eq(StringUtils.hasText(actual.getStatus()), AgentTask::getStatus, actual.getStatus())
                        .eq(StringUtils.hasText(actual.getPriority()), AgentTask::getPriority, actual.getPriority())
                        .orderByDesc(AgentTask::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toReviewedTaskVO).toList(), page.getTotal(), pageNo, pageSize);
    }

    private RunCreateResult createRun(Long userId, Long targetJobId, LocalDate planDate) {
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setAgentType(AGENT_TYPE);
        run.setTargetJobId(targetJobId);
        run.setPlanDate(planDate);
        run.setTriggerType(TRIGGER_MANUAL);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setStartedAt(LocalDateTime.now());
        run.setPromptType(AgentPromptBuilderImpl.PROMPT_TYPE);
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
        return request;
    }

    private boolean prepareRunForExecution(Long userId, AgentRun run) {
        if (run == null || run.getId() == null || !AgentRunStatusEnum.RUNNING.name().equals(run.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = agentRunMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name())
                .set(AgentRun::getStartedAt, now)
                .set(AgentRun::getFinishedAt, null)
                .set(AgentRun::getDurationMs, null)
                .set(AgentRun::getErrorCode, null)
                .set(AgentRun::getErrorMessage, null)
                .set(AgentRun::getUpdatedAt, now));
        if (rows <= 0) {
            return false;
        }
        run.setStartedAt(now);
        run.setFinishedAt(null);
        run.setDurationMs(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        return true;
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

    private boolean markSuccess(AgentRun run, DailyPlanResult planResult, RouteResult routeResult, long durationMs) {
        LocalDateTime finishedAt = LocalDateTime.now();
        String outputJson = toJson(planResult);
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        run.setOutputJson(outputJson);
        run.setRawOutputText(routeResult.getContent());
        run.setModelName(routeResult.getModel());
        run.setTraceId(routeResult.getRouteTrace());
        run.setAiCallLogId(routeResult.getAiCallLogId());
        run.setTokenInput(routeResult.getPromptTokens());
        run.setTokenOutput(routeResult.getCompletionTokens());
        run.setDurationMs(durationMs);
        run.setFinishedAt(finishedAt);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        int rows = agentRunMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, run.getUserId())
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name())
                .set(AgentRun::getStatus, AgentRunStatusEnum.SUCCESS.name())
                .set(AgentRun::getOutputJson, outputJson)
                .set(AgentRun::getRawOutputText, routeResult.getContent())
                .set(AgentRun::getModelName, routeResult.getModel())
                .set(AgentRun::getTraceId, routeResult.getRouteTrace())
                .set(AgentRun::getAiCallLogId, routeResult.getAiCallLogId())
                .set(AgentRun::getTokenInput, routeResult.getPromptTokens())
                .set(AgentRun::getTokenOutput, routeResult.getCompletionTokens())
                .set(AgentRun::getDurationMs, durationMs)
                .set(AgentRun::getFinishedAt, finishedAt)
                .set(AgentRun::getErrorCode, null)
                .set(AgentRun::getErrorMessage, null)
                .set(AgentRun::getUpdatedAt, finishedAt));
        return rows > 0;
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
        agentRunMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getUserId, run.getUserId())
                .eq(AgentRun::getDeleted, 0)
                .eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name())
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
        vo.setTargetJobId(run.getTargetJobId());
        vo.setDate(run.getPlanDate());
        vo.setCreatedAt(run.getCreatedAt());
        vo.setStatus(run.getStatus());
        vo.setErrorCode(run.getErrorCode());
        vo.setErrorMessage(friendlyAgentErrorMessage(run.getErrorCode(), run.getErrorMessage()));
        applyFailureDiagnosis(vo, run.getErrorCode(), run.getErrorMessage());
        vo.setDurationMs(run.getDurationMs());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
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
                .stream().map(this::toReviewedTaskVO).toList());
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
                .stream().map(this::toReviewedTaskVO).toList());
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
                .stream().map(this::toReviewedTaskVO).toList());
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

    private boolean isRunStillRunning(Long userId, Long runId) {
        AgentRun latest = agentRunMapper.selectById(runId);
        return latest != null
                && userId.equals(latest.getUserId())
                && AgentRunStatusEnum.RUNNING.name().equals(latest.getStatus());
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
        return Duration.between(run.getStartedAt(), LocalDateTime.now()).toMinutes() >= RUNNING_REUSE_WINDOW_MINUTES;
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
        return AgentConvert.toTaskVO(taskAfterTransitionEntity(userId, taskId, expectedStatus));
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
