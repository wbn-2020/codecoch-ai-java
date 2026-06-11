package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AgentMemoryQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewGenerateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentMemory;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.entity.SkillGrowthSnapshot;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import com.codecoachai.ai.agent.domain.vo.growth.GrowthOverviewVO;
import com.codecoachai.ai.agent.domain.vo.growth.ReadinessScoreRecordVO;
import com.codecoachai.ai.agent.domain.vo.growth.SkillGrowthSnapshotVO;
import com.codecoachai.ai.agent.domain.vo.memory.AgentMemoryVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewVO;
import com.codecoachai.ai.agent.mapper.AgentMemoryMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.ai.agent.mapper.SkillGrowthSnapshotMapper;
import com.codecoachai.ai.agent.service.AgentGrowthService;
import com.codecoachai.common.core.domain.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
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
public class AgentGrowthServiceImpl implements AgentGrowthService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AgentTaskMapper agentTaskMapper;
    private final AgentRunMapper agentRunMapper;
    private final AgentReviewMapper agentReviewMapper;
    private final SkillGrowthSnapshotMapper skillGrowthSnapshotMapper;
    private final ReadinessScoreRecordMapper readinessScoreRecordMapper;
    private final AgentMemoryMapper agentMemoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AgentReviewVO generateReview(Long userId, AgentReviewGenerateDTO dto) {
        LocalDate date = dto != null && dto.getDate() != null ? dto.getDate() : LocalDate.now();
        Long targetJobId = dto == null ? null : dto.getTargetJobId();
        List<AgentTask> tasks = agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDueDate, date)
                .eq(targetJobId != null, AgentTask::getTargetJobId, targetJobId));

        long done = countTasks(tasks, AgentTaskStatusEnum.DONE.name());
        long skipped = countTasks(tasks, AgentTaskStatusEnum.SKIPPED.name());
        long todo = tasks.size() - done - skipped;
        BigDecimal completionRate = rate(done, tasks.size());
        BigDecimal agentSuccessRate = agentSuccessRate(userId, date.minusDays(6), date);
        int readinessScore = readinessScore(completionRate, agentSuccessRate, tasks.size());
        List<String> nextActions = nextActions(tasks, done, skipped, todo);

        AgentRun latestRun = latestRun(userId, targetJobId, date);
        AgentReview review = new AgentReview();
        review.setUserId(userId);
        review.setTargetJobId(targetJobId);
        review.setReviewDate(date);
        review.setDoneCount((int) done);
        review.setSkippedCount((int) skipped);
        review.setTodoCount((int) todo);
        review.setCompletionRate(completionRate);
        review.setReadinessScore(readinessScore);
        review.setSummary(summary(tasks.size(), done, skipped, todo, readinessScore));
        review.setNextActionsJson(writeJson(nextActions));
        review.setReviewJson(writeJson(reviewPayload(date, tasks, completionRate, agentSuccessRate)));
        review.setAgentRunId(latestRun == null ? null : latestRun.getId());
        agentReviewMapper.insert(review);

        saveReadiness(userId, targetJobId, date, readinessScore, completionRate, agentSuccessRate, review.getReviewJson());
        saveSkillSnapshots(userId, date, tasks, review.getId());
        createReviewMemory(userId, review);
        return toReviewVO(review);
    }

    @Override
    public List<AgentReviewVO> listReviews(Long userId, Long targetJobId) {
        return agentReviewMapper.selectList(new LambdaQueryWrapper<AgentReview>()
                        .eq(AgentReview::getUserId, userId)
                        .eq(targetJobId != null, AgentReview::getTargetJobId, targetJobId)
                        .orderByDesc(AgentReview::getReviewDate)
                        .last("LIMIT 30"))
                .stream().map(this::toReviewVO).toList();
    }

    @Override
    public GrowthOverviewVO growthOverview(Long userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);
        List<AgentTask> tasks = tasks(userId, start, end);
        long done = countTasks(tasks, AgentTaskStatusEnum.DONE.name());
        BigDecimal completionRate = rate(done, tasks.size());
        BigDecimal successRate = agentSuccessRate(userId, start, end);
        GrowthOverviewVO vo = new GrowthOverviewVO();
        vo.setTaskCompletionRate(completionRate.doubleValue());
        vo.setAgentSuccessRate(successRate.doubleValue());
        vo.setReadinessScore(readinessScore(completionRate, successRate, tasks.size()));
        vo.setTotalReviewCount(agentReviewMapper.selectCount(new LambdaQueryWrapper<AgentReview>().eq(AgentReview::getUserId, userId)));
        vo.setTotalMemoryCount(agentMemoryMapper.selectCount(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, userId)
                .eq(AgentMemory::getEnabled, 1)));
        vo.setTopSkills(topSkillMetrics(tasks));
        return vo;
    }

    @Override
    public List<SkillGrowthSnapshotVO> skillTrend(Long userId, Integer days) {
        LocalDate start = LocalDate.now().minusDays(normalizeDays(days) - 1L);
        return skillGrowthSnapshotMapper.selectList(new LambdaQueryWrapper<SkillGrowthSnapshot>()
                        .eq(SkillGrowthSnapshot::getUserId, userId)
                        .ge(SkillGrowthSnapshot::getSnapshotDate, start)
                        .orderByAsc(SkillGrowthSnapshot::getSnapshotDate))
                .stream().map(this::toSkillVO).toList();
    }

    @Override
    public List<ReadinessScoreRecordVO> readinessTrend(Long userId, Integer days) {
        LocalDate start = LocalDate.now().minusDays(normalizeDays(days) - 1L);
        return readinessScoreRecordMapper.selectList(new LambdaQueryWrapper<ReadinessScoreRecord>()
                        .eq(ReadinessScoreRecord::getUserId, userId)
                        .ge(ReadinessScoreRecord::getScoreDate, start)
                        .orderByAsc(ReadinessScoreRecord::getScoreDate))
                .stream().map(this::toReadinessVO).toList();
    }

    @Override
    public PageResult<AgentMemoryVO> pageMemories(Long userId, AgentMemoryQueryDTO query) {
        AgentMemoryQueryDTO actual = query == null ? new AgentMemoryQueryDTO() : query;
        long pageNo = pageNo(actual.getPageNo());
        long pageSize = pageSize(actual.getPageSize());
        Page<AgentMemory> page = agentMemoryMapper.selectPage(Page.of(pageNo, pageSize), new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, userId)
                .eq(StringUtils.hasText(actual.getMemoryType()), AgentMemory::getMemoryType, actual.getMemoryType())
                .eq(actual.getEnabled() != null, AgentMemory::getEnabled, actual.getEnabled())
                .orderByDesc(AgentMemory::getUpdatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toMemoryVO).toList(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public AgentMemoryVO createMemory(Long userId, AgentMemoryCreateDTO dto) {
        AgentMemory memory = new AgentMemory();
        memory.setUserId(userId);
        memory.setMemoryType(firstText(dto == null ? null : dto.getMemoryType(), "USER_NOTE"));
        memory.setContent(firstText(dto == null ? null : dto.getContent(), "Manual job-preparation memory."));
        memory.setSourceType(firstText(dto == null ? null : dto.getSourceType(), "MANUAL"));
        memory.setSourceId(dto == null ? null : dto.getSourceId());
        memory.setConfidence(dto == null || dto.getConfidence() == null ? BigDecimal.valueOf(0.9) : dto.getConfidence());
        memory.setEnabled(1);
        agentMemoryMapper.insert(memory);
        return toMemoryVO(memory);
    }

    @Override
    public AgentMemoryVO setMemoryEnabled(Long userId, Long id, boolean enabled) {
        AgentMemory memory = ownedMemory(userId, id);
        memory.setEnabled(enabled ? 1 : 0);
        agentMemoryMapper.updateById(memory);
        return toMemoryVO(agentMemoryMapper.selectById(id));
    }

    @Override
    public void deleteMemory(Long userId, Long id) {
        ownedMemory(userId, id);
        agentMemoryMapper.deleteById(id);
    }

    private Map<String, Object> reviewPayload(LocalDate date, List<AgentTask> tasks, BigDecimal completionRate,
                                              BigDecimal agentSuccessRate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", date.toString());
        payload.put("taskCount", tasks.size());
        payload.put("completionRate", completionRate);
        payload.put("agentSuccessRate", agentSuccessRate);
        payload.put("topSkills", topSkillMetrics(tasks));
        return payload;
    }

    private void saveReadiness(Long userId, Long targetJobId, LocalDate date, int score, BigDecimal completionRate,
                               BigDecimal agentSuccessRate, String evidenceJson) {
        ReadinessScoreRecord record = new ReadinessScoreRecord();
        record.setUserId(userId);
        record.setTargetJobId(targetJobId);
        record.setScoreDate(date);
        record.setScore(score);
        record.setTaskCompletionRate(completionRate);
        record.setAgentSuccessRate(agentSuccessRate);
        record.setEvidenceJson(evidenceJson);
        readinessScoreRecordMapper.insert(record);
    }

    private void saveSkillSnapshots(Long userId, LocalDate date, List<AgentTask> tasks, Long reviewId) {
        Map<String, List<AgentTask>> bySkill = tasks.stream()
                .collect(Collectors.groupingBy(
                        task -> firstText(task.getRelatedSkillName(), task.getRelatedSkillCode(), "Unclassified"),
                        LinkedHashMap::new,
                        Collectors.toList()));
        bySkill.forEach((skill, skillTasks) -> {
            long done = countTasks(skillTasks, AgentTaskStatusEnum.DONE.name());
            SkillGrowthSnapshot snapshot = new SkillGrowthSnapshot();
            snapshot.setUserId(userId);
            snapshot.setSnapshotDate(date);
            snapshot.setSkillName(skill);
            snapshot.setSkillCode(skillTasks.stream().map(AgentTask::getRelatedSkillCode).filter(StringUtils::hasText).findFirst().orElse(null));
            snapshot.setTaskCount(skillTasks.size());
            snapshot.setDoneCount((int) done);
            snapshot.setScore(readinessScore(rate(done, skillTasks.size()), BigDecimal.valueOf(100), skillTasks.size()));
            snapshot.setSourceType("AGENT_REVIEW");
            snapshot.setSourceId(reviewId);
            skillGrowthSnapshotMapper.insert(snapshot);
        });
    }

    private void createReviewMemory(Long userId, AgentReview review) {
        if (review.getReadinessScore() == null || review.getReadinessScore() >= 60) {
            return;
        }
        AgentMemory memory = new AgentMemory();
        memory.setUserId(userId);
        memory.setMemoryType("WEAKNESS");
        memory.setContent("Recent review shows low readiness; future plans should prioritize high-impact tasks.");
        memory.setSourceType("AGENT_REVIEW");
        memory.setSourceId(review.getId());
        memory.setConfidence(BigDecimal.valueOf(0.75));
        memory.setEnabled(1);
        agentMemoryMapper.insert(memory);
    }

    private List<AgentTask> tasks(Long userId, LocalDate start, LocalDate end) {
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .ge(AgentTask::getDueDate, start)
                .le(AgentTask::getDueDate, end));
    }

    private AgentRun latestRun(Long userId, Long targetJobId, LocalDate date) {
        return agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(targetJobId != null, AgentRun::getTargetJobId, targetJobId)
                .eq(AgentRun::getPlanDate, date)
                .orderByDesc(AgentRun::getId)
                .last("LIMIT 1"));
    }

    private BigDecimal agentSuccessRate(Long userId, LocalDate start, LocalDate end) {
        List<AgentRun> runs = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .ge(AgentRun::getPlanDate, start)
                .le(AgentRun::getPlanDate, end));
        long success = runs.stream().filter(run -> AgentRunStatusEnum.SUCCESS.name().equals(run.getStatus())).count();
        return rate(success, runs.size());
    }

    private long countTasks(List<AgentTask> tasks, String status) {
        return tasks.stream().filter(task -> status.equals(task.getStatus())).count();
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private int readinessScore(BigDecimal completionRate, BigDecimal agentSuccessRate, int taskCount) {
        int base = completionRate.multiply(BigDecimal.valueOf(0.7))
                .add(agentSuccessRate.multiply(BigDecimal.valueOf(0.3)))
                .intValue();
        if (taskCount == 0) {
            base = Math.min(base, 50);
        }
        return Math.max(0, Math.min(100, base));
    }

    private List<String> nextActions(List<AgentTask> tasks, long done, long skipped, long todo) {
        List<String> actions = new ArrayList<>();
        if (todo > 0) {
            actions.add("先完成剩余的高优先级今日任务。");
        }
        if (skipped > 0) {
            actions.add("复盘已跳过的任务，必要时减少明天的任务量。");
        }
        if (done == tasks.size() && !tasks.isEmpty()) {
            actions.add("加练一次错题复盘或面试复述。");
        }
        if (actions.isEmpty()) {
            actions.add("先生成今日计划，后续才能复盘准备进度。");
        }
        return actions;
    }

    private String summary(int total, long done, long skipped, long todo, int score) {
        return "Agent review: total=" + total + ", done=" + done + ", skipped=" + skipped
                + ", todo=" + todo + ", readinessScore=" + score + ".";
    }

    private List<MetricPointVO> topSkillMetrics(List<AgentTask> tasks) {
        return tasks.stream()
                .map(task -> firstText(task.getRelatedSkillName(), task.getRelatedSkillCode(), "Unclassified"))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .limit(8)
                .map(entry -> {
                    MetricPointVO vo = new MetricPointVO();
                    vo.setName(entry.getKey());
                    vo.setValue(entry.getValue());
                    return vo;
                }).toList();
    }

    private AgentMemory ownedMemory(Long userId, Long id) {
        AgentMemory memory = agentMemoryMapper.selectById(id);
        if (memory == null || !Objects.equals(userId, memory.getUserId())) {
            throw new IllegalArgumentException("记忆记录不存在或无权访问");
        }
        return memory;
    }

    private AgentReviewVO toReviewVO(AgentReview review) {
        AgentReviewVO vo = new AgentReviewVO();
        vo.setId(review.getId());
        vo.setUserId(review.getUserId());
        vo.setTargetJobId(review.getTargetJobId());
        vo.setReviewDate(review.getReviewDate());
        vo.setSummary(review.getSummary());
        vo.setDoneCount(review.getDoneCount());
        vo.setSkippedCount(review.getSkippedCount());
        vo.setTodoCount(review.getTodoCount());
        vo.setCompletionRate(review.getCompletionRate());
        vo.setReadinessScore(review.getReadinessScore());
        vo.setNextActions(readStringList(review.getNextActionsJson()));
        vo.setAgentRunId(review.getAgentRunId());
        vo.setAiCallLogId(review.getAiCallLogId());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }

    private SkillGrowthSnapshotVO toSkillVO(SkillGrowthSnapshot snapshot) {
        SkillGrowthSnapshotVO vo = new SkillGrowthSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setSnapshotDate(snapshot.getSnapshotDate());
        vo.setSkillCode(snapshot.getSkillCode());
        vo.setSkillName(snapshot.getSkillName());
        vo.setScore(snapshot.getScore());
        vo.setTaskCount(snapshot.getTaskCount());
        vo.setDoneCount(snapshot.getDoneCount());
        return vo;
    }

    private ReadinessScoreRecordVO toReadinessVO(ReadinessScoreRecord record) {
        ReadinessScoreRecordVO vo = new ReadinessScoreRecordVO();
        vo.setId(record.getId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setScoreDate(record.getScoreDate());
        vo.setScore(record.getScore());
        vo.setTaskCompletionRate(record.getTaskCompletionRate());
        vo.setAgentSuccessRate(record.getAgentSuccessRate());
        return vo;
    }

    private AgentMemoryVO toMemoryVO(AgentMemory memory) {
        AgentMemoryVO vo = new AgentMemoryVO();
        vo.setId(memory.getId());
        vo.setMemoryType(memory.getMemoryType());
        vo.setContent(memory.getContent());
        vo.setSourceType(memory.getSourceType());
        vo.setSourceId(memory.getSourceId());
        vo.setConfidence(memory.getConfidence());
        vo.setEnabled(memory.getEnabled());
        vo.setCreatedAt(memory.getCreatedAt());
        vo.setUpdatedAt(memory.getUpdatedAt());
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return 30;
        }
        return Math.min(days, 90);
    }

    private long pageNo(Long value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long pageSize(Long value) {
        return value == null || value < 1 ? 10 : Math.min(value, 100);
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
