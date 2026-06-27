package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.AgentReminderCandidateVO;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.service.AgentReminderService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentReminderServiceImpl implements AgentReminderService {

    private static final String REMINDER_TYPE = "AGENT_REMINDER";
    private static final String BIZ_AGENT_TASK = "AGENT_TASK";
    private static final String BIZ_AGENT_RUN = "AGENT_RUN";
    private static final String BIZ_AGENT_TODAY = "AGENT_TODAY";
    private static final String BIZ_AGENT_DASHBOARD = "AGENT_DASHBOARD";

    private final AgentRunMapper agentRunMapper;
    private final AgentTaskMapper agentTaskMapper;

    @Override
    public List<AgentReminderCandidateVO> listCandidates(Long userId, LocalDate planDate) {
        if (userId == null) {
            return List.of();
        }
        LocalDate targetDate = planDate == null ? LocalDate.now().minusDays(1) : planDate;
        AgentRun run = latestRun(userId, targetDate);
        if (run == null) {
            return List.of();
        }
        List<AgentTask> tasks = yesterdayTasks(userId, targetDate, run.getId());
        List<AgentReminderCandidateVO> candidates = new ArrayList<>();

        AgentTask unfinishedTask = tasks.stream()
                .filter(task -> AgentTaskStatusEnum.TODO.name().equals(task.getStatus())
                        || AgentTaskStatusEnum.DOING.name().equals(task.getStatus()))
                .min(Comparator.comparing(AgentTask::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AgentTask::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
        if (unfinishedTask != null) {
            candidates.add(buildContinueCandidate(targetDate, run, unfinishedTask));
        }

        long doneCount = tasks.stream()
                .filter(task -> AgentTaskStatusEnum.DONE.name().equals(task.getStatus()))
                .count();
        if (doneCount > 0) {
            candidates.add(buildNextStepCandidate(targetDate, run, doneCount));
        }
        return candidates;
    }

    private AgentRun latestRun(Long userId, LocalDate planDate) {
        return agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getPlanDate, planDate)
                .orderByDesc(AgentRun::getId)
                .last("LIMIT 1"));
    }

    private List<AgentTask> yesterdayTasks(Long userId, LocalDate planDate, Long runId) {
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDueDate, planDate)
                .eq(runId != null, AgentTask::getAgentRunId, runId)
                .orderByAsc(AgentTask::getSortOrder)
                .orderByAsc(AgentTask::getId));
    }

    private AgentReminderCandidateVO buildContinueCandidate(LocalDate planDate, AgentRun run, AgentTask task) {
        String taskTitle = StringUtils.hasText(task.getTitle()) ? task.getTitle() : "未完成任务";
        String taskId = task.getId() == null ? "" : String.valueOf(task.getId());
        String actionUrl = task.getId() == null
                ? "/agent/today"
                : "/agent/tasks?bizType=agent.daily-plan.generate&bizId=" + taskId;
        String content = planDate + " 的训练里还有“" + taskTitle + "”未完成，今天可以继续这个计划。";
        return buildCandidate(planDate,
                task.getId() == null ? BIZ_AGENT_TODAY : BIZ_AGENT_TASK,
                task.getId() == null ? planDate.toString() : taskId,
                "继续昨天的训练计划",
                content,
                actionUrl,
                "/agent/today",
                "回到今日计划继续训练");
    }

    private AgentReminderCandidateVO buildNextStepCandidate(LocalDate planDate, AgentRun run, long doneCount) {
        boolean hasRunDetail = run.getId() != null && AgentRunStatusEnum.SUCCESS.name().equals(run.getStatus());
        String actionUrl = hasRunDetail ? "/agent/runs/" + run.getId() : "/dashboard";
        String content = planDate + " 的训练已完成 " + doneCount + " 项，今天可以继续下一步训练。";
        return buildCandidate(planDate,
                hasRunDetail ? BIZ_AGENT_RUN : BIZ_AGENT_DASHBOARD,
                hasRunDetail ? String.valueOf(run.getId()) : planDate.toString(),
                "开始下一步训练",
                content,
                actionUrl,
                hasRunDetail ? "/agent/today" : "/dashboard",
                hasRunDetail ? "回到今日计划继续训练" : "去训练入口面板");
    }

    private AgentReminderCandidateVO buildCandidate(LocalDate planDate, String bizType, String bizId, String title,
                                                    String content, String actionUrl, String fallbackPath,
                                                    String fallbackLabel) {
        AgentReminderCandidateVO candidate = new AgentReminderCandidateVO();
        candidate.setType(REMINDER_TYPE);
        candidate.setBizType(bizType);
        candidate.setBizId(bizId);
        candidate.setTitle(title);
        candidate.setContent(content);
        candidate.setActionUrl(actionUrl);
        candidate.setFallbackPath(fallbackPath);
        candidate.setFallbackLabel(fallbackLabel);
        candidate.setPlanDate(planDate);
        return candidate;
    }
}
