package com.codecoachai.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.ai.domain.dto.AiModelConfigSaveDTO;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminAiModelController {

    private final AiModelConfigMapper mapper;

    @GetMapping("/admin/ai/models")
    public Result<List<AiModelConfig>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String provider,
                                            @RequestParam(required = false) Integer enabled,
                                            @RequestParam(required = false) Integer status) {
        SecurityAssert.requireAdmin();
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
        SecurityAssert.requireAdmin();
        return Result.success(maskApiKey(get(id)));
    }

    @PostMapping("/admin/ai/models")
    public Result<AiModelConfig> create(@RequestBody AiModelConfigSaveDTO dto) {
        SecurityAssert.requireAdmin();
        AiModelConfig entity = new AiModelConfig();
        apply(entity, dto);
        if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
            clearDefault(entity.getProvider(), null);
        }
        mapper.insert(entity);
        return Result.success(maskApiKey(entity));
    }

    @PutMapping("/admin/ai/models/{id}")
    public Result<AiModelConfig> update(@PathVariable Long id, @RequestBody AiModelConfigSaveDTO dto) {
        SecurityAssert.requireAdmin();
        AiModelConfig entity = get(id);
        apply(entity, dto);
        if (Integer.valueOf(1).equals(entity.getDefaultModel())) {
            clearDefault(entity.getProvider(), id);
        }
        mapper.updateById(entity);
        return Result.success(maskApiKey(entity));
    }

    @PostMapping("/admin/ai/models/{id}/set-default")
    public Result<AiModelConfig> setDefault(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        AiModelConfig entity = get(id);
        clearDefault(entity.getProvider(), id);
        entity.setDefaultModel(1);
        entity.setEnabled(1);
        mapper.updateById(entity);
        return Result.success(maskApiKey(entity));
    }

    @PutMapping("/admin/ai/models/{id}/default")
    public Result<AiModelConfig> setDefaultCompat(@PathVariable Long id) {
        return setDefault(id);
    }

    @PutMapping("/admin/ai/models/{id}/status")
    public Result<AiModelConfig> updateStatus(@PathVariable Long id, @RequestBody ModelStatusDTO dto) {
        SecurityAssert.requireAdmin();
        AiModelConfig entity = get(id);
        Integer enabled = dto == null ? null : (dto.getEnabled() != null ? dto.getEnabled() : dto.getStatus());
        entity.setEnabled(enabled == null ? 1 : enabled);
        mapper.updateById(entity);
        return Result.success(maskApiKey(entity));
    }

    @DeleteMapping("/admin/ai/models/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
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
            entity.setApiKey(dto.getApiKey().trim());
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
            entity.setApiKeyMasked(mask(entity.getApiKey()));
        }
        return entity;
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String value = apiKey.trim();
        if (value.length() <= 8) {
            return "******";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    @lombok.Data
    public static class ModelStatusDTO {
        private Integer status;
        private Integer enabled;
    }
}
