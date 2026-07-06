package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.config.KnowledgeIndexExecutor;
import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeEvaluationDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.entity.AgentFeedback;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AnalyticsJobLog;
import com.codecoachai.ai.agent.domain.entity.AnalyticsMetricDefinition;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeChunk;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocumentVersion;
import com.codecoachai.ai.agent.domain.entity.PromptRegressionCase;
import com.codecoachai.ai.agent.domain.entity.PromptRegressionResult;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO.FeedbackTypeCount;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeChunkVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeConfigVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentOptionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVersionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateCleanupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateReviewItemVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateReviewVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeEvaluationVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeExactDuplicateGroupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchTraceVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO.DuplicateDocumentHotspot;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
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
import com.codecoachai.ai.agent.mapper.PersonalKnowledgeDocumentVersionMapper;
import com.codecoachai.ai.agent.mapper.PromptRegressionCaseMapper;
import com.codecoachai.ai.agent.mapper.PromptRegressionResultMapper;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.domain.dto.EmbeddingRequestDTO;
import com.codecoachai.ai.domain.vo.EmbeddingResponseVO;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.security.SensitiveTextMasker;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.EmbeddingService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentV4OpsServiceImpl implements AgentV4OpsService {

    private static final int KNOWLEDGE_DUPLICATE_REVIEW_DEFAULT_LIMIT = 20;
    private static final int KNOWLEDGE_DUPLICATE_REVIEW_MAX_LIMIT = 80;
    private static final int KNOWLEDGE_DUPLICATE_REVIEW_MAX_SCAN_CHUNKS = 80;
    private static final int KNOWLEDGE_DUPLICATE_REVIEW_MAX_VECTOR_PROBES = 20;
    private static final Set<String> SUPPORTED_AGENT_FEEDBACK_TYPES = Set.of(
            "HELPFUL",
            "NOT_HELPFUL",
            "INACCURATE",
            "NOT_MY_EXPERIENCE",
            "HALLUCINATION",
            "TOO_HARD",
            "TOO_EASY",
            "IRRELEVANT"
    );
    private static final Set<String> POSITIVE_AGENT_FEEDBACK_TYPES = Set.of("HELPFUL");
    private static final String VECTOR_DELETE_COLLECTION_KNOWLEDGE = "personal_knowledge_chunk";
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
    private static final String KNOWLEDGE_NORMALIZATION_VERSION = TextFingerprintUtils.NORMALIZATION_VERSION;
    private static final Pattern KNOWLEDGE_QUERY_TOKEN_PATTERN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}]+|[\\p{IsHan}]+");
    private static final Pattern MARKDOWN_LIST_ITEM_PATTERN = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+).+");
    private static final Set<String> KNOWLEDGE_QUERY_STOP_WORDS = Set.of(
            "什么", "哪些", "哪个", "如何", "怎么", "怎样", "为什么", "一下", "这个", "那个", "里面",
            "关于", "请问", "帮我", "介绍", "说明", "知识库", "上传", "我的", "是否", "有没有"
    );
    private static final List<String> KNOWLEDGE_QUERY_DOMAIN_TERMS = List.of(
            "Java", "Spring", "Spring Boot", "Spring Cloud", "MySQL", "Redis", "JVM", "GC", "Kafka",
            "RocketMQ", "Docker", "Kubernetes", "微服务", "分布式", "高并发", "线程池", "索引",
            "缓存", "简历", "项目", "后端", "前端", "面试", "题目", "答案", "解析", "难点"
    );
    private static final Map<String, String> KNOWLEDGE_PAYLOAD_INDEXES = Map.of(
            "userId", "integer",
            "documentId", "integer",
            "chunkId", "integer",
            "chunkIndex", "integer",
            "documentType", "keyword",
            "sourceRef", "keyword",
            "chunkHash", "keyword",
            "embeddingModel", "keyword"
    );

    private final AgentFeedbackMapper agentFeedbackMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentRunMapper agentRunMapper;
    private final PersonalKnowledgeDocumentMapper personalKnowledgeDocumentMapper;
    private final PersonalKnowledgeChunkMapper personalKnowledgeChunkMapper;
    private final PersonalKnowledgeDocumentVersionMapper personalKnowledgeDocumentVersionMapper;
    private final AnalyticsMetricDefinitionMapper analyticsMetricDefinitionMapper;
    private final AnalyticsJobLogMapper analyticsJobLogMapper;
    private final PromptRegressionCaseMapper promptRegressionCaseMapper;
    private final PromptRegressionResultMapper promptRegressionResultMapper;
    private final JobCoachAgentService jobCoachAgentService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiCallLogService aiCallLogService;
    private final EmbeddingService embeddingService;
    private final VectorStoreClient vectorStoreClient;
    private final AiRouterProperties aiRouterProperties;
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeIndexExecutor knowledgeIndexExecutor;

    @Override
    public AgentFeedbackVO createFeedback(Long userId, AgentFeedbackCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFeedbackType())) {
            throw new IllegalArgumentException("反馈类型不能为空");
        }
        validateFeedbackOwnership(userId, dto);
        AgentFeedback feedback = new AgentFeedback();
        feedback.setUserId(userId);
        feedback.setAgentTaskId(dto.getAgentTaskId());
        feedback.setAgentRunId(dto.getAgentRunId());
        feedback.setFeedbackType(normalizeAgentFeedbackType(dto.getFeedbackType()));
        feedback.setComment(dto.getComment());
        agentFeedbackMapper.insert(feedback);
        return toFeedbackVO(feedback);
    }

    @Override
    public PageResult<AgentFeedbackVO> pageFeedback(Long userId, Long taskId, Long runId, String feedbackType,
                                                    Long pageNo, Long pageSize) {
        long actualPageNo = pageNo(pageNo);
        long actualPageSize = pageSize(pageSize);
        String normalizedFeedbackType = normalizeOptionalAgentFeedbackType(feedbackType);
        Page<AgentFeedback> page = agentFeedbackMapper.selectPage(Page.of(actualPageNo, actualPageSize),
                new LambdaQueryWrapper<AgentFeedback>()
                        .eq(userId != null, AgentFeedback::getUserId, userId)
                        .eq(taskId != null, AgentFeedback::getAgentTaskId, taskId)
                        .eq(runId != null, AgentFeedback::getAgentRunId, runId)
                        .eq(StringUtils.hasText(normalizedFeedbackType), AgentFeedback::getFeedbackType, normalizedFeedbackType)
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
                .map(item -> normalizeOptionalAgentFeedbackType(item.getFeedbackType()))
                .map(item -> firstText(item, "UNKNOWN"))
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        long adoptedCount = byType.entrySet().stream()
                .filter(entry -> POSITIVE_AGENT_FEEDBACK_TYPES.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        long ignoredCount = Math.max(0L, feedback.size() - adoptedCount);
        AgentFeedbackStatsVO vo = new AgentFeedbackStatsVO();
        vo.setTotalFeedbackCount((long) feedback.size());
        vo.setAdoptedCount(adoptedCount);
        vo.setIgnoredCount(ignoredCount);
        vo.setLikedCount(byType.getOrDefault("HELPFUL", 0L));
        vo.setDislikedCount(ignoredCount);
        vo.setAdoptionRate(rate(adoptedCount, adoptedCount + ignoredCount));
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
            throw new IllegalArgumentException("标题和内容不能为空");
        }
        String normalizedContent = normalizeKnowledgeContent(dto.getContent());
        String contentHash = knowledgeHash(normalizedContent);
        PersonalKnowledgeDocument duplicateDocument = findExistingKnowledgeDocument(userId, contentHash);
        if (duplicateDocument != null) {
            KnowledgeDocumentVO duplicateVO = toKnowledgeDocumentVO(duplicateDocument, chunkCount(duplicateDocument.getId()), false);
            duplicateVO.setDuplicateDocument(true);
            duplicateVO.setDuplicateDocumentId(duplicateDocument.getId());
            duplicateVO.setDuplicateChunkCount(duplicateVO.getChunkCount());
            return duplicateVO;
        }
        PersonalKnowledgeDocument document = new PersonalKnowledgeDocument();
        document.setUserId(userId);
        document.setTitle(dto.getTitle().trim());
        document.setDocumentType(firstText(dto.getDocumentType(), "NOTE"));
        document.setContent(dto.getContent());
        document.setContentHash(contentHash);
        document.setNormalizationVersion(KNOWLEDGE_NORMALIZATION_VERSION);
        document.setStatus(semanticKnowledgeEnabled() ? "PENDING" : "INDEXED");
        personalKnowledgeDocumentMapper.insert(document);
        KnowledgeDocumentVO vo = rebuildKnowledgeDocumentChunks(userId, document, normalizedContent, true);
        vo.setDuplicateDocument(false);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO updateKnowledgeDocument(Long userId, Long documentId, KnowledgeDocumentCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTitle()) || !StringUtils.hasText(dto.getContent())) {
            throw new IllegalArgumentException("标题和内容不能为空");
        }
        PersonalKnowledgeDocument document = ownedDocument(userId, documentId);
        String normalizedContent = normalizeKnowledgeContent(dto.getContent());
        String contentHash = knowledgeHash(normalizedContent);
        PersonalKnowledgeDocument duplicateDocument = findExistingKnowledgeDocument(userId, contentHash, document.getId());
        if (duplicateDocument != null) {
            KnowledgeDocumentVO duplicateVO = toKnowledgeDocumentVO(duplicateDocument, chunkCount(duplicateDocument.getId()), false);
            duplicateVO.setDuplicateDocument(true);
            duplicateVO.setDuplicateDocumentId(duplicateDocument.getId());
            duplicateVO.setDuplicateChunkCount(duplicateVO.getChunkCount());
            return duplicateVO;
        }
        List<PersonalKnowledgeChunk> oldChunks = listDocumentChunks(userId, document.getId());
        snapshotKnowledgeDocumentVersion(userId, document, oldChunks.size());
        if (!oldChunks.isEmpty()) {
            deletePersonalKnowledgeVectors(oldChunks);
            personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                    .eq(PersonalKnowledgeChunk::getUserId, userId)
                    .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
        }
        document.setTitle(dto.getTitle().trim());
        document.setDocumentType(firstText(dto.getDocumentType(), "NOTE"));
        document.setContent(dto.getContent());
        document.setContentHash(contentHash);
        document.setNormalizationVersion(KNOWLEDGE_NORMALIZATION_VERSION);
        document.setStatus(semanticKnowledgeEnabled() ? "PENDING" : "INDEXED");
        personalKnowledgeDocumentMapper.updateById(document);
        return rebuildKnowledgeDocumentChunks(userId, document, normalizedContent, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO uploadKnowledgeDocument(Long userId, MultipartFile file, String documentType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先选择要上传的文件");
        }
        if (file.getSize() > knowledgeProperties.getUploadMaxBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不能超过 8MB");
        }
        String originalFilename = firstText(file.getOriginalFilename(), "knowledge.txt").trim();
        String extension = fileExtension(originalFilename);
        if (!knowledgeProperties.getUploadExtensions().contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 txt、md、markdown、pdf、docx、doc 文件");
        }
        try (InputStream inputStream = file.getInputStream()) {
            String content = extractKnowledgeFileText(extension, inputStream);
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容为空，请更换文件后重试");
            }
            KnowledgeDocumentCreateDTO dto = new KnowledgeDocumentCreateDTO();
            dto.setTitle(stripExtension(originalFilename));
            dto.setDocumentType(firstText(documentType, documentTypeByExtension(extension)));
            dto.setContent(content);
            return createKnowledgeDocument(userId, dto);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件读取失败，请更换文件后重试");
        }
    }

    @Override
    public PageResult<KnowledgeDocumentVO> pageKnowledgeDocuments(Long userId, String title, String documentType,
                                                                  String status, Long pageNo, Long pageSize) {
        long actualPageNo = pageNo(pageNo);
        long actualPageSize = pageSize(pageSize);
        LambdaQueryWrapper<PersonalKnowledgeDocument> query = new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                .eq(PersonalKnowledgeDocument::getUserId, userId)
                .like(StringUtils.hasText(title), PersonalKnowledgeDocument::getTitle, normalizeKeyword(title))
                .eq(StringUtils.hasText(documentType), PersonalKnowledgeDocument::getDocumentType, normalizeKeyword(documentType))
                .eq(StringUtils.hasText(status), PersonalKnowledgeDocument::getStatus, normalizeKeyword(status))
                .select(PersonalKnowledgeDocument::getId,
                        PersonalKnowledgeDocument::getUserId,
                        PersonalKnowledgeDocument::getTitle,
                        PersonalKnowledgeDocument::getDocumentType,
                        PersonalKnowledgeDocument::getContentHash,
                        PersonalKnowledgeDocument::getNormalizationVersion,
                        PersonalKnowledgeDocument::getStatus,
                        PersonalKnowledgeDocument::getCreatedAt,
                        PersonalKnowledgeDocument::getUpdatedAt)
                .orderByDesc(PersonalKnowledgeDocument::getUpdatedAt);
        Page<PersonalKnowledgeDocument> page = personalKnowledgeDocumentMapper.selectPage(Page.of(actualPageNo, actualPageSize), query);
        List<Long> documentIds = page.getRecords().stream()
                .map(PersonalKnowledgeDocument::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, KnowledgeDocumentAggregate> aggregateMap = knowledgeDocumentAggregates(documentIds);
        List<KnowledgeDocumentVO> records = page.getRecords()
                .stream()
                .map(document -> toKnowledgeDocumentVO(document,
                        aggregateMap.getOrDefault(document.getId(), KnowledgeDocumentAggregate.empty()),
                        false))
                .toList();
        return PageResult.of(records, page.getTotal(), actualPageNo, actualPageSize);
    }

    @Override
    public List<KnowledgeDocumentVO> listKnowledgeDocuments(Long userId, String title, String documentType, String status) {
        return pageKnowledgeDocuments(userId, title, documentType, status, 1L, 100L).getRecords();
    }

    @Override
    public List<String> listKnowledgeDocumentTypes(Long userId) {
        return personalKnowledgeDocumentMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .select(PersonalKnowledgeDocument::getDocumentType))
                .stream()
                .map(PersonalKnowledgeDocument::getDocumentType)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<KnowledgeDocumentOptionVO> listKnowledgeDocumentOptions(Long userId) {
        return personalKnowledgeDocumentMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .select(PersonalKnowledgeDocument::getId,
                                PersonalKnowledgeDocument::getTitle,
                                PersonalKnowledgeDocument::getDocumentType,
                                PersonalKnowledgeDocument::getStatus)
                        .orderByDesc(PersonalKnowledgeDocument::getUpdatedAt))
                .stream()
                .map(this::toKnowledgeDocumentOptionVO)
                .toList();
    }

    @Override
    public KnowledgeStatsVO getKnowledgeStats(Long userId) {
        long documentCount = queryLong("""
                SELECT COUNT(1)
                FROM personal_knowledge_document
                WHERE deleted = 0 AND user_id = ?
                """, userId);
        long chunkCount = queryLong("""
                SELECT COUNT(1)
                FROM personal_knowledge_chunk
                WHERE deleted = 0 AND user_id = ?
                """, userId);
        long duplicateChunkCount = queryLong("""
                SELECT COALESCE(SUM(duplicate_count), 0)
                FROM (
                    SELECT COUNT(1) - 1 AS duplicate_count
                    FROM personal_knowledge_chunk
                    WHERE deleted = 0
                      AND user_id = ?
                      AND chunk_hash IS NOT NULL
                      AND chunk_hash <> ''
                    GROUP BY chunk_hash
                    HAVING COUNT(1) > 1
                ) duplicate_groups
                """, userId);
        KnowledgeStatsVO vo = new KnowledgeStatsVO();
        boolean embeddingEnabled = embeddingEnabled();
        boolean semanticEnabled = vectorStoreClient.isEnabled() && embeddingEnabled;
        vo.setDocumentCount(toIntCount(documentCount));
        vo.setChunkCount(toIntCount(chunkCount));
        vo.setDuplicateChunkCount(toIntCount(duplicateChunkCount));
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setEmbeddingEnabled(embeddingEnabled);
        vo.setSemanticEnabled(semanticEnabled);
        vo.setEmbeddingDisabledReason(semanticEnabled ? null : embeddingDisabledReason());
        vo.setRetrievalMode(semanticEnabled ? "HYBRID" : "KEYWORD_FALLBACK");
        vo.setChunkStrategy(knowledgeProperties.getChunkStrategy());
        vo.setDocumentTypeCounts(queryMetricCounts("""
                SELECT COALESCE(NULLIF(TRIM(document_type), ''), 'UNKNOWN') AS name, COUNT(1) AS count
                FROM personal_knowledge_document
                WHERE deleted = 0 AND user_id = ?
                GROUP BY COALESCE(NULLIF(TRIM(document_type), ''), 'UNKNOWN')
                ORDER BY count DESC, name ASC
                """, userId));
        vo.setIndexStatusCounts(queryMetricCounts("""
                SELECT COALESCE(NULLIF(TRIM(index_status), ''), 'PENDING') AS name, COUNT(1) AS count
                FROM personal_knowledge_chunk
                WHERE deleted = 0 AND user_id = ?
                GROUP BY COALESCE(NULLIF(TRIM(index_status), ''), 'PENDING')
                ORDER BY count DESC, name ASC
                """, userId));
        vo.setEmbeddingModelCounts(queryMetricCounts("""
                SELECT COALESCE(NULLIF(TRIM(embedding_model), ''), 'UNKNOWN') AS name, COUNT(1) AS count
                FROM personal_knowledge_chunk
                WHERE deleted = 0 AND user_id = ?
                GROUP BY COALESCE(NULLIF(TRIM(embedding_model), ''), 'UNKNOWN')
                ORDER BY count DESC, name ASC
                """, userId));
        vo.setDuplicateTypeCounts(queryMetricCounts("""
                WITH user_chunks AS (
                    SELECT id, document_id, chunk_hash
                    FROM personal_knowledge_chunk
                    WHERE deleted = 0 AND user_id = ?
                ),
                duplicate_groups AS (
                    SELECT chunk_hash, MIN(id) AS keep_id
                    FROM user_chunks
                    WHERE chunk_hash IS NOT NULL AND chunk_hash <> ''
                    GROUP BY chunk_hash
                    HAVING COUNT(1) > 1
                )
                SELECT COALESCE(NULLIF(TRIM(d.document_type), ''), 'UNKNOWN') AS name, COUNT(1) AS count
                FROM user_chunks c
                JOIN duplicate_groups dup ON dup.chunk_hash = c.chunk_hash AND c.id <> dup.keep_id
                LEFT JOIN personal_knowledge_document d ON d.deleted = 0 AND d.id = c.document_id
                GROUP BY COALESCE(NULLIF(TRIM(d.document_type), ''), 'UNKNOWN')
                ORDER BY count DESC, name ASC
                """, userId));
        vo.setDuplicateDocumentHotspots(queryDuplicateDocumentHotspots(userId));
        attachKnowledgeStatsGovernance(vo);
        return vo;
    }

    private void attachKnowledgeStatsGovernance(KnowledgeStatsVO vo) {
        int documentCount = vo.getDocumentCount() == null ? 0 : vo.getDocumentCount();
        int chunkCount = vo.getChunkCount() == null ? 0 : vo.getChunkCount();
        int failedCount = metricCount(vo.getIndexStatusCounts(), "FAILED");
        int pendingCount = metricCount(vo.getIndexStatusCounts(), "PENDING");
        int duplicateCount = vo.getDuplicateChunkCount() == null ? 0 : vo.getDuplicateChunkCount();
        List<String> actions = new ArrayList<>();
        if (documentCount == 0 || chunkCount == 0) {
            actions.add("SUPPLEMENT_KNOWLEDGE_DOCUMENT");
            vo.setKnowledgeStatus("EMPTY");
            vo.setEvidenceTrustStatus("UNAVAILABLE");
            vo.setCanBeEvidence(false);
            vo.setLowConfidence(true);
            vo.setDisabledReason("EMPTY_KNOWLEDGE_BASE");
            vo.setGovernanceActions(actions);
            return;
        }
        if (failedCount > 0) {
            actions.add("RETRY_FAILED_INDEX");
        }
        if (pendingCount > 0) {
            actions.add("REBUILD_PENDING_INDEX");
        }
        if (duplicateCount > 0) {
            actions.add("REVIEW_DUPLICATE_CHUNKS");
        }
        if (!Boolean.TRUE.equals(vo.getSemanticEnabled())) {
            actions.add("ENABLE_SEMANTIC_RETRIEVAL");
        }
        boolean indexHealthy = failedCount == 0 && pendingCount == 0;
        boolean semanticHealthy = Boolean.TRUE.equals(vo.getSemanticEnabled());
        vo.setKnowledgeStatus(indexHealthy && semanticHealthy ? "READY" : "DEGRADED");
        vo.setEvidenceTrustStatus(indexHealthy && semanticHealthy ? "VERIFIED" : "PARTIAL");
        vo.setCanBeEvidence(indexHealthy);
        vo.setLowConfidence(!indexHealthy || !semanticHealthy);
        if (failedCount > 0) {
            vo.setDisabledReason("INDEX_FAILED");
        } else if (pendingCount > 0) {
            vo.setDisabledReason("INDEX_PENDING");
        } else if (!semanticHealthy) {
            vo.setDisabledReason("SEMANTIC_RETRIEVAL_DISABLED");
        } else {
            vo.setDisabledReason(null);
        }
        vo.setGovernanceActions(actions.stream().distinct().toList());
    }

    private int metricCount(Map<String, Integer> counts, String key) {
        if (counts == null || key == null) {
            return 0;
        }
        return counts.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0);
    }

    private Map<Long, KnowledgeDocumentAggregate> knowledgeDocumentAggregates(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = documentIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT document_id,
                       COUNT(1) AS chunk_count,
                       SUM(CASE WHEN COALESCE(NULLIF(TRIM(index_status), ''), 'PENDING') = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                       SUM(CASE WHEN COALESCE(NULLIF(TRIM(index_status), ''), 'PENDING') = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN COALESCE(NULLIF(TRIM(index_status), ''), 'PENDING') = 'INDEXED' THEN 1 ELSE 0 END) AS indexed_count,
                       SUM(CASE WHEN COALESCE(NULLIF(TRIM(index_status), ''), 'PENDING') = 'DISABLED' THEN 1 ELSE 0 END) AS disabled_count
                FROM personal_knowledge_chunk
                WHERE deleted = 0 AND document_id IN (%s)
                GROUP BY document_id
                """.formatted(placeholders), documentIds.toArray());
        Map<Long, KnowledgeDocumentAggregate> aggregates = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long documentId = nullableLong(rowValue(row, "document_id"));
            if (documentId == null) {
                continue;
            }
            aggregates.put(documentId, new KnowledgeDocumentAggregate(
                    longValue(rowValue(row, "chunk_count")),
                    longValue(rowValue(row, "failed_count")),
                    longValue(rowValue(row, "pending_count")),
                    longValue(rowValue(row, "indexed_count")),
                    longValue(rowValue(row, "disabled_count"))));
        }
        return aggregates;
    }

    private long queryLong(String sql, Long userId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return value == null ? 0L : value;
    }

    private Map<String, Integer> queryMetricCounts(String sql, Long userId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, userId)) {
            String name = firstText(String.valueOf(rowValue(row, "name")), "UNKNOWN");
            counts.put(name, toIntCount(longValue(rowValue(row, "count"))));
        }
        return counts;
    }

    private List<DuplicateDocumentHotspot> queryDuplicateDocumentHotspots(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                WITH user_chunks AS (
                    SELECT id, document_id, chunk_hash
                    FROM personal_knowledge_chunk
                    WHERE deleted = 0 AND user_id = ?
                ),
                duplicate_groups AS (
                    SELECT chunk_hash, MIN(id) AS keep_id
                    FROM user_chunks
                    WHERE chunk_hash IS NOT NULL AND chunk_hash <> ''
                    GROUP BY chunk_hash
                    HAVING COUNT(1) > 1
                ),
                document_chunk_counts AS (
                    SELECT document_id, COUNT(1) AS chunk_count
                    FROM user_chunks
                    GROUP BY document_id
                )
                SELECT d.id AS document_id,
                       d.title AS title,
                       COALESCE(NULLIF(TRIM(d.document_type), ''), 'UNKNOWN') AS document_type,
                       COUNT(1) AS duplicate_chunk_count,
                       COALESCE(MAX(total_chunks.chunk_count), 0) AS chunk_count
                FROM user_chunks c
                JOIN duplicate_groups dup ON dup.chunk_hash = c.chunk_hash AND c.id <> dup.keep_id
                JOIN personal_knowledge_document d ON d.deleted = 0 AND d.id = c.document_id
                LEFT JOIN document_chunk_counts total_chunks ON total_chunks.document_id = c.document_id
                GROUP BY d.id, d.title, COALESCE(NULLIF(TRIM(d.document_type), ''), 'UNKNOWN')
                ORDER BY duplicate_chunk_count DESC, d.id ASC
                LIMIT 5
                """, userId);
        return rows.stream()
                .map(row -> {
                    long duplicateChunkCount = longValue(rowValue(row, "duplicate_chunk_count"));
                    long chunkCount = longValue(rowValue(row, "chunk_count"));
                    DuplicateDocumentHotspot hotspot = new DuplicateDocumentHotspot();
                    hotspot.setDocumentId(nullableLong(rowValue(row, "document_id")));
                    hotspot.setTitle(stringValue(rowValue(row, "title")));
                    hotspot.setDocumentType(firstText(stringValue(rowValue(row, "document_type")), "UNKNOWN"));
                    hotspot.setDuplicateChunkCount(toIntCount(duplicateChunkCount));
                    hotspot.setChunkCount(toIntCount(chunkCount));
                    hotspot.setDuplicateRatio(rate(duplicateChunkCount, chunkCount));
                    return hotspot;
                })
                .toList();
    }

    @Override
    public KnowledgeConfigVO getKnowledgeConfig(Long userId) {
        KnowledgeConfigVO vo = new KnowledgeConfigVO();
        boolean embeddingEnabled = embeddingEnabled();
        boolean semanticEnabled = vectorStoreClient.isEnabled() && embeddingEnabled;
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setEmbeddingEnabled(embeddingEnabled);
        vo.setSemanticEnabled(semanticEnabled);
        vo.setEmbeddingDisabledReason(semanticEnabled ? null : embeddingDisabledReason());
        vo.setVectorCollection(knowledgeProperties.getCollection());
        vo.setRetrievalMode(semanticEnabled ? "HYBRID" : "KEYWORD_FALLBACK");
        vo.setNormalizationVersion(KNOWLEDGE_NORMALIZATION_VERSION);
        vo.setChunkStrategy(knowledgeProperties.getChunkStrategy());
        vo.setChunkSize(knowledgeProperties.safeChunkSize());
        vo.setChunkOverlap(knowledgeProperties.safeChunkOverlap());
        vo.setMinChunkSize(knowledgeProperties.safeMinChunkSize());
        vo.setNearDuplicateThreshold(knowledgeProperties.safeNearDuplicateThreshold());
        vo.setAskMinScore(knowledgeProperties.safeAskMinScore());
        vo.setUploadMaxBytes(knowledgeProperties.getUploadMaxBytes());
        vo.setUploadMaxTextChars(knowledgeProperties.safeUploadMaxTextChars());
        vo.setUploadExtensions(knowledgeProperties.getUploadExtensions().stream().sorted().toList());
        vo.setExactDedupScope("PER_USER_DOCUMENT_AND_CHUNK_HASH");
        vo.setNearDuplicateAction("WARN_ONLY");
        return vo;
    }

    private boolean semanticKnowledgeEnabled() {
        return vectorStoreClient.isEnabled() && embeddingEnabled();
    }

    private boolean embeddingEnabled() {
        AiRouterProperties.Router router = aiRouterProperties.getRouter();
        String providerName = firstText(router == null ? null : router.getEmbeddingProvider(),
                router == null ? null : router.getDefaultProvider());
        if (!StringUtils.hasText(providerName)) {
            return false;
        }
        Map<String, AiRouterProperties.ProviderConfig> providers = aiRouterProperties.getProviders();
        AiRouterProperties.ProviderConfig provider = providers == null ? null : providers.get(providerName);
        return provider != null
                && StringUtils.hasText(provider.getBaseUrl())
                && StringUtils.hasText(provider.getApiKey())
                && StringUtils.hasText(provider.getEmbeddingModel());
    }

    private String embeddingDisabledReason() {
        if (!vectorStoreClient.isEnabled()) {
            return "语义检索未启用，当前使用关键词检索。";
        }
        AiRouterProperties.Router router = aiRouterProperties.getRouter();
        String providerName = firstText(router == null ? null : router.getEmbeddingProvider(),
                router == null ? null : router.getDefaultProvider());
        if (!StringUtils.hasText(providerName)) {
            return "向量模型未配置，当前使用关键词检索。";
        }
        Map<String, AiRouterProperties.ProviderConfig> providers = aiRouterProperties.getProviders();
        AiRouterProperties.ProviderConfig provider = providers == null ? null : providers.get(providerName);
        if (provider == null) {
            return "向量模型未配置，当前使用关键词检索。";
        }
        if (!StringUtils.hasText(provider.getBaseUrl())
                || !StringUtils.hasText(provider.getApiKey())
                || !StringUtils.hasText(provider.getEmbeddingModel())) {
            return "向量服务地址、密钥或模型未配置，当前使用关键词检索。";
        }
        return null;
    }

    @Override
    public KnowledgeDocumentVO getKnowledgeDocument(Long userId, Long id) {
        PersonalKnowledgeDocument document = ownedDocument(userId, id);
        return toKnowledgeDocumentVO(document, chunkCount(document.getId()), true);
    }

    @Override
    public List<KnowledgeDocumentVersionVO> listKnowledgeDocumentVersions(Long userId, Long documentId) {
        ownedDocument(userId, documentId);
        return personalKnowledgeDocumentVersionMapper.selectList(
                        new LambdaQueryWrapper<PersonalKnowledgeDocumentVersion>()
                                .eq(PersonalKnowledgeDocumentVersion::getUserId, userId)
                                .eq(PersonalKnowledgeDocumentVersion::getDocumentId, documentId)
                                .orderByDesc(PersonalKnowledgeDocumentVersion::getVersionNo))
                .stream()
                .map(this::toKnowledgeDocumentVersionVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO restoreKnowledgeDocumentVersion(Long userId, Long documentId, Long versionId) {
        PersonalKnowledgeDocument document = ownedDocument(userId, documentId);
        PersonalKnowledgeDocumentVersion version = ownedKnowledgeDocumentVersion(userId, documentId, versionId);
        String content = firstText(version.getContent(), "");
        String normalizedContent = normalizeKnowledgeContent(content);
        String contentHash = StringUtils.hasText(version.getContentHash())
                ? version.getContentHash()
                : knowledgeHash(normalizedContent);
        PersonalKnowledgeDocument duplicateDocument = findExistingKnowledgeDocument(userId, contentHash, document.getId());
        if (duplicateDocument != null) {
            KnowledgeDocumentVO duplicateVO = toKnowledgeDocumentVO(duplicateDocument, chunkCount(duplicateDocument.getId()), false);
            duplicateVO.setDuplicateDocument(true);
            duplicateVO.setDuplicateDocumentId(duplicateDocument.getId());
            duplicateVO.setDuplicateChunkCount(duplicateVO.getChunkCount());
            return duplicateVO;
        }
        List<PersonalKnowledgeChunk> oldChunks = listDocumentChunks(userId, document.getId());
        snapshotKnowledgeDocumentVersion(userId, document, oldChunks.size());
        if (!oldChunks.isEmpty()) {
            deletePersonalKnowledgeVectors(oldChunks);
            personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                    .eq(PersonalKnowledgeChunk::getUserId, userId)
                    .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
        }
        document.setTitle(firstText(version.getTitle(), document.getTitle()));
        document.setDocumentType(firstText(version.getDocumentType(), "NOTE"));
        document.setContent(content);
        document.setContentHash(contentHash);
        document.setNormalizationVersion(firstText(version.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
        document.setStatus(semanticKnowledgeEnabled() ? "PENDING" : "INDEXED");
        personalKnowledgeDocumentMapper.updateById(document);
        return rebuildKnowledgeDocumentChunks(userId, document, normalizedContent, true);
    }

    @Override
    public List<KnowledgeChunkVO> listKnowledgeChunks(Long userId, Long documentId) {
        ownedDocument(userId, documentId);
        List<PersonalKnowledgeChunk> chunks = listDocumentChunks(userId, documentId);
        Set<String> seen = new HashSet<>();
        return chunks.stream()
                .map(chunk -> toKnowledgeChunkVO(chunk, StringUtils.hasText(chunk.getChunkHash()) && !seen.add(chunk.getChunkHash())))
                .toList();
    }

    @Override
    public KnowledgeChunkVO getKnowledgeChunk(Long userId, Long chunkId) {
        PersonalKnowledgeChunk chunk = ownedChunk(userId, chunkId);
        return toKnowledgeChunkVO(chunk, false);
    }

    @Override
    public List<KnowledgeSearchResultVO> listSimilarKnowledgeChunks(Long userId, Long chunkId, Integer limit) {
        PersonalKnowledgeChunk source = ownedChunk(userId, chunkId);
        if (!semanticKnowledgeEnabled()) {
            return List.of();
        }
        int size = normalizeLimit(limit);
        try {
            KnowledgeEmbeddingResult embedding = embedTexts(List.of(source.getContent()));
            if (embedding.vectors().isEmpty()) {
                return List.of();
            }
            List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                    .collectionName(knowledgeProperties.getCollection())
                    .vector(embedding.vectors().get(0))
                    .mustMatchPayload(Map.of("userId", userId))
                    .limit(size + 1)
                    .build());
            List<Long> candidateChunkIds = hits.stream()
                    .map(hit -> payloadLong(hit.getPayload(), "chunkId"))
                    .filter(Objects::nonNull)
                    .filter(id -> !Objects.equals(id, chunkId))
                    .distinct()
                    .limit(size)
                    .toList();
            if (candidateChunkIds.isEmpty()) {
                return List.of();
            }
            Map<Long, VectorSearchResult> hitMap = new LinkedHashMap<>();
            for (VectorSearchResult hit : hits) {
                Long hitChunkId = payloadLong(hit.getPayload(), "chunkId");
                if (hitChunkId != null && !Objects.equals(hitChunkId, chunkId)) {
                    hitMap.putIfAbsent(hitChunkId, hit);
                }
            }
            Map<Long, PersonalKnowledgeChunk> chunkMap = personalKnowledgeChunkMapper.selectList(
                            new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                                    .eq(PersonalKnowledgeChunk::getUserId, userId)
                                    .eq(PersonalKnowledgeChunk::getIndexStatus, "INDEXED")
                                    .in(PersonalKnowledgeChunk::getId, candidateChunkIds))
                    .stream()
                    .collect(Collectors.toMap(PersonalKnowledgeChunk::getId, Function.identity()));
            List<Long> documentIds = chunkMap.values().stream()
                    .map(PersonalKnowledgeChunk::getDocumentId)
                    .distinct()
                    .toList();
            Map<Long, PersonalKnowledgeDocument> documentMap = documentIds.isEmpty()
                    ? Map.of()
                    : personalKnowledgeDocumentMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                            .eq(PersonalKnowledgeDocument::getUserId, userId)
                            .in(PersonalKnowledgeDocument::getId, documentIds))
                    .stream()
                    .collect(Collectors.toMap(PersonalKnowledgeDocument::getId, Function.identity()));
            return candidateChunkIds.stream()
                    .map(chunkMap::get)
                    .filter(Objects::nonNull)
                    .map(chunk -> {
                        PersonalKnowledgeDocument document = documentMap.get(chunk.getDocumentId());
                        VectorSearchResult hit = hitMap.get(chunk.getId());
                        if (document == null || hit == null) {
                            return null;
                        }
                        return toKnowledgeSearchVO(document, chunk, snippet(chunk.getContent(), source.getContent()),
                                source.getContent(), hit.getScore(), "VECTOR_SIMILAR");
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            log.warn("List personal knowledge similar chunks failed userId={} chunkId={}", userId, chunkId, ex);
            return List.of();
        }
    }

    @Override
    public KnowledgeDuplicateReviewVO reviewDuplicateKnowledgeChunks(Long userId, Integer limit, Double threshold) {
        int size = normalizeDuplicateReviewLimit(limit);
        double minScore = threshold == null ? knowledgeProperties.safeNearDuplicateThreshold() : normalizeScore(threshold);
        KnowledgeDuplicateReviewVO vo = new KnowledgeDuplicateReviewVO();
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setThreshold(minScore);
        vo.setLimit(size);
        vo.setGeneratedAt(LocalDateTime.now());
        if (!semanticKnowledgeEnabled()) {
            vo.setScannedChunkCount(0);
            vo.setCandidateCount(0);
            vo.setItems(List.of());
            return vo;
        }
        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .eq(PersonalKnowledgeChunk::getIndexStatus, "INDEXED")
                        .orderByDesc(PersonalKnowledgeChunk::getUpdatedAt)
                        .last("LIMIT " + Math.min(Math.max(size * 2, size),
                                KNOWLEDGE_DUPLICATE_REVIEW_MAX_SCAN_CHUNKS)));
        vo.setScannedChunkCount(chunks.size());
        if (chunks.isEmpty()) {
            vo.setCandidateCount(0);
            vo.setItems(List.of());
            return vo;
        }
        KnowledgeEmbeddingResult embedding;
        try {
            embedding = embedTexts(chunks.stream()
                    .map(PersonalKnowledgeChunk::getContent)
                    .toList());
        } catch (Exception ex) {
            log.warn("Review personal knowledge duplicate chunks embedding failed userId={}", userId, ex);
            vo.setCandidateCount(0);
            vo.setItems(List.of());
            return vo;
        }
        List<List<Float>> vectors = embedding.vectors();
        if (vectors.size() != chunks.size()) {
            log.warn("Review personal knowledge duplicate chunks embedding size mismatch userId={} chunks={} vectors={}",
                    userId, chunks.size(), vectors.size());
            vo.setCandidateCount(0);
            vo.setItems(List.of());
            return vo;
        }
        int vectorProbeLimit = Math.min(chunks.size(), KNOWLEDGE_DUPLICATE_REVIEW_MAX_VECTOR_PROBES);
        Map<Long, List<VectorSearchResult>> sourceHits = new LinkedHashMap<>();
        Set<Long> candidateChunkIds = new LinkedHashSet<>();
        for (int index = 0; index < vectorProbeLimit; index++) {
            PersonalKnowledgeChunk chunk = chunks.get(index);
            try {
                List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                        .collectionName(knowledgeProperties.getCollection())
                        .vector(vectors.get(index))
                        .mustMatchPayload(Map.of("userId", userId))
                        .limit(4)
                        .build()).stream()
                        .filter(hit -> hit.getScore() != null && hit.getScore() >= minScore)
                        .filter(hit -> !Objects.equals(payloadLong(hit.getPayload(), "chunkId"), chunk.getId()))
                        .toList();
                if (!hits.isEmpty()) {
                    sourceHits.put(chunk.getId(), hits);
                    hits.stream()
                            .map(hit -> payloadLong(hit.getPayload(), "chunkId"))
                            .filter(Objects::nonNull)
                            .forEach(candidateChunkIds::add);
                }
            } catch (Exception ex) {
                log.warn("Review personal knowledge duplicate vector probe failed userId={} chunkId={}",
                        userId, chunk.getId(), ex);
            }
        }
        Map<Long, PersonalKnowledgeChunk> sourceChunkMap = chunks.stream()
                .collect(Collectors.toMap(PersonalKnowledgeChunk::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<Long, PersonalKnowledgeChunk> candidateChunkMap = candidateChunkIds.isEmpty()
                ? Map.of()
                : personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .eq(PersonalKnowledgeChunk::getIndexStatus, "INDEXED")
                        .in(PersonalKnowledgeChunk::getId, candidateChunkIds))
                .stream()
                .collect(Collectors.toMap(PersonalKnowledgeChunk::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Set<Long> documentIds = new LinkedHashSet<>();
        chunks.stream().map(PersonalKnowledgeChunk::getDocumentId).filter(Objects::nonNull).forEach(documentIds::add);
        candidateChunkMap.values().stream()
                .map(PersonalKnowledgeChunk::getDocumentId)
                .filter(Objects::nonNull)
                .forEach(documentIds::add);
        Map<Long, PersonalKnowledgeDocument> documentMap = documentIds.isEmpty()
                ? Map.of()
                : personalKnowledgeDocumentMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .in(PersonalKnowledgeDocument::getId, documentIds))
                .stream()
                .collect(Collectors.toMap(PersonalKnowledgeDocument::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        List<KnowledgeDuplicateReviewItemVO> items = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        for (Map.Entry<Long, List<VectorSearchResult>> entry : sourceHits.entrySet()) {
            if (items.size() >= size) {
                break;
            }
            PersonalKnowledgeChunk chunk = sourceChunkMap.get(entry.getKey());
            if (chunk == null) {
                continue;
            }
            List<KnowledgeSearchResultVO> matches = entry.getValue().stream()
                    .map(hit -> {
                        Long matchChunkId = payloadLong(hit.getPayload(), "chunkId");
                        PersonalKnowledgeChunk matchChunk = matchChunkId == null ? null : candidateChunkMap.get(matchChunkId);
                        PersonalKnowledgeDocument matchDocument = matchChunk == null
                                ? null : documentMap.get(matchChunk.getDocumentId());
                        if (matchChunk == null || matchDocument == null) {
                            return null;
                        }
                        return toKnowledgeSearchVO(matchDocument, matchChunk,
                                snippet(matchChunk.getContent(), chunk.getContent()), chunk.getContent(),
                                hit.getScore(), "VECTOR_SIMILAR");
                    })
                    .filter(Objects::nonNull)
                    .filter(match -> seenPairs.add(knowledgePairKey(chunk.getId(), match.getChunkId())))
                    .toList();
            if (matches.isEmpty()) {
                continue;
            }
            PersonalKnowledgeDocument document = documentMap.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            KnowledgeDuplicateReviewItemVO item = new KnowledgeDuplicateReviewItemVO();
            item.setDocumentId(document.getId());
            item.setChunkId(chunk.getId());
            item.setTitle(document.getTitle());
            item.setDocumentType(document.getDocumentType());
            item.setChunkIndex(chunk.getChunkIndex());
            item.setSourceRef(chunk.getSourceRef());
            item.setSnippet(snippet(chunk.getContent(), chunk.getContent()));
            item.setTopScore(matches.stream()
                    .map(KnowledgeSearchResultVO::getScore)
                    .filter(Objects::nonNull)
                    .max(Double::compareTo)
                    .orElse(null));
            item.setMatches(matches);
            items.add(item);
        }
        vo.setCandidateCount(items.size());
        vo.setItems(items);
        return vo;
    }

    @Override
    public List<KnowledgeExactDuplicateGroupVO> listExactDuplicateKnowledgeChunks(Long userId, Integer limit,
                                                                                 Long documentId, String documentType) {
        int size = normalizeDuplicateReviewLimit(limit);
        List<Long> scopedDocumentIds = scopedKnowledgeDocumentIds(userId, documentId, documentType);
        if (scopedDocumentIds.isEmpty()) {
            return List.of();
        }
        List<PersonalKnowledgeChunk> chunks = exactDuplicateScopeChunks(userId, scopedDocumentIds);
        Set<Long> scopedDocumentIdSet = new HashSet<>(scopedDocumentIds);
        return exactDuplicateGroups(chunks).entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(chunk -> scopedDocumentIdSet.contains(chunk.getDocumentId())))
                .filter(entry -> entry.getValue().size() > 1)
                .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
                .limit(size)
                .map(entry -> {
                    KnowledgeExactDuplicateGroupVO vo = new KnowledgeExactDuplicateGroupVO();
                    vo.setChunkHash(entry.getKey());
                    vo.setDuplicateCount(entry.getValue().size() - 1);
                    Set<Long> cleanupCandidateIds = exactDuplicateCleanupCandidates(entry.getValue()).stream()
                            .map(PersonalKnowledgeChunk::getId)
                            .collect(Collectors.toSet());
                    vo.setChunks(entry.getValue().stream()
                            .map(chunk -> toKnowledgeChunkVO(chunk, true, cleanupCandidateIds.contains(chunk.getId())))
                            .toList());
                    return vo;
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDuplicateCleanupVO cleanupExactDuplicateKnowledgeChunks(Long userId, Boolean dryRun, Integer limit,
                                                                           Long documentId, String documentType) {
        boolean previewOnly = dryRun == null || dryRun;
        List<List<PersonalKnowledgeChunk>> duplicateGroups = exactDuplicateChunkGroups(userId, limit, documentId, documentType);
        Set<Long> scopedDocumentIds = new HashSet<>(scopedKnowledgeDocumentIds(userId, documentId, documentType));
        List<PersonalKnowledgeChunk> deleteCandidates = exactDuplicateCleanupCandidates(duplicateGroups.stream()
                .flatMap(List::stream)
                .toList()).stream()
                .filter(chunk -> scopedDocumentIds.contains(chunk.getDocumentId()))
                .toList();
        KnowledgeDuplicateCleanupVO vo = new KnowledgeDuplicateCleanupVO();
        vo.setDryRun(previewOnly);
        vo.setDuplicateGroupCount(duplicateGroups.size());
        vo.setDeleteCandidateCount(deleteCandidates.size());
        vo.setDeletedCount(0);
        vo.setDeletedChunkIds(deleteCandidates.stream().map(PersonalKnowledgeChunk::getId).toList());
        vo.setGeneratedAt(LocalDateTime.now());
        if (previewOnly || deleteCandidates.isEmpty()) {
            return vo;
        }
        deletePersonalKnowledgeVectors(deleteCandidates);
        personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getUserId, userId)
                .in(PersonalKnowledgeChunk::getId, deleteCandidates.stream().map(PersonalKnowledgeChunk::getId).toList()));
        Set<Long> affectedDocumentIds = deleteCandidates.stream()
                .map(PersonalKnowledgeChunk::getDocumentId)
                .collect(Collectors.toSet());
        for (Long affectedDocumentId : affectedDocumentIds) {
            if (chunkCount(affectedDocumentId) == 0) {
                PersonalKnowledgeDocument document = ownedDocument(userId, affectedDocumentId);
                document.setStatus("EMPTY");
                personalKnowledgeDocumentMapper.updateById(document);
            }
        }
        vo.setDeletedCount(deleteCandidates.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeChunk(Long userId, Long chunkId) {
        PersonalKnowledgeChunk chunk = ownedChunk(userId, chunkId);
        deletePersonalKnowledgeVectors(List.of(chunk));
        personalKnowledgeChunkMapper.deleteById(chunk.getId());
        int remaining = chunkCount(chunk.getDocumentId());
        if (remaining == 0) {
            PersonalKnowledgeDocument document = ownedDocument(userId, chunk.getDocumentId());
            document.setStatus("EMPTY");
            personalKnowledgeDocumentMapper.updateById(document);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeDocument(Long userId, Long id) {
        PersonalKnowledgeDocument document = ownedDocument(userId, id);
        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
        deletePersonalKnowledgeVectors(chunks);
        personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getUserId, userId)
                .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
        personalKnowledgeDocumentMapper.deleteById(document.getId());
    }

    @Override
    public List<KnowledgeSearchResultVO> searchKnowledge(Long userId, String keyword, Integer limit, Double minScore,
                                                         Long documentId, String documentType) {
        return searchKnowledgePipeline(userId, keyword, limit, minScore, documentId, documentType).finalResults();
    }

    @Override
    public KnowledgeSearchTraceVO traceKnowledgeSearch(Long userId, String keyword, Integer limit, Double minScore,
                                                       Long documentId, String documentType) {
        KnowledgeSearchPipeline pipeline = searchKnowledgePipeline(userId, keyword, limit, minScore, documentId, documentType);
        KnowledgeSearchTraceVO vo = new KnowledgeSearchTraceVO();
        vo.setQuery(pipeline.query());
        vo.setExpandedTerms(pipeline.expandedTerms());
        vo.setLimit(pipeline.limit());
        vo.setRecallLimit(pipeline.recallLimit());
        vo.setMinScore(pipeline.minScore());
        vo.setDocumentId(documentId);
        vo.setDocumentType(pipeline.documentType());
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setRetrievalMode(semanticKnowledgeEnabled() ? "HYBRID" : "KEYWORD_FALLBACK");
        vo.setVectorCandidates(pipeline.vectorCandidates());
        vo.setKeywordCandidates(pipeline.keywordCandidates());
        vo.setMergedCandidates(pipeline.mergedCandidates());
        vo.setFinalResults(pipeline.finalResults());
        vo.setVectorCandidateCount(pipeline.vectorCandidates().size());
        vo.setKeywordCandidateCount(pipeline.keywordCandidates().size());
        vo.setMergedCandidateCount(pipeline.mergedCandidates().size());
        vo.setFilteredCandidateCount(pipeline.filteredCandidates().size());
        vo.setFinalCandidateCount(pipeline.finalResults().size());
        List<String> warnings = new ArrayList<>();
        if (!semanticKnowledgeEnabled()) {
            warnings.add(firstText(embeddingDisabledReason(), "语义检索暂不可用，当前仅使用关键词检索。"));
        }
        if (pipeline.finalResults().isEmpty()) {
            warnings.add("No candidate passed the current minScore and rerank filters.");
        }
        vo.setWarnings(warnings);
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    private KnowledgeSearchPipeline searchKnowledgePipeline(Long userId, String keyword, Integer limit, Double minScore,
                                                            Long documentId, String documentType) {
        if (!StringUtils.hasText(keyword)) {
            int size = normalizeLimit(limit);
            Double normalizedMinScore = normalizeScore(minScore);
            String normalizedDocumentType = normalizeKeyword(documentType);
            return new KnowledgeSearchPipeline("", List.of(), size, 0, normalizedMinScore, normalizedDocumentType,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }
        int size = normalizeLimit(limit);
        String value = keyword.trim();
        Double normalizedMinScore = normalizeScore(minScore);
        String normalizedDocumentType = normalizeKeyword(documentType);
        int recallSize = Math.min(size * knowledgeProperties.safeRecallMultiplier(), 50);
        List<String> expandedTerms = knowledgeQueryTerms(value);
        List<KnowledgeSearchResultVO> semanticResults = searchKnowledgeByVector(userId, value, recallSize, documentId, normalizedDocumentType);
        List<KnowledgeSearchResultVO> keywordResults = searchKnowledgeByKeyword(userId, value, expandedTerms, recallSize,
                documentId, normalizedDocumentType);
        List<KnowledgeSearchResultVO> mergedResults = mergeKnowledgeSearchResults(semanticResults, keywordResults);
        List<KnowledgeSearchResultVO> scoped = filterByMinScore(mergedResults, normalizedMinScore);
        List<KnowledgeSearchResultVO> finalResults = rerankWithMmr(scoped, size);
        return new KnowledgeSearchPipeline(value, expandedTerms, size, recallSize, normalizedMinScore,
                normalizedDocumentType, semanticResults, keywordResults, mergedResults, scoped, finalResults);
    }

    private record KnowledgeSearchPipeline(String query,
                                           List<String> expandedTerms,
                                           int limit,
                                           int recallLimit,
                                           Double minScore,
                                           String documentType,
                                           List<KnowledgeSearchResultVO> vectorCandidates,
                                           List<KnowledgeSearchResultVO> keywordCandidates,
                                           List<KnowledgeSearchResultVO> mergedCandidates,
                                           List<KnowledgeSearchResultVO> filteredCandidates,
                                           List<KnowledgeSearchResultVO> finalResults) {
    }

    /**
     * MMR 去冗余：在相关性与多样性间平衡，减少同一文档相邻分块刷屏。
     * 冗余度量用标题+片段的文本 Jaccard（同文档分块文本高度重叠），无额外向量成本。
     */
    private List<KnowledgeSearchResultVO> rerankWithMmr(List<KnowledgeSearchResultVO> candidates, int size) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (!knowledgeProperties.isMmrEnabled() || candidates.size() <= 1) {
            return candidates.stream().limit(size).toList();
        }
        double lambda = knowledgeProperties.safeMmrLambda();
        List<KnowledgeSearchResultVO> remaining = new ArrayList<>(candidates);
        List<KnowledgeSearchResultVO> selected = new ArrayList<>();
        while (!remaining.isEmpty() && selected.size() < size) {
            KnowledgeSearchResultVO best = null;
            double bestScore = -Double.MAX_VALUE;
            for (KnowledgeSearchResultVO candidate : remaining) {
                double relevance = candidate.getScore() == null ? 0D : candidate.getScore();
                double maxSim = 0D;
                for (KnowledgeSearchResultVO chosen : selected) {
                    maxSim = Math.max(maxSim, mmrRedundancy(candidate, chosen));
                }
                double mmr = lambda * relevance - (1D - lambda) * maxSim;
                if (mmr > bestScore) {
                    bestScore = mmr;
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            remaining.remove(best);
        }
        return selected;
    }

    private double mmrRedundancy(KnowledgeSearchResultVO a, KnowledgeSearchResultVO b) {
        // 同一文档的不同分块给一个冗余基线，叠加文本相似度
        double base = a.getDocumentId() != null && Objects.equals(a.getDocumentId(), b.getDocumentId()) ? 0.5D : 0D;
        String textA = firstText(a.getSnippet(), a.getTitle(), "");
        String textB = firstText(b.getSnippet(), b.getTitle(), "");
        return Math.min(1D, Math.max(base, textJaccard(textA, textB)));
    }

    /** 词级 Jaccard 相似度，用于 MMR 冗余度量（按空白与中英文标点切词）。 */
    private double textJaccard(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return 0D;
        }
        Set<String> setA = tokenizeForJaccard(a);
        Set<String> setB = tokenizeForJaccard(b);
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0D;
        }
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    private Set<String> tokenizeForJaccard(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase().split("[\\s\\p{Punct}　-〿＀-￯]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private List<String> knowledgeQueryTerms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String value = query.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String domainTerm : KNOWLEDGE_QUERY_DOMAIN_TERMS) {
            if (lower.contains(domainTerm.toLowerCase(Locale.ROOT))) {
                terms.add(domainTerm);
            }
        }
        Matcher matcher = KNOWLEDGE_QUERY_TOKEN_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (token.codePoints().allMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
                addChineseQueryTerms(token, terms);
            } else {
                String normalized = token.toLowerCase(Locale.ROOT);
                if (normalized.length() >= 2 && !KNOWLEDGE_QUERY_STOP_WORDS.contains(normalized)) {
                    terms.add(normalized);
                }
            }
        }
        if (terms.isEmpty()) {
            terms.add(value);
        }
        return terms.stream()
                .filter(StringUtils::hasText)
                .limit(16)
                .toList();
    }

    private void addChineseQueryTerms(String token, LinkedHashSet<String> terms) {
        if (token.length() < 2) {
            return;
        }
        if (token.length() <= 6 && !KNOWLEDGE_QUERY_STOP_WORDS.contains(token)) {
            terms.add(token);
        }
        for (int gramSize = 2; gramSize <= Math.min(4, token.length()); gramSize++) {
            for (int i = 0; i <= token.length() - gramSize; i++) {
                String gram = token.substring(i, i + gramSize);
                if (!KNOWLEDGE_QUERY_STOP_WORDS.contains(gram) && !gram.contains("的")) {
                    terms.add(gram);
                }
                if (terms.size() >= 32) {
                    return;
                }
            }
        }
    }

    private String bestKeywordTerm(String text, List<String> terms, String fallback) {
        if (!StringUtils.hasText(text) || terms == null || terms.isEmpty()) {
            return fallback;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return terms.stream()
                .filter(StringUtils::hasText)
                .filter(term -> lower.contains(term.toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(String::length))
                .orElse(fallback);
    }

    private double keywordScore(String text, List<String> terms, String fullQuery, boolean chunkLevel) {
        if (!StringUtils.hasText(text)) {
            return 0D;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        long matched = terms == null ? 0L : terms.stream()
                .filter(StringUtils::hasText)
                .map(term -> term.toLowerCase(Locale.ROOT))
                .distinct()
                .filter(lower::contains)
                .count();
        boolean fullQueryMatched = StringUtils.hasText(fullQuery)
                && fullQuery.trim().length() <= 64
                && lower.contains(fullQuery.trim().toLowerCase(Locale.ROOT));
        double base = chunkLevel ? 0.62D : 0.55D;
        double termBoost = Math.min(0.2D, matched * 0.04D);
        double exactBoost = fullQueryMatched ? 0.08D : 0D;
        double cap = chunkLevel ? 0.86D : 0.78D;
        return Math.min(cap, base + termBoost + exactBoost);
    }

    private List<KnowledgeSearchResultVO> searchKnowledgeByKeyword(Long userId, String value, List<String> expandedTerms,
                                                                    int size,
                                                                    Long documentId, String documentType) {
        List<String> terms = expandedTerms == null || expandedTerms.isEmpty() ? List.of(value) : expandedTerms;
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .eq(documentId != null, PersonalKnowledgeDocument::getId, documentId)
                        .eq(StringUtils.hasText(documentType), PersonalKnowledgeDocument::getDocumentType, documentType)
                        .and(query -> {
                            for (int i = 0; i < terms.size(); i++) {
                                if (i > 0) {
                                    query.or();
                                }
                                String term = terms.get(i);
                                query.like(PersonalKnowledgeDocument::getTitle, term)
                                        .or()
                                        .like(PersonalKnowledgeDocument::getContent, term);
                            }
                        })
                        .last("LIMIT " + Math.max(size, 20)));
        Map<Long, PersonalKnowledgeDocument> docMap = documents.stream()
                .collect(Collectors.toMap(PersonalKnowledgeDocument::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<Long> scopedDocumentIds = scopedKnowledgeDocumentIds(userId, documentId, documentType);
        if (scopedDocumentIds.isEmpty()) {
            return documents.stream()
                    .map(document -> toKnowledgeSearchVO(document, null,
                            snippet(document.getContent(), bestKeywordTerm(document.getTitle() + "\n" + document.getContent(), terms, value)),
                            String.join(" ", terms), keywordScore(document.getTitle() + "\n" + document.getContent(), terms, value, false),
                            "KEYWORD_DOCUMENT"))
                    .toList();
        }
        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .in(PersonalKnowledgeChunk::getDocumentId, scopedDocumentIds)
                        .and(query -> {
                            for (int i = 0; i < terms.size(); i++) {
                                if (i > 0) {
                                    query.or();
                                }
                                String term = terms.get(i);
                                query.like(PersonalKnowledgeChunk::getContent, term)
                                        .or()
                                        .like(PersonalKnowledgeChunk::getSourceRef, term);
                            }
                        })
                        .last("LIMIT " + Math.max(size, 30)));
        for (PersonalKnowledgeChunk chunk : chunks) {
            docMap.computeIfAbsent(chunk.getDocumentId(), personalKnowledgeDocumentMapper::selectById);
        }

        List<KnowledgeSearchResultVO> result = new ArrayList<>();
        for (PersonalKnowledgeDocument document : documents) {
            String searchable = document.getTitle() + "\n" + document.getContent();
            String bestTerm = bestKeywordTerm(searchable, terms, value);
            result.add(toKnowledgeSearchVO(document, null, snippet(document.getContent(), bestTerm),
                    String.join(" ", terms), keywordScore(searchable, terms, value, false), "KEYWORD_DOCUMENT"));
        }
        for (PersonalKnowledgeChunk chunk : chunks) {
            PersonalKnowledgeDocument document = docMap.get(chunk.getDocumentId());
            if (document != null && Objects.equals(document.getUserId(), userId)
                    && (!StringUtils.hasText(documentType) || Objects.equals(document.getDocumentType(), documentType))) {
                String searchable = document.getTitle() + "\n" + firstText(chunk.getSourceRef(), "") + "\n" + chunk.getContent();
                String bestTerm = bestKeywordTerm(searchable, terms, value);
                result.add(toKnowledgeSearchVO(document, chunk, snippet(chunk.getContent(), bestTerm),
                        String.join(" ", terms), keywordScore(searchable, terms, value, true), "KEYWORD_CHUNK"));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(KnowledgeSearchResultVO::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .toList();
    }

    private KnowledgeEvaluationVO.Item evaluateKnowledgeSample(Long userId, KnowledgeEvaluationDTO.Sample sample,
                                                               int limit, double minScore) {
        KnowledgeEvaluationVO.Item item = new KnowledgeEvaluationVO.Item();
        item.setCaseId(sample.getCaseId());
        item.setQuery(sample.getQuery());
        item.setExpectedDocumentId(sample.getExpectedDocumentId());
        item.setExpectedDocumentTitle(sample.getExpectedDocumentTitle());
        item.setExpectedDocumentType(sample.getExpectedDocumentType());
        item.setRetrievalDocumentId(sample.getRetrievalDocumentId());
        item.setRetrievalDocumentType(sample.getRetrievalDocumentType());
        item.setExpectNoAnswer(Boolean.TRUE.equals(sample.getExpectNoAnswer()));
        item.setNote(sample.getNote());
        KnowledgeAskDTO askDTO = new KnowledgeAskDTO();
        askDTO.setQuestion(sample.getQuery());
        askDTO.setLimit(limit);
        askDTO.setMinScore(minScore);
        askDTO.setDocumentId(sample.getRetrievalDocumentId());
        askDTO.setDocumentType(sample.getRetrievalDocumentType());
        KnowledgeAskVO askResult = askKnowledge(userId, askDTO);
        List<KnowledgeSearchResultVO> references = askResult.getReferences() == null ? List.of() : askResult.getReferences();
        item.setReferences(references);
        item.setReferenceCount(references.size());
        item.setCitationValid(askResult.getCitationValid());
        item.setAnswerGrounded(askResult.getAnswerGrounded());
        item.setAnswerExcerpt(truncateText(askResult.getAnswer(), 1000));
        item.setCitationWarning(askResult.getCitationWarning());
        KnowledgeSearchResultVO top = references.stream().findFirst().orElse(null);
        if (top != null) {
            item.setTopDocumentId(top.getDocumentId());
            item.setTopTitle(top.getTitle());
            item.setTopDocumentType(top.getDocumentType());
            item.setTopScore(top.getScore());
        }
        boolean retrievalPassed;
        if (Boolean.TRUE.equals(sample.getExpectNoAnswer())) {
            retrievalPassed = references.isEmpty();
                item.setFailureReason(retrievalPassed ? null : "期望不命中引用，但实际检索到 " + references.size() + " 条引用");
        } else {
            retrievalPassed = references.stream().anyMatch(reference -> expectedKnowledgeReferenceMatches(sample, reference));
            if (!retrievalPassed) {
                item.setFailureReason(references.isEmpty()
                        ? "没有引用达到最低相关度"
                        : "检索结果中没有命中期望文档");
            }
        }
        boolean answerTrustPassed = Boolean.TRUE.equals(sample.getExpectNoAnswer())
                || (Boolean.TRUE.equals(item.getCitationValid()) && Boolean.TRUE.equals(item.getAnswerGrounded()));
        if (retrievalPassed && !answerTrustPassed) {
            item.setFailureReason(firstText(item.getCitationWarning(),
                    "检索已命中期望来源，但生成答案没有通过引用依据校验"));
        }
        item.setPassed(retrievalPassed && answerTrustPassed);
        return item;
    }

    private boolean expectedKnowledgeReferenceMatches(KnowledgeEvaluationDTO.Sample sample,
                                                      KnowledgeSearchResultVO reference) {
        if (reference == null) {
            return false;
        }
        if (sample.getExpectedDocumentId() != null
                && Objects.equals(sample.getExpectedDocumentId(), reference.getDocumentId())) {
            return true;
        }
        if (StringUtils.hasText(sample.getExpectedDocumentTitle())
                && StringUtils.hasText(reference.getTitle())
                && reference.getTitle().toLowerCase().contains(sample.getExpectedDocumentTitle().trim().toLowerCase())) {
            return true;
        }
        return StringUtils.hasText(sample.getExpectedDocumentType())
                && StringUtils.hasText(reference.getDocumentType())
                && reference.getDocumentType().equalsIgnoreCase(sample.getExpectedDocumentType().trim());
    }

    @Override
    public KnowledgeAskVO askKnowledge(Long userId, KnowledgeAskDTO dto) {
        String question = dto == null ? null : dto.getQuestion();
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "问题不能为空");
        }
        String normalizedQuestion = question.trim();
        int limit = dto == null || dto.getLimit() == null ? knowledgeProperties.safeAskDefaultLimit() : normalizeLimit(dto.getLimit());
        double minScore = dto == null || dto.getMinScore() == null
                ? knowledgeProperties.safeAskMinScore()
                : normalizeScore(dto.getMinScore());
        String documentType = dto == null ? null : normalizeKeyword(dto.getDocumentType());
        Long documentId = dto == null ? null : dto.getDocumentId();
        List<KnowledgeSearchResultVO> references = searchKnowledge(userId, normalizedQuestion, limit, minScore, documentId, documentType).stream()
                .filter(reference -> reference.getScore() != null && reference.getScore() >= minScore)
                .toList();

        KnowledgeAskVO vo = new KnowledgeAskVO();
        vo.setQuestion(normalizedQuestion);
        vo.setReferences(references);
        vo.setReferenceCount(references.size());
        vo.setTopReferenceScore(references.stream()
                .map(KnowledgeSearchResultVO::getScore)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null));
        vo.setMinReferenceScore(minScore);
        vo.setInsufficientReferences(references.isEmpty());
        vo.setGeneratedAt(LocalDateTime.now());
        if (references.isEmpty()) {
            vo.setAnswer("个人知识库中没有找到足够相关的内容，请先补充或上传更匹配的资料。");
            vo.setAnswerGrounded(false);
            vo.setCitationValid(false);
            vo.setCitationWarning("没有引用达到最低相关度。");
            vo.setCitedReferenceNumbers(List.of());
            vo.setInvalidReferenceNumbers(List.of());
            attachKnowledgeAskGovernance(vo);
            return vo;
        }

        try {
            AiCallContext ctx = new AiCallContext();
            ctx.setScene("PERSONAL_KNOWLEDGE_ASK");
            ctx.setUserId(userId);
            ctx.setBusinessId("knowledge:" + userId);
            ctx.setPrompt(buildKnowledgeAskPrompt(userId, normalizedQuestion, references));
            ctx.setResponseFormat("TEXT");
            ctx.setRequestBody(writeJson(Map.of(
                    "questionLength", normalizedQuestion.length(),
                    "referenceCount", references.size()
            )));
            RouteResult result = aiCallLogService.callAndLog(ctx);
            applyCitationValidation(vo, result.getContent(), references.size());
            applyGroundingCheck(vo, result.getContent(), references, userId);
            vo.setAiCallLogId(result.getAiCallLogId());
        } catch (Exception ex) {
            log.warn("Personal knowledge ask generation failed userId={}", userId, ex);
            vo.setAnswer("已找到相关引用，但暂时无法生成 AI 答案，请先查看下方检索到的资料。");
            vo.setAnswerGrounded(false);
            vo.setCitationValid(false);
            vo.setCitationWarning("答案生成失败，未进入引用校验。");
            vo.setCitedReferenceNumbers(List.of());
            vo.setInvalidReferenceNumbers(List.of());
        }
        attachKnowledgeAskGovernance(vo);
        return vo;
    }

    @Override
    public void askKnowledgeStream(Long userId, KnowledgeAskDTO dto,
                                   AgentV4OpsService.KnowledgeAskStreamListener listener) {
        try {
            String question = dto == null ? null : dto.getQuestion();
            if (!StringUtils.hasText(question)) {
                listener.onError("问题不能为空");
                return;
            }
            String normalizedQuestion = question.trim();
            int limit = dto == null || dto.getLimit() == null
                    ? knowledgeProperties.safeAskDefaultLimit() : normalizeLimit(dto.getLimit());
            double minScore = dto == null || dto.getMinScore() == null
                    ? knowledgeProperties.safeAskMinScore() : normalizeScore(dto.getMinScore());
            String documentType = dto == null ? null : normalizeKeyword(dto.getDocumentType());
            Long documentId = dto == null ? null : dto.getDocumentId();

            List<KnowledgeSearchResultVO> references = searchKnowledge(userId, normalizedQuestion, limit, minScore,
                    documentId, documentType).stream()
                    .filter(reference -> reference.getScore() != null && reference.getScore() >= minScore)
                    .toList();
            listener.onReferences(references);

            if (references.isEmpty()) {
                KnowledgeAskVO empty = new KnowledgeAskVO();
                empty.setQuestion(normalizedQuestion);
                empty.setReferences(references);
                empty.setReferenceCount(0);
                empty.setInsufficientReferences(true);
                empty.setAnswer("个人知识库中没有找到足够相关的内容，请先补充或上传更匹配的资料。");
                empty.setAnswerGrounded(false);
                empty.setCitationValid(false);
                empty.setCitationWarning("没有引用达到最低相关度。");
                empty.setCitedReferenceNumbers(List.of());
                empty.setInvalidReferenceNumbers(List.of());
                empty.setGeneratedAt(LocalDateTime.now());
                attachKnowledgeAskGovernance(empty);
                listener.onCitation(empty);
                listener.onDone(null);
                return;
            }

            AiCallContext ctx = new AiCallContext();
            ctx.setScene("PERSONAL_KNOWLEDGE_ASK");
            ctx.setUserId(userId);
            ctx.setBusinessId("knowledge:" + userId);
            ctx.setPrompt(buildKnowledgeAskPrompt(userId, normalizedQuestion, references));
            ctx.setResponseFormat("TEXT");
            ctx.setRequestBody(writeJson(Map.of(
                    "questionLength", normalizedQuestion.length(),
                    "referenceCount", references.size(),
                    "stream", true
            )));
            try {
                RouteResult result = aiCallLogService.callStreamAndLog(ctx, listener::onToken);
                KnowledgeAskVO vo = new KnowledgeAskVO();
                vo.setQuestion(normalizedQuestion);
                vo.setReferences(references);
                vo.setReferenceCount(references.size());
                vo.setMinReferenceScore(minScore);
                vo.setGeneratedAt(LocalDateTime.now());
                applyCitationValidation(vo, result.getContent(), references.size());
                applyGroundingCheck(vo, result.getContent(), references, userId);
                vo.setAiCallLogId(result.getAiCallLogId());
                attachKnowledgeAskGovernance(vo);
                listener.onCitation(vo);
                listener.onDone(result.getAiCallLogId());
            } catch (Exception ex) {
                log.warn("Personal knowledge ask stream generation failed userId={}", userId, ex);
                listener.onError("答案生成失败，请先查看下方检索到的引用资料。");
            }
        } catch (Exception ex) {
            log.warn("Personal knowledge ask stream failed userId={}", userId, ex);
            listener.onError(firstText(ex.getMessage(), "知识库问答流式生成失败"));
        }
    }

    @Override
    public KnowledgeEvaluationVO evaluateKnowledge(Long userId, KnowledgeEvaluationDTO dto) {
        List<KnowledgeEvaluationDTO.Sample> samples = dto == null || dto.getSamples() == null
                ? List.of()
                : dto.getSamples().stream()
                .filter(Objects::nonNull)
                .filter(sample -> StringUtils.hasText(sample.getQuery()))
                .limit(100)
                .toList();
        int limit = dto == null || dto.getLimit() == null ? knowledgeProperties.safeAskDefaultLimit() : normalizeLimit(dto.getLimit());
        double minScore = dto == null || dto.getMinScore() == null
                ? knowledgeProperties.safeAskMinScore()
                : normalizeScore(dto.getMinScore());
        KnowledgeEvaluationVO vo = new KnowledgeEvaluationVO();
        vo.setSampleCount(samples.size());
        vo.setLimit(limit);
        vo.setMinScore(minScore);
        vo.setGeneratedAt(LocalDateTime.now());
        if (samples.isEmpty()) {
            vo.setEvaluatedCount(0);
            vo.setPassedCount(0);
            vo.setFailedCount(0);
            vo.setPassRate(0D);
            vo.setItems(List.of());
            return vo;
        }
        List<KnowledgeEvaluationVO.Item> items = samples.stream()
                .map(sample -> evaluateKnowledgeSample(userId, sample, limit, minScore))
                .toList();
        int passed = (int) items.stream().filter(item -> Boolean.TRUE.equals(item.getPassed())).count();
        vo.setEvaluatedCount(items.size());
        vo.setPassedCount(passed);
        vo.setFailedCount(items.size() - passed);
        vo.setPassRate(items.isEmpty() ? 0D : Math.round((passed * 10000D) / items.size()) / 100D);
        vo.setItems(items);
        return vo;
    }

    @Override
    public KnowledgeVectorRebuildVO rebuildKnowledgeVectors(Long userId, Long documentId) {
        List<PersonalKnowledgeDocument> documents = listRebuildDocuments(userId, documentId);
        return rebuildKnowledgeVectorsForDocuments(userId, documents);
    }

    @Override
    public KnowledgeVectorRebuildVO retryFailedKnowledgeVectors(Long userId, Integer limit) {
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 500));
        int vectorDeleted = retryPendingKnowledgeVectorDeletes(size);
        List<Long> documentIds = jdbcTemplate.queryForList("""
                SELECT document_id
                FROM personal_knowledge_chunk
                WHERE deleted = 0 AND user_id = ?
                  AND (
                    index_status = 'FAILED'
                    OR (index_status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
                  )
                GROUP BY document_id
                ORDER BY MIN(updated_at) ASC
                LIMIT ?
                """, Long.class, userId, size);
        if (documentIds.isEmpty()) {
            KnowledgeVectorRebuildVO vo = new KnowledgeVectorRebuildVO();
            applyKnowledgeVectorCapability(vo);
            vo.setDocumentCount(0);
            vo.setChunkCount(0);
            vo.setVectorUpdated(0);
            vo.setVectorDeleted(vectorDeleted);
            vo.setDuplicateChunkCount(0);
            vo.setFailedDocuments(List.of());
            vo.setErrors(List.of());
            return vo;
        }
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .in(PersonalKnowledgeDocument::getId, documentIds)
                        .orderByDesc(PersonalKnowledgeDocument::getUpdatedAt));
        KnowledgeVectorRebuildVO vo = rebuildKnowledgeVectorsForDocuments(userId, documents);
        vo.setVectorDeleted(vectorDeleted);
        return vo;
    }

    private KnowledgeVectorRebuildVO rebuildKnowledgeVectorsForDocuments(Long userId, List<PersonalKnowledgeDocument> documents) {
        return rebuildKnowledgeVectorsForDocuments(documents.stream()
                .filter(document -> userId != null && userId.equals(document.getUserId()))
                .toList());
    }

    private KnowledgeVectorRebuildVO rebuildKnowledgeVectorsForDocuments(List<PersonalKnowledgeDocument> documents) {
        boolean semanticEnabled = semanticKnowledgeEnabled();
        int chunkCount = 0;
        int vectorUpdated = 0;
        int duplicateChunkCount = 0;
        List<Long> failedDocuments = new ArrayList<>();
        List<String> failedDocumentRefs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (!semanticEnabled && !documents.isEmpty()) {
            errors.add(embeddingDisabledReason());
        }

        for (PersonalKnowledgeDocument document : documents) {
            Long ownerId = document.getUserId();
            if (ownerId == null || document.getId() == null) {
                continue;
            }
            List<PersonalKnowledgeChunk> chunks = listDocumentChunks(ownerId, document.getId());
            chunkCount += chunks.size();
            duplicateChunkCount += duplicateChunkCount(chunks);
            if (!semanticEnabled || chunks.isEmpty()) {
                continue;
            }
            try {
                vectorUpdated += upsertPersonalKnowledgeVectors(ownerId, document, chunks);
            } catch (Exception ex) {
                markPersonalKnowledgeIndexFailed(chunks, ex);
                failedDocuments.add(document.getId());
                String documentRef = maskOperationalIdentifier(document.getId());
                failedDocumentRefs.add(documentRef);
                String error = "documentRef=" + documentRef + ": " + sanitizeOperationalError(ex);
                errors.add(error);
                log.warn("Personal knowledge vector rebuild failed userId={} documentId={}", ownerId, document.getId(), ex);
            }
        }

        KnowledgeVectorRebuildVO vo = new KnowledgeVectorRebuildVO();
        applyKnowledgeVectorCapability(vo);
        vo.setDocumentCount(documents.size());
        vo.setChunkCount(chunkCount);
        vo.setVectorUpdated(vectorUpdated);
        vo.setVectorDeleted(0);
        vo.setDuplicateChunkCount(duplicateChunkCount);
        vo.setFailedDocuments(List.of());
        vo.setFailedDocumentCount(failedDocuments.size());
        vo.setFailedDocumentRefs(failedDocumentRefs);
        vo.setErrors(errors);
        return vo;
    }

    @Override
    public KnowledgeVectorRebuildVO rebuildAllKnowledgeVectors(Integer limit) {
        int size = normalizeAdminKnowledgeVectorLimit(limit);
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .orderByAsc(PersonalKnowledgeDocument::getId)
                        .last("LIMIT " + size));
        return rebuildKnowledgeVectorsForDocuments(documents);
    }

    @Override
    public KnowledgeVectorRebuildVO retryAllFailedKnowledgeVectors(Integer limit) {
        int size = normalizeAdminKnowledgeVectorLimit(limit);
        int vectorDeleted = retryPendingKnowledgeVectorDeletes(size);
        List<Long> documentIds = jdbcTemplate.queryForList("""
                SELECT document_id
                FROM personal_knowledge_chunk
                WHERE deleted = 0
                  AND (
                    index_status = 'FAILED'
                    OR (index_status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
                  )
                GROUP BY document_id
                ORDER BY MIN(updated_at) ASC
                LIMIT ?
                """, Long.class, size);
        if (documentIds.isEmpty()) {
            KnowledgeVectorRebuildVO vo = new KnowledgeVectorRebuildVO();
            applyKnowledgeVectorCapability(vo);
            vo.setDocumentCount(0);
            vo.setChunkCount(0);
            vo.setVectorUpdated(0);
            vo.setVectorDeleted(vectorDeleted);
            vo.setDuplicateChunkCount(0);
            vo.setFailedDocuments(List.of());
            vo.setErrors(List.of());
            return vo;
        }
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .in(PersonalKnowledgeDocument::getId, documentIds)
                        .orderByDesc(PersonalKnowledgeDocument::getUpdatedAt));
        KnowledgeVectorRebuildVO vo = rebuildKnowledgeVectorsForDocuments(documents);
        vo.setVectorDeleted(vectorDeleted);
        return vo;
    }

    private void applyKnowledgeVectorCapability(KnowledgeVectorRebuildVO vo) {
        boolean embeddingEnabled = embeddingEnabled();
        boolean semanticEnabled = vectorStoreClient.isEnabled() && embeddingEnabled;
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setEmbeddingEnabled(embeddingEnabled);
        vo.setSemanticEnabled(semanticEnabled);
        vo.setEmbeddingDisabledReason(semanticEnabled ? null : embeddingDisabledReason());
    }

    private int normalizeAdminKnowledgeVectorLimit(Integer limit) {
        return limit == null ? 500 : Math.max(1, Math.min(limit, 5000));
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
            throw new IllegalArgumentException("指标编码和指标名称不能为空");
        }
        AnalyticsMetricDefinition metric = dto.getId() == null
                ? new AnalyticsMetricDefinition()
                : analyticsMetricDefinitionMapper.selectById(dto.getId());
        if (metric == null) {
            throw new IllegalArgumentException("指标不存在");
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
            throw new IllegalArgumentException("任务日志不存在");
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
                errors.add("userRef=" + maskOperationalIdentifier(userId) + ": " + sanitizeOperationalError(ex));
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
        int userLimit = normalizeDailyPlanUserLimit(request.getUserLimit());
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT u.id
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
                WHERE u.deleted = 0
                  AND u.status = 1
                  AND r.role_code = 'USER'
                ORDER BY u.id
                LIMIT ?
                """, Long.class, userLimit);
    }

    private int normalizeDailyPlanUserLimit(Integer userLimit) {
        if (userLimit == null || userLimit < 1) {
            return 100;
        }
        return Math.min(userLimit, 1000);
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用例名称、提示词类型和输入 JSON 不能为空");
        }
        PromptRegressionCase item = dto.getId() == null ? new PromptRegressionCase() : promptRegressionCaseMapper.selectById(dto.getId());
        if (item == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提示词回归用例不存在");
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
            throw new IllegalArgumentException("提示词回归用例不存在");
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
            result.setErrorMessage(sanitizeOperationalError(ex));
            result.setOutputJson("{}");
        }
        promptRegressionResultMapper.insert(result);
        return toPromptResultVO(result);
    }

    private void validateFeedbackOwnership(Long userId, AgentFeedbackCreateDTO dto) {
        if (dto.getAgentTaskId() != null) {
            AgentTask task = agentTaskMapper.selectById(dto.getAgentTaskId());
            if (task == null || !Objects.equals(task.getUserId(), userId)) {
                throw new IllegalArgumentException("Agent 任务不存在或无权访问");
            }
            if (dto.getAgentRunId() == null) {
                dto.setAgentRunId(task.getAgentRunId());
            }
        }
        if (dto.getAgentRunId() != null) {
            AgentRun run = agentRunMapper.selectById(dto.getAgentRunId());
            if (run == null || !Objects.equals(run.getUserId(), userId)) {
                throw new IllegalArgumentException("Agent 运行记录不存在或无权访问");
            }
        }
    }

    private String normalizeAgentFeedbackType(String feedbackType) {
        String normalized = normalizeOptionalAgentFeedbackType(feedbackType);
        if (!StringUtils.hasText(normalized) || !SUPPORTED_AGENT_FEEDBACK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("涓嶆敮鎸佺殑鍙嶉绫诲瀷");
        }
        return normalized;
    }

    private String normalizeOptionalAgentFeedbackType(String feedbackType) {
        return StringUtils.hasText(feedbackType) ? feedbackType.trim().toUpperCase(Locale.ROOT) : null;
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
            throw new IllegalArgumentException("知识文档不存在或无权访问");
        }
        return document;
    }

    private PersonalKnowledgeChunk ownedChunk(Long userId, Long id) {
        PersonalKnowledgeChunk chunk = personalKnowledgeChunkMapper.selectById(id);
        if (chunk == null || !Objects.equals(chunk.getUserId(), userId)) {
            throw new IllegalArgumentException("知识片段不存在或无权访问");
        }
        return chunk;
    }

    private PersonalKnowledgeDocumentVersion ownedKnowledgeDocumentVersion(Long userId, Long documentId, Long versionId) {
        PersonalKnowledgeDocumentVersion version = personalKnowledgeDocumentVersionMapper.selectById(versionId);
        if (version == null
                || !Objects.equals(version.getUserId(), userId)
                || !Objects.equals(version.getDocumentId(), documentId)) {
            throw new IllegalArgumentException("知识文档版本不存在或无权访问");
        }
        return version;
    }

    private void snapshotKnowledgeDocumentVersion(Long userId, PersonalKnowledgeDocument document, int chunkCount) {
        Integer currentMax = personalKnowledgeDocumentVersionMapper.selectList(
                        new LambdaQueryWrapper<PersonalKnowledgeDocumentVersion>()
                                .eq(PersonalKnowledgeDocumentVersion::getUserId, userId)
                                .eq(PersonalKnowledgeDocumentVersion::getDocumentId, document.getId())
                                .select(PersonalKnowledgeDocumentVersion::getVersionNo)
                                .orderByDesc(PersonalKnowledgeDocumentVersion::getVersionNo)
                                .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(PersonalKnowledgeDocumentVersion::getVersionNo)
                .orElse(0);
        PersonalKnowledgeDocumentVersion version = new PersonalKnowledgeDocumentVersion();
        version.setUserId(userId);
        version.setDocumentId(document.getId());
        version.setVersionNo(currentMax + 1);
        version.setTitle(document.getTitle());
        version.setDocumentType(document.getDocumentType());
        version.setContent(document.getContent());
        version.setContentHash(document.getContentHash());
        version.setNormalizationVersion(firstText(document.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
        version.setChunkCount(chunkCount);
        personalKnowledgeDocumentVersionMapper.insert(version);
    }

    private int chunkCount(Long documentId) {
        return personalKnowledgeChunkMapper.selectCount(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getDocumentId, documentId)).intValue();
    }

    private void updateDocumentStatus(Long documentId, String status) {
        if (documentId == null || !StringUtils.hasText(status)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE personal_knowledge_document
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND deleted = 0
                """, status, documentId);
    }

    private String aggregateDocumentIndexStatus(Long documentId, int chunkCount, String fallbackStatus) {
        if (chunkCount <= 0) {
            return "EMPTY";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COALESCE(index_status, 'PENDING') AS status, COUNT(1) AS count
                FROM personal_knowledge_chunk
                WHERE deleted = 0 AND document_id = ?
                GROUP BY COALESCE(index_status, 'PENDING')
                """, documentId);
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("status")),
                        row -> ((Number) row.get("count")).longValue(),
                        Long::sum,
                        LinkedHashMap::new));
        if (counts.getOrDefault("FAILED", 0L) > 0) {
            return "FAILED";
        }
        if (counts.getOrDefault("PENDING", 0L) > 0) {
            return "PENDING";
        }
        if (counts.getOrDefault("INDEXED", 0L) == chunkCount) {
            return "INDEXED";
        }
        if (counts.getOrDefault("DISABLED", 0L) == chunkCount) {
            return "INDEXED";
        }
        return firstText(fallbackStatus, "PENDING");
    }

    private PersonalKnowledgeDocument findExistingKnowledgeDocument(Long userId, String contentHash) {
        return findExistingKnowledgeDocument(userId, contentHash, null);
    }

    private PersonalKnowledgeDocument findExistingKnowledgeDocument(Long userId, String contentHash, Long excludeDocumentId) {
        if (!StringUtils.hasText(contentHash)) {
            return null;
        }
        PersonalKnowledgeDocument matched = personalKnowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                .eq(PersonalKnowledgeDocument::getUserId, userId)
                .eq(PersonalKnowledgeDocument::getContentHash, contentHash)
                .ne(excludeDocumentId != null, PersonalKnowledgeDocument::getId, excludeDocumentId)
                .last("LIMIT 1"));
        if (matched != null) {
            return matched;
        }
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .ne(excludeDocumentId != null, PersonalKnowledgeDocument::getId, excludeDocumentId)
                        .select(PersonalKnowledgeDocument::getId,
                                PersonalKnowledgeDocument::getUserId,
                                PersonalKnowledgeDocument::getTitle,
                                PersonalKnowledgeDocument::getDocumentType,
                                PersonalKnowledgeDocument::getContent,
                                PersonalKnowledgeDocument::getContentHash,
                                PersonalKnowledgeDocument::getNormalizationVersion,
                                PersonalKnowledgeDocument::getStatus)
                        .orderByAsc(PersonalKnowledgeDocument::getId));
        for (PersonalKnowledgeDocument document : documents) {
            String canonicalHash = knowledgeHash(document.getContent());
            if (!contentHash.equals(canonicalHash)) {
                continue;
            }
            if (!contentHash.equals(document.getContentHash())
                    || !KNOWLEDGE_NORMALIZATION_VERSION.equals(document.getNormalizationVersion())) {
                document.setContentHash(contentHash);
                document.setNormalizationVersion(KNOWLEDGE_NORMALIZATION_VERSION);
                personalKnowledgeDocumentMapper.updateById(document);
            }
            return document;
        }
        return null;
    }

    private Set<String> existingChunkHashes(Long userId) {
        return personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .select(PersonalKnowledgeChunk::getChunkHash, PersonalKnowledgeChunk::getContent))
                .stream()
                .map(chunk -> firstText(chunk.getChunkHash(), knowledgeHash(chunk.getContent())))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private int duplicateChunkCount(List<PersonalKnowledgeChunk> chunks) {
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        for (PersonalKnowledgeChunk chunk : chunks) {
            String hash = chunk.getChunkHash();
            if (StringUtils.hasText(hash) && !seen.add(hash)) {
                duplicates++;
            }
        }
        return duplicates;
    }

    private List<List<PersonalKnowledgeChunk>> exactDuplicateChunkGroups(Long userId, Integer limit) {
        return exactDuplicateChunkGroups(userId, limit, null, null);
    }

    private List<List<PersonalKnowledgeChunk>> exactDuplicateChunkGroups(Long userId, Integer limit,
                                                                        Long documentId, String documentType) {
        int size = normalizeDuplicateReviewLimit(limit);
        List<Long> scopedDocumentIds = scopedKnowledgeDocumentIds(userId, documentId, documentType);
        if (scopedDocumentIds.isEmpty()) {
            return List.of();
        }
        Set<Long> scopedDocumentIdSet = new HashSet<>(scopedDocumentIds);
        return exactDuplicateGroups(exactDuplicateScopeChunks(userId, scopedDocumentIds))
                .values()
                .stream()
                .filter(group -> group.stream().anyMatch(chunk -> scopedDocumentIdSet.contains(chunk.getDocumentId())))
                .filter(group -> group.size() > 1)
                .sorted((left, right) -> Integer.compare(right.size(), left.size()))
                .limit(size)
                .toList();
    }

    private Map<String, List<PersonalKnowledgeChunk>> exactDuplicateGroups(List<PersonalKnowledgeChunk> chunks) {
        return chunks.stream()
                .filter(chunk -> StringUtils.hasText(chunk.getChunkHash()))
                .sorted(Comparator.comparing(PersonalKnowledgeChunk::getChunkHash)
                        .thenComparing(PersonalKnowledgeChunk::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PersonalKnowledgeChunk::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(PersonalKnowledgeChunk::getChunkHash, LinkedHashMap::new, Collectors.toList()));
    }

    private List<PersonalKnowledgeChunk> exactDuplicateScopeChunks(Long userId, List<Long> scopedDocumentIds) {
        List<String> scopedHashes = personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .in(PersonalKnowledgeChunk::getDocumentId, scopedDocumentIds)
                        .isNotNull(PersonalKnowledgeChunk::getChunkHash)
                        .select(PersonalKnowledgeChunk::getChunkHash))
                .stream()
                .map(PersonalKnowledgeChunk::getChunkHash)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (scopedHashes.isEmpty()) {
            return List.of();
        }
        return personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getUserId, userId)
                .in(PersonalKnowledgeChunk::getChunkHash, scopedHashes)
                .orderByAsc(PersonalKnowledgeChunk::getChunkHash)
                .orderByAsc(PersonalKnowledgeChunk::getCreatedAt)
                .orderByAsc(PersonalKnowledgeChunk::getId));
    }

    private List<Long> scopedKnowledgeDocumentIds(Long userId, Long documentId, String documentType) {
        LambdaQueryWrapper<PersonalKnowledgeDocument> query = new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                .eq(PersonalKnowledgeDocument::getUserId, userId)
                .eq(documentId != null, PersonalKnowledgeDocument::getId, documentId)
                .eq(StringUtils.hasText(documentType), PersonalKnowledgeDocument::getDocumentType, normalizeKeyword(documentType))
                .select(PersonalKnowledgeDocument::getId);
        return personalKnowledgeDocumentMapper.selectList(query).stream()
                .map(PersonalKnowledgeDocument::getId)
                .toList();
    }

    private List<PersonalKnowledgeChunk> exactDuplicateCleanupCandidates(List<PersonalKnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return exactDuplicateGroups(chunks)
                .values()
                .stream()
                .filter(group -> group.size() > 1)
                .flatMap(group -> group.stream().skip(1))
                .toList();
    }

    private List<PersonalKnowledgeDocument> listRebuildDocuments(Long userId, Long documentId) {
        if (documentId != null) {
            return List.of(ownedDocument(userId, documentId));
        }
        return personalKnowledgeDocumentMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                .eq(PersonalKnowledgeDocument::getUserId, userId)
                .orderByAsc(PersonalKnowledgeDocument::getId));
    }

    private KnowledgeDocumentVO rebuildKnowledgeDocumentChunks(Long userId, PersonalKnowledgeDocument document,
                                                              String normalizedContent, boolean includeContent) {
        int index = 0;
        int duplicateChunkCount = 0;
        List<PersonalKnowledgeChunk> chunks = new ArrayList<>();
        Set<String> existingChunkHashes = existingChunkHashes(userId);
        Set<String> seenChunkHashes = new HashSet<>();
        for (KnowledgeChunkDraft draft : splitChunkDrafts(document.getTitle(), normalizedContent)) {
            String chunkContent = draft.content();
            String chunkHash = knowledgeHash(chunkContent);
            if (!StringUtils.hasText(chunkHash) || !seenChunkHashes.add(chunkHash)) {
                duplicateChunkCount++;
                continue;
            }
            if (existingChunkHashes.contains(chunkHash)) {
                duplicateChunkCount++;
            }
            PersonalKnowledgeChunk chunk = new PersonalKnowledgeChunk();
            chunk.setUserId(userId);
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkContent);
            chunk.setChunkHash(chunkHash);
            chunk.setNormalizationVersion(KNOWLEDGE_NORMALIZATION_VERSION);
            chunk.setSourceRef(truncateText(firstText(draft.sourceRef(), document.getTitle() + "#" + index), 200));
            boolean semanticEnabled = semanticKnowledgeEnabled();
            chunk.setIndexStatus(semanticEnabled ? "PENDING" : "DISABLED");
            personalKnowledgeChunkMapper.insert(chunk);
            chunks.add(chunk);
        }
        boolean semanticEnabled = semanticKnowledgeEnabled();
        boolean asyncIndex = knowledgeProperties.isAsyncIndexEnabled() && semanticEnabled;
        int nearDuplicateChunkCount;
        if (asyncIndex) {
            // 异步模式：不在请求线程做近重统计（避免一次额外 embedding），索引提交后由线程池执行，
            // 近重统计在索引任务内复用同一批向量完成，结果延迟回填到 chunk 表。
            nearDuplicateChunkCount = 0;
            indexPersonalKnowledgeVectorsAsyncAfterCommit(userId, document, chunks);
        } else if (semanticEnabled) {
            // 同步回退：保留改造前行为（请求线程内统计近重 + 提交后索引）。
            nearDuplicateChunkCount = countNearDuplicateChunks(userId, chunks);
            indexPersonalKnowledgeVectorsAfterCommit(userId, document, chunks);
        } else {
            nearDuplicateChunkCount = 0;
        }
        if (chunks.isEmpty()) {
            updateDocumentStatus(document.getId(), "EMPTY");
        }
        KnowledgeDocumentVO vo = toKnowledgeDocumentVO(document, index, includeContent);
        vo.setDuplicateChunkCount(duplicateChunkCount);
        vo.setNearDuplicateChunkCount(nearDuplicateChunkCount);
        vo.setNearDuplicateThreshold(knowledgeProperties.safeNearDuplicateThreshold());
        return vo;
    }

    private List<PersonalKnowledgeChunk> listDocumentChunks(Long userId, Long documentId) {
        return personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getUserId, userId)
                .eq(PersonalKnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(PersonalKnowledgeChunk::getChunkIndex)
                .orderByAsc(PersonalKnowledgeChunk::getId));
    }

    private List<KnowledgeChunkDraft> splitChunkDrafts(String documentTitle, String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String normalized = normalizeKnowledgeContent(content);
        List<SemanticBlockDraft> blocks = splitSemanticBlockDrafts(documentTitle, normalized);
        List<KnowledgeChunkDraft> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentSourceRef = null;
        int currentBlockCount = 0;
        for (SemanticBlockDraft block : blocks) {
            List<String> parts = splitOversizedBlock(block.content());
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                String sourceRef = parts.size() > 1 ? block.sourceRef() + " / part " + (i + 1) : block.sourceRef();
                if (current.isEmpty()) {
                    current.append(part);
                    currentSourceRef = sourceRef;
                    currentBlockCount = 1;
                    continue;
                }
                if (current.length() + 2 + part.length() <= knowledgeProperties.safeChunkSize()) {
                    current.append("\n\n").append(part);
                    currentBlockCount++;
                    continue;
                }
                String overlap = tailOverlap(current.toString());
                flushKnowledgeChunkDraft(chunks, current, currentSourceRef, currentBlockCount);
                if (StringUtils.hasText(overlap) && overlap.length() + 2 + part.length() <= knowledgeProperties.safeChunkSize()) {
                    current.append(overlap).append("\n\n");
                }
                current.append(part);
                currentSourceRef = sourceRef;
                currentBlockCount = 1;
            }
        }
        flushKnowledgeChunkDraft(chunks, current, currentSourceRef, currentBlockCount);
        if (chunks.isEmpty() && StringUtils.hasText(normalized)) {
            chunks.add(new KnowledgeChunkDraft(normalized, truncateText(firstText(documentTitle, "Untitled knowledge"), 200)));
        }
        return chunks;
    }
    private String normalizeKnowledgeContent(String content) {
        return TextFingerprintUtils.normalizeContent(content);
    }

    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String fileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase();
    }

    private String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "Untitled knowledge";
        }
        int index = filename.lastIndexOf('.');
        String value = index > 0 ? filename.substring(0, index) : filename;
        return StringUtils.hasText(value) ? value.trim() : "Untitled knowledge";
    }

    private String documentTypeByExtension(String extension) {
        return switch (extension) {
            case "pdf" -> "PDF";
            case "doc", "docx" -> "WORD";
            case "txt" -> "TEXT";
            default -> "MARKDOWN";
        };
    }

    private String extractKnowledgeFileText(String extension, InputStream inputStream) throws Exception {
        int maxChars = knowledgeProperties.safeUploadMaxTextChars();
        String text = switch (extension) {
            case "pdf" -> extractPdfText(inputStream, maxChars);
            case "docx" -> extractDocxText(inputStream, maxChars);
            case "doc" -> extractDocText(inputStream, maxChars);
            default -> extractPlainText(inputStream, maxChars);
        };
        String normalized = normalizeKnowledgeContent(text);
        if (normalized.length() > maxChars) {
            log.warn("Personal knowledge uploaded text truncated, extension={}, originalChars={}, maxChars={}",
                    extension, normalized.length(), maxChars);
            return normalized.substring(0, maxChars);
        }
        return normalized;
    }

    private String extractPdfText(InputStream inputStream, int maxChars) throws Exception {
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(buffer)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(document.getNumberOfPages(), knowledgeProperties.safeUploadMaxPdfPages()));
            return limitText(stripper.getText(document), maxChars);
        }
    }

    private String extractDocxText(InputStream inputStream, int maxChars) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder(Math.min(maxChars, 8192));
            for (var paragraph : document.getParagraphs()) {
                if (!appendLimitedLine(builder, firstText(paragraph.getText(), ""), maxChars)) {
                    break;
                }
            }
            return builder.toString();
        }
    }

    private String extractDocText(InputStream inputStream, int maxChars) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            StringBuilder builder = new StringBuilder(Math.min(maxChars, 8192));
            for (String paragraph : extractor.getParagraphText()) {
                if (!appendLimitedLine(builder, paragraph, maxChars)) {
                    break;
                }
            }
            return builder.toString();
        }
    }

    private String extractPlainText(InputStream inputStream, int maxChars) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder(Math.min(maxChars, 8192));
            char[] buffer = new char[4096];
            int length;
            while (builder.length() < maxChars && (length = reader.read(buffer)) != -1) {
                int remaining = maxChars - builder.length();
                builder.append(buffer, 0, Math.min(length, remaining));
            }
            return builder.toString();
        }
    }

    private boolean appendLimitedLine(StringBuilder builder, String line, int maxChars) {
        if (!StringUtils.hasText(line) || builder.length() >= maxChars) {
            return builder.length() < maxChars;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        int remaining = maxChars - builder.length();
        if (remaining <= 0) {
            return false;
        }
        String value = line.trim();
        builder.append(value, 0, Math.min(value.length(), remaining));
        return builder.length() < maxChars;
    }

    private String limitText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private String normalizeKnowledgeFingerprint(String content) {
        return TextFingerprintUtils.normalizeFingerprint(content);
    }

    private String knowledgeHash(String content) {
        return TextFingerprintUtils.sha256Hex(normalizeKnowledgeFingerprint(content));
    }

    private List<SemanticBlockDraft> splitSemanticBlockDrafts(String documentTitle, String content) {
        List<SemanticBlockDraft> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] headingStack = new String[6];
        boolean inCodeBlock = false;
        boolean currentCodeBlock = false;
        int paragraphOrdinal = 0;
        int codeOrdinal = 0;
        int headingOrdinal = 0;
        int listOrdinal = 0;
        int tableOrdinal = 0;
        int quoteOrdinal = 0;
        String currentSourceRef = null;
        String currentBlockType = null;
        for (String rawLine : content.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();
            boolean codeFence = trimmed.startsWith("```");
            boolean heading = !inCodeBlock && isMarkdownHeading(line);
            boolean blank = !StringUtils.hasText(line);
            boolean tableLine = !inCodeBlock && isMarkdownTableLine(line);
            boolean listLine = !inCodeBlock && isMarkdownListItem(line);
            boolean quoteLine = !inCodeBlock && isMarkdownQuote(line);
            if (heading) {
                flushKnowledgeBlockDraft(blocks, current, currentSourceRef, currentCodeBlock);
                int level = markdownHeadingLevel(line);
                String title = markdownHeadingText(line);
                if (level >= 1 && level <= headingStack.length) {
                    headingStack[level - 1] = title;
                    for (int i = level; i < headingStack.length; i++) {
                        headingStack[i] = null;
                    }
                }
                headingOrdinal++;
                currentSourceRef = buildSourceRef(documentTitle, headingStack, "heading", headingOrdinal);
                currentCodeBlock = false;
                currentBlockType = "heading";
            } else if (!inCodeBlock && blank) {
                flushKnowledgeBlockDraft(blocks, current, currentSourceRef, currentCodeBlock);
                currentSourceRef = null;
                currentCodeBlock = false;
                currentBlockType = null;
                continue;
            } else if (codeFence && !inCodeBlock && !current.isEmpty() && !currentCodeBlock) {
                flushKnowledgeBlockDraft(blocks, current, currentSourceRef, currentCodeBlock);
                codeOrdinal++;
                currentCodeBlock = true;
                currentBlockType = "code";
                currentSourceRef = buildSourceRef(documentTitle, headingStack, "code", codeOrdinal);
            } else if (!inCodeBlock && shouldStartNewStructuredBlock(current, currentBlockType, tableLine, listLine, quoteLine)) {
                flushKnowledgeBlockDraft(blocks, current, currentSourceRef, currentCodeBlock);
                currentCodeBlock = false;
                currentBlockType = null;
                currentSourceRef = null;
            }
            if (current.isEmpty() && currentSourceRef == null) {
                if (codeFence) {
                    codeOrdinal++;
                    currentCodeBlock = true;
                    currentBlockType = "code";
                    currentSourceRef = buildSourceRef(documentTitle, headingStack, "code", codeOrdinal);
                } else if (tableLine) {
                    tableOrdinal++;
                    currentCodeBlock = false;
                    currentBlockType = "table";
                    currentSourceRef = buildSourceRef(documentTitle, headingStack, "table", tableOrdinal);
                } else if (listLine) {
                    listOrdinal++;
                    currentCodeBlock = false;
                    currentBlockType = "list";
                    currentSourceRef = buildSourceRef(documentTitle, headingStack, "list", listOrdinal);
                } else if (quoteLine) {
                    quoteOrdinal++;
                    currentCodeBlock = false;
                    currentBlockType = "quote";
                    currentSourceRef = buildSourceRef(documentTitle, headingStack, "quote", quoteOrdinal);
                } else {
                    paragraphOrdinal++;
                    currentCodeBlock = false;
                    currentBlockType = "paragraph";
                    currentSourceRef = buildSourceRef(documentTitle, headingStack, "paragraph", paragraphOrdinal);
                }
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
            if (codeFence) {
                inCodeBlock = !inCodeBlock;
            }
        }
        flushKnowledgeBlockDraft(blocks, current, currentSourceRef, currentCodeBlock);
        return blocks;
    }

    private boolean isMarkdownHeading(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }
        int count = 0;
        while (count < trimmed.length() && trimmed.charAt(count) == '#') {
            count++;
        }
        return count > 0 && count <= 6 && count < trimmed.length() && Character.isWhitespace(trimmed.charAt(count));
    }

    private boolean shouldStartNewStructuredBlock(StringBuilder current, String currentBlockType,
                                                  boolean tableLine, boolean listLine, boolean quoteLine) {
        if (current.isEmpty() || !StringUtils.hasText(currentBlockType)) {
            return false;
        }
        String nextBlockType = nextStructuredBlockType(tableLine, listLine, quoteLine);
        return nextBlockType != null && !nextBlockType.equals(currentBlockType);
    }

    private String nextStructuredBlockType(boolean tableLine, boolean listLine, boolean quoteLine) {
        if (tableLine) {
            return "table";
        }
        if (listLine) {
            return "list";
        }
        if (quoteLine) {
            return "quote";
        }
        return null;
    }

    private boolean isMarkdownListItem(String line) {
        return MARKDOWN_LIST_ITEM_PATTERN.matcher(line).matches();
    }

    private boolean isMarkdownTableLine(String line) {
        String trimmed = line.trim();
        if (!trimmed.contains("|")) {
            return false;
        }
        return trimmed.startsWith("|") || trimmed.endsWith("|") || trimmed.indexOf('|') != trimmed.lastIndexOf('|');
    }

    private boolean isMarkdownQuote(String line) {
        return line.trim().startsWith(">");
    }

    private int markdownHeadingLevel(String line) {
        String trimmed = line.trim();
        int count = 0;
        while (count < trimmed.length() && trimmed.charAt(count) == '#') {
            count++;
        }
        return count;
    }

    private String markdownHeadingText(String line) {
        String trimmed = line.trim();
        int level = markdownHeadingLevel(trimmed);
        if (level <= 0 || level >= trimmed.length()) {
            return trimmed;
        }
        return trimmed.substring(level).trim();
    }

    private String buildSourceRef(String documentTitle, String[] headingStack, String blockType, int ordinal) {
        List<String> segments = new ArrayList<>();
        segments.add(firstText(documentTitle, "Untitled knowledge"));
        if (headingStack != null) {
            for (String heading : headingStack) {
                if (StringUtils.hasText(heading)) {
                    segments.add(heading.trim());
                }
            }
        }
        segments.add(blockType + " " + ordinal);
        return truncateText(String.join(" / ", segments), 200);
    }

    private void flushKnowledgeBlockDraft(List<SemanticBlockDraft> blocks, StringBuilder current,
                                          String sourceRef, boolean codeBlock) {
        String value = current.toString().trim();
        if (StringUtils.hasText(value)) {
            blocks.add(new SemanticBlockDraft(value, truncateText(firstText(sourceRef, "Knowledge chunk"), 200), codeBlock));
        }
        current.setLength(0);
    }

    private void flushKnowledgeChunkDraft(List<KnowledgeChunkDraft> chunks, StringBuilder current,
                                          String sourceRef, int blockCount) {
        String value = current.toString().trim();
        if (StringUtils.hasText(value)) {
            String resolvedSourceRef = firstText(sourceRef, "Knowledge chunk");
            if (blockCount > 1) {
                resolvedSourceRef = resolvedSourceRef + " / +" + (blockCount - 1) + " blocks";
            }
            chunks.add(new KnowledgeChunkDraft(value, truncateText(resolvedSourceRef, 200)));
        }
        current.setLength(0);
    }

    private String tailOverlap(String value) {
        int overlap = knowledgeProperties.safeChunkOverlap();
        if (!StringUtils.hasText(value) || overlap <= 0) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= overlap) {
            return trimmed;
        }
        int start = Math.max(0, trimmed.length() - overlap);
        int boundary = Math.max(trimmed.lastIndexOf('\n', start), trimmed.lastIndexOf(' ', start));
        if (boundary > 0 && trimmed.length() - boundary <= overlap * 2) {
            start = boundary + 1;
        }
        return trimmed.substring(start).trim();
    }

    private List<String> splitOversizedBlock(String block) {
        int chunkSize = knowledgeProperties.safeChunkSize();
        int chunkOverlap = knowledgeProperties.safeChunkOverlap();
        if (block.length() <= chunkSize) {
            return List.of(block);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(block.length(), start + chunkSize);
            int splitAt = findSemanticSplit(block, start, end);
            if (splitAt <= start) {
                splitAt = end;
            }
            chunks.add(block.substring(start, splitAt).trim());
            if (splitAt >= block.length()) {
                break;
            }
            start = Math.max(splitAt - chunkOverlap, start + 1);
        }
        return chunks.stream().filter(StringUtils::hasText).toList();
    }

    private int findSemanticSplit(String text, int start, int end) {
        int min = Math.min(end, start + knowledgeProperties.safeMinChunkSize());
        String delimiters = "\u3002\uff01\uff1f\uff1b.!?\n;";
        for (int i = end - 1; i >= min; i--) {
            if (delimiters.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        int whitespace = text.lastIndexOf(' ', end - 1);
        return whitespace >= min ? whitespace : end;
    }

    private void indexPersonalKnowledgeVectors(Long userId, PersonalKnowledgeDocument document,
                                               List<PersonalKnowledgeChunk> chunks) {
        if (!semanticKnowledgeEnabled() || chunks.isEmpty()) {
            return;
        }
        try {
            int pointCount = upsertPersonalKnowledgeVectors(userId, document, chunks);
            log.debug("Personal knowledge vector indexed userId={} documentId={} chunkCount={} points={}",
                    userId, document.getId(), chunks.size(), pointCount);
        } catch (Exception ex) {
            log.warn("Personal knowledge vector indexing failed userId={} documentId={}", userId, document.getId(), ex);
            markPersonalKnowledgeIndexFailed(chunks, ex);
        }
    }

    private void indexPersonalKnowledgeVectorsAfterCommit(Long userId, PersonalKnowledgeDocument document,
                                                          List<PersonalKnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        runAfterCommit(() -> indexPersonalKnowledgeVectors(userId, document, chunks));
    }

    /**
     * 事务提交后，将向量索引任务提交到专用线程池异步执行（不阻塞请求线程）。
     * 索引内部一次 embedding 完成 upsert，并复用同批向量统计近重，结果回填到 chunk 表。
     */
    private void indexPersonalKnowledgeVectorsAsyncAfterCommit(Long userId, PersonalKnowledgeDocument document,
                                                               List<PersonalKnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<Long> chunkIds = chunks.stream()
                .map(PersonalKnowledgeChunk::getId)
                .filter(Objects::nonNull)
                .toList();
        runAfterCommit(() -> knowledgeIndexExecutor.submit(() -> {
            // 重新加载，确保拿到事务已提交的最新 chunk 状态
            List<PersonalKnowledgeChunk> committed = chunkIds.isEmpty()
                    ? List.of()
                    : personalKnowledgeChunkMapper.selectBatchIds(chunkIds);
            if (committed.isEmpty()) {
                return;
            }
            indexPersonalKnowledgeVectors(userId, document, committed);
        }));
    }

    private int countNearDuplicateChunks(Long userId, List<PersonalKnowledgeChunk> chunks) {
        if (!semanticKnowledgeEnabled() || chunks.isEmpty()) {
            return 0;
        }
        try {
            KnowledgeEmbeddingResult embedding = embedTexts(chunks.stream()
                    .map(PersonalKnowledgeChunk::getContent)
                    .toList());
            List<List<Float>> vectors = embedding.vectors();
            if (vectors.size() != chunks.size()) {
                return 0;
            }
            int count = 0;
            for (List<Float> vector : vectors) {
                List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                        .collectionName(knowledgeProperties.getCollection())
                        .vector(vector)
                        .mustMatchPayload(Map.of("userId", userId))
                        .limit(1)
                        .build());
                if (!hits.isEmpty() && hits.get(0).getScore() >= knowledgeProperties.safeNearDuplicateThreshold()) {
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            log.warn("Personal knowledge near duplicate check failed userId={}", userId, ex);
            return 0;
        }
    }

    /**
     * 对给定分块执行向量 upsert，并复用本批生成的向量统计近重数（避免二次 embedding）。
     * 近重数仅记录日志，不作为返回值，以保持调用方对返回值（写入向量点数）的既有语义。
     *
     * @return 实际 upsert 的向量点数量
     */
    private int upsertPersonalKnowledgeVectors(Long userId, PersonalKnowledgeDocument document,
                                               List<PersonalKnowledgeChunk> chunks) {
        KnowledgeEmbeddingResult embedding = embedTexts(chunks.stream()
                .map(PersonalKnowledgeChunk::getContent)
                .toList());
        List<List<Float>> vectors = embedding.vectors();
        if (vectors.size() != chunks.size() || vectors.isEmpty()) {
            throw new IllegalStateException("embedding size mismatch, chunkCount=" + chunks.size()
                    + ", vectorCount=" + vectors.size());
        }
        vectorStoreClient.ensureCollection(knowledgeProperties.getCollection(), vectors.get(0).size());
        vectorStoreClient.ensurePayloadIndexes(knowledgeProperties.getCollection(), KNOWLEDGE_PAYLOAD_INDEXES);
        // upsert 之前用本批向量统计近重（此时这些向量尚未写入库，命中的都是已存在的相似内容）
        int nearDuplicate = countNearDuplicateByVectors(userId, vectors);
        if (nearDuplicate > 0) {
            log.debug("Personal knowledge near duplicate detected userId={} documentId={} nearDuplicate={}",
                    userId, document.getId(), nearDuplicate);
        }
        List<VectorPoint> points = new ArrayList<>();
        LocalDateTime indexedAt = LocalDateTime.now();
        for (int i = 0; i < chunks.size(); i++) {
            PersonalKnowledgeChunk chunk = chunks.get(i);
            points.add(VectorPoint.builder()
                    .id(knowledgePointId(chunk.getId()))
                    .vector(vectors.get(i))
                    .payload(knowledgePayload(userId, document, chunk, embedding.model(), vectors.get(i).size(), indexedAt))
                    .build());
        }
        vectorStoreClient.upsert(knowledgeProperties.getCollection(), points);
        for (int i = 0; i < chunks.size(); i++) {
            PersonalKnowledgeChunk chunk = chunks.get(i);
            chunk.setEmbeddingDimension(vectors.get(i).size());
            chunk.setEmbeddingModel(embedding.model());
            chunk.setIndexedAt(indexedAt);
            chunk.setIndexStatus("INDEXED");
            chunk.setLastError(null);
            personalKnowledgeChunkMapper.updateById(chunk);
        }
        updateDocumentStatus(document.getId(), "INDEXED");
        return points.size();
    }

    /**
     * 用已生成的向量统计近重数量，避免重新 embedding。
     */
    private int countNearDuplicateByVectors(Long userId, List<List<Float>> vectors) {
        if (!semanticKnowledgeEnabled() || vectors == null || vectors.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (List<Float> vector : vectors) {
            try {
                List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                        .collectionName(knowledgeProperties.getCollection())
                        .vector(vector)
                        .mustMatchPayload(Map.of("userId", userId))
                        .limit(1)
                        .build());
                if (!hits.isEmpty() && hits.get(0).getScore() >= knowledgeProperties.safeNearDuplicateThreshold()) {
                    count++;
                }
            } catch (Exception ex) {
                log.warn("Personal knowledge near duplicate count by vector failed userId={}", userId, ex);
            }
        }
        return count;
    }

    private Map<String, Object> knowledgePayload(Long userId, PersonalKnowledgeDocument document,
                                                 PersonalKnowledgeChunk chunk, String embeddingModel,
                                                 Integer embeddingDimension, LocalDateTime indexedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("documentId", document.getId());
        payload.put("chunkId", chunk.getId());
        payload.put("chunkIndex", chunk.getChunkIndex());
        payload.put("title", document.getTitle());
        payload.put("documentType", firstText(document.getDocumentType(), "NOTE"));
        payload.put("sourceRef", firstText(chunk.getSourceRef(), document.getTitle()));
        if (StringUtils.hasText(chunk.getChunkHash())) {
            payload.put("chunkHash", chunk.getChunkHash());
        }
        if (StringUtils.hasText(embeddingModel)) {
            payload.put("embeddingModel", embeddingModel);
        }
        if (embeddingDimension != null) {
            payload.put("embeddingDimension", embeddingDimension);
        }
        if (indexedAt != null) {
            payload.put("indexedAt", indexedAt.toString());
        }
        return payload;
    }

    private void markPersonalKnowledgeIndexFailed(List<PersonalKnowledgeChunk> chunks, Exception ex) {
        String error = truncateText(sanitizeOperationalError(ex), 512);
        for (PersonalKnowledgeChunk chunk : chunks) {
            chunk.setIndexStatus("FAILED");
            chunk.setLastError(error);
            personalKnowledgeChunkMapper.updateById(chunk);
        }
        chunks.stream()
                .map(PersonalKnowledgeChunk::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(documentId -> updateDocumentStatus(documentId, "FAILED"));
    }

    private void deletePersonalKnowledgeVectors(List<PersonalKnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        for (PersonalKnowledgeChunk chunk : chunks) {
            chunk.setIndexStatus("DELETED");
            chunk.setLastError(null);
            personalKnowledgeChunkMapper.updateById(chunk);
        }
        chunks.stream()
                .map(PersonalKnowledgeChunk::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(documentId -> updateDocumentStatus(documentId, "PENDING"));
        List<String> pointIds = chunks.stream()
                .map(PersonalKnowledgeChunk::getId)
                .filter(Objects::nonNull)
                .map(this::knowledgePointId)
                .toList();
        if (vectorStoreClient.isEnabled() && !pointIds.isEmpty()) {
            recordVectorDeleteOutbox(pointIds);
            runAfterCommit(() -> deleteVectorPointsFromOutbox(pointIds));
        }
    }

    private void recordVectorDeleteOutbox(List<String> pointIds) {
        for (String pointId : pointIds) {
            jdbcTemplate.update("""
                    INSERT INTO vector_delete_outbox(collection_name, point_id, biz_type, status, retry_count, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, 'PENDING', 0, NOW(), NOW(), 0)
                    ON DUPLICATE KEY UPDATE status = CASE WHEN status = 'DONE' THEN 'DONE' ELSE 'PENDING' END,
                                            updated_at = NOW(),
                                            deleted = 0
                    """, knowledgeProperties.getCollection(), pointId, VECTOR_DELETE_COLLECTION_KNOWLEDGE);
        }
    }

    private int retryPendingKnowledgeVectorDeletes(int limit) {
        if (!vectorStoreClient.isEnabled()) {
            return 0;
        }
        List<String> pointIds = jdbcTemplate.queryForList("""
                SELECT point_id
                FROM vector_delete_outbox
                WHERE deleted = 0
                  AND collection_name = ?
                  AND biz_type = ?
                  AND status IN ('PENDING', 'FAILED')
                ORDER BY updated_at ASC
                LIMIT ?
                """, String.class, knowledgeProperties.getCollection(), VECTOR_DELETE_COLLECTION_KNOWLEDGE, limit);
        return deleteVectorPointsFromOutbox(pointIds);
    }

    private int deleteVectorPointsFromOutbox(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return 0;
        }
        try {
            vectorStoreClient.delete(knowledgeProperties.getCollection(), pointIds);
            jdbcTemplate.update("""
                    UPDATE vector_delete_outbox
                    SET status = 'DONE', last_error = NULL, updated_at = NOW()
                    WHERE collection_name = ? AND point_id IN (%s)
                    """.formatted(sqlPlaceholders(pointIds.size())),
                    vectorDeleteSqlArgs(pointIds).toArray());
            return pointIds.size();
        } catch (Exception ex) {
            jdbcTemplate.update("""
                    UPDATE vector_delete_outbox
                    SET status = 'FAILED', retry_count = retry_count + 1, last_error = ?, updated_at = NOW()
                    WHERE collection_name = ? AND point_id IN (%s)
                    """.formatted(sqlPlaceholders(pointIds.size())),
                    vectorDeleteSqlArgs(pointIds, truncateText(sanitizeOperationalError(ex), 512)).toArray());
            log.warn("Personal knowledge vector delete failed pointCount={}", pointIds.size(), ex);
            return 0;
        }
    }

    private String sqlPlaceholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private List<Object> vectorDeleteSqlArgs(List<String> pointIds) {
        List<Object> args = new ArrayList<>();
        args.add(knowledgeProperties.getCollection());
        args.addAll(pointIds);
        return args;
    }

    private List<Object> vectorDeleteSqlArgs(List<String> pointIds, String error) {
        List<Object> args = new ArrayList<>();
        args.add(error);
        args.add(knowledgeProperties.getCollection());
        args.addAll(pointIds);
        return args;
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private List<KnowledgeSearchResultVO> searchKnowledgeByVector(Long userId, String keyword, int limit,
                                                                  Long documentId, String documentType) {
        if (!semanticKnowledgeEnabled()) {
            return List.of();
        }
        try {
            KnowledgeEmbeddingResult embedding = embedTexts(List.of(keyword));
            if (embedding.vectors().isEmpty()) {
                return List.of();
            }
            Map<String, Object> payloadFilter = new LinkedHashMap<>();
            payloadFilter.put("userId", userId);
            if (documentId != null) {
                payloadFilter.put("documentId", documentId);
            }
            if (StringUtils.hasText(documentType)) {
                payloadFilter.put("documentType", documentType);
            }
            List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                    .collectionName(knowledgeProperties.getCollection())
                    .vector(embedding.vectors().get(0))
                    .mustMatchPayload(payloadFilter)
                    .limit(limit)
                    .build());
            if (hits.isEmpty()) {
                return List.of();
            }
            List<Long> chunkIds = hits.stream()
                    .map(hit -> payloadLong(hit.getPayload(), "chunkId"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (chunkIds.isEmpty()) {
                return List.of();
            }
            Map<Long, VectorSearchResult> hitMap = new LinkedHashMap<>();
            for (VectorSearchResult hit : hits) {
                Long chunkId = payloadLong(hit.getPayload(), "chunkId");
                if (chunkId != null) {
                    hitMap.putIfAbsent(chunkId, hit);
                }
            }
            Map<Long, PersonalKnowledgeChunk> chunkMap = personalKnowledgeChunkMapper.selectList(
                            new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                                    .eq(PersonalKnowledgeChunk::getUserId, userId)
                                    .eq(PersonalKnowledgeChunk::getIndexStatus, "INDEXED")
                                    .in(PersonalKnowledgeChunk::getId, chunkIds))
                    .stream()
                    .collect(Collectors.toMap(PersonalKnowledgeChunk::getId, Function.identity()));
            List<Long> documentIds = chunkMap.values().stream()
                    .map(PersonalKnowledgeChunk::getDocumentId)
                    .distinct()
                    .toList();
            if (documentIds.isEmpty()) {
                return List.of();
            }
            Map<Long, PersonalKnowledgeDocument> documentMap = personalKnowledgeDocumentMapper.selectList(
                            new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                                    .eq(PersonalKnowledgeDocument::getUserId, userId)
                                    .in(PersonalKnowledgeDocument::getId, documentIds))
                    .stream()
                    .collect(Collectors.toMap(PersonalKnowledgeDocument::getId, Function.identity()));
            return chunkIds.stream()
                    .map(chunkMap::get)
                    .filter(Objects::nonNull)
                    .map(chunk -> {
                        PersonalKnowledgeDocument document = documentMap.get(chunk.getDocumentId());
                        VectorSearchResult hit = hitMap.get(chunk.getId());
                        if (document == null || hit == null) {
                            return null;
                        }
                        return toKnowledgeSearchVO(document, chunk, snippet(chunk.getContent(), keyword), keyword,
                                hit.getScore(), "VECTOR");
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(KnowledgeSearchResultVO::getScore,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
        } catch (Exception ex) {
            log.warn("Personal knowledge vector search failed userId={}", userId, ex);
            return List.of();
        }
    }

    private KnowledgeEmbeddingResult embedTexts(List<String> texts) {
        List<List<Float>> vectors = new ArrayList<>();
        String provider = null;
        String model = null;
        Integer dimension = null;
        for (int start = 0; start < texts.size(); start += knowledgeProperties.safeEmbeddingBatchSize()) {
            int end = Math.min(texts.size(), start + knowledgeProperties.safeEmbeddingBatchSize());
            EmbeddingRequestDTO request = new EmbeddingRequestDTO();
            request.setTexts(texts.subList(start, end));
            EmbeddingResponseVO response = embeddingService.embed(request);
            if (response != null) {
                provider = firstText(provider, response.getProvider());
                model = firstText(model, response.getModel());
                dimension = dimension == null ? response.getDimension() : dimension;
            }
            if (response != null && response.getVectors() != null) {
                vectors.addAll(response.getVectors());
            }
        }
        return new KnowledgeEmbeddingResult(provider, model, dimension, vectors);
    }

    private record KnowledgeEmbeddingResult(String provider, String model, Integer dimension,
                                            List<List<Float>> vectors) {
    }

    private String knowledgePointId(Long chunkId) {
        return UUID.nameUUIDFromBytes(("personal-knowledge-chunk:" + chunkId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Long payloadLong(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private Double normalizeScore(Double score) {
        if (score == null) {
            return null;
        }
        return Math.min(Math.max(score, 0D), 1D);
    }

    private List<KnowledgeSearchResultVO> filterByMinScore(List<KnowledgeSearchResultVO> results, Double minScore) {
        if (minScore == null || results.isEmpty()) {
            return results;
        }
        return results.stream()
                .filter(result -> result.getScore() != null && result.getScore() >= minScore)
                .toList();
    }

    private List<KnowledgeSearchResultVO> mergeKnowledgeSearchResults(List<KnowledgeSearchResultVO> semanticResults,
                                                                      List<KnowledgeSearchResultVO> keywordResults) {
        Map<String, KnowledgeSearchResultVO> merged = new LinkedHashMap<>();
        mergeKnowledgeSearchResultGroup(merged, semanticResults);
        mergeKnowledgeSearchResultGroup(merged, keywordResults);
        return merged.values().stream()
                .sorted(Comparator.comparing(KnowledgeSearchResultVO::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void mergeKnowledgeSearchResultGroup(Map<String, KnowledgeSearchResultVO> merged,
                                                 List<KnowledgeSearchResultVO> results) {
        for (KnowledgeSearchResultVO result : results) {
            if (result == null) {
                continue;
            }
            String key = knowledgeSearchResultKey(result);
            KnowledgeSearchResultVO existing = merged.get(key);
            if (existing == null) {
                merged.put(key, result);
                continue;
            }
            double existingScore = existing.getScore() == null ? 0D : existing.getScore();
            double resultScore = result.getScore() == null ? 0D : result.getScore();
            existing.setScore(Math.max(existingScore, resultScore));
            existing.setMatchType("HYBRID");
            if (StringUtils.hasText(result.getSnippet()) && resultScore >= existingScore) {
                existing.setSnippet(result.getSnippet());
                existing.setHighlightedSnippet(result.getHighlightedSnippet());
                existing.setMatchedTerms(result.getMatchedTerms());
            }
        }
    }

    private String knowledgeSearchResultKey(KnowledgeSearchResultVO result) {
        if (result.getChunkId() != null) {
            return "chunk:" + result.getChunkId();
        }
        return "document:" + result.getDocumentId();
    }

    private int normalizeDuplicateReviewLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return KNOWLEDGE_DUPLICATE_REVIEW_DEFAULT_LIMIT;
        }
        return Math.min(limit, KNOWLEDGE_DUPLICATE_REVIEW_MAX_LIMIT);
    }

    private String knowledgePairKey(Long left, Long right) {
        if (left == null || right == null) {
            return String.valueOf(left) + ":" + right;
        }
        return left < right ? left + ":" + right : right + ":" + left;
    }

    private String buildKnowledgeAskPrompt(Long userId, String question, List<KnowledgeSearchResultVO> references) {
        Map<Long, String> chunkContentMap = referenceChunkContentMap(userId, references);
        String safeQuestion = sanitizeUserInput(question);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a personal RAG assistant. Answer in the same language as the user question.\n")
                .append("Use only the references below. If the references are insufficient, say what is missing.\n")
                .append("Keep the answer concise and cite every factual claim with reference numbers like [1], [2].\n")
                .append("Never cite a reference number that is not listed below.\n\n")
                .append("Question:\n")
                .append("---BEGIN-USER-INPUT---\n")
                .append(safeQuestion)
                .append("\n---END-USER-INPUT---\n\nReferences:\n");
        for (int i = 0; i < references.size(); i++) {
            KnowledgeSearchResultVO ref = references.get(i);
            String content = ref.getChunkId() == null ? ref.getSnippet() : chunkContentMap.get(ref.getChunkId());
            prompt.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append(firstText(ref.getTitle(), "Untitled"))
                    .append(" / ")
                    .append(firstText(ref.getSourceRef(), ref.getDocumentType(), "knowledge"))
                    .append("\n")
                    .append(truncateText(firstText(content, ref.getSnippet(), ""), 1200))
                    .append("\n\n");
        }
        return prompt.toString();
    }

    private void attachKnowledgeAskGovernance(KnowledgeAskVO vo) {
        boolean hasReferences = vo.getReferenceCount() != null && vo.getReferenceCount() > 0;
        boolean citationValid = Boolean.TRUE.equals(vo.getCitationValid());
        boolean answerGrounded = Boolean.TRUE.equals(vo.getAnswerGrounded());
        List<String> actions = new ArrayList<>();
        if (!hasReferences || Boolean.TRUE.equals(vo.getInsufficientReferences())) {
            actions.add("SUPPLEMENT_KNOWLEDGE_DOCUMENT");
            actions.add("NARROW_KNOWLEDGE_QUESTION");
            vo.setCitationTrustStatus("UNAVAILABLE");
            vo.setCanBeEvidence(false);
            vo.setLowConfidence(true);
            vo.setDisabledReason("INSUFFICIENT_REFERENCES");
            vo.setGovernanceActions(actions);
            return;
        }
        if (citationValid && answerGrounded) {
            vo.setCitationTrustStatus("VERIFIED");
            vo.setCanBeEvidence(true);
            vo.setLowConfidence(false);
            vo.setDisabledReason(null);
            vo.setGovernanceActions(List.of());
            return;
        }
        actions.add("REVIEW_KNOWLEDGE_CITATION");
        if (!citationValid) {
            actions.add("ADD_KNOWLEDGE_EVAL_CASE");
        }
        if (!answerGrounded) {
            actions.add("RERUN_KNOWLEDGE_EVALUATION");
        }
        vo.setCitationTrustStatus("LOW_CONFIDENCE");
        vo.setCanBeEvidence(false);
        vo.setLowConfidence(true);
        vo.setDisabledReason(!citationValid ? "CITATION_INVALID" : "ANSWER_NOT_GROUNDED");
        vo.setGovernanceActions(actions.stream().distinct().toList());
    }

    private void applyCitationValidation(KnowledgeAskVO vo, String answer, int referenceCount) {
        List<Integer> citedNumbers = extractCitationNumbers(answer);
        List<Integer> invalidNumbers = citedNumbers.stream()
                .filter(number -> number < 1 || number > referenceCount)
                .distinct()
                .toList();
        boolean citationValid = !citedNumbers.isEmpty() && invalidNumbers.isEmpty();
        vo.setCitedReferenceNumbers(citedNumbers);
        vo.setInvalidReferenceNumbers(invalidNumbers);
        vo.setCitationValid(citationValid);
        vo.setAnswerGrounded(citationValid && referenceCount > 0);
        if (citationValid) {
            vo.setAnswer(answer);
            vo.setCitationWarning(null);
            return;
        }
        if (citedNumbers.isEmpty()) {
            vo.setCitationWarning("The generated answer did not cite any retrieved reference.");
        } else {
            vo.setCitationWarning("生成答案引用了未知编号：" + invalidNumbers + "。");
        }
        vo.setAnswer(answer);
    }

    /**
     * 答案 grounding 校验：将答案按句切分，带引用 [n] 的句子与其引用片段做词级相似度，
     * 低于阈值的句子视为疑似未被来源支撑，写入 vo.unsupportedSentences，并下调 answerGrounded。
     */
    private void applyGroundingCheck(KnowledgeAskVO vo, String answer, List<KnowledgeSearchResultVO> references,
                                     Long userId) {
        if (!knowledgeProperties.isGroundingCheckEnabled() || !StringUtils.hasText(answer)
                || references == null || references.isEmpty()) {
            return;
        }
        Map<Long, String> chunkContentMap = referenceChunkContentMap(userId, references);
        List<String> unsupported = new ArrayList<>();
        for (String sentence : answer.split("(?<=[。！？!?\\n])")) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 8) {
                continue;
            }
            List<Integer> cited = extractCitationNumbers(trimmed);
            if (cited.isEmpty()) {
                continue;
            }
            double best = 0D;
            for (Integer number : cited) {
                if (number == null || number < 1 || number > references.size()) {
                    continue;
                }
                KnowledgeSearchResultVO ref = references.get(number - 1);
                String refContent = ref.getChunkId() == null
                        ? ref.getSnippet()
                        : firstText(chunkContentMap.get(ref.getChunkId()), ref.getSnippet());
                best = Math.max(best, textJaccard(trimmed, firstText(refContent, "")));
            }
            if (best < knowledgeProperties.getGroundingThreshold()) {
                unsupported.add(truncateText(trimmed, 300));
            }
        }
        vo.setUnsupportedSentences(unsupported);
        if (!unsupported.isEmpty()) {
            vo.setAnswerGrounded(false);
            String warn = "部分句子可能未被引用来源支撑（" + unsupported.size() + " 句）。";
            vo.setCitationWarning(StringUtils.hasText(vo.getCitationWarning())
                    ? vo.getCitationWarning() + " " + warn : warn);
        }
    }

    private List<Integer> extractCitationNumbers(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find()) {
            try {
                int number = Integer.parseInt(matcher.group(1));
                if (!numbers.contains(number)) {
                    numbers.add(number);
                }
            } catch (NumberFormatException ignored) {
                // Regex limits the group to digits; keep this guard for very large values.
            }
        }
        return numbers;
    }

    private Map<Long, String> referenceChunkContentMap(Long userId, List<KnowledgeSearchResultVO> references) {
        List<Long> chunkIds = references.stream()
                .map(KnowledgeSearchResultVO::getChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        return personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .in(PersonalKnowledgeChunk::getId, chunkIds))
                .stream()
                .collect(Collectors.toMap(PersonalKnowledgeChunk::getId, PersonalKnowledgeChunk::getContent));
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * Sanitize user input before injecting into the RAG prompt to prevent
     * prompt injection / instruction override attacks.
     * <ul>
     *   <li>Removes newline characters to break instruction injection</li>
     *   <li>Truncates excessively long input (max 2000 chars)</li>
     *   <li>Normalises whitespace</li>
     * </ul>
     * Chinese content is preserved as-is.
     */
    private String sanitizeUserInput(String input) {
        if (input == null) {
            return "";
        }
        // Truncate to max 2000 characters
        String truncated = input.length() > 2000 ? input.substring(0, 2000) : input;
        // Remove newlines to prevent instruction injection
        String noNewlines = truncated.replace('\n', ' ').replace('\r', ' ');
        // Collapse multiple spaces into one
        return noNewlines.replaceAll(" {2,}", " ").trim();
    }

    private String maskOperationalIdentifier(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= 4) {
            return "***" + text;
        }
        return text.substring(0, 2) + "***" + text.substring(text.length() - 2);
    }

    private String sanitizeOperationalError(Exception ex) {
        String text = firstText(ex == null ? null : ex.getMessage(),
                ex == null ? null : ex.getClass().getSimpleName(),
                "unknown error");
        String sanitized = text
                .replaceAll("https?://[^\\s,;]+", "[endpoint]")
                .replaceAll("(?i)(token|secret|password|accessKey|apiKey|key)=([^\\s,;]+)", "$1=***");
        return "errorRef=" + Integer.toHexString(text.hashCode()) + "; summary=" + truncateText(sanitized, 180);
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

    private List<String> matchedTerms(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return List.of();
        }
        String lowerText = text.toLowerCase();
        return Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")
                .matcher(keyword.toLowerCase())
                .results()
                .map(MatchResult::group)
                .filter(term -> term.length() >= 2)
                .distinct()
                .filter(lowerText::contains)
                .limit(8)
                .toList();
    }

    private String highlightedSnippet(String snippet, List<String> terms) {
        if (!StringUtils.hasText(snippet) || terms == null || terms.isEmpty()) {
            return snippet;
        }
        String highlighted = snippet;
        for (String term : terms) {
            highlighted = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(highlighted)
                    .replaceAll(match -> "[[H]]" + Matcher.quoteReplacement(match.group()) + "[[/H]]");
        }
        return highlighted;
    }

    private AgentFeedbackVO toFeedbackVO(AgentFeedback feedback) {
        AgentFeedbackVO vo = new AgentFeedbackVO();
        vo.setId(feedback.getId());
        vo.setAgentTaskId(feedback.getAgentTaskId());
        vo.setAgentRunId(feedback.getAgentRunId());
        vo.setFeedbackType(feedback.getFeedbackType());
        vo.setComment(feedback.getComment());
        vo.setCreatedAt(feedback.getCreatedAt());
        return vo;
    }

    private KnowledgeDocumentOptionVO toKnowledgeDocumentOptionVO(PersonalKnowledgeDocument document) {
        KnowledgeDocumentOptionVO vo = new KnowledgeDocumentOptionVO();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setDocumentType(document.getDocumentType());
        vo.setStatus(document.getStatus());
        return vo;
    }

    private KnowledgeDocumentVO toKnowledgeDocumentVO(PersonalKnowledgeDocument document, int chunkCount, boolean includeContent) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setDocumentType(document.getDocumentType());
        vo.setStatus(aggregateDocumentIndexStatus(document.getId(), chunkCount, document.getStatus()));
        vo.setNormalizationVersion(firstText(document.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
        vo.setChunkCount(chunkCount);
        vo.setDuplicateDocument(false);
        vo.setDuplicateChunkCount(0);
        vo.setNearDuplicateChunkCount(0);
        vo.setNearDuplicateThreshold(knowledgeProperties.safeNearDuplicateThreshold());
        vo.setContent(includeContent ? document.getContent() : null);
        vo.setCreatedAt(document.getCreatedAt());
        vo.setUpdatedAt(document.getUpdatedAt());
        return vo;
    }

    private KnowledgeDocumentVO toKnowledgeDocumentVO(PersonalKnowledgeDocument document,
                                                      KnowledgeDocumentAggregate aggregate,
                                                      boolean includeContent) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        long chunkCount = aggregate == null ? 0L : aggregate.chunkCount();
        vo.setId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setDocumentType(document.getDocumentType());
        vo.setStatus(resolveDocumentIndexStatus(aggregate, document.getStatus()));
        vo.setNormalizationVersion(firstText(document.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
        vo.setChunkCount(toIntCount(chunkCount));
        vo.setDuplicateDocument(false);
        vo.setDuplicateChunkCount(0);
        vo.setNearDuplicateChunkCount(0);
        vo.setNearDuplicateThreshold(knowledgeProperties.safeNearDuplicateThreshold());
        vo.setContent(includeContent ? document.getContent() : null);
        vo.setCreatedAt(document.getCreatedAt());
        vo.setUpdatedAt(document.getUpdatedAt());
        return vo;
    }

    private KnowledgeDocumentVersionVO toKnowledgeDocumentVersionVO(PersonalKnowledgeDocumentVersion version) {
        KnowledgeDocumentVersionVO vo = new KnowledgeDocumentVersionVO();
        vo.setId(version.getId());
        vo.setDocumentId(version.getDocumentId());
        vo.setVersionNo(version.getVersionNo());
        vo.setTitle(version.getTitle());
        vo.setDocumentType(version.getDocumentType());
        vo.setContent(version.getContent());
        vo.setContentHash(version.getContentHash());
        vo.setNormalizationVersion(firstText(version.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
        vo.setChunkCount(version.getChunkCount());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setUpdatedAt(version.getUpdatedAt());
        return vo;
    }

    private KnowledgeSearchResultVO toKnowledgeSearchVO(PersonalKnowledgeDocument document, PersonalKnowledgeChunk chunk,
                                                        String snippet, String keyword, Double score, String matchType) {
        KnowledgeSearchResultVO vo = new KnowledgeSearchResultVO();
        vo.setDocumentId(document.getId());
        vo.setChunkId(chunk == null ? null : chunk.getId());
        vo.setTitle(document.getTitle());
        vo.setDocumentType(document.getDocumentType());
        vo.setSnippet(snippet);
        List<String> terms = matchedTerms(snippet, keyword);
        vo.setMatchedTerms(terms);
        vo.setHighlightedSnippet(highlightedSnippet(snippet, terms));
        vo.setSourceRef(chunk == null ? document.getTitle() : chunk.getSourceRef());
        if (chunk != null) {
            vo.setChunkIndex(chunk.getChunkIndex());
            vo.setChunkHash(chunk.getChunkHash());
            vo.setNormalizationVersion(firstText(chunk.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
            vo.setEmbeddingModel(chunk.getEmbeddingModel());
            vo.setEmbeddingDimension(chunk.getEmbeddingDimension());
            vo.setIndexedAt(chunk.getIndexedAt());
            vo.setIndexStatus(chunk.getIndexStatus());
        }
        vo.setScore(score);
        vo.setMatchType(matchType);
        return vo;
    }

    private KnowledgeChunkVO toKnowledgeChunkVO(PersonalKnowledgeChunk chunk, boolean duplicateInDocument) {
        return toKnowledgeChunkVO(chunk, duplicateInDocument, false);
    }

    private KnowledgeChunkVO toKnowledgeChunkVO(PersonalKnowledgeChunk chunk, boolean duplicateInDocument,
                                               boolean cleanupCandidate) {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setChunkHash(chunk.getChunkHash());
        vo.setNormalizationVersion(firstText(chunk.getNormalizationVersion(), KNOWLEDGE_NORMALIZATION_VERSION));
        vo.setSourceRef(chunk.getSourceRef());
        vo.setEmbeddingModel(chunk.getEmbeddingModel());
        vo.setEmbeddingDimension(chunk.getEmbeddingDimension());
        vo.setIndexedAt(chunk.getIndexedAt());
        vo.setIndexStatus(chunk.getIndexStatus());
        vo.setLastError(chunk.getLastError());
        vo.setDuplicateInDocument(duplicateInDocument);
        vo.setCleanupCandidate(cleanupCandidate);
        vo.setCreatedAt(chunk.getCreatedAt());
        vo.setUpdatedAt(chunk.getUpdatedAt());
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
        vo.setErrorMessage(SensitiveTextMasker.maskText(log.getErrorMessage()));
        vo.setOutputJson(SensitiveTextMasker.maskText(log.getOutputJson()));
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
        vo.setOutputJson(SensitiveTextMasker.maskText(item.getOutputJson()));
        vo.setScore(item.getScore());
        vo.setErrorMessage(SensitiveTextMasker.maskText(item.getErrorMessage()));
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
                throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " 内容格式不正确，请填写对象或数组结构");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " 内容不是有效 JSON");
        }
    }

    private long pageNo(Long value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long pageSize(Long value) {
        return value == null || value < 1 ? 10 : Math.min(value, 100);
    }

    private String resolveDocumentIndexStatus(KnowledgeDocumentAggregate aggregate, String fallbackStatus) {
        if (aggregate == null || aggregate.chunkCount() <= 0) {
            return "EMPTY";
        }
        if (aggregate.failedCount() > 0) {
            return "FAILED";
        }
        if (aggregate.pendingCount() > 0) {
            return "PENDING";
        }
        if (aggregate.indexedCount() == aggregate.chunkCount()) {
            return "INDEXED";
        }
        if (aggregate.disabledCount() == aggregate.chunkCount()) {
            return "INDEXED";
        }
        return firstText(fallbackStatus, "PENDING");
    }

    private Object rowValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String upperKey = key.toUpperCase(Locale.ROOT);
        if (row.containsKey(upperKey)) {
            return row.get(upperKey);
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (row.containsKey(lowerKey)) {
            return row.get(lowerKey);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long longValue(Object value) {
        Long parsed = nullableLong(value);
        return parsed == null ? 0L : parsed;
    }

    private int toIntCount(long value) {
        return Math.toIntExact(Math.min(Math.max(value, 0L), Integer.MAX_VALUE));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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


    private record KnowledgeChunkDraft(String content, String sourceRef) {
    }

    private record SemanticBlockDraft(String content, String sourceRef, boolean codeBlock) {
    }

    private record KnowledgeDocumentAggregate(long chunkCount, long failedCount, long pendingCount,
                                              long indexedCount, long disabledCount) {
        private static KnowledgeDocumentAggregate empty() {
            return new KnowledgeDocumentAggregate(0, 0, 0, 0, 0);
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
}
