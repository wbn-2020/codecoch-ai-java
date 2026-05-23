package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.entity.AgentFeedback;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AnalyticsJobLog;
import com.codecoachai.ai.agent.domain.entity.AnalyticsMetricDefinition;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeChunk;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.entity.PromptRegressionCase;
import com.codecoachai.ai.agent.domain.entity.PromptRegressionResult;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO.FeedbackTypeCount;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsMetricDefinitionVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.ai.agent.mapper.AgentFeedbackMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.AnalyticsJobLogMapper;
import com.codecoachai.ai.agent.mapper.AnalyticsMetricDefinitionMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeChunkMapper;
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeDocumentMapper;
import com.codecoachai.ai.agent.mapper.PromptRegressionCaseMapper;
import com.codecoachai.ai.agent.mapper.PromptRegressionResultMapper;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentV4OpsServiceImpl implements AgentV4OpsService {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 80;

    private final AgentFeedbackMapper agentFeedbackMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentRunMapper agentRunMapper;
    private final PersonalKnowledgeDocumentMapper personalKnowledgeDocumentMapper;
    private final PersonalKnowledgeChunkMapper personalKnowledgeChunkMapper;
    private final AnalyticsMetricDefinitionMapper analyticsMetricDefinitionMapper;
    private final AnalyticsJobLogMapper analyticsJobLogMapper;
    private final PromptRegressionCaseMapper promptRegressionCaseMapper;
    private final PromptRegressionResultMapper promptRegressionResultMapper;
    private final JobCoachAgentService jobCoachAgentService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public AgentFeedbackVO createFeedback(Long userId, AgentFeedbackCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFeedbackType())) {
            throw new IllegalArgumentException("feedbackType is required");
        }
        validateFeedbackOwnership(userId, dto);
        AgentFeedback feedback = new AgentFeedback();
        feedback.setUserId(userId);
        feedback.setAgentTaskId(dto.getAgentTaskId());
        feedback.setAgentRunId(dto.getAgentRunId());
        feedback.setFeedbackType(dto.getFeedbackType().trim().toUpperCase());
        feedback.setComment(dto.getComment());
        agentFeedbackMapper.insert(feedback);
        return toFeedbackVO(feedback);
    }

    @Override
    public PageResult<AgentFeedbackVO> pageFeedback(Long userId, Long taskId, Long runId, String feedbackType,
                                                    Long pageNo, Long pageSize) {
        long actualPageNo = pageNo(pageNo);
        long actualPageSize = pageSize(pageSize);
        Page<AgentFeedback> page = agentFeedbackMapper.selectPage(Page.of(actualPageNo, actualPageSize),
                new LambdaQueryWrapper<AgentFeedback>()
                        .eq(userId != null, AgentFeedback::getUserId, userId)
                        .eq(taskId != null, AgentFeedback::getAgentTaskId, taskId)
                        .eq(runId != null, AgentFeedback::getAgentRunId, runId)
                        .eq(StringUtils.hasText(feedbackType), AgentFeedback::getFeedbackType, feedbackType)
                        .orderByDesc(AgentFeedback::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toFeedbackVO).toList(),
                page.getTotal(), actualPageNo, actualPageSize);
    }

    @Override
    public AgentFeedbackStatsVO feedbackStats(Integer days) {
        LocalDateTime start = LocalDateTime.now().minusDays(normalizeDays(days));
        List<AgentFeedback> feedback = agentFeedbackMapper.selectList(new LambdaQueryWrapper<AgentFeedback>()
                .ge(AgentFeedback::getCreatedAt, start));
        Map<String, Long> byType = feedback.stream()
                .map(item -> firstText(item.getFeedbackType(), "UNKNOWN"))
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        AgentFeedbackStatsVO vo = new AgentFeedbackStatsVO();
        vo.setTotalFeedbackCount((long) feedback.size());
        vo.setAdoptedCount(byType.getOrDefault("ADOPTED", 0L));
        vo.setIgnoredCount(byType.getOrDefault("IGNORED", 0L));
        vo.setLikedCount(byType.getOrDefault("LIKE", 0L) + byType.getOrDefault("LIKED", 0L));
        vo.setDislikedCount(byType.getOrDefault("DISLIKE", 0L) + byType.getOrDefault("DISLIKED", 0L));
        vo.setAdoptionRate(rate(vo.getAdoptedCount(), vo.getAdoptedCount() + vo.getIgnoredCount()));
        vo.setTypeDistribution(byType.entrySet().stream().map(entry -> {
            FeedbackTypeCount count = new FeedbackTypeCount();
            count.setFeedbackType(entry.getKey());
            count.setCount(entry.getValue());
            return count;
        }).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO createKnowledgeDocument(Long userId, KnowledgeDocumentCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTitle()) || !StringUtils.hasText(dto.getContent())) {
            throw new IllegalArgumentException("title and content are required");
        }
        PersonalKnowledgeDocument document = new PersonalKnowledgeDocument();
        document.setUserId(userId);
        document.setTitle(dto.getTitle().trim());
        document.setDocumentType(firstText(dto.getDocumentType(), "NOTE"));
        document.setContent(dto.getContent());
        document.setStatus("INDEXED");
        personalKnowledgeDocumentMapper.insert(document);

        int index = 0;
        for (String chunkContent : splitChunks(dto.getContent())) {
            PersonalKnowledgeChunk chunk = new PersonalKnowledgeChunk();
            chunk.setUserId(userId);
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkContent);
            chunk.setSourceRef(document.getTitle() + "#" + index);
            personalKnowledgeChunkMapper.insert(chunk);
        }
        return toKnowledgeDocumentVO(document, index, true);
    }

    @Override
    public List<KnowledgeDocumentVO> listKnowledgeDocuments(Long userId) {
        return personalKnowledgeDocumentMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .orderByDesc(PersonalKnowledgeDocument::getUpdatedAt))
                .stream()
                .map(document -> toKnowledgeDocumentVO(document, chunkCount(document.getId()), false))
                .toList();
    }

    @Override
    public KnowledgeDocumentVO getKnowledgeDocument(Long userId, Long id) {
        PersonalKnowledgeDocument document = ownedDocument(userId, id);
        return toKnowledgeDocumentVO(document, chunkCount(document.getId()), true);
    }

    @Override
    public List<KnowledgeSearchResultVO> searchKnowledge(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String value = keyword.trim();
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .and(query -> query.like(PersonalKnowledgeDocument::getTitle, value)
                                .or()
                                .like(PersonalKnowledgeDocument::getContent, value))
                        .last("LIMIT 20"));
        Map<Long, PersonalKnowledgeDocument> docMap = documents.stream()
                .collect(Collectors.toMap(PersonalKnowledgeDocument::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .like(PersonalKnowledgeChunk::getContent, value)
                        .last("LIMIT 30"));
        for (PersonalKnowledgeChunk chunk : chunks) {
            docMap.computeIfAbsent(chunk.getDocumentId(), personalKnowledgeDocumentMapper::selectById);
        }

        List<KnowledgeSearchResultVO> result = new ArrayList<>();
        for (PersonalKnowledgeDocument document : documents) {
            result.add(toKnowledgeSearchVO(document, null, snippet(document.getContent(), value)));
        }
        for (PersonalKnowledgeChunk chunk : chunks) {
            PersonalKnowledgeDocument document = docMap.get(chunk.getDocumentId());
            if (document != null && Objects.equals(document.getUserId(), userId)) {
                result.add(toKnowledgeSearchVO(document, chunk, snippet(chunk.getContent(), value)));
            }
        }
        return result.stream().limit(50).toList();
    }

    @Override
    public List<AnalyticsMetricDefinitionVO> listMetrics(String category, Integer enabled) {
        return analyticsMetricDefinitionMapper.selectList(new LambdaQueryWrapper<AnalyticsMetricDefinition>()
                        .eq(StringUtils.hasText(category), AnalyticsMetricDefinition::getCategory, category)
                        .eq(enabled != null, AnalyticsMetricDefinition::getEnabled, enabled)
                        .orderByAsc(AnalyticsMetricDefinition::getCategory)
                        .orderByAsc(AnalyticsMetricDefinition::getMetricCode))
                .stream().map(this::toMetricVO).toList();
    }

    @Override
    public AnalyticsMetricDefinitionVO saveMetric(AdminAnalyticsMetricSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getMetricCode()) || !StringUtils.hasText(dto.getMetricName())) {
            throw new IllegalArgumentException("metricCode and metricName are required");
        }
        AnalyticsMetricDefinition metric = dto.getId() == null
                ? new AnalyticsMetricDefinition()
                : analyticsMetricDefinitionMapper.selectById(dto.getId());
        if (metric == null) {
            throw new IllegalArgumentException("Metric not found");
        }
        metric.setMetricCode(dto.getMetricCode());
        metric.setMetricName(dto.getMetricName());
        metric.setCategory(firstText(dto.getCategory(), "GENERAL"));
        metric.setDefinition(dto.getDefinition());
        metric.setDataSource(dto.getDataSource());
        metric.setRefreshFrequency(dto.getRefreshFrequency());
        metric.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        if (metric.getId() == null) {
            analyticsMetricDefinitionMapper.insert(metric);
        } else {
            analyticsMetricDefinitionMapper.updateById(metric);
        }
        return toMetricVO(metric);
    }

    @Override
    public PageResult<AnalyticsJobLogVO> pageJobs(String jobCode, String status, Long pageNo, Long pageSize) {
        long actualPageNo = pageNo(pageNo);
        long actualPageSize = pageSize(pageSize);
        Page<AnalyticsJobLog> page = analyticsJobLogMapper.selectPage(Page.of(actualPageNo, actualPageSize),
                new LambdaQueryWrapper<AnalyticsJobLog>()
                        .eq(StringUtils.hasText(jobCode), AnalyticsJobLog::getJobCode, jobCode)
                        .eq(StringUtils.hasText(status), AnalyticsJobLog::getStatus, status)
                        .orderByDesc(AnalyticsJobLog::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toJobVO).toList(), page.getTotal(), actualPageNo, actualPageSize);
    }

    @Override
    public AnalyticsJobLogVO rerunJob(Long jobId) {
        AnalyticsJobLog source = analyticsJobLogMapper.selectById(jobId);
        if (source == null) {
            throw new IllegalArgumentException("Job log not found");
        }
        AnalyticsJobLog log = startJob(source.getJobCode(), source.getJobName(), source.getStatDate());
        finishJob(log, "SUCCESS", "manual rerun recorded", null);
        return toJobVO(log);
    }

    @Override
    public AnalyticsJobLogVO runDailyPlanBatch(AnalyticsJobRunDTO dto) {
        AnalyticsJobRunDTO request = dto == null ? new AnalyticsJobRunDTO() : dto;
        String jobCode = firstText(request.getJobCode(), "AGENT_DAILY_PLAN");
        String jobName = firstText(request.getJobName(), "Agent daily plan batch");
        LocalDate planDate = request.getStatDate() == null ? LocalDate.now() : request.getStatDate();
        AnalyticsJobLog log = startJob(jobCode, jobName, planDate);
        Map<String, Object> output = new LinkedHashMap<>();
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<Long> userIds = resolveDailyPlanUserIds(request);
        for (Long userId : userIds) {
            try {
                DailyPlanGenerateDTO generateDTO = new DailyPlanGenerateDTO();
                generateDTO.setTargetJobId(request.getTargetJobId());
                generateDTO.setDate(planDate);
                generateDTO.setTaskCount(request.getTaskCount());
                generateDTO.setMaxTotalMinutes(request.getMaxTotalMinutes());
                generateDTO.setForceRegenerate(false);
                jobCoachAgentService.generateDailyPlan(userId, generateDTO);
                success++;
            } catch (Exception ex) {
                failed++;
                errors.add("userId=" + userId + ": " + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
            }
        }
        output.put("mode", request.getUserIds() == null || request.getUserIds().isEmpty() ? "ALL_ACTIVE_USER_ROLE" : "SPECIFIED_USERS");
        output.put("planDate", planDate);
        output.put("total", userIds.size());
        output.put("success", success);
        output.put("failed", failed);
        output.put("errors", errors);
        finishJob(log, failed == 0 ? "SUCCESS" : "FAILED", writeJson(output), errors.isEmpty() ? null : String.join("; ", errors));
        return toJobVO(log);
    }

    private List<Long> resolveDailyPlanUserIds(AnalyticsJobRunDTO request) {
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            return request.getUserIds().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT u.id
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
                WHERE u.deleted = 0
                  AND u.status = 1
                  AND r.role_code = 'USER'
                ORDER BY u.id
                """, Long.class);
    }

    @Override
    public List<PromptRegressionCaseVO> listPromptCases(String promptType, Integer enabled) {
        return promptRegressionCaseMapper.selectList(new LambdaQueryWrapper<PromptRegressionCase>()
                        .eq(StringUtils.hasText(promptType), PromptRegressionCase::getPromptType, promptType)
                        .eq(enabled != null, PromptRegressionCase::getEnabled, enabled)
                        .orderByDesc(PromptRegressionCase::getUpdatedAt))
                .stream().map(this::toPromptCaseVO).toList();
    }

    @Override
    public PromptRegressionCaseVO savePromptCase(PromptRegressionCaseSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCaseName()) || !StringUtils.hasText(dto.getPromptType())
                || !StringUtils.hasText(dto.getInputJson())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "caseName, promptType and inputJson are required");
        }
        PromptRegressionCase item = dto.getId() == null ? new PromptRegressionCase() : promptRegressionCaseMapper.selectById(dto.getId());
        if (item == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt regression case not found");
        }
        validateJsonObject(dto.getInputJson(), "inputJson");
        if (StringUtils.hasText(dto.getExpectedSchemaJson())) {
            validateJsonObject(dto.getExpectedSchemaJson(), "expectedSchemaJson");
        }
        item.setCaseName(dto.getCaseName().trim());
        item.setPromptType(dto.getPromptType().trim());
        item.setInputJson(dto.getInputJson().trim());
        item.setExpectedSchemaJson(StringUtils.hasText(dto.getExpectedSchemaJson()) ? dto.getExpectedSchemaJson().trim() : null);
        item.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        if (item.getId() == null) {
            promptRegressionCaseMapper.insert(item);
        } else {
            promptRegressionCaseMapper.updateById(item);
        }
        return toPromptCaseVO(item);
    }

    @Override
    public PromptRegressionResultVO runPromptCase(Long caseId, Long promptVersionId) {
        PromptRegressionCase item = promptRegressionCaseMapper.selectById(caseId);
        if (item == null) {
            throw new IllegalArgumentException("Prompt regression case not found");
        }
        PromptRegressionResult result = new PromptRegressionResult();
        result.setCaseId(caseId);
        result.setPromptVersionId(promptVersionId);
        try {
            JsonNode input = objectMapper.readTree(item.getInputJson());
            JsonNode schema = StringUtils.hasText(item.getExpectedSchemaJson())
                    ? objectMapper.readTree(item.getExpectedSchemaJson()) : objectMapper.createObjectNode();
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("promptType", item.getPromptType());
            output.put("inputValid", input != null && input.isContainerNode());
            output.put("schemaPresent", schema != null && schema.isContainerNode());
            output.put("mode", "STATIC_REGRESSION_CHECK");
            result.setStatus("SUCCESS");
            result.setScore(Boolean.TRUE.equals(output.get("inputValid")) ? 100 : 60);
            result.setOutputJson(writeJson(output));
        } catch (Exception ex) {
            result.setStatus("FAILED");
            result.setScore(0);
            result.setErrorMessage(ex.getMessage());
            result.setOutputJson("{}");
        }
        promptRegressionResultMapper.insert(result);
        return toPromptResultVO(result);
    }

    private void validateFeedbackOwnership(Long userId, AgentFeedbackCreateDTO dto) {
        if (dto.getAgentTaskId() != null) {
            AgentTask task = agentTaskMapper.selectById(dto.getAgentTaskId());
            if (task == null || !Objects.equals(task.getUserId(), userId)) {
                throw new IllegalArgumentException("Agent task not found or forbidden");
            }
            if (dto.getAgentRunId() == null) {
                dto.setAgentRunId(task.getAgentRunId());
            }
        }
        if (dto.getAgentRunId() != null) {
            AgentRun run = agentRunMapper.selectById(dto.getAgentRunId());
            if (run == null || !Objects.equals(run.getUserId(), userId)) {
                throw new IllegalArgumentException("Agent run not found or forbidden");
            }
        }
    }

    @Override
    public List<PromptRegressionResultVO> listPromptResults(Long caseId) {
        return promptRegressionResultMapper.selectList(new LambdaQueryWrapper<PromptRegressionResult>()
                        .eq(caseId != null, PromptRegressionResult::getCaseId, caseId)
                        .orderByDesc(PromptRegressionResult::getCreatedAt)
                        .last("LIMIT 50"))
                .stream().map(this::toPromptResultVO).toList();
    }

    private AnalyticsJobLog startJob(String code, String name, LocalDate statDate) {
        AnalyticsJobLog log = new AnalyticsJobLog();
        log.setJobCode(code);
        log.setJobName(name);
        log.setStatus("RUNNING");
        log.setStatDate(statDate);
        log.setStartedAt(LocalDateTime.now());
        analyticsJobLogMapper.insert(log);
        return log;
    }

    private void finishJob(AnalyticsJobLog log, String status, String outputJson, String errorMessage) {
        log.setStatus(status);
        log.setFinishedAt(LocalDateTime.now());
        log.setDurationMs(java.time.Duration.between(log.getStartedAt(), log.getFinishedAt()).toMillis());
        log.setOutputJson(outputJson);
        log.setErrorMessage(errorMessage);
        analyticsJobLogMapper.updateById(log);
    }

    private PersonalKnowledgeDocument ownedDocument(Long userId, Long id) {
        PersonalKnowledgeDocument document = personalKnowledgeDocumentMapper.selectById(id);
        if (document == null || !Objects.equals(document.getUserId(), userId)) {
            throw new IllegalArgumentException("Knowledge document not found or forbidden");
        }
        return document;
    }

    private int chunkCount(Long documentId) {
        return personalKnowledgeChunkMapper.selectCount(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getDocumentId, documentId)).intValue();
    }

    private List<String> splitChunks(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + CHUNK_SIZE);
            chunks.add(content.substring(start, end));
            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private String snippet(String text, String keyword) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int index = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (index < 0) {
            return text.length() <= 160 ? text : text.substring(0, 160);
        }
        int start = Math.max(0, index - 60);
        int end = Math.min(text.length(), index + keyword.length() + 100);
        return text.substring(start, end);
    }

    private AgentFeedbackVO toFeedbackVO(AgentFeedback feedback) {
        AgentFeedbackVO vo = new AgentFeedbackVO();
        vo.setId(feedback.getId());
        vo.setUserId(feedback.getUserId());
        vo.setAgentTaskId(feedback.getAgentTaskId());
        vo.setAgentRunId(feedback.getAgentRunId());
        vo.setFeedbackType(feedback.getFeedbackType());
        vo.setComment(feedback.getComment());
        vo.setCreatedAt(feedback.getCreatedAt());
        return vo;
    }

    private KnowledgeDocumentVO toKnowledgeDocumentVO(PersonalKnowledgeDocument document, int chunkCount, boolean includeContent) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setDocumentType(document.getDocumentType());
        vo.setStatus(document.getStatus());
        vo.setChunkCount(chunkCount);
        vo.setContent(includeContent ? document.getContent() : null);
        vo.setCreatedAt(document.getCreatedAt());
        vo.setUpdatedAt(document.getUpdatedAt());
        return vo;
    }

    private KnowledgeSearchResultVO toKnowledgeSearchVO(PersonalKnowledgeDocument document, PersonalKnowledgeChunk chunk, String snippet) {
        KnowledgeSearchResultVO vo = new KnowledgeSearchResultVO();
        vo.setDocumentId(document.getId());
        vo.setChunkId(chunk == null ? null : chunk.getId());
        vo.setTitle(document.getTitle());
        vo.setDocumentType(document.getDocumentType());
        vo.setSnippet(snippet);
        vo.setSourceRef(chunk == null ? document.getTitle() : chunk.getSourceRef());
        return vo;
    }

    private AnalyticsMetricDefinitionVO toMetricVO(AnalyticsMetricDefinition metric) {
        AnalyticsMetricDefinitionVO vo = new AnalyticsMetricDefinitionVO();
        vo.setId(metric.getId());
        vo.setMetricCode(metric.getMetricCode());
        vo.setMetricName(metric.getMetricName());
        vo.setCategory(metric.getCategory());
        vo.setDefinition(metric.getDefinition());
        vo.setDataSource(metric.getDataSource());
        vo.setRefreshFrequency(metric.getRefreshFrequency());
        vo.setEnabled(metric.getEnabled());
        vo.setCreatedAt(metric.getCreatedAt());
        vo.setUpdatedAt(metric.getUpdatedAt());
        return vo;
    }

    private AnalyticsJobLogVO toJobVO(AnalyticsJobLog log) {
        AnalyticsJobLogVO vo = new AnalyticsJobLogVO();
        vo.setId(log.getId());
        vo.setJobCode(log.getJobCode());
        vo.setJobName(log.getJobName());
        vo.setStatus(log.getStatus());
        vo.setStatDate(log.getStatDate());
        vo.setStartedAt(log.getStartedAt());
        vo.setFinishedAt(log.getFinishedAt());
        vo.setDurationMs(log.getDurationMs());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setOutputJson(log.getOutputJson());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private PromptRegressionCaseVO toPromptCaseVO(PromptRegressionCase item) {
        PromptRegressionCaseVO vo = new PromptRegressionCaseVO();
        vo.setId(item.getId());
        vo.setCaseName(item.getCaseName());
        vo.setPromptType(item.getPromptType());
        vo.setInputJson(item.getInputJson());
        vo.setExpectedSchemaJson(item.getExpectedSchemaJson());
        vo.setEnabled(item.getEnabled());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private PromptRegressionResultVO toPromptResultVO(PromptRegressionResult item) {
        PromptRegressionResultVO vo = new PromptRegressionResultVO();
        vo.setId(item.getId());
        vo.setCaseId(item.getCaseId());
        vo.setPromptVersionId(item.getPromptVersionId());
        vo.setStatus(item.getStatus());
        vo.setOutputJson(item.getOutputJson());
        vo.setScore(item.getScore());
        vo.setErrorMessage(item.getErrorMessage());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void validateJsonObject(String value, String fieldName) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isContainerNode()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " must be a JSON object or array");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " must be valid JSON");
        }
    }

    private long pageNo(Long value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long pageSize(Long value) {
        return value == null || value < 1 ? 10 : Math.min(value, 100);
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return 30;
        }
        return Math.min(days, 365);
    }

    private Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round(numerator * 10000D / denominator) / 100D;
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
