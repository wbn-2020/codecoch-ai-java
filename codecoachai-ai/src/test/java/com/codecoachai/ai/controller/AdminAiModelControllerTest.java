package com.codecoachai.ai.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.domain.dto.AiModelConfigSaveDTO;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAiModelControllerTest {

    @Mock
    private AiModelConfigMapper mapper;
    @Mock
    private AesGcmTextEncryptor apiKeyEncryptor;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminAiModelController controller;

    @BeforeEach
    void setUp() {
        initTableInfo(AiModelConfig.class);
        controller = new AdminAiModelController(
                mapper,
                apiKeyEncryptor,
                permissionGuard,
                operationConfirmationGuard);
    }

    @Test
    void createForwardsDryRunToConfirmationGuardBeforeInsert() {
        AiModelConfigSaveDTO dto = saveDto(false);

        AiModelConfig result = controller.create(dto).getData();

        assertEquals("openai", result.getProvider());
        assertEquals("gpt-test", result.getModelCode());
        verify(permissionGuard).require("admin:ai:model:write");
        verify(operationConfirmationGuard).requireConfirmed(
                "ai-model-create:openai:gpt-test",
                true,
                false,
                "confirm ai model save",
                "ai-model-create-1234");
        verify(mapper).insert(any(AiModelConfig.class));
        verify(operationConfirmationGuard, never()).release(any());
    }

    @Test
    void updateRejectsDryRunBeforeLoadingModel() {
        AiModelConfigSaveDTO dto = saveDto(true);
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "dryRun rejected"))
                .when(operationConfirmationGuard)
                .requireConfirmed(
                        eq("ai-model-update:7"),
                        eq(true),
                        eq(true),
                        eq("confirm ai model save"),
                        eq("ai-model-create-1234"));

        assertThrows(BusinessException.class, () -> controller.update(7L, dto));

        verify(mapper, never()).selectById(any(Long.class));
        verify(mapper, never()).updateById(any(AiModelConfig.class));
    }

    @Test
    void deleteRejectsDefaultModelAndReleasesIdempotencyLock() {
        AdminAiModelController.AdminOperationConfirmDTO dto = confirmDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                "ai-model-delete:7",
                true,
                false,
                "confirm ai model operation",
                "ai-model-operation-1234"))
                .thenReturn("redis-lock-key");
        when(mapper.selectById(7L)).thenReturn(model(7L, 1, 1));

        assertThrows(BusinessException.class, () -> controller.delete(7L, dto));

        verify(operationConfirmationGuard).release("redis-lock-key");
        verify(mapper, never()).deleteById(any(Long.class));
    }

    @Test
    void statusRejectsDisablingDefaultModelAndReleasesIdempotencyLock() {
        AdminAiModelController.ModelStatusDTO dto = statusDto(0, false);
        when(operationConfirmationGuard.requireConfirmed(
                "ai-model-status:7",
                true,
                false,
                "confirm ai model status",
                "ai-model-status-1234"))
                .thenReturn("redis-lock-key");
        when(mapper.selectById(7L)).thenReturn(model(7L, 1, 1));

        assertThrows(BusinessException.class, () -> controller.updateStatus(7L, dto));

        verify(operationConfirmationGuard).release("redis-lock-key");
        verify(mapper, never()).updateById(any(AiModelConfig.class));
    }

    @Test
    void setDefaultEnablesTargetAndKeepsIdempotencyLockOnSuccess() {
        AdminAiModelController.AdminOperationConfirmDTO dto = confirmDto(false);
        when(mapper.selectById(7L)).thenReturn(model(7L, 0, 0));

        AiModelConfig result = controller.setDefault(7L, dto).getData();

        ArgumentCaptor<AiModelConfig> captor = ArgumentCaptor.forClass(AiModelConfig.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(7L, result.getId());
        assertEquals(1, captor.getValue().getDefaultModel());
        assertEquals(1, captor.getValue().getEnabled());
        verify(operationConfirmationGuard, never()).release(any());
    }

    @Test
    void setDefaultReleasesIdempotencyLockWhenMapperUpdateFails() {
        AdminAiModelController.AdminOperationConfirmDTO dto = confirmDto(false);
        when(operationConfirmationGuard.requireConfirmed(
                "ai-model-default:7",
                true,
                false,
                "confirm ai model operation",
                "ai-model-operation-1234"))
                .thenReturn("redis-lock-key");
        when(mapper.selectById(7L)).thenReturn(model(7L, 0, 0));
        when(mapper.updateById(any(AiModelConfig.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> controller.setDefault(7L, dto));

        verify(operationConfirmationGuard).release("redis-lock-key");
    }

    private static AiModelConfigSaveDTO saveDto(Boolean dryRun) {
        AiModelConfigSaveDTO dto = new AiModelConfigSaveDTO();
        dto.setProvider("openai");
        dto.setModelName("gpt-test");
        dto.setDisplayName("GPT Test");
        dto.setEnabled(1);
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm ai model save");
        dto.setIdempotencyKey("ai-model-create-1234");
        return dto;
    }

    private static AdminAiModelController.AdminOperationConfirmDTO confirmDto(Boolean dryRun) {
        AdminAiModelController.AdminOperationConfirmDTO dto = new AdminAiModelController.AdminOperationConfirmDTO();
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm ai model operation");
        dto.setIdempotencyKey("ai-model-operation-1234");
        return dto;
    }

    private static AdminAiModelController.ModelStatusDTO statusDto(Integer status, Boolean dryRun) {
        AdminAiModelController.ModelStatusDTO dto = new AdminAiModelController.ModelStatusDTO();
        dto.setStatus(status);
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm ai model status");
        dto.setIdempotencyKey("ai-model-status-1234");
        return dto;
    }

    private static AiModelConfig model(Long id, Integer defaultModel, Integer enabled) {
        AiModelConfig model = new AiModelConfig();
        model.setId(id);
        model.setProvider("openai");
        model.setModelCode("gpt-test");
        model.setModelName("GPT Test");
        model.setDefaultModel(defaultModel);
        model.setEnabled(enabled);
        return model;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
