package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.config.KnowledgeIndexExecutor;
import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.entity.AgentFeedback;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AnalyticsJobLog;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeChunk;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.entity.PromptRegressionResult;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeChunkVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeStatsVO;
import com.codecoachai.common.core.domain.PageResult;
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
import com.codecoachai.ai.agent.service.AgentContextUsageReferenceService;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.EmbeddingService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AgentV4OpsServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private AgentFeedbackMapper agentFeedbackMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private PersonalKnowledgeDocumentMapper personalKnowledgeDocumentMapper;
    @Mock
    private PersonalKnowledgeChunkMapper personalKnowledgeChunkMapper;
    @Mock
    private PersonalKnowledgeDocumentVersionMapper personalKnowledgeDocumentVersionMapper;
    @Mock
    private AnalyticsMetricDefinitionMapper analyticsMetricDefinitionMapper;
    @Mock
    private AnalyticsJobLogMapper analyticsJobLogMapper;
    @Mock
    private PromptRegressionCaseMapper promptRegressionCaseMapper;
    @Mock
    private PromptRegressionResultMapper promptRegressionResultMapper;
    @Mock
    private JobCoachAgentService jobCoachAgentService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreClient vectorStoreClient;
    @Mock
    private KnowledgeIndexExecutor knowledgeIndexExecutor;
    @Mock
    private AgentContextUsageReferenceService usageReferenceService;

    private AgentV4OpsServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(PersonalKnowledgeDocument.class);
        initTableInfo(PersonalKnowledgeChunk.class);
    }

    @BeforeEach
    void setUp() {
        service = new AgentV4OpsServiceImpl(
                agentFeedbackMapper,
                agentTaskMapper,
                agentRunMapper,
                personalKnowledgeDocumentMapper,
                personalKnowledgeChunkMapper,
                personalKnowledgeDocumentVersionMapper,
                analyticsMetricDefinitionMapper,
                analyticsJobLogMapper,
                promptRegressionCaseMapper,
                promptRegressionResultMapper,
                jobCoachAgentService,
                jdbcTemplate,
                new ObjectMapper(),
                aiCallLogService,
                embeddingService,
                vectorStoreClient,
                new AiRouterProperties(),
                new KnowledgeProperties(),
                knowledgeIndexExecutor,
                usageReferenceService);
    }

    @Test
    void getKnowledgeDocumentRejectsOtherUsersDocumentBeforeCountingChunks() {
        when(personalKnowledgeDocumentMapper.selectById(100L)).thenReturn(document(100L, 20L));

        assertThrows(IllegalArgumentException.class, () -> service.getKnowledgeDocument(USER_ID, 100L));

        verify(personalKnowledgeChunkMapper, never()).selectCount(any());
    }

    @Test
    void getKnowledgeDocumentReturnsOwnedDocumentContent() {
        when(personalKnowledgeDocumentMapper.selectById(100L)).thenReturn(document(100L, USER_ID));
        when(personalKnowledgeChunkMapper.selectCount(any())).thenReturn(0L);

        KnowledgeDocumentVO vo = service.getKnowledgeDocument(USER_ID, 100L);

        assertEquals(100L, vo.getId());
        assertEquals("Java 面试知识", vo.getTitle());
        assertEquals("NOTE", vo.getDocumentType());
        assertEquals("EMPTY", vo.getStatus());
        assertEquals("MySQL 索引复习要点", vo.getContent());
    }

    @Test
    void getKnowledgeChunkRejectsOtherUsersChunk() {
        when(personalKnowledgeChunkMapper.selectById(200L)).thenReturn(chunk(200L, 20L));

        assertThrows(IllegalArgumentException.class, () -> service.getKnowledgeChunk(USER_ID, 200L));
    }

    @Test
    void getKnowledgeChunkReturnsOwnedChunk() {
        when(personalKnowledgeChunkMapper.selectById(200L)).thenReturn(chunk(200L, USER_ID));

        KnowledgeChunkVO vo = service.getKnowledgeChunk(USER_ID, 200L);

        assertEquals(200L, vo.getId());
        assertEquals(100L, vo.getDocumentId());
        assertEquals("MySQL 索引复习要点", vo.getContent());
        assertEquals("INDEXED", vo.getIndexStatus());
    }

    @Test
    void deleteKnowledgeDocumentRejectsOtherUsersDocumentBeforeMutating() {
        when(personalKnowledgeDocumentMapper.selectById(100L)).thenReturn(document(100L, 20L));

        assertThrows(IllegalArgumentException.class, () -> service.deleteKnowledgeDocument(USER_ID, 100L));

        verify(personalKnowledgeChunkMapper, never()).selectList(any());
        verify(personalKnowledgeChunkMapper, never()).delete(any());
        verify(personalKnowledgeDocumentMapper, never()).deleteById(100L);
    }

    @Test
    void deleteKnowledgeChunkRejectsOtherUsersChunkBeforeMutating() {
        when(personalKnowledgeChunkMapper.selectById(200L)).thenReturn(chunk(200L, 20L));

        assertThrows(IllegalArgumentException.class, () -> service.deleteKnowledgeChunk(USER_ID, 200L));

        verify(personalKnowledgeChunkMapper, never()).updateById(any(PersonalKnowledgeChunk.class));
        verify(personalKnowledgeChunkMapper, never()).deleteById(200L);
    }

    @Test
    void pageJobsMasksSensitiveOutputAndErrorText() {
        AnalyticsJobLog log = new AnalyticsJobLog();
        log.setId(300L);
        log.setJobCode("AGENT_DAILY_PLAN");
        log.setJobName("Agent daily plan batch");
        log.setStatus("FAILED");
        log.setOutputJson("""
                {"phone":"13812345678","email":"ops@example.com","apiKey":"sk-live-secret","authorization":"Bearer abc.def"}
                """);
        log.setErrorMessage("notify 13812345678 ops@example.com token=abc123");
        Page<AnalyticsJobLog> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(java.util.List.of(log));
        when(analyticsJobLogMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.pageJobs(null, null, 1L, 10L);
        var vo = result.getRecords().get(0);

        assertFalse(vo.getOutputJson().contains("13812345678"));
        assertFalse(vo.getOutputJson().contains("ops@example.com"));
        assertFalse(vo.getOutputJson().contains("sk-live-secret"));
        assertFalse(vo.getOutputJson().contains("Bearer abc.def"));
        assertFalse(vo.getErrorMessage().contains("13812345678"));
        assertFalse(vo.getErrorMessage().contains("ops@example.com"));
        assertTrue(vo.getOutputJson().contains("1**********"));
        assertTrue(vo.getOutputJson().contains("***@***"));
    }

    @Test
    void pagePromptResultsMasksSensitiveOutputAndErrorText() {
        PromptRegressionResult result = new PromptRegressionResult();
        result.setId(400L);
        result.setCaseId(500L);
        result.setStatus("FAILED");
        result.setOutputJson("""
                {"phone":"13812345678","email":"ops@example.com","apiKey":"sk-live-secret","authorization":"Bearer abc.def"}
                """);
        result.setErrorMessage("prompt regression failed for 13812345678 ops@example.com token=abc123");
        Page<PromptRegressionResult> page = Page.of(1, 20);
        page.setTotal(1);
        page.setRecords(java.util.List.of(result));
        when(promptRegressionResultMapper.selectPage(any(), any())).thenReturn(page);

        var vo = service.pagePromptResults(500L, 1L, 20L).getRecords().get(0);

        assertFalse(vo.getOutputJson().contains("13812345678"));
        assertFalse(vo.getOutputJson().contains("ops@example.com"));
        assertFalse(vo.getOutputJson().contains("sk-live-secret"));
        assertFalse(vo.getOutputJson().contains("Bearer abc.def"));
        assertFalse(vo.getErrorMessage().contains("13812345678"));
        assertFalse(vo.getErrorMessage().contains("ops@example.com"));
        assertTrue(vo.getOutputJson().contains("1**********"));
        assertTrue(vo.getOutputJson().contains("***@***"));
    }

    @Test
    void pageKnowledgeDocumentsUsesBatchChunkAggregatesInsteadOfPerDocumentCounts() {
        PersonalKnowledgeDocument first = document(100L, USER_ID);
        PersonalKnowledgeDocument second = document(101L, USER_ID);
        second.setTitle("Redis 面试知识");
        Page<PersonalKnowledgeDocument> page = Page.of(1, 20);
        page.setTotal(2);
        page.setRecords(List.of(first, second));
        when(personalKnowledgeDocumentMapper.selectPage(any(), any()))
                .thenReturn(page);
        when(jdbcTemplate.queryForList(anyString(), eq(100L), eq(101L))).thenReturn(List.of(
                Map.of("document_id", 100L, "chunk_count", 2L, "indexed_count", 2L),
                Map.of("document_id", 101L, "chunk_count", 0L)));

        PageResult<KnowledgeDocumentVO> result = service.pageKnowledgeDocuments(USER_ID, null, null, null, 1L, 20L);
        List<KnowledgeDocumentVO> documents = result.getRecords();

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(2, documents.size());
        assertEquals(2, documents.get(0).getChunkCount());
        assertEquals("INDEXED", documents.get(0).getStatus());
        assertEquals(0, documents.get(1).getChunkCount());
        verify(personalKnowledgeChunkMapper, never()).selectCount(any());
    }

    @Test
    void getKnowledgeStatsUsesAggregateQueriesInsteadOfLoadingAllChunks() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(USER_ID))).thenReturn(2L, 4L, 1L);
        when(jdbcTemplate.queryForList(anyString(), eq(USER_ID)))
                .thenReturn(List.of(
                        Map.of("name", "NOTE", "count", 1L),
                        Map.of("name", "PDF", "count", 1L)))
                .thenReturn(List.of(
                        Map.of("name", "INDEXED", "count", 3L),
                        Map.of("name", "FAILED", "count", 1L)))
                .thenReturn(List.of(Map.of("name", "bge-small", "count", 4L)))
                .thenReturn(List.of(Map.of("name", "NOTE", "count", 1L)))
                .thenReturn(List.of(Map.of(
                        "document_id", 100L,
                        "title", "Java 面试知识",
                        "document_type", "NOTE",
                        "duplicate_chunk_count", 1L,
                        "chunk_count", 2L)));

        KnowledgeStatsVO stats = service.getKnowledgeStats(USER_ID);

        assertEquals(2, stats.getDocumentCount());
        assertEquals(4, stats.getChunkCount());
        assertEquals(1, stats.getDuplicateChunkCount());
        assertEquals(1, stats.getDocumentTypeCounts().get("NOTE"));
        assertEquals(3, stats.getIndexStatusCounts().get("INDEXED"));
        assertEquals(1, stats.getDuplicateDocumentHotspots().size());
        verify(personalKnowledgeDocumentMapper, never()).selectList(any());
        verify(personalKnowledgeChunkMapper, never()).selectList(any());
    }

    @Test
    void getKnowledgeStatsDerivesGovernanceStatusAndActionsFromExistingCounts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(USER_ID))).thenReturn(2L, 4L, 2L);
        when(jdbcTemplate.queryForList(anyString(), eq(USER_ID)))
                .thenReturn(List.of(Map.of("name", "NOTE", "count", 2L)))
                .thenReturn(List.of(
                        Map.of("name", "INDEXED", "count", 2L),
                        Map.of("name", "FAILED", "count", 1L),
                        Map.of("name", "PENDING", "count", 1L)))
                .thenReturn(List.of(Map.of("name", "UNKNOWN", "count", 4L)))
                .thenReturn(List.of(Map.of("name", "NOTE", "count", 2L)))
                .thenReturn(List.of(Map.of(
                        "document_id", 100L,
                        "title", "Java 闈㈣瘯鐭ヨ瘑",
                        "document_type", "NOTE",
                        "duplicate_chunk_count", 2L,
                        "chunk_count", 4L)));

        KnowledgeStatsVO stats = service.getKnowledgeStats(USER_ID);

        assertEquals("DEGRADED", stats.getKnowledgeStatus());
        assertEquals("PARTIAL", stats.getEvidenceTrustStatus());
        assertFalse(stats.getCanBeEvidence());
        assertTrue(stats.getLowConfidence());
        assertEquals("INDEX_FAILED", stats.getDisabledReason());
        assertTrue(stats.getGovernanceActions().contains("RETRY_FAILED_INDEX"));
        assertTrue(stats.getGovernanceActions().contains("REVIEW_DUPLICATE_CHUNKS"));
    }

    @Test
    void askKnowledgeWithoutReferencesIsExplicitlyNotCitableEvidence() {
        AgentV4OpsServiceImpl spyService = spy(service);
        doReturn(List.of()).when(spyService).searchKnowledge(eq(USER_ID), anyString(), any(), any(), any(), any());
        KnowledgeAskDTO dto = new KnowledgeAskDTO();
        dto.setQuestion("How should I review Java concurrency?");

        KnowledgeAskVO vo = spyService.askKnowledge(USER_ID, dto);

        assertEquals("UNAVAILABLE", vo.getCitationTrustStatus());
        assertFalse(vo.getCanBeEvidence());
        assertTrue(vo.getLowConfidence());
        assertEquals("INSUFFICIENT_REFERENCES", vo.getDisabledReason());
        assertTrue(vo.getGovernanceActions().contains("SUPPLEMENT_KNOWLEDGE_DOCUMENT"));
        verify(aiCallLogService, never()).callAndLog(any());
    }

    @Test
    void uploadKnowledgeDocumentReadsFromInputStreamWithoutCallingGetBytes() throws Exception {
        byte[] bytes = "Java interview notes".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "knowledge.txt";
            }

            @Override
            public String getContentType() {
                return "text/plain";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return bytes.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("should not buffer whole upload");
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void transferTo(java.io.File dest) {
                throw new UnsupportedOperationException();
            }
        };

        AgentV4OpsServiceImpl spyService = spy(service);
        KnowledgeDocumentVO created = new KnowledgeDocumentVO();
        created.setId(999L);
        created.setTitle("knowledge");
        doReturn(created).when(spyService).createKnowledgeDocument(anyLong(), any());

        KnowledgeDocumentVO result = spyService.uploadKnowledgeDocument(USER_ID, file, null);

        assertEquals(999L, result.getId());
        verify(spyService).createKnowledgeDocument(anyLong(), any());
    }

    @Test
    void createFeedbackNormalizesSupportedFeedbackTypeAndDerivesRunIdFromTask() {
        AgentFeedbackCreateDTO dto = new AgentFeedbackCreateDTO();
        dto.setAgentTaskId(88L);
        dto.setFeedbackType(" helpful ");
        dto.setComment("solid suggestion");
        AgentTask task = new AgentTask();
        task.setId(88L);
        task.setUserId(USER_ID);
        task.setAgentRunId(66L);
        when(agentTaskMapper.selectById(88L)).thenReturn(task);
        AgentRun run = new AgentRun();
        run.setId(66L);
        run.setUserId(USER_ID);
        when(agentRunMapper.selectById(66L)).thenReturn(run);

        AgentFeedbackVO result = service.createFeedback(USER_ID, dto);
        ArgumentCaptor<AgentFeedback> captor = ArgumentCaptor.forClass(AgentFeedback.class);

        verify(agentFeedbackMapper).insert(captor.capture());
        assertEquals(66L, dto.getAgentRunId());
        assertEquals("HELPFUL", captor.getValue().getFeedbackType());
        assertEquals("HELPFUL", result.getFeedbackType());
        assertEquals(88L, result.getAgentTaskId());
        assertEquals(66L, result.getAgentRunId());
    }

    @Test
    void createFeedbackRejectsUnsupportedFeedbackType() {
        AgentFeedbackCreateDTO dto = new AgentFeedbackCreateDTO();
        dto.setFeedbackType("adopted");

        assertThrows(IllegalArgumentException.class, () -> service.createFeedback(USER_ID, dto));

        verify(agentFeedbackMapper, never()).insert(any(AgentFeedback.class));
    }

    @Test
    void pageFeedbackOmitsUserIdAndNormalizesFeedbackTypeFilter() {
        AgentFeedback feedback = new AgentFeedback();
        feedback.setId(101L);
        feedback.setUserId(USER_ID);
        feedback.setAgentTaskId(8L);
        feedback.setAgentRunId(9L);
        feedback.setFeedbackType("HELPFUL");
        feedback.setComment("clear next step");
        feedback.setCreatedAt(LocalDateTime.now());
        Page<AgentFeedback> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(feedback));
        when(agentFeedbackMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<AgentFeedbackVO> result = service.pageFeedback(USER_ID, 8L, 9L, "helpful", 1L, 10L);

        assertEquals(1, result.getRecords().size());
        assertEquals("HELPFUL", result.getRecords().get(0).getFeedbackType());
    }

    @Test
    void feedbackStatsAggregatesCurrentFrontendFeedbackTypes() {
        AgentFeedback helpful = new AgentFeedback();
        helpful.setFeedbackType("HELPFUL");
        helpful.setCreatedAt(LocalDateTime.now());
        AgentFeedback tooHard = new AgentFeedback();
        tooHard.setFeedbackType("TOO_HARD");
        tooHard.setCreatedAt(LocalDateTime.now());
        AgentFeedback inaccurate = new AgentFeedback();
        inaccurate.setFeedbackType("INACCURATE");
        inaccurate.setCreatedAt(LocalDateTime.now());
        AgentFeedback hallucination = new AgentFeedback();
        hallucination.setFeedbackType("HALLUCINATION");
        hallucination.setCreatedAt(LocalDateTime.now());
        when(agentFeedbackMapper.selectList(any())).thenReturn(List.of(helpful, tooHard, inaccurate, hallucination));

        AgentFeedbackStatsVO stats = service.feedbackStats(7);

        assertEquals(4L, stats.getTotalFeedbackCount());
        assertEquals(1L, stats.getAdoptedCount());
        assertEquals(3L, stats.getIgnoredCount());
        assertEquals(25.0d, stats.getAdoptionRate());
        assertEquals(4, stats.getTypeDistribution().size());
        assertTrue(stats.getTypeDistribution().stream().anyMatch(item ->
                "TOO_HARD".equals(item.getFeedbackType()) && Long.valueOf(1L).equals(item.getCount())));
        assertTrue(stats.getTypeDistribution().stream().anyMatch(item ->
                "HALLUCINATION".equals(item.getFeedbackType()) && Long.valueOf(1L).equals(item.getCount())));
    }

    @Test
    void createFeedbackRejectsTaskOwnedByAnotherUser() {
        AgentFeedbackCreateDTO dto = new AgentFeedbackCreateDTO();
        dto.setAgentTaskId(88L);
        dto.setFeedbackType("HELPFUL");
        AgentTask task = new AgentTask();
        task.setId(88L);
        task.setUserId(999L);
        when(agentTaskMapper.selectById(88L)).thenReturn(task);

        assertThrows(IllegalArgumentException.class, () -> service.createFeedback(USER_ID, dto));

        verify(agentRunMapper, never()).selectById(anyLong());
        verify(agentFeedbackMapper, never()).insert(any(AgentFeedback.class));
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    private PersonalKnowledgeDocument document(Long id, Long userId) {
        PersonalKnowledgeDocument document = new PersonalKnowledgeDocument();
        document.setId(id);
        document.setUserId(userId);
        document.setTitle("Java 面试知识");
        document.setDocumentType("NOTE");
        document.setContent("MySQL 索引复习要点");
        document.setStatus("INDEXED");
        document.setNormalizationVersion("v1");
        return document;
    }

    private PersonalKnowledgeChunk chunk(Long id, Long userId) {
        PersonalKnowledgeChunk chunk = new PersonalKnowledgeChunk();
        chunk.setId(id);
        chunk.setUserId(userId);
        chunk.setDocumentId(100L);
        chunk.setChunkIndex(0);
        chunk.setContent("MySQL 索引复习要点");
        chunk.setChunkHash("chunk-hash");
        chunk.setNormalizationVersion("v1");
        chunk.setSourceRef("Java 面试知识#1");
        chunk.setIndexStatus("INDEXED");
        return chunk;
    }
}
