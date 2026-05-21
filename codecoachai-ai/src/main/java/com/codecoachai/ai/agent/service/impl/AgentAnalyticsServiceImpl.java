package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentTaskStatsVO;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAiOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import com.codecoachai.ai.agent.domain.vo.analytics.PersonalAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.TrendPointVO;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.service.AgentAnalyticsService;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentAnalyticsServiceImpl implements AgentAnalyticsService {

    private final AgentRunMapper agentRunMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AiCallLogMapper aiCallLogMapper;

    @Override
    public PersonalAgentOverviewVO personalOverview(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        List<AgentTask> todayTasks = userTasks(userId, today, today);
        List<AgentTask> weekTasks = userTasks(userId, weekStart, today);
        List<AgentRun> runs = userRuns(userId, null, today);

        PersonalAgentOverviewVO vo = new PersonalAgentOverviewVO();
        vo.setTodayTaskCount((long) todayTasks.size());
        vo.setTodayDoneCount(countTasks(todayTasks, AgentTaskStatusEnum.DONE.name()));
        vo.setTodaySkippedCount(countTasks(todayTasks, AgentTaskStatusEnum.SKIPPED.name()));
        vo.setTodayEstimatedMinutes(sumEstimated(todayTasks));
        vo.setLast7DaysTaskCount((long) weekTasks.size());
        vo.setLast7DaysDoneCount(countTasks(weekTasks, AgentTaskStatusEnum.DONE.name()));
        vo.setLast7DaysCompletionRate(rate(vo.getLast7DaysDoneCount(), vo.getLast7DaysTaskCount()));
        vo.setTotalAgentPlanCount((long) runs.size());
        vo.setAgentGeneratedTaskCount((long) userTasks(userId, null, today).size());
        vo.setTaskCompletionRate(rate(countTasks(weekTasks, AgentTaskStatusEnum.DONE.name()), (long) weekTasks.size()));
        vo.setAgentSuccessRate(rate(countRuns(runs, AgentRunStatusEnum.SUCCESS.name()), (long) runs.size()));
        vo.setAvgAgentDurationMs(avgDuration(runs));
        return vo;
    }

    @Override
    public List<TrendPointVO> personalTaskTrend(Long userId, Integer days) {
        int range = normalizeDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(range - 1L);
        List<AgentTask> tasks = userTasks(userId, start, end);
        return buildTaskTrend(tasks, start, end);
    }

    @Override
    public List<TrendPointVO> personalInterviewTrend(Long userId, Integer days) {
        int range = normalizeDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(range - 1L);
        List<AgentTask> tasks = userTasks(userId, start, end).stream()
                .filter(task -> AgentTaskTypeEnum.INTERVIEW.name().equals(task.getTaskType()))
                .toList();
        return buildTaskTrend(tasks, start, end);
    }

    private List<TrendPointVO> buildTaskTrend(List<AgentTask> tasks, LocalDate start, LocalDate end) {
        Map<LocalDate, List<AgentTask>> byDate = tasks.stream()
                .collect(Collectors.groupingBy(AgentTask::getDueDate));
        List<TrendPointVO> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<AgentTask> daily = byDate.getOrDefault(date, List.of());
            TrendPointVO point = new TrendPointVO();
            point.setDate(date);
            point.setGeneratedCount((long) daily.size());
            point.setCompletedCount(countTasks(daily, AgentTaskStatusEnum.DONE.name()));
            point.setSkippedCount(countTasks(daily, AgentTaskStatusEnum.SKIPPED.name()));
            point.setEstimatedMinutes(sumEstimated(daily));
            point.setCompletedMinutes(sumEstimated(daily.stream()
                    .filter(task -> AgentTaskStatusEnum.DONE.name().equals(task.getStatus()))
                    .toList()));
            result.add(point);
        }
        return result;
    }

    @Override
    public List<MetricPointVO> personalSkillDistribution(Long userId, Integer days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(normalizeDays(days) - 1L);
        return topMetrics(userTasks(userId, start, end).stream()
                .map(task -> firstText(task.getRelatedSkillName(), task.getRelatedSkillCode(), "Unclassified"))
                .toList(), 5);
    }

    @Override
    public AdminAgentOverviewVO adminAgentOverview(Integer days) {
        LocalDateTime startTime = LocalDate.now().minusDays(normalizeDays(days) - 1L).atStartOfDay();
        List<AgentRun> runs = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .ge(AgentRun::getCreatedAt, startTime));
        List<AgentTask> tasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .ge(AgentTask::getCreatedAt, startTime));
        AdminAgentOverviewVO vo = new AdminAgentOverviewVO();
        vo.setTotalAgentRuns((long) runs.size());
        vo.setSuccessAgentRuns(countRuns(runs, AgentRunStatusEnum.SUCCESS.name()));
        vo.setFailedAgentRuns(countRuns(runs, AgentRunStatusEnum.FAILED.name()));
        vo.setAgentSuccessRate(rate(vo.getSuccessAgentRuns(), vo.getTotalAgentRuns()));
        vo.setAvgDurationMs(avgDuration(runs));
        vo.setTotalAgentTasks((long) tasks.size());
        vo.setDoneTaskCount(countTasks(tasks, AgentTaskStatusEnum.DONE.name()));
        vo.setSkippedTaskCount(countTasks(tasks, AgentTaskStatusEnum.SKIPPED.name()));
        vo.setTaskCompletionRate(rate(vo.getDoneTaskCount(), vo.getTotalAgentTasks()));
        return vo;
    }

    @Override
    public List<TrendPointVO> adminAgentTrend(Integer days) {
        int range = normalizeDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(range - 1L);
        List<AgentRun> runs = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .ge(AgentRun::getPlanDate, start)
                .le(AgentRun::getPlanDate, end));
        Map<LocalDate, List<AgentRun>> byDate = runs.stream()
                .filter(run -> run.getPlanDate() != null)
                .collect(Collectors.groupingBy(AgentRun::getPlanDate));
        List<TrendPointVO> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<AgentRun> daily = byDate.getOrDefault(date, List.of());
            TrendPointVO point = new TrendPointVO();
            point.setDate(date);
            point.setRunCount((long) daily.size());
            point.setSuccessRunCount(countRuns(daily, AgentRunStatusEnum.SUCCESS.name()));
            point.setFailedRunCount(countRuns(daily, AgentRunStatusEnum.FAILED.name()));
            result.add(point);
        }
        return result;
    }

    @Override
    public AdminAgentTaskStatsVO adminAgentTasks(Integer days) {
        LocalDateTime startTime = LocalDate.now().minusDays(normalizeDays(days) - 1L).atStartOfDay();
        List<AgentTask> tasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .ge(AgentTask::getCreatedAt, startTime));
        AdminAgentTaskStatsVO vo = new AdminAgentTaskStatsVO();
        vo.setTotalAgentTasks((long) tasks.size());
        vo.setDoneTaskCount(countTasks(tasks, AgentTaskStatusEnum.DONE.name()));
        vo.setSkippedTaskCount(countTasks(tasks, AgentTaskStatusEnum.SKIPPED.name()));
        vo.setTaskCompletionRate(rate(vo.getDoneTaskCount(), vo.getTotalAgentTasks()));
        vo.setTaskTypeDistribution(groupTasks(tasks, AgentTask::getTaskType));
        vo.setPriorityDistribution(groupTasks(tasks, AgentTask::getPriority));
        return vo;
    }

    @Override
    public AdminAiOverviewVO adminAiOverview(Integer days) {
        LocalDateTime startTime = LocalDate.now().minusDays(normalizeDays(days) - 1L).atStartOfDay();
        List<AiCallLog> logs = aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreatedAt, startTime));
        long success = logs.stream().filter(log -> Integer.valueOf(1).equals(log.getSuccess())).count();
        AdminAiOverviewVO vo = new AdminAiOverviewVO();
        vo.setTotalAiCalls((long) logs.size());
        vo.setSuccessAiCalls(success);
        vo.setFailedAiCalls((long) logs.size() - success);
        vo.setAiSuccessRate(rate(success, (long) logs.size()));
        vo.setAvgElapsedMs(avg(logs.stream().map(AiCallLog::getElapsedMs).filter(Objects::nonNull).toList()));
        vo.setTotalInputTokens(sum(logs.stream().map(AiCallLog::getPromptTokens).filter(Objects::nonNull).toList()));
        vo.setTotalOutputTokens(sum(logs.stream().map(AiCallLog::getCompletionTokens).filter(Objects::nonNull).toList()));
        vo.setTotalTokens(sum(logs.stream().map(AiCallLog::getTotalTokens).filter(Objects::nonNull).toList()));
        return vo;
    }

    @Override
    public List<MetricPointVO> adminAiFailures(Integer days) {
        LocalDateTime startTime = LocalDate.now().minusDays(normalizeDays(days) - 1L).atStartOfDay();
        List<String> failures = aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                        .ge(AiCallLog::getCreatedAt, startTime)
                        .eq(AiCallLog::getSuccess, 0))
                .stream()
                .map(log -> firstText(log.getErrorMessage(), "UNKNOWN"))
                .map(message -> message.length() > 80 ? message.substring(0, 80) : message)
                .toList();
        return topMetrics(failures, 10);
    }

    private List<AgentTask> userTasks(Long userId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId);
        if (start != null) {
            wrapper.ge(AgentTask::getDueDate, start);
        }
        if (end != null) {
            wrapper.le(AgentTask::getDueDate, end);
        }
        return agentTaskMapper.selectList(wrapper);
    }

    private List<AgentRun> userRuns(Long userId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId);
        if (start != null) {
            wrapper.ge(AgentRun::getPlanDate, start);
        }
        if (end != null) {
            wrapper.le(AgentRun::getPlanDate, end);
        }
        return agentRunMapper.selectList(wrapper);
    }

    private Long countTasks(List<AgentTask> tasks, String status) {
        return tasks.stream().filter(task -> status.equals(task.getStatus())).count();
    }

    private Long countRuns(List<AgentRun> runs, String status) {
        return runs.stream().filter(run -> status.equals(run.getStatus())).count();
    }

    private Long sumEstimated(List<AgentTask> tasks) {
        return sum(tasks.stream().map(AgentTask::getEstimatedMinutes).filter(Objects::nonNull).toList());
    }

    private Long sum(List<? extends Number> values) {
        return values.stream().mapToLong(Number::longValue).sum();
    }

    private Long avgDuration(List<AgentRun> runs) {
        return avg(runs.stream().map(AgentRun::getDurationMs).filter(Objects::nonNull).toList());
    }

    private Long avg(List<? extends Number> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        return Math.round(values.stream().mapToLong(Number::longValue).average().orElse(0));
    }

    private Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round(numerator * 10000D / denominator) / 100D;
    }

    private List<MetricPointVO> groupTasks(List<AgentTask> tasks, Function<AgentTask, String> classifier) {
        return topMetrics(tasks.stream().map(classifier).map(value -> firstText(value, "UNKNOWN")).toList(), 20);
    }

    private List<MetricPointVO> topMetrics(List<String> values, int limit) {
        Map<String, Long> countMap = values.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> {
                    MetricPointVO vo = new MetricPointVO();
                    vo.setName(entry.getKey());
                    vo.setValue(entry.getValue());
                    return vo;
                })
                .toList();
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return 7;
        }
        return Math.min(days, 90);
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
}
