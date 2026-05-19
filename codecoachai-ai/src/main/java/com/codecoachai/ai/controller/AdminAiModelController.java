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
    public Result<List<AiModelConfig>> list(@RequestParam(required = false) String provider,
                                            @RequestParam(required = false) Integer enabled) {
        SecurityAssert.requireAdmin();
        return Result.success(mapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .eq(StringUtils.hasText(provider), AiModelConfig::getProvider, provider)
                .eq(enabled != null, AiModelConfig::getEnabled, enabled)
                .orderByDesc(AiModelConfig::getDefaultModel)
                .orderByAsc(AiModelConfig::getSortOrder)
                .orderByDesc(AiModelConfig::getUpdatedAt)));
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
        return Result.success(entity);
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
        return Result.success(entity);
    }

    @PostMapping("/admin/ai/models/{id}/set-default")
    public Result<AiModelConfig> setDefault(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        AiModelConfig entity = get(id);
        clearDefault(entity.getProvider(), id);
        entity.setDefaultModel(1);
        entity.setEnabled(1);
        mapper.updateById(entity);
        return Result.success(entity);
    }

    @DeleteMapping("/admin/ai/models/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        mapper.deleteById(id);
        return Result.success();
    }

    private void apply(AiModelConfig entity, AiModelConfigSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getProvider()) || !StringUtils.hasText(dto.getModelCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "provider and modelCode are required");
        }
        entity.setProvider(dto.getProvider().trim());
        entity.setModelCode(dto.getModelCode().trim());
        entity.setModelName(StringUtils.hasText(dto.getModelName()) ? dto.getModelName().trim() : entity.getModelCode());
        entity.setCapabilityTags(dto.getCapabilityTags());
        entity.setDefaultModel(dto.getDefaultModel() == null ? 0 : dto.getDefaultModel());
        entity.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        entity.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
        entity.setRemark(dto.getRemark());
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
}
