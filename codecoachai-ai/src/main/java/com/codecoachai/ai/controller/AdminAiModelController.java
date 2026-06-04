package com.codecoachai.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.ai.domain.dto.AiModelConfigSaveDTO;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.codecoachai.ai.security.SensitiveTextMasker;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final AiModelConfigMapper mapper;
    private final AesGcmTextEncryptor apiKeyEncryptor;
    private final AdminPermissionGuard permissionGuard;

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
        rows.forEach(this::maskApiKey);
        return Result.success(rows);
    }

    @GetMapping("/admin/ai/models/{id}")
    public Result<AiModelConfig> detail(@PathVariable Long id) {
        permissionGuard.require(PERM_MODEL_LIST);
        return Result.success(maskApiKey(get(id)));
    }

    @PostMapping("/admin/ai/models")
    @OperationLog(module = "ai", action = "CREATE_AI_MODEL", description = "Create AI model config", logArgs = false, logResponse = false)
    public Result<AiModelConfig> create(@RequestBody AiModelConfigSaveDTO dto) {
        permissionGuard.require(PERM_MODEL_WRITE);
        AiModelConfig entity = new AiModelConfig();
        apply(entity, dto);
        if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
            clearDefault(entity.getProvider(), null);
        }
        encryptPlainApiKeyBeforeSave(entity);
        mapper.insert(entity);
        return Result.success(maskApiKey(entity));
    }

    @PutMapping("/admin/ai/models/{id}")
    @OperationLog(module = "ai", action = "UPDATE_AI_MODEL", description = "Update AI model config", logArgs = false, logResponse = false)
    public Result<AiModelConfig> update(@PathVariable Long id, @RequestBody AiModelConfigSaveDTO dto) {
        permissionGuard.require(PERM_MODEL_WRITE);
        AiModelConfig entity = get(id);
        apply(entity, dto);
        if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
            clearDefault(entity.getProvider(), id);
        }
        encryptPlainApiKeyBeforeSave(entity);
        mapper.updateById(entity);
        return Result.success(maskApiKey(entity));
    }

    @PostMapping("/admin/ai/models/{id}/set-default")
    @OperationLog(module = "ai", action = "SET_DEFAULT_AI_MODEL", description = "Set default AI model", logResponse = false)
    public Result<AiModelConfig> setDefault(@PathVariable Long id) {
        permissionGuard.require(PERM_MODEL_PUBLISH);
        AiModelConfig entity = get(id);
        clearDefault(entity.getProvider(), id);
        entity.setDefaultModel(1);
        entity.setEnabled(1);
        encryptPlainApiKeyBeforeSave(entity);
        mapper.updateById(entity);
        return Result.success(maskApiKey(entity));
    }

    @PutMapping("/admin/ai/models/{id}/default")
    @OperationLog(module = "ai", action = "SET_DEFAULT_AI_MODEL_COMPAT", description = "Set default AI model via compatibility endpoint", logResponse = false)
    public Result<AiModelConfig> setDefaultCompat(@PathVariable Long id) {
        return setDefault(id);
    }

    @PutMapping("/admin/ai/models/{id}/status")
    @OperationLog(module = "ai", action = "UPDATE_AI_MODEL_STATUS", description = "Update AI model status", logResponse = false)
    public Result<AiModelConfig> updateStatus(@PathVariable Long id, @RequestBody ModelStatusDTO dto) {
        permissionGuard.require(PERM_MODEL_PUBLISH);
        AiModelConfig entity = get(id);
        Integer enabled = dto == null ? null : (dto.getEnabled() != null ? dto.getEnabled() : dto.getStatus());
        entity.setEnabled(enabled == null ? 1 : enabled);
        encryptPlainApiKeyBeforeSave(entity);
        mapper.updateById(entity);
        return Result.success(maskApiKey(entity));
    }

    @DeleteMapping("/admin/ai/models/{id}")
    @OperationLog(module = "ai", action = "DELETE_AI_MODEL", description = "Delete AI model config", logResponse = false)
    public Result<Void> delete(@PathVariable Long id) {
        permissionGuard.require(PERM_MODEL_WRITE);
        mapper.deleteById(id);
        return Result.success();
    }

    private void apply(AiModelConfig entity, AiModelConfigSaveDTO dto) {
        String modelCode = dto == null ? null : (StringUtils.hasText(dto.getModelCode()) ? dto.getModelCode() : dto.getModelName());
        if (dto == null || !StringUtils.hasText(dto.getProvider()) || !StringUtils.hasText(modelCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "provider and modelCode are required");
        }
        entity.setProvider(dto.getProvider().trim());
        entity.setModelCode(modelCode.trim());
        entity.setModelName(StringUtils.hasText(dto.getDisplayName()) ? dto.getDisplayName().trim()
                : StringUtils.hasText(dto.getModelName()) ? dto.getModelName().trim() : entity.getModelCode());
        entity.setCapabilityTags(dto.getCapabilityTags());
        entity.setApiBaseUrl(StringUtils.hasText(dto.getApiBaseUrl()) ? dto.getApiBaseUrl().trim() : null);
        if (StringUtils.hasText(dto.getApiKey())) {
            entity.setApiKey(encryptApiKey(dto.getApiKey()));
        }
        entity.setTemperature(dto.getTemperature());
        entity.setMaxTokens(dto.getMaxTokens());
        entity.setDefaultModel(dto.getDefaultModel() != null ? dto.getDefaultModel()
                : dto.getIsDefault() == null ? 0 : dto.getIsDefault());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled()
                : dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
        entity.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : dto.getDescription());
    }

    private AiModelConfig get(Long id) {
        AiModelConfig entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "AI model config not found");
        }
        return entity;
    }

    private void clearDefault(String provider, Long excludeId) {
        mapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getProvider, provider)
                .ne(excludeId != null, AiModelConfig::getId, excludeId)
                .set(AiModelConfig::getDefaultModel, 0));
    }

    private AiModelConfig maskApiKey(AiModelConfig entity) {
        if (entity != null) {
            entity.setApiKeyMasked(maskStoredApiKey(entity.getApiKey()));
            entity.setApiKey(null);
        }
        return entity;
    }

    private String encryptApiKey(String apiKey) {
        try {
            return apiKeyEncryptor.encrypt(apiKey);
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "AI model apiKey encryption key is not configured or invalid");
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
    }
}
