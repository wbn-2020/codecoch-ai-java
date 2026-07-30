package com.codecoachai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.enums.PromptVersionStatus;
import com.codecoachai.ai.mapper.PromptTemplateMapper;
import com.codecoachai.ai.mapper.PromptTemplateVersionMapper;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.ai.service.PromptSceneContracts;
import com.codecoachai.ai.service.PromptSceneContracts.PromptSceneContract;
import com.codecoachai.common.core.constant.CommonConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
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
        PromptSource source = compatibleActivePromptSource(scene);
        String content = source == null ? fallbackContent : source.content();
        String enforcedSuffix = PromptSceneContracts.find(scene)
                .map(PromptSceneContract::enforcedPromptSuffix)
                .orElse("");
        String baseTemplate = firstText(prefix, "")
                + firstText(content, fallbackContent)
                + firstText(suffix, "");
        String finalTemplate = appendPromptSuffix(baseTemplate, enforcedSuffix);
        Map<String, String> safeVariables = safeVariables(variables);
        String rendered = PromptTemplateVariableValidator.render(finalTemplate,
                source == null ? null : source.variablesJson(), safeVariables);
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

    void verifyActivePromptContracts(boolean failFast) {
        for (PromptSceneContract contract : PromptSceneContracts.all()) {
            PromptSource source = findActivePromptSource(contract.scene());
            if (source == null) {
                handleContractViolation(
                        failFast,
                        "Managed prompt source is missing: scene=" + contract.scene()
                                + ", expectedVersionPrefix=" + contract.managedVersionPrefix());
                continue;
            }
            PromptSceneContractValidator.Compatibility compatibility =
                    compatibility(contract.scene(), source);
            if (compatibility.compatible()) {
                continue;
            }
            handleContractViolation(
                    failFast,
                    "Active prompt version violates scene contract: scene="
                            + contract.scene() + ", versionCode=" + source.versionCode()
                            + ", reason=" + compatibility.reason());
        }
    }

    private void handleContractViolation(boolean failFast, String message) {
        if (failFast) {
            throw new IllegalStateException(message);
        }
        log.error(message);
    }

    private PromptSource compatibleActivePromptSource(String scene) {
        PromptSource source = findActivePromptSource(scene);
        if (source == null) {
            return null;
        }
        PromptSceneContractValidator.Compatibility compatibility = compatibility(scene, source);
        if (compatibility.compatible()) {
            return source;
        }
        log.error("Ignore incompatible active prompt and use builtin fallback: scene={}, versionCode={}, reason={}",
                scene, source.versionCode(), compatibility.reason());
        return null;
    }

    private PromptSceneContractValidator.Compatibility compatibility(String scene, PromptSource source) {
        return PromptSceneContractValidator.evaluate(
                scene,
                source.versionCode(),
                source.content(),
                source.variablesJson());
    }

    private PromptSource findActivePromptSource(String scene) {
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
        PromptTemplateVersion version =
                promptTemplateVersionMapper.selectActiveVersionOwnedByEnabledTemplate(scene);
        if (isActive(version)) {
            return toSource(null, version);
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
                version.getVariablesJson(), version.getModelParamsJson());
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

    private String appendPromptSuffix(String prompt, String enforcedSuffix) {
        if (!StringUtils.hasText(enforcedSuffix)) {
            return prompt;
        }
        if (!StringUtils.hasText(prompt)) {
            return enforcedSuffix.trim();
        }
        return prompt.endsWith("\n")
                ? prompt + enforcedSuffix.trim()
                : prompt + "\n" + enforcedSuffix.trim();
    }

    private record PromptSource(Long templateId, Long versionId, String versionCode, String content,
                                String variablesJson, String modelParamsJson) {
    }
}
