package com.codecoachai.ai.agent.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.ai.agent.service.KnowledgeEvaluationService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.vector.service.VectorIndexJobService;
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
}
