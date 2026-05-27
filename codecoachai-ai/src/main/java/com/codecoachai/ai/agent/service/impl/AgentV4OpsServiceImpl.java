package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeDocumentCreateDTO;
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
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVersionVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateReviewItemVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateReviewVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO;
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
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.EmbeddingService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentV4OpsServiceImpl implements AgentV4OpsService {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 80;
    private static final int MIN_CHUNK_SIZE = 180;
    private static final int EMBEDDING_BATCH_SIZE = 64;
    private static final String KNOWLEDGE_COLLECTION = "personal_knowledge_chunk";
    private static final int ASK_DEFAULT_LIMIT = 5;
    private static final long KNOWLEDGE_UPLOAD_MAX_BYTES = 8L * 1024 * 1024;
    private static final int KNOWLEDGE_UPLOAD_MAX_TEXT_CHARS = 100_000;
    private static final double KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD = 0.88D;
    private static final Set<String> KNOWLEDGE_UPLOAD_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf", "docx", "doc");
    private static final String KNOWLEDGE_CHUNK_STRATEGY = "SEMANTIC_BLOCK_800_OVERLAP_80";
    private static final int KNOWLEDGE_DUPLICATE_REVIEW_DEFAULT_LIMIT = 20;
    private static final int KNOWLEDGE_DUPLICATE_REVIEW_MAX_LIMIT = 80;

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
        document.setStatus("INDEXED");
        personalKnowledgeDocumentMapper.insert(document);
        KnowledgeDocumentVO vo = rebuildKnowledgeDocumentChunks(userId, document, normalizedContent, true);
        vo.setDuplicateDocument(false);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO updateKnowledgeDocument(Long userId, Long documentId, KnowledgeDocumentCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTitle()) || !StringUtils.hasText(dto.getContent())) {
            throw new IllegalArgumentException("title and content are required");
        }
        PersonalKnowledgeDocument document = ownedDocument(userId, documentId);
        List<PersonalKnowledgeChunk> oldChunks = listDocumentChunks(userId, document.getId());
        snapshotKnowledgeDocumentVersion(userId, document, oldChunks.size());
        if (!oldChunks.isEmpty()) {
            personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                    .eq(PersonalKnowledgeChunk::getUserId, userId)
                    .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
            deletePersonalKnowledgeVectors(oldChunks);
        }
        String normalizedContent = normalizeKnowledgeContent(dto.getContent());
        document.setTitle(dto.getTitle().trim());
        document.setDocumentType(firstText(dto.getDocumentType(), "NOTE"));
        document.setContent(dto.getContent());
        document.setContentHash(knowledgeHash(normalizedContent));
        document.setStatus("INDEXED");
        personalKnowledgeDocumentMapper.updateById(document);
        return rebuildKnowledgeDocumentChunks(userId, document, normalizedContent, true);
    }

    @Override
    public KnowledgeDocumentVO uploadKnowledgeDocument(Long userId, MultipartFile file, String documentType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file is required");
        }
        if (file.getSize() > KNOWLEDGE_UPLOAD_MAX_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file size must be <= 8MB");
        }
        String originalFilename = firstText(file.getOriginalFilename(), "knowledge.txt").trim();
        String extension = fileExtension(originalFilename);
        if (!KNOWLEDGE_UPLOAD_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "only txt, md, markdown, pdf, docx and doc files are supported");
        }
        try {
            String content = extractKnowledgeFileText(extension, file.getBytes());
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "file content is empty");
            }
            KnowledgeDocumentCreateDTO dto = new KnowledgeDocumentCreateDTO();
            dto.setTitle(stripExtension(originalFilename));
            dto.setDocumentType(firstText(documentType, documentTypeByExtension(extension)));
            dto.setContent(content);
            return createKnowledgeDocument(userId, dto);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file read failed");
        }
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
    public KnowledgeStatsVO getKnowledgeStats(Long userId) {
        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .select(PersonalKnowledgeChunk::getId, PersonalKnowledgeChunk::getChunkHash));
        long duplicateCount = chunks.stream()
                .map(PersonalKnowledgeChunk::getChunkHash)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values()
                .stream()
                .filter(count -> count > 1)
                .mapToLong(count -> count - 1)
                .sum();
        KnowledgeStatsVO vo = new KnowledgeStatsVO();
        vo.setDocumentCount(personalKnowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                .eq(PersonalKnowledgeDocument::getUserId, userId)).intValue());
        vo.setChunkCount(chunks.size());
        vo.setDuplicateChunkCount(Math.toIntExact(duplicateCount));
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setRetrievalMode(vectorStoreClient.isEnabled() ? "VECTOR_FIRST" : "KEYWORD_FALLBACK");
        vo.setChunkStrategy(KNOWLEDGE_CHUNK_STRATEGY);
        return vo;
    }

    @Override
    public KnowledgeConfigVO getKnowledgeConfig(Long userId) {
        KnowledgeConfigVO vo = new KnowledgeConfigVO();
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setVectorCollection(KNOWLEDGE_COLLECTION);
        vo.setRetrievalMode(vectorStoreClient.isEnabled() ? "VECTOR_FIRST" : "KEYWORD_FALLBACK");
        vo.setChunkStrategy(KNOWLEDGE_CHUNK_STRATEGY);
        vo.setChunkSize(CHUNK_SIZE);
        vo.setChunkOverlap(CHUNK_OVERLAP);
        vo.setMinChunkSize(MIN_CHUNK_SIZE);
        vo.setNearDuplicateThreshold(KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD);
        vo.setUploadMaxBytes(KNOWLEDGE_UPLOAD_MAX_BYTES);
        vo.setUploadMaxTextChars(KNOWLEDGE_UPLOAD_MAX_TEXT_CHARS);
        vo.setUploadExtensions(KNOWLEDGE_UPLOAD_EXTENSIONS.stream().sorted().toList());
        vo.setExactDedupScope("PER_USER_DOCUMENT_AND_CHUNK_HASH");
        vo.setNearDuplicateAction("WARN_ONLY");
        return vo;
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
        List<PersonalKnowledgeChunk> oldChunks = listDocumentChunks(userId, document.getId());
        snapshotKnowledgeDocumentVersion(userId, document, oldChunks.size());
        if (!oldChunks.isEmpty()) {
            personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                    .eq(PersonalKnowledgeChunk::getUserId, userId)
                    .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
            deletePersonalKnowledgeVectors(oldChunks);
        }
        String content = firstText(version.getContent(), "");
        String normalizedContent = normalizeKnowledgeContent(content);
        document.setTitle(firstText(version.getTitle(), document.getTitle()));
        document.setDocumentType(firstText(version.getDocumentType(), "NOTE"));
        document.setContent(content);
        document.setContentHash(StringUtils.hasText(version.getContentHash())
                ? version.getContentHash()
                : knowledgeHash(normalizedContent));
        document.setStatus("INDEXED");
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
    public List<KnowledgeSearchResultVO> listSimilarKnowledgeChunks(Long userId, Long chunkId, Integer limit) {
        PersonalKnowledgeChunk source = ownedChunk(userId, chunkId);
        if (!vectorStoreClient.isEnabled()) {
            return List.of();
        }
        int size = normalizeLimit(limit);
        try {
            List<List<Float>> vectors = embedTexts(List.of(source.getContent()));
            if (vectors.isEmpty()) {
                return List.of();
            }
            List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                    .collectionName(KNOWLEDGE_COLLECTION)
                    .vector(vectors.get(0))
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
    public KnowledgeDuplicateReviewVO reviewDuplicateKnowledgeChunks(Long userId, Integer limit) {
        int size = normalizeDuplicateReviewLimit(limit);
        KnowledgeDuplicateReviewVO vo = new KnowledgeDuplicateReviewVO();
        vo.setVectorEnabled(vectorStoreClient.isEnabled());
        vo.setThreshold(KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD);
        vo.setLimit(size);
        vo.setGeneratedAt(LocalDateTime.now());
        if (!vectorStoreClient.isEnabled()) {
            vo.setScannedChunkCount(0);
            vo.setCandidateCount(0);
            vo.setItems(List.of());
            return vo;
        }
        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .orderByDesc(PersonalKnowledgeChunk::getUpdatedAt)
                        .last("LIMIT " + Math.max(size * 5, size)));
        vo.setScannedChunkCount(chunks.size());
        if (chunks.isEmpty()) {
            vo.setCandidateCount(0);
            vo.setItems(List.of());
            return vo;
        }
        List<KnowledgeDuplicateReviewItemVO> items = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        for (PersonalKnowledgeChunk chunk : chunks) {
            if (items.size() >= size) {
                break;
            }
            List<KnowledgeSearchResultVO> matches = listSimilarKnowledgeChunks(userId, chunk.getId(), 3).stream()
                    .filter(match -> match.getScore() != null && match.getScore() >= KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD)
                    .filter(match -> seenPairs.add(knowledgePairKey(chunk.getId(), match.getChunkId())))
                    .toList();
            if (matches.isEmpty()) {
                continue;
            }
            PersonalKnowledgeDocument document = ownedDocument(userId, chunk.getDocumentId());
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeChunk(Long userId, Long chunkId) {
        PersonalKnowledgeChunk chunk = ownedChunk(userId, chunkId);
        personalKnowledgeChunkMapper.deleteById(chunk.getId());
        deletePersonalKnowledgeVectors(List.of(chunk));
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
        personalKnowledgeChunkMapper.delete(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getUserId, userId)
                .eq(PersonalKnowledgeChunk::getDocumentId, document.getId()));
        personalKnowledgeDocumentMapper.deleteById(document.getId());
        deletePersonalKnowledgeVectors(chunks);
    }

    @Override
    public List<KnowledgeSearchResultVO> searchKnowledge(Long userId, String keyword, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int size = normalizeLimit(limit);
        String value = keyword.trim();
        List<KnowledgeSearchResultVO> semanticResults = searchKnowledgeByVector(userId, value, size);
        if (!semanticResults.isEmpty()) {
            return semanticResults;
        }
        List<PersonalKnowledgeDocument> documents = personalKnowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                        .eq(PersonalKnowledgeDocument::getUserId, userId)
                        .and(query -> query.like(PersonalKnowledgeDocument::getTitle, value)
                                .or()
                                .like(PersonalKnowledgeDocument::getContent, value))
                        .last("LIMIT " + size));
        Map<Long, PersonalKnowledgeDocument> docMap = documents.stream()
                .collect(Collectors.toMap(PersonalKnowledgeDocument::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<PersonalKnowledgeChunk> chunks = personalKnowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .like(PersonalKnowledgeChunk::getContent, value)
                        .last("LIMIT " + Math.max(size, 30)));
        for (PersonalKnowledgeChunk chunk : chunks) {
            docMap.computeIfAbsent(chunk.getDocumentId(), personalKnowledgeDocumentMapper::selectById);
        }

        List<KnowledgeSearchResultVO> result = new ArrayList<>();
        for (PersonalKnowledgeDocument document : documents) {
            result.add(toKnowledgeSearchVO(document, null, snippet(document.getContent(), value), value, 0.6D, "KEYWORD_DOCUMENT"));
        }
        for (PersonalKnowledgeChunk chunk : chunks) {
            PersonalKnowledgeDocument document = docMap.get(chunk.getDocumentId());
            if (document != null && Objects.equals(document.getUserId(), userId)) {
                result.add(toKnowledgeSearchVO(document, chunk, snippet(chunk.getContent(), value), value, 0.75D, "KEYWORD_CHUNK"));
            }
        }
        return result.stream().limit(size).toList();
    }

    @Override
    public KnowledgeAskVO askKnowledge(Long userId, KnowledgeAskDTO dto) {
        String question = dto == null ? null : dto.getQuestion();
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question is required");
        }
        String normalizedQuestion = question.trim();
        int limit = dto == null || dto.getLimit() == null ? ASK_DEFAULT_LIMIT : normalizeLimit(dto.getLimit());
        List<KnowledgeSearchResultVO> references = searchKnowledge(userId, normalizedQuestion, limit);

        KnowledgeAskVO vo = new KnowledgeAskVO();
        vo.setQuestion(normalizedQuestion);
        vo.setReferences(references);
        vo.setGeneratedAt(LocalDateTime.now());
        if (references.isEmpty()) {
            vo.setAnswer("No relevant content was found in your personal knowledge base. Add or upload materials first.");
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
            vo.setAnswer(result.getContent());
            vo.setAiCallLogId(result.getAiCallLogId());
        } catch (Exception ex) {
            log.warn("Personal knowledge ask generation failed userId={}", userId, ex);
            vo.setAnswer("Relevant references were found, but the AI answer could not be generated. Please review the cited snippets.");
        }
        return vo;
    }

    @Override
    public KnowledgeVectorRebuildVO rebuildKnowledgeVectors(Long userId, Long documentId) {
        List<PersonalKnowledgeDocument> documents = listRebuildDocuments(userId, documentId);
        boolean vectorEnabled = vectorStoreClient.isEnabled();
        int chunkCount = 0;
        int vectorUpdated = 0;
        int duplicateChunkCount = 0;
        List<Long> failedDocuments = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (PersonalKnowledgeDocument document : documents) {
            List<PersonalKnowledgeChunk> chunks = listDocumentChunks(userId, document.getId());
            chunkCount += chunks.size();
            duplicateChunkCount += duplicateChunkCount(chunks);
            if (!vectorEnabled || chunks.isEmpty()) {
                continue;
            }
            try {
                vectorUpdated += upsertPersonalKnowledgeVectors(userId, document, chunks);
            } catch (Exception ex) {
                failedDocuments.add(document.getId());
                String error = "documentId=" + document.getId() + ": " + firstText(ex.getMessage(), ex.getClass().getSimpleName());
                errors.add(error);
                log.warn("Personal knowledge vector rebuild failed userId={} documentId={}", userId, document.getId(), ex);
            }
        }

        KnowledgeVectorRebuildVO vo = new KnowledgeVectorRebuildVO();
        vo.setVectorEnabled(vectorEnabled);
        vo.setDocumentCount(documents.size());
        vo.setChunkCount(chunkCount);
        vo.setVectorUpdated(vectorUpdated);
        vo.setDuplicateChunkCount(duplicateChunkCount);
        vo.setFailedDocuments(failedDocuments);
        vo.setErrors(errors);
        return vo;
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

    private PersonalKnowledgeChunk ownedChunk(Long userId, Long id) {
        PersonalKnowledgeChunk chunk = personalKnowledgeChunkMapper.selectById(id);
        if (chunk == null || !Objects.equals(chunk.getUserId(), userId)) {
            throw new IllegalArgumentException("Knowledge chunk not found or forbidden");
        }
        return chunk;
    }

    private PersonalKnowledgeDocumentVersion ownedKnowledgeDocumentVersion(Long userId, Long documentId, Long versionId) {
        PersonalKnowledgeDocumentVersion version = personalKnowledgeDocumentVersionMapper.selectById(versionId);
        if (version == null
                || !Objects.equals(version.getUserId(), userId)
                || !Objects.equals(version.getDocumentId(), documentId)) {
            throw new IllegalArgumentException("Knowledge document version not found or forbidden");
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
        version.setChunkCount(chunkCount);
        personalKnowledgeDocumentVersionMapper.insert(version);
    }

    private int chunkCount(Long documentId) {
        return personalKnowledgeChunkMapper.selectCount(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getDocumentId, documentId)).intValue();
    }

    private PersonalKnowledgeDocument findExistingKnowledgeDocument(Long userId, String contentHash) {
        if (!StringUtils.hasText(contentHash)) {
            return null;
        }
        return personalKnowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<PersonalKnowledgeDocument>()
                .eq(PersonalKnowledgeDocument::getUserId, userId)
                .eq(PersonalKnowledgeDocument::getContentHash, contentHash)
                .last("LIMIT 1"));
    }

    private Set<String> existingChunkHashes(Long userId) {
        return personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                        .eq(PersonalKnowledgeChunk::getUserId, userId)
                        .isNotNull(PersonalKnowledgeChunk::getChunkHash))
                .stream()
                .map(PersonalKnowledgeChunk::getChunkHash)
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
        for (String chunkContent : splitChunks(normalizedContent)) {
            String chunkHash = knowledgeHash(chunkContent);
            if (!StringUtils.hasText(chunkHash) || existingChunkHashes.contains(chunkHash) || !seenChunkHashes.add(chunkHash)) {
                duplicateChunkCount++;
                continue;
            }
            PersonalKnowledgeChunk chunk = new PersonalKnowledgeChunk();
            chunk.setUserId(userId);
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkContent);
            chunk.setChunkHash(chunkHash);
            chunk.setSourceRef(document.getTitle() + "#" + index);
            personalKnowledgeChunkMapper.insert(chunk);
            chunks.add(chunk);
        }
        int nearDuplicateChunkCount = countNearDuplicateChunks(userId, chunks);
        indexPersonalKnowledgeVectors(userId, document, chunks);
        KnowledgeDocumentVO vo = toKnowledgeDocumentVO(document, index, includeContent);
        vo.setDuplicateChunkCount(duplicateChunkCount);
        vo.setNearDuplicateChunkCount(nearDuplicateChunkCount);
        vo.setNearDuplicateThreshold(KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD);
        return vo;
    }

    private List<PersonalKnowledgeChunk> listDocumentChunks(Long userId, Long documentId) {
        return personalKnowledgeChunkMapper.selectList(new LambdaQueryWrapper<PersonalKnowledgeChunk>()
                .eq(PersonalKnowledgeChunk::getUserId, userId)
                .eq(PersonalKnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(PersonalKnowledgeChunk::getChunkIndex)
                .orderByAsc(PersonalKnowledgeChunk::getId));
    }

    private List<String> splitChunks(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String normalized = normalizeKnowledgeContent(content);
        List<String> blocks = splitSemanticBlocks(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            for (String part : splitOversizedBlock(block)) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                if (current.isEmpty()) {
                    current.append(part);
                    continue;
                }
                if (current.length() + 2 + part.length() <= CHUNK_SIZE) {
                    current.append("\n\n").append(part);
                    continue;
                }
                flushKnowledgeChunk(chunks, current);
                current.append(part);
            }
        }
        flushKnowledgeChunk(chunks, current);
        if (chunks.isEmpty() && StringUtils.hasText(normalized)) {
            chunks.add(normalized);
        }
        return chunks;
    }

    private String normalizeKnowledgeContent(String content) {
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
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

    private String extractKnowledgeFileText(String extension, byte[] bytes) throws Exception {
        String text = switch (extension) {
            case "pdf" -> extractPdfText(bytes);
            case "docx" -> extractDocxText(bytes);
            case "doc" -> extractDocText(bytes);
            default -> new String(bytes, StandardCharsets.UTF_8);
        };
        String normalized = normalizeKnowledgeContent(text);
        if (normalized.length() > KNOWLEDGE_UPLOAD_MAX_TEXT_CHARS) {
            log.warn("Personal knowledge uploaded text truncated, extension={}, originalChars={}, maxChars={}",
                    extension, normalized.length(), KNOWLEDGE_UPLOAD_MAX_TEXT_CHARS);
            return normalized.substring(0, KNOWLEDGE_UPLOAD_MAX_TEXT_CHARS);
        }
        return normalized;
    }

    private String extractPdfText(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocxText(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> firstText(paragraph.getText(), ""))
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private String extractDocText(byte[] bytes) throws Exception {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String normalizeKnowledgeFingerprint(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return normalizeKnowledgeContent(content)
                .toLowerCase()
                .replaceAll("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]+", "");
    }

    private String knowledgeHash(String content) {
        String normalized = normalizeKnowledgeFingerprint(content);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private List<String> splitSemanticBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCodeBlock = false;
        for (String rawLine : content.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            boolean codeFence = line.trim().startsWith("```");
            boolean heading = isMarkdownHeading(line);
            boolean blank = !StringUtils.hasText(line);
            if (codeFence) {
                inCodeBlock = !inCodeBlock;
            }
            if (!inCodeBlock && heading && !current.isEmpty()) {
                flushKnowledgeBlock(blocks, current);
            }
            if (!inCodeBlock && blank) {
                flushKnowledgeBlock(blocks, current);
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        flushKnowledgeBlock(blocks, current);
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

    private void flushKnowledgeBlock(List<String> blocks, StringBuilder current) {
        String value = current.toString().trim();
        if (StringUtils.hasText(value)) {
            blocks.add(value);
        }
        current.setLength(0);
    }

    private List<String> splitOversizedBlock(String block) {
        if (block.length() <= CHUNK_SIZE) {
            return List.of(block);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(block.length(), start + CHUNK_SIZE);
            int splitAt = findSemanticSplit(block, start, end);
            if (splitAt <= start) {
                splitAt = end;
            }
            chunks.add(block.substring(start, splitAt).trim());
            if (splitAt >= block.length()) {
                break;
            }
            start = Math.max(splitAt - CHUNK_OVERLAP, start + 1);
        }
        return chunks.stream().filter(StringUtils::hasText).toList();
    }

    private int findSemanticSplit(String text, int start, int end) {
        int min = Math.min(end, start + MIN_CHUNK_SIZE);
        String delimiters = "。！？.!?\n；;";
        for (int i = end - 1; i >= min; i--) {
            if (delimiters.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        int whitespace = text.lastIndexOf(' ', end - 1);
        return whitespace >= min ? whitespace : end;
    }

    private void flushKnowledgeChunk(List<String> chunks, StringBuilder current) {
        String value = current.toString().trim();
        if (StringUtils.hasText(value)) {
            chunks.add(value);
        }
        current.setLength(0);
    }

    private void indexPersonalKnowledgeVectors(Long userId, PersonalKnowledgeDocument document,
                                               List<PersonalKnowledgeChunk> chunks) {
        if (!vectorStoreClient.isEnabled() || chunks.isEmpty()) {
            return;
        }
        try {
            upsertPersonalKnowledgeVectors(userId, document, chunks);
        } catch (Exception ex) {
            log.warn("Personal knowledge vector indexing failed userId={} documentId={}", userId, document.getId(), ex);
        }
    }

    private int countNearDuplicateChunks(Long userId, List<PersonalKnowledgeChunk> chunks) {
        if (!vectorStoreClient.isEnabled() || chunks.isEmpty()) {
            return 0;
        }
        try {
            List<List<Float>> vectors = embedTexts(chunks.stream()
                    .map(PersonalKnowledgeChunk::getContent)
                    .toList());
            if (vectors.size() != chunks.size()) {
                return 0;
            }
            int count = 0;
            for (List<Float> vector : vectors) {
                List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                        .collectionName(KNOWLEDGE_COLLECTION)
                        .vector(vector)
                        .mustMatchPayload(Map.of("userId", userId))
                        .limit(1)
                        .build());
                if (!hits.isEmpty() && hits.get(0).getScore() >= KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD) {
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            log.warn("Personal knowledge near duplicate check failed userId={}", userId, ex);
            return 0;
        }
    }

    private int upsertPersonalKnowledgeVectors(Long userId, PersonalKnowledgeDocument document,
                                               List<PersonalKnowledgeChunk> chunks) {
        List<List<Float>> vectors = embedTexts(chunks.stream()
                .map(PersonalKnowledgeChunk::getContent)
                .toList());
        if (vectors.size() != chunks.size() || vectors.isEmpty()) {
            throw new IllegalStateException("embedding size mismatch, chunkCount=" + chunks.size()
                    + ", vectorCount=" + vectors.size());
        }
        vectorStoreClient.ensureCollection(KNOWLEDGE_COLLECTION, vectors.get(0).size());
        List<VectorPoint> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            PersonalKnowledgeChunk chunk = chunks.get(i);
            points.add(VectorPoint.builder()
                    .id(knowledgePointId(chunk.getId()))
                    .vector(vectors.get(i))
                    .payload(Map.of(
                            "userId", userId,
                            "documentId", document.getId(),
                            "chunkId", chunk.getId(),
                            "title", document.getTitle(),
                            "documentType", firstText(document.getDocumentType(), "NOTE"),
                            "sourceRef", firstText(chunk.getSourceRef(), document.getTitle())
                    ))
                    .build());
        }
        vectorStoreClient.upsert(KNOWLEDGE_COLLECTION, points);
        return points.size();
    }

    private void deletePersonalKnowledgeVectors(List<PersonalKnowledgeChunk> chunks) {
        if (!vectorStoreClient.isEnabled() || chunks.isEmpty()) {
            return;
        }
        vectorStoreClient.delete(KNOWLEDGE_COLLECTION, chunks.stream()
                .map(PersonalKnowledgeChunk::getId)
                .filter(Objects::nonNull)
                .map(this::knowledgePointId)
                .toList());
    }

    private List<KnowledgeSearchResultVO> searchKnowledgeByVector(Long userId, String keyword, int limit) {
        if (!vectorStoreClient.isEnabled()) {
            return List.of();
        }
        try {
            List<List<Float>> vectors = embedTexts(List.of(keyword));
            if (vectors.isEmpty()) {
                return List.of();
            }
            List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                    .collectionName(KNOWLEDGE_COLLECTION)
                    .vector(vectors.get(0))
                    .mustMatchPayload(Map.of("userId", userId))
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

    private List<List<Float>> embedTexts(List<String> texts) {
        List<List<Float>> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(texts.size(), start + EMBEDDING_BATCH_SIZE);
            EmbeddingRequestDTO request = new EmbeddingRequestDTO();
            request.setTexts(texts.subList(start, end));
            EmbeddingResponseVO response = embeddingService.embed(request);
            if (response.getVectors() != null) {
                vectors.addAll(response.getVectors());
            }
        }
        return vectors;
    }

    private String knowledgePointId(Long chunkId) {
        return "pkc-" + chunkId;
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
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a personal RAG assistant. Answer in the same language as the user question.\n")
                .append("Use only the references below. If the references are insufficient, say what is missing.\n")
                .append("Keep the answer concise and cite references as [1], [2].\n\n")
                .append("Question:\n")
                .append(question)
                .append("\n\nReferences:\n");
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
        vo.setDuplicateDocument(false);
        vo.setDuplicateChunkCount(0);
        vo.setNearDuplicateChunkCount(0);
        vo.setNearDuplicateThreshold(KNOWLEDGE_NEAR_DUPLICATE_THRESHOLD);
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
        vo.setScore(score);
        vo.setMatchType(matchType);
        return vo;
    }

    private KnowledgeChunkVO toKnowledgeChunkVO(PersonalKnowledgeChunk chunk, boolean duplicateInDocument) {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setChunkHash(chunk.getChunkHash());
        vo.setSourceRef(chunk.getSourceRef());
        vo.setDuplicateInDocument(duplicateInDocument);
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
