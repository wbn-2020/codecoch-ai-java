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
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentGrowthServiceImpl implements AgentGrowthService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final int GROWTH_WINDOW_DAYS = 30;
    private static final int MIN_TRUSTED_TASK_COUNT = 3;
    private static final int MIN_TRUSTED_DONE_TASK_COUNT = 2;
    private static final BigDecimal MIN_STRONG_MEMORY_CONFIDENCE = BigDecimal.valueOf(0.6);
    private static final String DEFAULT_GROWTH_TIME_WINDOW = "最近30天";
    private static final String INCLUDED_TASK_SOURCE_LABEL = "当前纳入：任务完成记录";
    private static final String EXCLUDED_GROWTH_SOURCE_LABEL =
            "当前未纳入：AI 教练运行记录、复盘记录、成长记忆、反馈信号、提醒信号";
    private static final int MAX_MANUAL_MEMORY_CONTENT_LENGTH = 2_000;
    private static final Set<String> ALLOWED_MEMORY_TYPES = Set.of(
            "USER_NOTE",
            "SKILL_GAP",
            "CAREER_GOAL",
            "INTERVIEW_PREFERENCE",
            "JOB_SEARCH_PREFERENCE",
            "REVIEW_SUMMARY"
    );
    private static final Set<String> ALLOWED_MEMORY_SOURCE_TYPES = Set.of(
            "MANUAL",
            "AGENT_REVIEW",
            "AGENT_FEEDBACK",
            "JOB_EXPERIMENT",
            "RESUME_JOB_MATCH"
    );

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
        LocalDate start = end.minusDays(GROWTH_WINDOW_DAYS - 1L);
        List<AgentTask> tasks = tasks(userId, start, end);
        long done = countTasks(tasks, AgentTaskStatusEnum.DONE.name());
        BigDecimal completionRate = rate(done, tasks.size());
        List<AgentRun> runs = agentRuns(userId, start, end);
        BigDecimal successRate = successRate(runs);
        long totalReviewCount = agentReviewMapper.selectCount(
                new LambdaQueryWrapper<AgentReview>().eq(AgentReview::getUserId, userId));
        long totalMemoryCount = agentMemoryMapper.selectCount(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getUserId, userId)
                .eq(AgentMemory::getEnabled, 1));
        GrowthEvidencePolicy policy = buildEvidencePolicy(tasks.size(), done);

        GrowthOverviewVO vo = new GrowthOverviewVO();
        vo.setTotalReviewCount(totalReviewCount);
        vo.setTotalMemoryCount(totalMemoryCount);
        vo.setTopSkills(topSkillMetrics(tasks));
        vo.setConfidenceLevel(policy.getConfidenceLevel());
        vo.setEvidenceCount(policy.getEvidenceCount());
        vo.setTimeWindow(DEFAULT_GROWTH_TIME_WINDOW);
        vo.setDataSourceLabels(policy.getDataSourceLabels());
        if (policy.isTrusted()) {
            vo.setTaskCompletionRate(completionRate.doubleValue());
            vo.setAgentSuccessRate(successRate.doubleValue());
            vo.setReadinessScore(readinessScore(completionRate, successRate, tasks.size()));
            vo.setDisplayPolicy(GrowthOverviewVO.DisplayPolicy.trusted(true, !vo.getTopSkills().isEmpty()));
            vo.setNextEvidenceActions(List.of());
        } else {
            vo.setColdStartReason(policy.getColdStartReason());
            vo.setNextEvidenceActions(policy.getNextEvidenceActions());
            vo.setDisplayPolicy(GrowthOverviewVO.DisplayPolicy.coldStart());
        }
        return vo;
    }

    @Override
    public List<SkillGrowthSnapshotVO> skillTrend(Long userId, Integer days) {
        int actualDays = normalizeDays(days);
        LocalDate start = LocalDate.now().minusDays(actualDays - 1L);
        String timeWindow = "最近" + actualDays + "天";
        return skillGrowthSnapshotMapper.selectList(new LambdaQueryWrapper<SkillGrowthSnapshot>()
                        .eq(SkillGrowthSnapshot::getUserId, userId)
                        .ge(SkillGrowthSnapshot::getSnapshotDate, start)
                        .orderByAsc(SkillGrowthSnapshot::getSnapshotDate))
                .stream().map(snapshot -> toSkillVO(snapshot, timeWindow)).toList();
    }

    @Override
    public List<ReadinessScoreRecordVO> readinessTrend(Long userId, Integer days) {
        int actualDays = normalizeDays(days);
        LocalDate start = LocalDate.now().minusDays(actualDays - 1L);
        String timeWindow = "最近" + actualDays + "天";
        return readinessScoreRecordMapper.selectList(new LambdaQueryWrapper<ReadinessScoreRecord>()
                        .eq(ReadinessScoreRecord::getUserId, userId)
                        .ge(ReadinessScoreRecord::getScoreDate, start)
                        .orderByAsc(ReadinessScoreRecord::getScoreDate))
                .stream().map(record -> toReadinessVO(record, timeWindow)).toList();
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
        String content = normalizeManualMemoryContent(dto == null ? null : dto.getContent());
        AgentMemory memory = new AgentMemory();
        memory.setUserId(userId);
        memory.setMemoryType(normalizeMemoryCode(dto == null ? null : dto.getMemoryType(), "USER_NOTE", ALLOWED_MEMORY_TYPES));
        memory.setContent(content);
        memory.setSourceType(normalizeMemoryCode(dto == null ? null : dto.getSourceType(), "MANUAL", ALLOWED_MEMORY_SOURCE_TYPES));
        memory.setSourceId(dto == null ? null : dto.getSourceId());
        memory.setConfidence(clampConfidence(dto == null ? null : dto.getConfidence()));
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
    public AgentMemoryVO confirmMemory(Long userId, Long id) {
        AgentMemory memory = ownedMemory(userId, id);
        memory.setEnabled(1);
        memory.setUpdatedAt(LocalDateTime.now());
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
        long doneTaskCount = countTasks(tasks, AgentTaskStatusEnum.DONE.name());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", date.toString());
        payload.put("taskCount", tasks.size());
        payload.put("doneCount", doneTaskCount);
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
            snapshot.setSkillCode(skillTasks.stream()
                    .map(AgentTask::getRelatedSkillCode)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null));
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
        memory.setMemoryType("SKILL_GAP");
        memory.setContent("Candidate memory: recent review shows low readiness; enable after user confirmation if future plans should prioritize high-impact tasks.");
        memory.setSourceType("AGENT_REVIEW");
        memory.setSourceId(review.getId());
        memory.setConfidence(BigDecimal.valueOf(0.75));
        memory.setEnabled(0);
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
        return successRate(agentRuns(userId, start, end));
    }

    private List<AgentRun> agentRuns(Long userId, LocalDate start, LocalDate end) {
        return agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .ge(AgentRun::getPlanDate, start)
                .le(AgentRun::getPlanDate, end));
    }

    private BigDecimal successRate(List<AgentRun> runs) {
        long success = runs.stream().filter(run -> AgentRunStatusEnum.SUCCESS.name().equals(run.getStatus())).count();
        return rate(success, runs.size());
    }

    private GrowthEvidencePolicy buildEvidencePolicy(long taskCount, long doneCount) {
        int normalizedTaskCount = safeInt(taskCount);
        int normalizedDoneCount = safeInt(doneCount);
        boolean enoughTasks = normalizedTaskCount >= MIN_TRUSTED_TASK_COUNT;
        boolean enoughDone = normalizedDoneCount >= MIN_TRUSTED_DONE_TASK_COUNT;
        boolean trusted = enoughTasks && enoughDone;
        return new GrowthEvidencePolicy(
                trusted,
                trusted ? "HIGH" : "LOW",
                normalizedTaskCount,
                List.of(INCLUDED_TASK_SOURCE_LABEL, EXCLUDED_GROWTH_SOURCE_LABEL),
                trusted ? null : unifiedColdStartReason(enoughTasks, enoughDone),
                trusted ? List.of() : unifiedNextEvidenceActions(enoughTasks, enoughDone));
    }

    private GrowthEvidencePolicy readinessEvidencePolicy(String evidenceJson) {
        if (!StringUtils.hasText(evidenceJson)) {
            return buildEvidencePolicy(0, 0);
        }
        try {
            JsonNode root = objectMapper.readTree(evidenceJson);
            int taskCount = positiveInt(root.path("taskCount").isNumber() ? root.path("taskCount").asInt() : null);
            int doneCount = positiveInt(root.path("doneCount").isNumber() ? root.path("doneCount").asInt() : null);
            return buildEvidencePolicy(taskCount, doneCount);
        } catch (Exception ignored) {
            return buildEvidencePolicy(0, 0);
        }
    }

    private String unifiedColdStartReason(boolean enoughTasks, boolean enoughDone) {
        List<String> reasons = new ArrayList<>();
        if (!enoughTasks) {
            reasons.add("至少需要 " + MIN_TRUSTED_TASK_COUNT + " 条任务记录");
        }
        if (!enoughDone) {
            reasons.add("至少需要 " + MIN_TRUSTED_DONE_TASK_COUNT + " 条已完成任务");
        }
        return "Growth 仅在至少 " + MIN_TRUSTED_TASK_COUNT + " 条任务记录且 "
                + MIN_TRUSTED_DONE_TASK_COUNT + " 条已完成任务时展示强结论。当前"
                + String.join("，", reasons) + "。";
    }

    private List<String> unifiedNextEvidenceActions(boolean enoughTasks, boolean enoughDone) {
        List<String> actions = new ArrayList<>();
        if (!enoughTasks) {
            actions.add("至少补齐到 " + MIN_TRUSTED_TASK_COUNT + " 条任务记录");
        }
        if (!enoughDone) {
            actions.add("至少完成 " + MIN_TRUSTED_DONE_TASK_COUNT + " 条任务记录");
        }
        actions.add("完成更多带技能标签的任务后再刷新 Growth 趋势");
        return actions.stream().distinct().toList();
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

    private SkillGrowthSnapshotVO toSkillVO(SkillGrowthSnapshot snapshot, String timeWindow) {
        SkillGrowthSnapshotVO vo = new SkillGrowthSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setSnapshotDate(snapshot.getSnapshotDate());
        vo.setSkillCode(snapshot.getSkillCode());
        vo.setSkillName(snapshot.getSkillName());
        vo.setScore(snapshot.getScore());
        vo.setTaskCount(snapshot.getTaskCount());
        vo.setDoneCount(snapshot.getDoneCount());
        GrowthEvidencePolicy policy = buildEvidencePolicy(positiveInt(snapshot.getTaskCount()), positiveInt(snapshot.getDoneCount()));
        vo.setEvidenceCount(policy.getEvidenceCount());
        vo.setConfidenceLevel(policy.getConfidenceLevel());
        vo.setTimeWindow(timeWindow);
        vo.setDataSourceLabels(policy.getDataSourceLabels());
        if (!policy.isTrusted()) {
            vo.setColdStartReason(policy.getColdStartReason());
            vo.setNextEvidenceActions(policy.getNextEvidenceActions());
        }
        return vo;
    }

    private ReadinessScoreRecordVO toReadinessVO(ReadinessScoreRecord record, String timeWindow) {
        ReadinessScoreRecordVO vo = new ReadinessScoreRecordVO();
        vo.setId(record.getId());
        vo.setTargetJobId(record.getTargetJobId());
        vo.setScoreDate(record.getScoreDate());
        vo.setScore(record.getScore());
        vo.setTaskCompletionRate(record.getTaskCompletionRate());
        vo.setAgentSuccessRate(record.getAgentSuccessRate());
        GrowthEvidencePolicy policy = readinessEvidencePolicy(record.getEvidenceJson());
        vo.setEvidenceCount(policy.getEvidenceCount());
        vo.setConfidenceLevel(policy.getConfidenceLevel());
        vo.setTimeWindow(timeWindow);
        vo.setDataSourceLabels(policy.getDataSourceLabels());
        if (!policy.isTrusted()) {
            vo.setColdStartReason(policy.getColdStartReason());
            vo.setNextEvidenceActions(policy.getNextEvidenceActions());
        }
        return vo;
    }

    private int positiveInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int safeInt(long value) {
        return Math.toIntExact(Math.max(0L, Math.min(Integer.MAX_VALUE, value)));
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
        attachMemoryGovernance(vo, memory);
        return vo;
    }

    private void attachMemoryGovernance(AgentMemoryVO vo, AgentMemory memory) {
        boolean enabled = memory.getEnabled() != null && memory.getEnabled() == 1;
        boolean lowConfidence = memory.getConfidence() == null
                || memory.getConfidence().compareTo(MIN_STRONG_MEMORY_CONFIDENCE) < 0;
        boolean manual = isManualMemorySource(memory.getSourceType());
        boolean confirmed = manual || isConfirmedCompatible(memory);
        boolean candidate = isCandidateMemorySource(memory.getSourceType()) && !confirmed;
        vo.setLowConfidence(lowConfidence);
        vo.setImpactPreview(List.of(
                "AGENT_TASK",
                "JOB_EXPERIMENT_REVIEW",
                "QUESTION_RECOMMENDATION",
                "INTERVIEW_TRAINING",
                "RESUME_PROJECT_SUGGESTION"));
        if (candidate) {
            vo.setMemoryStatus("CANDIDATE");
            vo.setEvidenceTrustStatus("CANDIDATE");
            vo.setCanBeEvidence(false);
            vo.setDisabledReason("WAITING_USER_CONFIRMATION");
            vo.setConfirmedAt(null);
            return;
        }
        if (!enabled) {
            vo.setMemoryStatus("DISABLED");
            vo.setEvidenceTrustStatus("DISABLED");
            vo.setCanBeEvidence(false);
            vo.setDisabledReason("DISABLED_BY_USER");
            vo.setConfirmedAt(null);
            return;
        }
        vo.setConfirmedAt(firstTime(memory.getUpdatedAt(), memory.getCreatedAt(), LocalDateTime.now()));
        if (lowConfidence) {
            vo.setMemoryStatus("LOW_CONFIDENCE");
            vo.setEvidenceTrustStatus("PARTIAL");
            vo.setCanBeEvidence(false);
            vo.setDisabledReason("LOW_CONFIDENCE");
            return;
        }
        vo.setMemoryStatus("CONFIRMED");
        vo.setEvidenceTrustStatus("VERIFIED");
        vo.setCanBeEvidence(true);
        vo.setDisabledReason(null);
    }

    private boolean isCandidateMemorySource(String sourceType) {
        String normalized = sourceType == null ? null : sourceType.trim().toUpperCase(Locale.ROOT);
        return Set.of("AGENT_REVIEW", "AGENT_FEEDBACK", "JOB_EXPERIMENT", "RESUME_JOB_MATCH", "AI_SUMMARY", "SYSTEM")
                .contains(normalized);
    }

    private boolean isManualMemorySource(String sourceType) {
        String normalized = sourceType == null ? "MANUAL" : sourceType.trim().toUpperCase(Locale.ROOT);
        return Set.of("MANUAL", "USER_MANUAL", "USER_NOTE").contains(normalized);
    }

    private boolean isConfirmedCompatible(AgentMemory memory) {
        return memory.getEnabled() != null
                && memory.getEnabled() == 1
                && memory.getCreatedAt() != null
                && memory.getUpdatedAt() != null
                && memory.getUpdatedAt().isAfter(memory.getCreatedAt());
    }

    private LocalDateTime firstTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private String normalizeManualMemoryContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Memory content cannot be empty");
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_MANUAL_MEMORY_CONTENT_LENGTH) {
            return normalized.substring(0, MAX_MANUAL_MEMORY_CONTENT_LENGTH);
        }
        return normalized;
    }

    private String normalizeMemoryCode(String value, String fallback, Set<String> allowedValues) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
        return allowedValues.contains(normalized) ? normalized : fallback;
    }

    private BigDecimal clampConfidence(BigDecimal value) {
        BigDecimal confidence = value == null ? BigDecimal.valueOf(0.9) : value;
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (confidence.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return confidence;
    }

    private static final class GrowthEvidencePolicy {
        private final boolean trusted;
        private final String confidenceLevel;
        private final int evidenceCount;
        private final List<String> dataSourceLabels;
        private final String coldStartReason;
        private final List<String> nextEvidenceActions;

        private GrowthEvidencePolicy(boolean trusted, String confidenceLevel, int evidenceCount, List<String> dataSourceLabels,
                                     String coldStartReason, List<String> nextEvidenceActions) {
            this.trusted = trusted;
            this.confidenceLevel = confidenceLevel;
            this.evidenceCount = evidenceCount;
            this.dataSourceLabels = dataSourceLabels;
            this.coldStartReason = coldStartReason;
            this.nextEvidenceActions = nextEvidenceActions;
        }

        private boolean isTrusted() {
            return trusted;
        }

        private String getConfidenceLevel() {
            return confidenceLevel;
        }

        private int getEvidenceCount() {
            return evidenceCount;
        }

        private List<String> getDataSourceLabels() {
            return dataSourceLabels;
        }

        private String getColdStartReason() {
            return coldStartReason;
        }

        private List<String> getNextEvidenceActions() {
            return nextEvidenceActions;
        }
    }
}
