package com.codecoachai.question.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.vector.domain.VectorCollectionInfo;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.codecoachai.question.config.QuestionDuplicateProperties;
import com.codecoachai.question.controller.QuestionEmbeddingController.RebuildDTO;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionEmbeddingControllerTest {

    @Mock
    private QuestionEmbeddingIndexService questionEmbeddingIndexService;
    @Mock
    private VectorIndexJobService vectorIndexJobService;
    @Mock
    private VectorStoreClient vectorStoreClient;
    @Mock
    private AdminPermissionGuard adminPermissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private QuestionEmbeddingController controller;

    @BeforeEach
    void setUp() {
        controller = new QuestionEmbeddingController(
                questionEmbeddingIndexService,
                new QuestionDuplicateProperties(),
                vectorIndexJobService,
                vectorStoreClient,
                adminPermissionGuard,
                operationConfirmationGuard
        );
    }

    @Test
    void previewRemainsAvailableWhenVectorStoreIsDisabled() {
        when(operationConfirmationGuard.cleanReason(null)).thenReturn(null);
        when(operationConfirmationGuard.cleanIdempotencyKey(null)).thenReturn(null);
        when(questionEmbeddingIndexService.stats()).thenReturn(Map.of("vectorEnabled", false));

        Map<String, Object> result = controller.rebuild(null).getData();

        assertEquals(true, result.get("requiresConfirmation"));
        assertEquals(true, result.get("dryRun"));
        verify(vectorStoreClient, never()).isEnabled();
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(null));
    }

    @Test
    void confirmedRebuildRejectsDisabledVectorStoreBeforeLockAndJob() {
        RebuildDTO dto = confirmedDto("rebuild-disabled", "rebuild-key");
        stubCleanInput(dto);
        when(vectorStoreClient.isEnabled()).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> controller.rebuild(dto));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("未启用"));
        verify(operationConfirmationGuard, never())
                .requireConfirmed(anyString(), eq(true), eq(false), anyString(), anyString());
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(100));
        verify(questionEmbeddingIndexService, never()).rebuild(eq(100));
    }

    @Test
    void confirmedRetryRejectsMissingCollectionBeforeLockAndJob() {
        RebuildDTO dto = confirmedDto("retry-missing", "retry-key");
        stubCleanInput(dto);
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        when(vectorStoreClient.collectionInfo(QuestionEmbeddingIndexService.QUESTION_COLLECTION))
                .thenReturn(missingCollection());

        BusinessException error = assertThrows(BusinessException.class, () -> controller.retryFailed(dto));

        assertEquals(ErrorCode.SEMANTIC_VALIDATION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("先执行受控全量重建"));
        verify(operationConfirmationGuard, never())
                .requireConfirmed(anyString(), eq(true), eq(false), anyString(), anyString());
        verify(vectorIndexJobService, never()).start(anyString(), anyString(), anyString(), eq(100));
        verify(questionEmbeddingIndexService, never()).retryFailed(eq(100));
    }

    @Test
    void confirmedRebuildMayInitializeMissingCollection() {
        RebuildDTO dto = confirmedDto("initialize-question", "initialize-key");
        stubCleanInput(dto);
        when(vectorStoreClient.isEnabled()).thenReturn(true);
        when(vectorStoreClient.collectionInfo(QuestionEmbeddingIndexService.QUESTION_COLLECTION))
                .thenReturn(missingCollection());
        when(operationConfirmationGuard.requireConfirmed(
                "question-vector-maintenance:QUESTION_REBUILD",
                true,
                false,
                "initialize-question",
                "initialize-key")).thenReturn("question-lock");
        when(vectorIndexJobService.start("QUESTION_REBUILD", "QUESTION", null, 100)).thenReturn(9L);
        when(questionEmbeddingIndexService.rebuild(100)).thenReturn(Map.of(
                "updated", 2,
                "vectorUpdated", 2,
                "vectorDeleted", 0,
                "failedBatches", 0,
                "errors", java.util.List.of()
        ));

        Map<String, Object> result = controller.rebuild(dto).getData();

        assertEquals(9L, result.get("vectorJobId"));
        assertEquals("SUCCESS", result.get("vectorJobStatus"));
        verify(questionEmbeddingIndexService).rebuild(100);
    }

    private void stubCleanInput(RebuildDTO dto) {
        when(operationConfirmationGuard.cleanReason(dto.getReason())).thenReturn(dto.getReason());
        when(operationConfirmationGuard.cleanIdempotencyKey(dto.getIdempotencyKey()))
                .thenReturn(dto.getIdempotencyKey());
    }

    private RebuildDTO confirmedDto(String reason, String idempotencyKey) {
        RebuildDTO dto = new RebuildDTO();
        dto.setLimit(100);
        dto.setConfirm(true);
        dto.setDryRun(false);
        dto.setReason(reason);
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private VectorCollectionInfo missingCollection() {
        return VectorCollectionInfo.builder()
                .collectionName(QuestionEmbeddingIndexService.QUESTION_COLLECTION)
                .exists(false)
                .status("NOT_FOUND")
                .build();
    }
}
