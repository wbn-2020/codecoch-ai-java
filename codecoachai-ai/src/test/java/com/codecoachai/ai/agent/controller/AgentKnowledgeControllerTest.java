package com.codecoachai.ai.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDocumentVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeDuplicateCleanupVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeVectorRebuildVO;
import com.codecoachai.ai.agent.service.AgentContextUsageReferenceService;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.KnowledgeEvaluationService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeControllerTest {

    private static final long USER_ID = 10L;

    @Mock
    private AgentV4OpsService agentV4OpsService;
    @Mock
    private AgentContextUsageReferenceService usageReferenceService;
    @Mock
    private KnowledgeEvaluationService knowledgeEvaluationService;
    @Mock
    private VectorIndexJobService vectorIndexJobService;
    @Mock
    private V4FeatureGate v4FeatureGate;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AgentKnowledgeController controller;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("phase9-user")
                .build());
        controller = new AgentKnowledgeController(
                agentV4OpsService,
                usageReferenceService,
                knowledgeEvaluationService,
                vectorIndexJobService,
                v4FeatureGate,
                operationConfirmationGuard);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void listDocumentsReturnsPagedResultAndPassesPaginationParams() {
        KnowledgeDocumentVO document = new KnowledgeDocumentVO();
        document.setId(100L);
        PageResult<KnowledgeDocumentVO> page = PageResult.of(java.util.List.of(document), 1, 2, 50);
        when(agentV4OpsService.pageKnowledgeDocuments(USER_ID, "Java", "NOTE", "INDEXED", 2L, 50L))
                .thenReturn(page);

        var response = controller.listDocuments("Java", "NOTE", "INDEXED", 2L, 50L);

        assertEquals(1, response.getData().getTotal());
        assertEquals(2, response.getData().getPageNo());
        assertEquals(50, response.getData().getPageSize());
        verify(agentV4OpsService).pageKnowledgeDocuments(USER_ID, "Java", "NOTE", "INDEXED", 2L, 50L);
    }

    @Test
    void deleteChunkWithoutValidConfirmationDoesNotCallService() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_DELETE_CHUNK:10:200"),
                eq(false),
                eq(false),
                eq("确认删除"),
                eq("op-12345678")))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class,
                () -> controller.deleteChunk(200L, false, false, "确认删除", "op-12345678"));

        verify(agentV4OpsService, never()).deleteKnowledgeChunk(any(), any());
    }

    @Test
    void deleteDocumentReleasesConfirmationLockWhenServiceFails() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_DELETE_DOCUMENT:10:100"),
                eq(true),
                eq(false),
                eq("确认删除"),
                eq("op-12345678")))
                .thenReturn("lock-key");
        doThrow(new IllegalArgumentException("知识文档不存在或无权访问"))
                .when(agentV4OpsService).deleteKnowledgeDocument(USER_ID, 100L);

        assertThrows(IllegalArgumentException.class,
                () -> controller.deleteDocument(100L, true, false, "确认删除", "op-12345678"));

        verify(operationConfirmationGuard).release("lock-key");
    }

    @Test
    void cleanupExactDuplicatesWithoutConfirmationDoesNotCallService() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_DUPLICATE_CLEANUP:10"),
                isNull(),
                eq(false),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class,
                () -> controller.cleanupExactDuplicates(false, 20, 100L, "NOTE", null, null, null));

        verify(agentV4OpsService, never()).cleanupExactDuplicateKnowledgeChunks(any(), any(), any(), any(), any());
    }

    @Test
    void cleanupExactDuplicatesReleasesConfirmationLockWhenServiceFails() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_DUPLICATE_CLEANUP:10"),
                eq(true),
                eq(false),
                eq("cleanup reason"),
                eq("cleanup-op-12345678")))
                .thenReturn("cleanup-lock");
        doThrow(new IllegalStateException("cleanup failed"))
                .when(agentV4OpsService)
                .cleanupExactDuplicateKnowledgeChunks(USER_ID, false, 20, 100L, "NOTE");

        assertThrows(IllegalStateException.class,
                () -> controller.cleanupExactDuplicates(false, 20, 100L, "NOTE",
                        true, "cleanup reason", "cleanup-op-12345678"));

        verify(operationConfirmationGuard).release("cleanup-lock");
    }

    @Test
    void restoreDocumentVersionWithoutConfirmationDoesNotCallService() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_RESTORE_DOCUMENT_VERSION:10:100:300"),
                isNull(),
                eq(false),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class,
                () -> invokeRestoreDocumentVersion(100L, 300L, null, false, null, null));

        verify(agentV4OpsService, never()).restoreKnowledgeDocumentVersion(any(), any(), any());
    }

    @Test
    void restoreDocumentVersionWithConfirmationKeepsOriginalServiceCall() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_RESTORE_DOCUMENT_VERSION:10:100:300"),
                eq(true),
                eq(false),
                eq("restore reason"),
                eq("restore-op-12345678")))
                .thenReturn("restore-lock");

        invokeRestoreDocumentVersion(100L, 300L, true, false, "restore reason", "restore-op-12345678");

        verify(agentV4OpsService).restoreKnowledgeDocumentVersion(USER_ID, 100L, 300L);
    }

    @Test
    void deleteEvalCaseWithoutConfirmationDoesNotCallService() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_DELETE_EVAL_CASE:10:400"),
                isNull(),
                eq(false),
                isNull(),
                isNull()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "confirm required"));

        assertThrows(BusinessException.class,
                () -> invokeDeleteEvalCase(400L, null, false, null, null));

        verify(knowledgeEvaluationService, never()).deleteCase(any(), any());
    }

    @Test
    void deleteEvalCaseWithConfirmationKeepsOriginalServiceCall() {
        when(operationConfirmationGuard.requireConfirmed(
                eq("KNOWLEDGE_DELETE_EVAL_CASE:10:400"),
                eq(true),
                eq(false),
                eq("delete eval reason"),
                eq("delete-eval-op-12345678")))
                .thenReturn("delete-eval-lock");

        invokeDeleteEvalCase(400L, true, false, "delete eval reason", "delete-eval-op-12345678");

        verify(knowledgeEvaluationService).deleteCase(USER_ID, 400L);
    }

    @Test
    void rebuildVectorsWithoutConfirmationReturnsPreviewWithoutStartingJob() {
        when(operationConfirmationGuard.cleanReason("rebuild reason")).thenReturn("rebuild reason");
        when(operationConfirmationGuard.cleanIdempotencyKey("vector-rebuild-1234"))
                .thenReturn("vector-rebuild-1234");

        KnowledgeVectorRebuildVO result = controller
                .rebuildVectors(42L, null, "rebuild reason", null, "vector-rebuild-1234")
                .getData();

        assertEquals(Boolean.TRUE, result.getRequiresConfirmation());
        assertEquals(Boolean.TRUE, result.getDryRun());
        assertEquals("KNOWLEDGE_REBUILD", result.getOperation());
        assertEquals("PREVIEW", result.getVectorJobStatus());
        assertEquals("42", result.getVectorScopeId());
        verify(vectorIndexJobService, never()).start(any(), any(), any(), any());
        verify(agentV4OpsService, never()).rebuildKnowledgeVectors(any(), any());
    }

    @Test
    void retryFailedVectorsWithConfirmationStartsJobAndCallsService() {
        when(operationConfirmationGuard.cleanReason("retry reason")).thenReturn("retry reason");
        when(operationConfirmationGuard.cleanIdempotencyKey("vector-retry-1234"))
                .thenReturn("vector-retry-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("knowledge-vector-maintenance:KNOWLEDGE_RETRY:10"),
                eq(true),
                eq(false),
                eq("retry reason"),
                eq("vector-retry-1234")))
                .thenReturn("retry-lock");
        when(vectorIndexJobService.start("KNOWLEDGE_RETRY", "KNOWLEDGE", "FAILED_OR_STALE", 5))
                .thenReturn(77L);
        KnowledgeVectorRebuildVO rebuild = new KnowledgeVectorRebuildVO();
        rebuild.setChunkCount(6);
        rebuild.setVectorUpdated(6);
        rebuild.setVectorDeleted(0);
        rebuild.setFailedDocuments(java.util.List.of());
        when(agentV4OpsService.retryFailedKnowledgeVectors(USER_ID, 5)).thenReturn(rebuild);

        KnowledgeVectorRebuildVO result = controller
                .retryFailedVectors(5, true, "retry reason", false, "vector-retry-1234")
                .getData();

        assertEquals(Boolean.FALSE, result.getRequiresConfirmation());
        assertEquals(Boolean.FALSE, result.getDryRun());
        assertEquals("KNOWLEDGE_RETRY", result.getOperation());
        assertEquals(5, result.getRequestedLimit());
        assertEquals(77L, result.getVectorJobId());
        assertEquals("SUCCESS", result.getVectorJobStatus());
        verify(agentV4OpsService).retryFailedKnowledgeVectors(USER_ID, 5);
        verify(vectorIndexJobService).finish(eq(77L), eq("SUCCESS"), any(),
                eq(6L), eq(6L), eq(0L), eq(6L), eq(0L), isNull());
    }

    @Test
    void rebuildVectorsReleasesConfirmationLockAndFailsJobWhenServiceFails() {
        when(operationConfirmationGuard.cleanReason("rebuild reason")).thenReturn("rebuild reason");
        when(operationConfirmationGuard.cleanIdempotencyKey("vector-rebuild-1234"))
                .thenReturn("vector-rebuild-1234");
        when(operationConfirmationGuard.requireConfirmed(
                eq("knowledge-vector-maintenance:KNOWLEDGE_REBUILD:10"),
                eq(true),
                eq(false),
                eq("rebuild reason"),
                eq("vector-rebuild-1234")))
                .thenReturn("rebuild-lock");
        when(vectorIndexJobService.start("KNOWLEDGE_REBUILD", "KNOWLEDGE", "42", null))
                .thenReturn(88L);
        IllegalStateException failure = new IllegalStateException("vector rebuild failed");
        doThrow(failure).when(agentV4OpsService).rebuildKnowledgeVectors(USER_ID, 42L);

        assertThrows(IllegalStateException.class,
                () -> controller.rebuildVectors(42L, true, "rebuild reason", false, "vector-rebuild-1234"));

        verify(operationConfirmationGuard).release("rebuild-lock");
        verify(vectorIndexJobService).fail(88L, failure);
    }

    private void invokeRestoreDocumentVersion(Long documentId,
                                              Long versionId,
                                              Boolean confirm,
                                              Boolean dryRun,
                                              String reason,
                                              String idempotencyKey) {
        invokeControllerMethod("restoreDocumentVersion",
                new Class<?>[] {Long.class, Long.class, Boolean.class, Boolean.class, String.class, String.class},
                documentId, versionId, confirm, dryRun, reason, idempotencyKey);
    }

    private void invokeDeleteEvalCase(Long id,
                                      Boolean confirm,
                                      Boolean dryRun,
                                      String reason,
                                      String idempotencyKey) {
        invokeControllerMethod("deleteEvalCase",
                new Class<?>[] {Long.class, Boolean.class, Boolean.class, String.class, String.class},
                id, confirm, dryRun, reason, idempotencyKey);
    }

    private void invokeControllerMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = AgentKnowledgeController.class.getMethod(methodName, parameterTypes);
            method.invoke(controller, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
