package com.codecoachai.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.domain.dto.AiModelConfigSaveDTO;
import com.codecoachai.ai.domain.dto.AiModelProbeDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.domain.vo.AiModelHealthLogRow;
import com.codecoachai.ai.domain.vo.AiModelProbeVO;
import com.codecoachai.ai.domain.vo.AiModelHealthSummaryVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.codecoachai.ai.security.AiProviderEndpointPolicy;
import com.codecoachai.ai.security.SensitiveTextMasker;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminAiModelController {

    private static final String PERM_MODEL_LIST = "admin:ai:model:list";
    private static final String PERM_MODEL_WRITE = "admin:ai:model:write";
    private static final String PERM_MODEL_PUBLISH = "admin:ai:model:publish";
    private static final String MODEL_PROBE_PROMPT = "请仅回复：连接正常。";

    private final AiModelConfigMapper mapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final ProviderAiCaller providerAiCaller;
    private final AesGcmTextEncryptor apiKeyEncryptor;
    private final AiProviderEndpointPolicy endpointPolicy;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/ai/models")
    public Result<List<AiModelConfig>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String provider,
                                            @RequestParam(required = false) Integer enabled,
                                            @RequestParam(required = false) Integer status) {
        permissionGuard.require(PERM_MODEL_LIST);
        Integer resolvedEnabled = enabled != null ? enabled : status;
        List<AiModelConfig> rows = mapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(AiModelConfig::getProvider, keyword)
                        .or().like(AiModelConfig::getModelCode, keyword)
                        .or().like(AiModelConfig::getModelName, keyword)
                        .or().like(AiModelConfig::getRemark, keyword))
                .eq(StringUtils.hasText(provider), AiModelConfig::getProvider, provider)
                .eq(resolvedEnabled != null, AiModelConfig::getEnabled, resolvedEnabled)
                .orderByDesc(AiModelConfig::getDefaultModel)
                .orderByAsc(AiModelConfig::getSortOrder)
                .orderByDesc(AiModelConfig::getUpdatedAt));
        Map<Long, Map<String, AiModelHealthLogRow>> healthRows = loadHealthRows(rows);
        rows.forEach(row -> applyListSummary(row, healthRows.getOrDefault(row.getId(), Collections.emptyMap())));
        return Result.success(rows);
    }

    @GetMapping("/admin/ai/model-configs")
    public Result<List<AiModelConfig>> listCompat(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String provider,
                                                  @RequestParam(required = false) Integer enabled,
                                                  @RequestParam(required = false) Integer status) {
        return list(keyword, provider, enabled, status);
    }

    @GetMapping("/admin/ai/models/{id}")
    public Result<AiModelConfig> detail(@PathVariable Long id) {
        permissionGuard.require(PERM_MODEL_LIST);
        return Result.success(maskApiKey(get(id)));
    }

    @GetMapping("/admin/ai/model-configs/{id}")
    public Result<AiModelConfig> detailCompat(@PathVariable Long id) {
        return detail(id);
    }

    @GetMapping({"/admin/ai/models/{id}/health", "/admin/ai/model-configs/{id}/health"})
    public Result<AiModelHealthSummaryVO> health(@PathVariable Long id) {
        permissionGuard.require(PERM_MODEL_LIST);
        AiModelConfig modelConfig = get(id);
        Map<Long, Map<String, AiModelHealthLogRow>> healthRows = loadHealthRows(List.of(modelConfig));
        return Result.success(buildHealthSummary(
                modelConfig,
                healthRows.getOrDefault(modelConfig.getId(), Collections.emptyMap())));
    }

    @PostMapping("/admin/ai/models")
    @OperationLog(module = "ai", action = "CREATE_AI_MODEL", description = "Create AI model config", logArgs = false, logResponse = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<AiModelConfig> create(@RequestBody AiModelConfigSaveDTO dto) {
        permissionGuard.require(PERM_MODEL_WRITE);
        return runConfirmedOperation("ai-model-create:" + modelOperationTarget(dto),
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    AiModelConfig entity = new AiModelConfig();
                    apply(entity, dto);
                    ensureDefaultModelEnabled(entity);
                    ensureModelCodeUnique(entity.getProvider(), entity.getModelCode(), null);
                    if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
                        clearGlobalDefault(null);
                    }
                    encryptPlainApiKeyBeforeSave(entity);
                    writeModelConfigWithDefaultGuard(() -> mapper.insert(entity));
                    return Result.success(maskApiKey(entity));
                });
    }

    @PutMapping("/admin/ai/models/{id}")
    @OperationLog(module = "ai", action = "UPDATE_AI_MODEL", description = "Update AI model config", logArgs = false, logResponse = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<AiModelConfig> update(@PathVariable Long id, @RequestBody AiModelConfigSaveDTO dto) {
        permissionGuard.require(PERM_MODEL_WRITE);
        return runConfirmedOperation("ai-model-update:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    AiModelConfig entity = get(id);
                    Integer originalDefaultModel = entity.getDefaultModel();
                    apply(entity, dto);
                    ensureExistingDefaultIsNotCleared(originalDefaultModel, entity.getDefaultModel());
                    ensureDefaultModelEnabled(entity);
                    ensureModelCodeUnique(entity.getProvider(), entity.getModelCode(), id);
                    if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
                        clearGlobalDefault(id);
                    }
                    encryptPlainApiKeyBeforeSave(entity);
                    writeModelConfigWithDefaultGuard(() -> mapper.updateById(entity));
                    return Result.success(maskApiKey(entity));
                });
    }

    @PostMapping("/admin/ai/models/{id}/set-default")
    @OperationLog(module = "ai", action = "SET_DEFAULT_AI_MODEL", description = "Set default AI model", logResponse = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<AiModelConfig> setDefault(@PathVariable Long id,
                                            @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        permissionGuard.require(PERM_MODEL_PUBLISH);
        return runConfirmedOperation("ai-model-default:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    AiModelConfig entity = get(id);
                    clearGlobalDefault(id);
                    entity.setDefaultModel(1);
                    entity.setEnabled(1);
                    encryptPlainApiKeyBeforeSave(entity);
                    writeModelConfigWithDefaultGuard(() -> mapper.updateById(entity));
                    return Result.success(maskApiKey(entity));
                });
    }

    @PutMapping("/admin/ai/models/{id}/default")
    @OperationLog(module = "ai", action = "SET_DEFAULT_AI_MODEL_COMPAT", description = "Set default AI model via compatibility endpoint", logResponse = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<AiModelConfig> setDefaultCompat(@PathVariable Long id,
                                                  @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        return setDefault(id, dto);
    }

    @PutMapping("/admin/ai/models/{id}/status")
    @OperationLog(module = "ai", action = "UPDATE_AI_MODEL_STATUS", description = "Update AI model status", logResponse = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<AiModelConfig> updateStatus(@PathVariable Long id, @RequestBody ModelStatusDTO dto) {
        permissionGuard.require(PERM_MODEL_PUBLISH);
        return runConfirmedOperation("ai-model-status:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    AiModelConfig entity = get(id);
                    Integer enabled = dto == null ? null : (dto.getEnabled() != null ? dto.getEnabled() : dto.getStatus());
                    ensureDefaultModelNotDisabled(entity, enabled);
                    entity.setEnabled(enabled == null ? 1 : enabled);
                    encryptPlainApiKeyBeforeSave(entity);
                    mapper.updateById(entity);
                    return Result.success(maskApiKey(entity));
        });
    }

    @PostMapping("/admin/ai/models/{id}/probe")
    @OperationLog(module = "ai", action = "PROBE_AI_MODEL", description = "Probe AI model connectivity",
            logArgs = false, logResponse = false)
    public Result<AiModelProbeVO> probe(@PathVariable Long id,
                                        @RequestBody(required = false) AiModelProbeDTO dto) {
        permissionGuard.require(PERM_MODEL_PUBLISH);
        return runConfirmedOperation("ai-model-probe:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    AiModelConfig modelConfig = get(id);
                    String probePrompt = resolveProbePrompt(dto == null ? null : dto.getPrompt());
                    long startedAt = System.currentTimeMillis();
                    try {
                        ProviderAiCaller.CallResult callResult =
                                providerAiCaller.probe(modelConfig, probePrompt);
                        AiModelProbeVO result = successProbe(modelConfig, callResult, probePrompt);
                        recordProbeLog(modelConfig, callResult, null, 1,
                                System.currentTimeMillis() - startedAt);
                        return Result.success(result);
                    } catch (AiProviderException ex) {
                        AiModelProbeVO result = failureProbe(modelConfig, ex,
                                System.currentTimeMillis() - startedAt, probePrompt);
                        recordProbeLog(modelConfig, null, result.getMessage(), 0, result.getElapsedMs());
                        return Result.success(result);
                    }
                });
    }

    @DeleteMapping("/admin/ai/models/{id}")
    @OperationLog(module = "ai", action = "DELETE_AI_MODEL", description = "Delete AI model config", logResponse = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id,
                               @RequestBody(required = false) AdminOperationConfirmDTO dto) {
        permissionGuard.require(PERM_MODEL_WRITE);
        return runConfirmedOperation("ai-model-delete:" + id,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey(),
                () -> {
                    AiModelConfig entity = get(id);
                    ensureDefaultModelNotDeleted(entity);
                    mapper.deleteById(id);
                    return Result.success();
                });
    }

    private <T> Result<T> runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                                String reason, String idempotencyKey,
                                                Supplier<Result<T>> action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(operation, confirm, dryRun, reason, idempotencyKey);
        try {
            return action.get();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    private String modelOperationTarget(AiModelConfigSaveDTO dto) {
        if (dto == null) {
            return "new";
        }
        String modelCode = StringUtils.hasText(dto.getModelCode()) ? dto.getModelCode() : dto.getModelName();
        return (dto.getProvider() == null ? "" : dto.getProvider().trim())
                + ":" + (modelCode == null ? "" : modelCode.trim());
    }

    private void apply(AiModelConfig entity, AiModelConfigSaveDTO dto) {
        String modelCode = dto == null ? null : (StringUtils.hasText(dto.getModelCode()) ? dto.getModelCode() : dto.getModelName());
        if (dto == null || !StringUtils.hasText(dto.getProvider()) || !StringUtils.hasText(modelCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "供应商和模型标识不能为空");
        }
        entity.setProvider(dto.getProvider().trim());
        entity.setModelCode(modelCode.trim());
        entity.setModelName(StringUtils.hasText(dto.getDisplayName()) ? dto.getDisplayName().trim()
                : StringUtils.hasText(dto.getModelName()) ? dto.getModelName().trim() : entity.getModelCode());
        entity.setCapabilityTags(dto.getCapabilityTags());
        entity.setApiBaseUrl(normalizeApiBaseUrl(dto.getApiBaseUrl()));
        if (StringUtils.hasText(dto.getApiKey())) {
            entity.setApiKey(encryptApiKey(dto.getApiKey()));
        }
        entity.setTemperature(dto.getTemperature());
        entity.setMaxTokens(dto.getMaxTokens());
        Integer requestedDefault = dto.getDefaultModel() != null ? dto.getDefaultModel() : dto.getIsDefault();
        if (requestedDefault != null) {
            entity.setDefaultModel(requestedDefault);
        } else if (entity.getDefaultModel() == null) {
            entity.setDefaultModel(0);
        }
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled()
                : dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
        entity.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : dto.getDescription());
    }

    private AiModelConfig get(Long id) {
        AiModelConfig entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型配置不存在或已不可用");
        }
        return entity;
    }

    private void clearGlobalDefault(Long excludeId) {
        mapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .ne(excludeId != null, AiModelConfig::getId, excludeId)
                .set(AiModelConfig::getDefaultModel, 0));
    }

    private void ensureDefaultModelEnabled(AiModelConfig entity) {
        if (Integer.valueOf(1).equals(entity.getDefaultModel())
                && !Integer.valueOf(1).equals(entity.getEnabled())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "默认模型必须保持启用状态");
        }
    }

    private void ensureExistingDefaultIsNotCleared(Integer originalDefaultModel, Integer updatedDefaultModel) {
        if (Integer.valueOf(1).equals(originalDefaultModel)
                && !Integer.valueOf(1).equals(updatedDefaultModel)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "当前默认模型不能通过编辑取消默认，请先将其它启用模型设为默认");
        }
    }

    private void writeModelConfigWithDefaultGuard(Runnable action) {
        try {
            action.run();
        } catch (DuplicateKeyException ex) {
            if (isModelCodeDuplicate(ex)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "同一供应商下已存在相同的模型标识，请修改后重试");
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "全局默认模型冲突，请刷新后重试");
        }
    }

    private void ensureModelCodeUnique(String provider, String modelCode, Long excludeId) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getProvider, provider)
                .eq(AiModelConfig::getModelCode, modelCode)
                .ne(excludeId != null, AiModelConfig::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "同一供应商下已存在相同的模型标识，请修改后重试");
        }
    }

    private boolean isModelCodeDuplicate(DuplicateKeyException ex) {
        String message = ex == null ? null : ex.getMessage();
        return StringUtils.hasText(message) && message.contains("uk_ai_model_provider_code");
    }

    private void ensureDefaultModelNotDisabled(AiModelConfig entity, Integer enabled) {
        if (Integer.valueOf(1).equals(entity.getDefaultModel()) && Integer.valueOf(0).equals(enabled)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Default AI model cannot be disabled. Set another default model first.");
        }
    }

    private void ensureDefaultModelNotDeleted(AiModelConfig entity) {
        if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Default AI model cannot be deleted. Set another default model first.");
        }
    }

    private AiModelConfig maskApiKey(AiModelConfig entity) {
        if (entity != null) {
            entity.setApiKeyMasked(maskStoredApiKey(entity.getApiKey()));
            entity.setApiKey(null);
        }
        return entity;
    }

    private AiModelProbeVO successProbe(
            AiModelConfig modelConfig,
            ProviderAiCaller.CallResult callResult,
            String probePrompt) {
        AiModelProbeVO result = new AiModelProbeVO();
        result.setModelId(modelConfig.getId());
        result.setProvider(modelConfig.getProvider());
        result.setModelCode(modelConfig.getModelCode());
        result.setSuccess(true);
        result.setStatus("SUCCESS");
        result.setFailureType(AiFailureType.NONE.name());
        result.setElapsedMs(callResult.getElapsedMs());
        result.setPromptTokens(callResult.getPromptTokens());
        result.setCompletionTokens(callResult.getCompletionTokens());
        result.setTotalTokens(callResult.getTotalTokens());
        result.setMessage("连接成功");
        result.setRequestPromptPreview(SensitiveTextMasker.safePreview(probePrompt));
        result.setResponsePreview(SensitiveTextMasker.safePreview(callResult.getContent()));
        return result;
    }

    private AiModelProbeVO failureProbe(
            AiModelConfig modelConfig,
            AiProviderException exception,
            long elapsedMs,
            String probePrompt) {
        AiModelProbeVO result = new AiModelProbeVO();
        result.setModelId(modelConfig.getId());
        result.setProvider(modelConfig.getProvider());
        result.setModelCode(modelConfig.getModelCode());
        result.setSuccess(false);
        result.setStatus("FAILED");
        result.setFailureType(exception.getFailureType() == null
                ? AiFailureType.UNKNOWN_ERROR.name()
                : exception.getFailureType().name());
        result.setHttpStatus(exception.getHttpStatus());
        result.setElapsedMs(elapsedMs);
        result.setMessage(SensitiveTextMasker.safePreview(exception.getMessage()));
        result.setRequestPromptPreview(SensitiveTextMasker.safePreview(probePrompt));
        return result;
    }

    private String resolveProbePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return MODEL_PROBE_PROMPT;
        }
        String value = prompt.trim();
        if (value.length() > 500) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "测试语句不能超过 500 个字符");
        }
        return value;
    }

    private void recordProbeLog(
            AiModelConfig modelConfig,
            ProviderAiCaller.CallResult callResult,
            String failureMessage,
            Integer success,
            long elapsedMs) {
        try {
            AiCallLog logEntry = new AiCallLog();
            logEntry.setScene("ADMIN_MODEL_PROBE");
            logEntry.setModelName(modelConfig.getModelCode());
            logEntry.setModel(modelConfig.getModelCode());
            logEntry.setRouteTrace(modelConfig.getProvider());
            logEntry.setElapsedMs(elapsedMs);
            logEntry.setCostMillis(elapsedMs);
            logEntry.setSuccess(success);
            logEntry.setStatus(success);
            logEntry.setErrorMessage(failureMessage);
            if (callResult != null) {
                logEntry.setPromptTokens(callResult.getPromptTokens());
                logEntry.setCompletionTokens(callResult.getCompletionTokens());
                logEntry.setTotalTokens(callResult.getTotalTokens());
                logEntry.setEstimatedCost(callResult.getEstimatedCost());
                logEntry.setTokenCost(callResult.getEstimatedCost());
            }
            aiCallLogMapper.insert(logEntry);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist AI model probe log for modelId={}", modelConfig.getId(), ex);
        }
    }

    private void applyListSummary(AiModelConfig entity, Map<String, AiModelHealthLogRow> healthRows) {
        if (entity == null) {
            return;
        }
        AiModelHealthSummaryVO summary = buildHealthSummary(entity, healthRows);
        entity.setCallHealthStatus(summary.getHealthStatus());
        entity.setLastCallSuccessAt(summary.getLastSuccessAt());
        entity.setLastCallFailureAt(summary.getLastFailureAt());
        entity.setLastCallFailureSummary(summary.getLastFailureSummary());
        maskApiKey(entity);
    }

    private AiModelHealthSummaryVO buildHealthSummary(
            AiModelConfig modelConfig,
            Map<String, AiModelHealthLogRow> healthRows) {
        AiModelHealthLogRow latestCall = healthRows.get("LATEST");
        AiModelHealthLogRow latestSuccess = healthRows.get("SUCCESS");
        AiModelHealthLogRow latestFailure = healthRows.get("FAILURE");

        AiModelHealthSummaryVO vo = new AiModelHealthSummaryVO();
        vo.setModelId(modelConfig.getId());
        vo.setProvider(modelConfig.getProvider());
        vo.setModelCode(modelConfig.getModelCode());
        vo.setHealthStatus(resolveHealthStatus(latestCall));
        vo.setLastCallStatus(resolveCallStatus(latestCall));
        vo.setLastCallAt(createdAt(latestCall));
        vo.setLastSuccessAt(createdAt(latestSuccess));
        vo.setLastFailureAt(createdAt(latestFailure));
        vo.setLastFailureSummary(latestFailure == null
                ? null
                : SensitiveTextMasker.safePreview(latestFailure.getErrorMessage()));
        return vo;
    }

    private Map<Long, Map<String, AiModelHealthLogRow>> loadHealthRows(List<AiModelConfig> modelConfigs) {
        if (modelConfigs == null || modelConfigs.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiModelHealthLogRow> rows = aiCallLogMapper.selectModelHealthRows(modelConfigs);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.stream()
                .filter(row -> row.getModelConfigId() != null && StringUtils.hasText(row.getHealthBucket()))
                .collect(Collectors.groupingBy(
                        AiModelHealthLogRow::getModelConfigId,
                        Collectors.toMap(
                                AiModelHealthLogRow::getHealthBucket,
                                Function.identity(),
                                (left, right) -> left)));
    }

    private String resolveHealthStatus(AiModelHealthLogRow latestCall) {
        String callStatus = resolveCallStatus(latestCall);
        if ("SUCCESS".equals(callStatus)) {
            return "HEALTHY";
        }
        if ("FAILED".equals(callStatus)) {
            return "DEGRADED";
        }
        return "UNKNOWN";
    }

    private String resolveCallStatus(AiModelHealthLogRow logEntry) {
        if (logEntry == null) {
            return "UNKNOWN";
        }
        Integer success = logEntry.getSuccess() != null ? logEntry.getSuccess() : logEntry.getStatus();
        if (Integer.valueOf(1).equals(success)) {
            return "SUCCESS";
        }
        if (Integer.valueOf(0).equals(success)) {
            return "FAILED";
        }
        return "UNKNOWN";
    }

    private java.time.LocalDateTime createdAt(AiModelHealthLogRow logEntry) {
        return logEntry == null ? null : logEntry.getCreatedAt();
    }

    private String encryptApiKey(String apiKey) {
        try {
            return apiKeyEncryptor.encrypt(apiKey);
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "模型密钥加密配置不可用，请先检查运行环境配置");
        }
    }

    private String normalizeApiBaseUrl(String apiBaseUrl) {
        if (!StringUtils.hasText(apiBaseUrl)) {
            return null;
        }
        try {
            return endpointPolicy.validateAndNormalizeBaseUrl(apiBaseUrl);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ex.getMessage());
        }
    }

    private void encryptPlainApiKeyBeforeSave(AiModelConfig entity) {
        if (entity != null && StringUtils.hasText(entity.getApiKey())
                && !apiKeyEncryptor.isEncrypted(entity.getApiKey())) {
            entity.setApiKey(encryptApiKey(entity.getApiKey()));
        }
    }

    private String maskStoredApiKey(String storedApiKey) {
        if (!StringUtils.hasText(storedApiKey)) {
            return "";
        }
        try {
            return SensitiveTextMasker.maskSecret(apiKeyEncryptor.decryptIfNeeded(storedApiKey));
        } catch (IllegalStateException ex) {
            log.warn("AI_MODEL_API_KEY_DECRYPT_UNAVAILABLE encrypted={} reason={}",
                    apiKeyEncryptor.isEncrypted(storedApiKey), ex.getMessage());
            return "******";
        }
    }

    @lombok.Data
    public static class ModelStatusDTO {
        private Integer status;
        private Integer enabled;
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }

    @lombok.Data
    public static class AdminOperationConfirmDTO {
        private Boolean confirm;
        private Boolean dryRun;
        private String reason;
        private String idempotencyKey;
    }
}
