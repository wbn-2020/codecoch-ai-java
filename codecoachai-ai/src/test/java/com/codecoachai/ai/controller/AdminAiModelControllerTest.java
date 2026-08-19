package com.codecoachai.ai.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.domain.dto.AiModelConfigSaveDTO;
import com.codecoachai.ai.domain.dto.AiModelProbeDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.domain.vo.AiModelHealthLogRow;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.codecoachai.ai.security.AiProviderEndpointPolicy;
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
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AdminAiModelControllerTest {

    @Mock
    private AiModelConfigMapper mapper;
    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private ProviderAiCaller providerAiCaller;
    @Mock
    private AesGcmTextEncryptor apiKeyEncryptor;
    @Mock
    private AiProviderEndpointPolicy endpointPolicy;
    @Mock
    private AdminPermissionGuard permissionGuard;
    @Mock
    private AdminOperationConfirmationGuard operationConfirmationGuard;

    private AdminAiModelController controller;

    @BeforeEach
    void setUp() {
        initTableInfo(AiModelConfig.class);
        initTableInfo(AiCallLog.class);
        org.mockito.Mockito.lenient()
                .when(endpointPolicy.validateAndNormalizeBaseUrl(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        controller = new AdminAiModelController(
                mapper,
                aiCallLogMapper,
                providerAiCaller,
                apiKeyEncryptor,
                endpointPolicy,
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
    void healthUsesPersistedCallHistoryAndMasksFailureSummary() {
        when(mapper.selectById(7L)).thenReturn(model(7L, 0, 1));
        LocalDateTime failureAt = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime successAt = LocalDateTime.of(2026, 8, 10, 8, 0);
        AiModelHealthLogRow latestFailure = healthRow(7L, "LATEST", 0, failureAt,
                "Authorization: Bearer sk-live-secret token=private-value alice@example.com");
        AiModelHealthLogRow latestSuccess = healthRow(7L, "SUCCESS", 1, successAt, null);
        AiModelHealthLogRow failureBucket = healthRow(7L, "FAILURE", 0, failureAt,
                "Authorization: Bearer sk-live-secret token=private-value alice@example.com");
        when(aiCallLogMapper.selectModelHealthRows(any()))
                .thenReturn(List.of(latestFailure, latestSuccess, failureBucket));

        var result = controller.health(7L).getData();

        assertEquals("DEGRADED", result.getHealthStatus());
        assertEquals("FAILED", result.getLastCallStatus());
        assertEquals(failureAt, result.getLastCallAt());
        assertEquals(successAt, result.getLastSuccessAt());
        assertEquals(failureAt, result.getLastFailureAt());
        assertTrue(result.getLastFailureSummary().contains("******"));
        assertFalse(result.getLastFailureSummary().contains("sk-live-secret"));
        assertFalse(result.getLastFailureSummary().contains("private-value"));
        assertFalse(result.getLastFailureSummary().contains("alice@example.com"));
        verify(permissionGuard).require("admin:ai:model:list");
    }

    @Test
    void healthReturnsUnknownWithoutMatchingCallHistory() {
        when(mapper.selectById(7L)).thenReturn(model(7L, 0, 1));

        var result = controller.health(7L).getData();

        assertEquals("UNKNOWN", result.getHealthStatus());
        assertEquals("UNKNOWN", result.getLastCallStatus());
        assertNull(result.getLastFailureSummary());
    }

    @Test
    void probeUsesExactModelConfigAndPersistsSuccessfulHealthLog() {
        AiModelConfig model = model(7L, 0, 0);
        model.setApiBaseUrl("https://api.example.com/v1");
        model.setApiKey("encrypted-key");
        when(mapper.selectById(7L)).thenReturn(model);

        ProviderAiCaller.CallResult callResult = new ProviderAiCaller.CallResult();
        callResult.setProvider("openai");
        callResult.setModel("gpt-test");
        callResult.setContent("连接正常");
        callResult.setElapsedMs(123L);
        callResult.setPromptTokens(8);
        callResult.setCompletionTokens(4);
        callResult.setTotalTokens(12);
        when(providerAiCaller.probe(eq(model), any())).thenReturn(callResult);

        var result = controller.probe(7L, probeDto(false, "你好，请回复：已连接。")).getData();

        assertTrue(result.isSuccess());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(123L, result.getElapsedMs());
        verify(permissionGuard).require("admin:ai:model:publish");
        verify(providerAiCaller).probe(eq(model), eq("你好，请回复：已连接。"));
        assertEquals("你好，请回复：已连接。", result.getRequestPromptPreview());
        assertEquals("连接正常", result.getResponsePreview());
        verify(aiCallLogMapper).insert(any(AiCallLog.class));
        verify(mapper, never()).updateById(any(AiModelConfig.class));
    }

    @Test
    void probeReturnsMaskedFailureWithoutChangingModelState() {
        AiModelConfig model = model(7L, 0, 0);
        when(mapper.selectById(7L)).thenReturn(model);
        when(providerAiCaller.probe(eq(model), any()))
                .thenThrow(new AiProviderException(
                        AiFailureType.HTTP_ERROR,
                        "Provider openai HTTP 401",
                        401,
                        null));

        var result = controller.probe(7L, probeDto(false, "hi")).getData();

        assertFalse(result.isSuccess());
        assertEquals("FAILED", result.getStatus());
        assertEquals("HTTP_ERROR", result.getFailureType());
        assertEquals(401, result.getHttpStatus());
        verify(aiCallLogMapper).insert(any(AiCallLog.class));
        verify(mapper, never()).updateById(any(AiModelConfig.class));
    }

    @Test
    void probeUsesDefaultPromptWhenCustomPromptIsBlank() {
        AiModelConfig model = model(7L, 0, 0);
        when(mapper.selectById(7L)).thenReturn(model);
        ProviderAiCaller.CallResult callResult = new ProviderAiCaller.CallResult();
        callResult.setContent("连接正常");
        when(providerAiCaller.probe(eq(model), any())).thenReturn(callResult);

        controller.probe(7L, probeDto(false, "  "));

        verify(providerAiCaller).probe(eq(model), eq("请仅回复：连接正常。"));
    }

    @Test
    void probeRejectsPromptLongerThanFiveHundredCharactersBeforeCallingProvider() {
        AiModelConfig model = model(7L, 0, 0);
        when(mapper.selectById(7L)).thenReturn(model);
        String oversizedPrompt = "x".repeat(501);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.probe(7L, probeDto(false, oversizedPrompt)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(providerAiCaller, never()).probe(any(), any());
    }

    @Test
    void listIncludesHealthSummaryFromPersistedCallHistory() {
        AiModelConfig configuredModel = model(7L, 0, 1);
        LocalDateTime successAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        when(mapper.selectList(any())).thenReturn(List.of(configuredModel));
        when(aiCallLogMapper.selectModelHealthRows(any()))
                .thenReturn(List.of(
                        healthRow(7L, "LATEST", 1, successAt, null),
                        healthRow(7L, "SUCCESS", 1, successAt, null)));

        AiModelConfig result = controller.list(null, null, null, null).getData().get(0);

        assertEquals("HEALTHY", result.getCallHealthStatus());
        assertEquals(successAt, result.getLastCallSuccessAt());
        assertNull(result.getLastCallFailureAt());
        assertNull(result.getLastCallFailureSummary());
        verify(permissionGuard).require("admin:ai:model:list");
    }

    @Test
    void createNormalizesAndValidatesProviderBaseUrlBeforeInsert() {
        AiModelConfigSaveDTO dto = saveDto(false);
        dto.setApiBaseUrl("https://API.EXAMPLE.COM/v1/");
        when(endpointPolicy.validateAndNormalizeBaseUrl(dto.getApiBaseUrl()))
                .thenReturn("https://api.example.com/v1");

        controller.create(dto);

        ArgumentCaptor<AiModelConfig> captor = ArgumentCaptor.forClass(AiModelConfig.class);
        verify(mapper).insert(captor.capture());
        assertEquals("https://api.example.com/v1", captor.getValue().getApiBaseUrl());
    }

    @Test
    void createRejectsProviderBaseUrlOutsideSecurityPolicy() {
        AiModelConfigSaveDTO dto = saveDto(false);
        dto.setApiBaseUrl("http://127.0.0.1:8080");
        when(endpointPolicy.validateAndNormalizeBaseUrl(dto.getApiBaseUrl()))
                .thenThrow(new IllegalArgumentException("AI Provider base URL must use HTTPS"));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.create(dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), exception.getCode());
        assertEquals("接口地址必须使用 HTTPS", exception.getFieldErrors().get("apiBaseUrl"));
        verify(mapper, never()).insert(any(AiModelConfig.class));
    }

    @Test
    void createRejectsDuplicateProviderModelCodeBeforeInsert() {
        AiModelConfigSaveDTO dto = saveDto(false);
        when(mapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.create(dto));

        assertTrue(exception.getMessage().contains("已存在相同的模型标识"));
        verify(mapper, never()).insert(any(AiModelConfig.class));
    }

    @Test
    void createReportsGlobalDefaultConflictForUnexpectedDuplicateKey() {
        AiModelConfigSaveDTO dto = saveDto(false);
        when(mapper.insert(any(AiModelConfig.class)))
                .thenThrow(new DuplicateKeyException("uk_ai_model_one_global_default"));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.create(dto));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("全局默认模型发生并发冲突"));
        assertEquals(Boolean.TRUE, exception.getRetryable());
    }

    @Test
    void createRejectsDisabledDefaultModelAndReleasesIdempotencyLock() {
        AiModelConfigSaveDTO dto = saveDto(false);
        dto.setDefaultModel(1);
        dto.setEnabled(0);
        when(operationConfirmationGuard.requireConfirmed(
                "ai-model-create:openai:gpt-test",
                true,
                false,
                "confirm ai model save",
                "ai-model-create-1234"))
                .thenReturn("redis-lock-key");

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.create(dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("默认模型必须保持启用状态"));
        verify(operationConfirmationGuard).release("redis-lock-key");
        verify(mapper, never()).insert(any(AiModelConfig.class));
    }

    @Test
    void updateRejectsDisabledDefaultModelAndReleasesIdempotencyLock() {
        AiModelConfigSaveDTO dto = saveDto(false);
        dto.setDefaultModel(1);
        dto.setEnabled(0);
        when(operationConfirmationGuard.requireConfirmed(
                "ai-model-update:7",
                true,
                false,
                "confirm ai model save",
                "ai-model-create-1234"))
                .thenReturn("redis-lock-key");
        when(mapper.selectById(7L)).thenReturn(model(7L, 0, 1));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.update(7L, dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("默认模型必须保持启用状态"));
        verify(operationConfirmationGuard).release("redis-lock-key");
        verify(mapper, never()).updateById(any(AiModelConfig.class));
    }

    @Test
    void updateKeepsExistingDefaultWhenDefaultFlagIsNotIncluded() {
        AiModelConfigSaveDTO dto = saveDto(false);
        dto.setDefaultModel(null);
        dto.setIsDefault(null);
        when(mapper.selectById(7L)).thenReturn(model(7L, 1, 1));

        controller.update(7L, dto);

        ArgumentCaptor<AiModelConfig> captor = ArgumentCaptor.forClass(AiModelConfig.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getDefaultModel());
    }

    @Test
    void updateRejectsClearingCurrentDefaultWithoutSelectingReplacement() {
        AiModelConfigSaveDTO dto = saveDto(false);
        dto.setDefaultModel(0);
        when(mapper.selectById(7L)).thenReturn(model(7L, 1, 1));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.update(7L, dto));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("不能直接取消默认"));
        verify(mapper, never()).updateById(any(AiModelConfig.class));
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
        dto.setApiBaseUrl("https://api.example.com/v1");
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

    private static AiModelProbeDTO probeDto(Boolean dryRun, String prompt) {
        AiModelProbeDTO dto = new AiModelProbeDTO();
        dto.setConfirm(true);
        dto.setDryRun(dryRun);
        dto.setReason("confirm ai model operation");
        dto.setIdempotencyKey("ai-model-operation-1234");
        dto.setPrompt(prompt);
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
        model.setApiBaseUrl("https://api.example.com/v1");
        model.setApiKey("encrypted-test-key");
        return model;
    }

    private static AiModelHealthLogRow healthRow(
            Long modelConfigId,
            String bucket,
            Integer success,
            LocalDateTime createdAt,
            String errorMessage) {
        AiModelHealthLogRow row = new AiModelHealthLogRow();
        row.setModelConfigId(modelConfigId);
        row.setHealthBucket(bucket);
        row.setSuccess(success);
        row.setCreatedAt(createdAt);
        row.setErrorMessage(errorMessage);
        return row;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
