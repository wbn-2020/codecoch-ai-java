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
import com.codecoachai.ai.agent.domain.dto.AgentTaskCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskSkipDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentRunUserDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.service.AgentContextBuilder;
import com.codecoachai.ai.agent.service.AgentOutputParser;
import com.codecoachai.ai.agent.service.AgentOutputValidator;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobCoachAgentServiceImpl implements JobCoachAgentService {

    private static final String AGENT_TYPE = "JOB_COACH";
    private static final String TRIGGER_MANUAL = "MANUAL";
    private static final int DEFAULT_TASK_COUNT = 3;
    private static final int DEFAULT_MAX_TOTAL_MINUTES = 120;
    private static final long RUNNING_REUSE_WINDOW_MINUTES = 15L;

    private final AgentRunMapper agentRunMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentContextBuilder agentContextBuilder;
    private final CandidateTaskBuilder candidateTaskBuilder;
    private final AgentPromptBuilder agentPromptBuilder;
    private final AgentOutputParser agentOutputParser;
    private final AgentOutputValidator agentOutputValidator;
    private final AiCallLogService aiCallLogService;
    private final ObjectMapper objectMapper;

    @Override
    public DailyPlanVO generateDailyPlan(Long userId, DailyPlanGenerateDTO dto) {
        DailyPlanGenerateDTO request = dto == null ? new DailyPlanGenerateDTO() : dto;
        LocalDate planDate = request.getDate() == null ? LocalDate.now() : request.getDate();
        int taskCount = valueOrDefault(request.getTaskCount(), DEFAULT_TASK_COUNT);
        int maxTotalMinutes = valueOrDefault(request.getMaxTotalMinutes(), DEFAULT_MAX_TOTAL_MINUTES);
        if (!Boolean.TRUE.equals(request.getForceRegenerate())) {
            AgentRun existing = latestVisibleRun(userId, request.getTargetJobId(), planDate);
            if (existing != null && AgentRunStatusEnum.SUCCESS.name().equals(existing.getStatus())) {
                return toDailyPlan(existing);
            }
            if (existing != null && AgentRunStatusEnum.RUNNING.name().equals(existing.getStatus())) {
                if (!isStaleRunning(existing)) {
                    return toDailyPlan(existing);
                }
                markFailed(existing, AgentErrorCode.RUN_TIMEOUT, "计划生成超时，请重新生成今日计划。",
                        durationFromStart(existing));
            }
        }

        long start = System.currentTimeMillis();
        AgentRun run = createRun(userId, request.getTargetJobId(), planDate);
        try {
            JobCoachAgentContext context = agentContextBuilder.build(userId, request.getTargetJobId(), planDate);
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
            saveTasks(userId, run, planResult, candidates);
            markSuccess(run, planResult, routeResult, System.currentTimeMillis() - start);
            return toDailyPlan(agentRunMapper.selectById(run.getId()));
        } catch (BusinessException ex) {
            String errorCode = agentErrorCode(ex.getMessage());
            markFailed(run, errorCode, ex.getMessage(), System.currentTimeMillis() - start);
            throw new BusinessException(ex.getCode(), friendlyAgentErrorMessage(errorCode, ex.getMessage()));
        } catch (RuntimeException ex) {
            markFailed(run, AgentErrorCode.AI_CALL_FAILED, firstText(ex.getMessage(), "AI call failed"),
                    System.currentTimeMillis() - start);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    friendlyAgentErrorMessage(AgentErrorCode.AI_CALL_FAILED, ex.getMessage()));
        }
    }

    @Override
    public DailyPlanVO latestDailyPlan(Long userId, Long targetJobId, LocalDate date) {
        LocalDate planDate = date == null ? LocalDate.now() : date;
        AgentRun run = latestVisibleRun(userId, targetJobId, planDate);
        if (run != null && AgentRunStatusEnum.RUNNING.name().equals(run.getStatus()) && isStaleRunning(run)) {
            markFailed(run, AgentErrorCode.RUN_TIMEOUT, "计划生成超时，请重新生成今日计划。",
                    durationFromStart(run));
            run = agentRunMapper.selectById(run.getId());
        }
        DailyPlanVO recovered = recoverableMissingTargetFailure(run, userId, targetJobId, planDate);
        if (recovered != null) {
            return recovered;
        }
        if (run == null) {
            return emptyDailyPlan(targetJobId, planDate,
                    "今天还没有 Agent 计划。先确认目标岗位和默认简历；如果缺少弱点证据，可以先完成一次题目练习或模拟面试，再生成今日计划。");
        }
        return toDailyPlan(run);
    }

    @Override
    public List<AgentTaskVO> todayTasks(Long userId, Long targetJobId, LocalDate date, String status) {
        LocalDate dueDate = date == null ? LocalDate.now() : date;
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getUserId, userId)
                        .eq(AgentTask::getDueDate, dueDate)
                        .eq(targetJobId != null, AgentTask::getTargetJobId, targetJobId)
                        .eq(StringUtils.hasText(status), AgentTask::getStatus, status)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByAsc(AgentTask::getId))
                .stream().map(AgentConvert::toTaskVO).toList();
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
        return PageResult.of(page.getRecords().stream().map(AgentConvert::toTaskVO).toList(), page.getTotal(), pageNo, pageSize);
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
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.DONE.name().equals(task.getStatus())) {
            return AgentConvert.toTaskVO(task);
        }
        LocalDateTime now = LocalDateTime.now();
        transitionTask(userId, taskId, AgentTaskStatusEnum.DONE.name(),
                List.of(AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name()),
                wrapper -> wrapper
                        .set(AgentTask::getStartedAt, task.getStartedAt() == null ? now : task.getStartedAt())
                        .set(AgentTask::getCompletedAt, now)
                        .set(AgentTask::getSkippedAt, null)
                        .set(AgentTask::getSkipReason, null));
        return taskAfterTransition(userId, taskId, AgentTaskStatusEnum.DONE.name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTaskVO skipTask(Long userId, Long taskId, AgentTaskSkipDTO dto) {
        AgentTask task = requireUserTask(userId, taskId);
        if (AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus())) {
            return AgentConvert.toTaskVO(task);
        }
        transitionTask(userId, taskId, AgentTaskStatusEnum.SKIPPED.name(),
                List.of(AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name()),
                wrapper -> wrapper
                        .set(AgentTask::getSkippedAt, LocalDateTime.now())
                        .set(AgentTask::getSkipReason, dto == null ? null : dto.getSkipReason()));
        return taskAfterTransition(userId, taskId, AgentTaskStatusEnum.SKIPPED.name());
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
        return PageResult.of(page.getRecords().stream().map(AgentConvert::toTaskVO).toList(), page.getTotal(), pageNo, pageSize);
    }

    private AgentRun createRun(Long userId, Long targetJobId, LocalDate planDate) {
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setAgentType(AGENT_TYPE);
        run.setTargetJobId(targetJobId);
        run.setPlanDate(planDate);
        run.setTriggerType(TRIGGER_MANUAL);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setStartedAt(LocalDateTime.now());
        run.setPromptType(AgentPromptBuilderImpl.PROMPT_TYPE);
        agentRunMapper.insert(run);
        return run;
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
            task.setTaskType(firstText(item.getType(), candidate == null ? null : candidate.getType()));
            task.setTitle(item.getTitle());
            task.setDescription(item.getDescription());
            task.setReason(item.getReason());
            task.setPriority(item.getPriority());
            task.setEstimatedMinutes(item.getEstimatedMinutes());
            task.setRelatedSkillCode(firstText(item.getRelatedSkillCode(), candidate == null ? null : candidate.getRelatedSkillCode()));
            task.setRelatedSkillName(firstText(item.getRelatedSkillName(), candidate == null ? null : candidate.getRelatedSkillName()));
            task.setRelatedBizType(firstText(item.getRelatedBizType(), candidate == null ? null : candidate.getRelatedBizType()));
            task.setRelatedBizId(item.getRelatedBizId() == null && candidate != null ? candidate.getRelatedBizId() : item.getRelatedBizId());
            task.setActionUrl(firstText(item.getActionUrl(), candidate == null ? null : candidate.getActionUrl()));
            task.setStatus(AgentTaskStatusEnum.TODO.name());
            task.setDueDate(run.getPlanDate());
            task.setSortOrder(++order);
            agentTaskMapper.insert(task);
        }
    }

    private void markSuccess(AgentRun run, DailyPlanResult planResult, RouteResult routeResult, long durationMs) {
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        run.setOutputJson(toJson(planResult));
        run.setRawOutputText(routeResult.getContent());
        run.setModelName(routeResult.getModel());
        run.setTraceId(routeResult.getRouteTrace());
        run.setAiCallLogId(routeResult.getAiCallLogId());
        run.setTokenInput(routeResult.getPromptTokens());
        run.setTokenOutput(routeResult.getCompletionTokens());
        run.setDurationMs(durationMs);
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorCode(null);
        run.setErrorMessage(null);
        agentRunMapper.updateById(run);
    }

    private void markFailed(AgentRun run, String errorCode, String errorMessage, long durationMs) {
        if (run == null || run.getId() == null) {
            return;
        }
        run.setStatus(AgentRunStatusEnum.FAILED.name());
        run.setErrorCode(truncate(errorCode, 128));
        run.setErrorMessage(truncate(errorMessage, 1024));
        run.setDurationMs(durationMs);
        run.setFinishedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
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
                .stream().map(AgentConvert::toTaskVO).toList());
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
                .stream().map(AgentConvert::toTaskVO).toList());
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
                .stream().map(AgentConvert::toTaskVO).toList());
        return vo;
    }

    private AgentRun latestVisibleRun(Long userId, Long targetJobId, LocalDate planDate) {
        return agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getPlanDate, planDate)
                .eq(targetJobId != null, AgentRun::getTargetJobId, targetJobId)
                .in(AgentRun::getStatus,
                        AgentRunStatusEnum.RUNNING.name(),
                        AgentRunStatusEnum.SUCCESS.name(),
                        AgentRunStatusEnum.FAILED.name())
                .orderByDesc(AgentRun::getCreatedAt)
                .last("limit 1"));
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

    private AgentTask requireUserTask(Long userId, Long taskId) {
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TASK_NOT_FOUND);
        }
        return task;
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
        AgentTask latest = requireUserTask(userId, taskId);
        if (!expectedStatus.equals(latest.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, AgentErrorCode.TASK_STATUS_INVALID);
        }
        return AgentConvert.toTaskVO(latest);
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
        return new LambdaQueryWrapper<>();
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON serialize failed");
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

    private String agentErrorCode(String message) {
        if (!StringUtils.hasText(message)) {
            return AgentErrorCode.AI_CALL_FAILED;
        }
        String value = message.trim();
        if (AgentErrorCode.TARGET_JOB_REQUIRED.equals(value)
                || AgentErrorCode.AI_CALL_FAILED.equals(value)
                || AgentErrorCode.OUTPUT_PARSE_FAILED.equals(value)
                || AgentErrorCode.OUTPUT_VALIDATE_FAILED.equals(value)
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
        if (StringUtils.hasText(rawMessage) && !rawMessage.trim().startsWith("AGENT_")) {
            String value = rawMessage.trim();
            if (!value.equalsIgnoreCase("AI call failed") && !value.equalsIgnoreCase("JSON serialize failed")) {
                return value;
            }
        }
        return "AI 生成暂时失败，请稍后重试。";
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
