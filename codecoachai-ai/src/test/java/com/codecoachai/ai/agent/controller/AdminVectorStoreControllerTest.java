package com.codecoachai.ai.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.config.KnowledgeProperties;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.vector.config.VectorStoreProperties;
import com.codecoachai.common.vector.domain.VectorCollectionInfo;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminVectorStoreControllerTest {

    private static final String QUESTION_COLLECTION = "question_embedding";
    private static final String KNOWLEDGE_COLLECTION = "personal_knowledge_chunk";

    @Mock
    private VectorStoreClient vectorStoreClient;
    @Mock
    private VectorIndexJobService vectorIndexJobService;
    @Mock
    private V4AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AgentV4OpsService agentV4OpsService;

    private AdminVectorStoreController controller;

    @BeforeEach
    void setUp() {
        KnowledgeProperties knowledgeProperties = new KnowledgeProperties();
        VectorStoreProperties vectorStoreProperties = new VectorStoreProperties();
        controller = new AdminVectorStoreController(
                vectorStoreClient,
                vectorIndexJobService,
                permissionGuard,
                operationConfirmationGuard,
                jdbcTemplate,
                agentV4OpsService,
                knowledgeProperties,
                vectorStoreProperties
        );
    }

    @Test
    void healthTreatsMissingQuestionCollectionAsNotRequiredWhenQuestionBankIsEmpty() {
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        stubSourceCounts(0L, 2L);
        when(vectorStoreClient.collectionInfo(QUESTION_COLLECTION)).thenReturn(missingCollection(QUESTION_COLLECTION));
        when(vectorStoreClient.collectionInfo(KNOWLEDGE_COLLECTION)).thenReturn(healthyCollection(KNOWLEDGE_COLLECTION));

        Map<String, Object> health = controller.health().getData();
        Map<String, Object> questionState = nested(
                nested(health.get("collectionStates")).get("questionEmbedding"));
        Map<String, Object> checks = nested(health.get("checks"));

        assertEquals("NOT_REQUIRED", questionState.get("state"));
        assertFalse((Boolean) questionState.get("required"));
        assertEquals(0L, questionState.get("sourceCount"));
        assertTrue((Boolean) checks.get("collectionsPresent"));
        assertFalse((Boolean) checks.get("allCollectionsPhysicallyPresent"));
        assertEquals("HEALTHY", health.get("status"));
        verify(permissionGuard).require("admin:analytics:ai");
    }

    @Test
    void healthReturnsErrorStateInsteadOfFailingWhenCollectionProbeThrows() {
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        stubSourceCounts(3L, 0L);
        when(vectorStoreClient.collectionInfo(QUESTION_COLLECTION))
                .thenThrow(new IllegalStateException("qdrant timeout"));
        when(vectorStoreClient.collectionInfo(KNOWLEDGE_COLLECTION)).thenReturn(missingCollection(KNOWLEDGE_COLLECTION));

        Map<String, Object> health = controller.health().getData();
        Map<String, Object> questionState = nested(
                nested(health.get("collectionStates")).get("questionEmbedding"));

        assertEquals("ERROR", health.get("status"));
        assertEquals("ERROR", questionState.get("state"));
        assertTrue(String.valueOf(questionState.get("message")).contains("恢复 Qdrant"));
    }

    @Test
    void unhealthyRuntimeStillAllowsMaintenancePreviewWithoutStartingJob() {
        when(vectorStoreClient.isEnabled()).thenReturn(false);
        when(operationConfirmationGuard.cleanReason("preview")).thenReturn("preview");
        when(operationConfirmationGuard.cleanIdempotencyKey("preview-key")).thenReturn("preview-key");

        KnowledgeVectorRebuildVO preview = controller.rebuildKnowledgeVectors(
                100, false, "preview", false, "preview-key").getData();

        assertTrue(preview.getRequiresConfirmation());
        assertTrue(preview.getDryRun());
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(100));
        verify(agentV4OpsService, never()).rebuildAllKnowledgeVectors(eq(100));
    }

    @Test
    void rebuildRejectsBeforeLockAndJobWhenVectorStoreIsDisabled() {
        when(vectorStoreClient.isEnabled()).thenReturn(false);
        stubSourceCounts(0L, 4L);
        stubConfirmedInput("disabled-rebuild", "disabled-key");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.rebuildKnowledgeVectors(
                        100, true, "disabled-rebuild", false, "disabled-key"));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("向量存储未启用"));
        verify(operationConfirmationGuard, never())
                .requireConfirmed(anyString(), eq(true), eq(false), anyString(), anyString());
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(100));
        verify(agentV4OpsService, never()).rebuildAllKnowledgeVectors(eq(100));
    }

    @Test
    void retryRejectsMissingCollectionAndPointsToFullRebuild() {
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        stubSourceCounts(0L, 4L);
        when(vectorStoreClient.collectionInfo(QUESTION_COLLECTION)).thenReturn(missingCollection(QUESTION_COLLECTION));
        when(vectorStoreClient.collectionInfo(KNOWLEDGE_COLLECTION)).thenReturn(missingCollection(KNOWLEDGE_COLLECTION));
        stubConfirmedInput("retry-missing", "retry-key");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.retryFailedKnowledgeVectors(
                        100, true, "retry-missing", false, "retry-key"));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("先执行全量重建"));
        verify(operationConfirmationGuard, never())
                .requireConfirmed(anyString(), eq(true), eq(false), anyString(), anyString());
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(100));
        verify(agentV4OpsService, never()).retryAllFailedKnowledgeVectors(eq(100));
    }

    @Test
    void fullRebuildMayInitializeMissingCollectionAfterConfirmation() {
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        stubSourceCounts(0L, 4L);
        when(vectorStoreClient.collectionInfo(QUESTION_COLLECTION)).thenReturn(missingCollection(QUESTION_COLLECTION));
        when(vectorStoreClient.collectionInfo(KNOWLEDGE_COLLECTION)).thenReturn(missingCollection(KNOWLEDGE_COLLECTION));
        stubConfirmedInput("initialize-knowledge", "initialize-key");
        when(operationConfirmationGuard.requireConfirmed(
                "admin-vector-maintenance:KNOWLEDGE_REBUILD",
                true,
                false,
                "initialize-knowledge",
                "initialize-key")).thenReturn("initialize-lock");
        when(vectorIndexJobService.start("KNOWLEDGE_REBUILD", "KNOWLEDGE", null, 100)).thenReturn(8L);
        KnowledgeVectorRebuildVO rebuild = successfulRebuild();
        when(agentV4OpsService.rebuildAllKnowledgeVectors(100)).thenReturn(rebuild);

        KnowledgeVectorRebuildVO result = controller.rebuildKnowledgeVectors(
                100, true, "initialize-knowledge", false, "initialize-key").getData();

        assertEquals(8L, result.getJobId());
        assertEquals("SUCCESS", result.getVectorJobStatus());
        verify(agentV4OpsService).rebuildAllKnowledgeVectors(100);
    }

    @Test
    void deleteCompensationRejectsUnavailableTargetCollection() {
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        stubSourceCounts(0L, 0L);
        when(vectorStoreClient.collectionInfo(QUESTION_COLLECTION)).thenReturn(missingCollection(QUESTION_COLLECTION));
        when(vectorStoreClient.collectionInfo(KNOWLEDGE_COLLECTION)).thenReturn(missingCollection(KNOWLEDGE_COLLECTION));
        when(jdbcTemplate.queryForList(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM vector_delete_outbox") && sql.contains("GROUP BY status")) {
                return List.of(Map.of("status", "PENDING", "count", 1L));
            }
            if (sql.contains("FROM vector_delete_outbox") && sql.contains("GROUP BY collection_name")) {
                return List.of(Map.of(
                        "collectionName", QUESTION_COLLECTION,
                        "status", "PENDING",
                        "count", 1L
                ));
            }
            return List.of();
        });
        stubConfirmedInput("delete-retry", "delete-key");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.retryVectorDeletes(
                        100, true, "delete-retry", false, "delete-key"));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("删除补偿目标集合 question_embedding 当前不可用"));
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(100));
        verify(vectorStoreClient, never()).delete(eq(QUESTION_COLLECTION), eq(List.of()));
    }

    private void stubSourceCounts(long activeQuestions, long knowledgeChunks) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM question WHERE")) {
                return activeQuestions;
            }
            if (sql.contains("`personal_knowledge_chunk`")) {
                return knowledgeChunks;
            }
            return 0L;
        });
    }

    private void stubConfirmedInput(String reason, String idempotencyKey) {
        when(operationConfirmationGuard.cleanReason(reason)).thenReturn(reason);
        when(operationConfirmationGuard.cleanIdempotencyKey(idempotencyKey)).thenReturn(idempotencyKey);
    }

    private VectorCollectionInfo missingCollection(String name) {
        return VectorCollectionInfo.builder()
                .collectionName(name)
                .exists(false)
                .status("NOT_FOUND")
                .build();
    }

    private VectorCollectionInfo healthyCollection(String name) {
        return VectorCollectionInfo.builder()
                .collectionName(name)
                .exists(true)
                .status("green")
                .pointCount(2L)
                .vectorSize(1024)
                .distance("Cosine")
                .build();
    }

    private KnowledgeVectorRebuildVO successfulRebuild() {
        KnowledgeVectorRebuildVO result = new KnowledgeVectorRebuildVO();
        result.setDocumentCount(2);
        result.setChunkCount(4);
        result.setVectorUpdated(4);
        result.setVectorDeleted(0);
        result.setFailedDocuments(List.of());
        result.setErrors(List.of());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Object value) {
        return (Map<String, Object>) value;
    }
}
