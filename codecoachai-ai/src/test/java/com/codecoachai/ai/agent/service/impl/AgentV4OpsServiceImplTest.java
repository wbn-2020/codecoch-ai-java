package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.config.KnowledgeIndexExecutor;
import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.agent.domain.entity.AnalyticsJobLog;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeChunk;
import com.codecoachai.ai.agent.domain.entity.PersonalKnowledgeDocument;
import com.codecoachai.ai.agent.domain.entity.PromptRegressionResult;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeChunkVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
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
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.EmbeddingService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

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
                knowledgeIndexExecutor);
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
    void listPromptResultsMasksSensitiveOutputAndErrorText() {
        PromptRegressionResult result = new PromptRegressionResult();
        result.setId(400L);
        result.setCaseId(500L);
        result.setStatus("FAILED");
        result.setOutputJson("""
                {"phone":"13812345678","email":"ops@example.com","apiKey":"sk-live-secret","authorization":"Bearer abc.def"}
                """);
        result.setErrorMessage("prompt regression failed for 13812345678 ops@example.com token=abc123");
        when(promptRegressionResultMapper.selectList(any())).thenReturn(java.util.List.of(result));

        var vo = service.listPromptResults(500L).get(0);

        assertFalse(vo.getOutputJson().contains("13812345678"));
        assertFalse(vo.getOutputJson().contains("ops@example.com"));
        assertFalse(vo.getOutputJson().contains("sk-live-secret"));
        assertFalse(vo.getOutputJson().contains("Bearer abc.def"));
        assertFalse(vo.getErrorMessage().contains("13812345678"));
        assertFalse(vo.getErrorMessage().contains("ops@example.com"));
        assertTrue(vo.getOutputJson().contains("1**********"));
        assertTrue(vo.getOutputJson().contains("***@***"));
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
