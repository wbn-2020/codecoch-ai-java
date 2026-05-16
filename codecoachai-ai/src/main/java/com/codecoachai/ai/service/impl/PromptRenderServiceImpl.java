package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.enums.PromptVersionStatus;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PromptRenderServiceImpl implements PromptRenderService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptTemplateVersionMapper promptTemplateVersionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables) {
        return render(scene, fallbackContent, variables, null, null);
    }

    @Override
    public PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables,
                                     String prefix, String suffix) {
        PromptSource source = activePromptSource(scene);
        String content = source == null ? fallbackContent : source.content();
        String finalTemplate = firstText(prefix, "") + firstText(content, fallbackContent) + firstText(suffix, "");
        Map<String, String> safeVariables = safeVariables(variables);
        String rendered = renderTemplate(finalTemplate, safeVariables);
        return PromptRenderResult.builder()
                .scene(scene)
                .renderedPrompt(rendered)
                .promptTemplateId(source == null ? null : source.templateId())
                .promptTemplateVersionId(source == null ? null : source.versionId())
                .promptVersion(source == null ? "BUILTIN" : source.versionCode())
                .inputVariablesJson(toJson(safeVariables))
                .modelParamsJson(source == null ? null : source.modelParamsJson())
                .promptHash(sha256(rendered))
                .fallbackUsed(source == null)
                .build();
    }

    private PromptSource activePromptSource(String scene) {
        PromptTemplate template = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getScene, scene)
                .eq(PromptTemplate::getStatus, CommonConstants.YES)
                .and(wrapper -> wrapper.eq(PromptTemplate::getEnabled, CommonConstants.YES)
                        .or()
                        .isNull(PromptTemplate::getEnabled))
                .orderByDesc(PromptTemplate::getUpdatedAt)
                .last("limit 1"));
        if (template != null && template.getActiveVersionId() != null) {
            PromptTemplateVersion version = promptTemplateVersionMapper.selectById(template.getActiveVersionId());
            if (isActive(version)) {
                return toSource(template, version);
            }
        }
        PromptTemplateVersion version = promptTemplateVersionMapper.selectOne(new LambdaQueryWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getScene, scene)
                .eq(PromptTemplateVersion::getStatus, PromptVersionStatus.ACTIVE.name())
                .eq(PromptTemplateVersion::getIsActive, CommonConstants.YES)
                .orderByDesc(PromptTemplateVersion::getActivatedAt)
                .orderByDesc(PromptTemplateVersion::getUpdatedAt)
                .last("limit 1"));
        if (isActive(version)) {
            PromptTemplate owner = version.getTemplateId() == null ? null : promptTemplateMapper.selectById(version.getTemplateId());
            return toSource(owner, version);
        }
        return null;
    }

    private boolean isActive(PromptTemplateVersion version) {
        return version != null
                && PromptVersionStatus.ACTIVE.name().equals(version.getStatus())
                && CommonConstants.YES.equals(version.getIsActive());
    }

    private PromptSource toSource(PromptTemplate template, PromptTemplateVersion version) {
        if (!StringUtils.hasText(version.getContent())) {
            return null;
        }
        Long templateId = template == null ? version.getTemplateId() : template.getId();
        return new PromptSource(templateId, version.getId(), version.getVersionCode(), version.getContent(),
                version.getModelParamsJson());
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        String prompt = firstText(template, "");
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return prompt;
    }

    private Map<String, String> safeVariables(Map<String, String> variables) {
        Map<String, String> values = new LinkedHashMap<>();
        if (variables == null) {
            return values;
        }
        variables.forEach((key, value) -> values.put(key, value == null ? "" : value));
        return values;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(firstText(value, "").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record PromptSource(Long templateId, Long versionId, String versionCode, String content,
                                String modelParamsJson) {
    }
}
