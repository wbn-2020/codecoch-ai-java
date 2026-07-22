package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Narrative;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class AiCampaignPulseNarrativeEnhancer implements CampaignPulseNarrativeEnhancer {

    private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern FORBIDDEN_OUTCOME_CLAIM = Pattern.compile(
            "录用概率|面试通过率|招聘方意图|成功率|一定录用|必然通过");
    private static final int MAX_SUMMARY_LENGTH = 800;

    private final PromptRenderService promptRenderService;
    private final AiCallLogService aiCallLogService;
    private final ObjectMapper objectMapper;
    private final RuleOnlyCampaignPulseNarrativeEnhancer ruleEnhancer;

    @Override
    public Narrative enhance(Long userId, Long campaignId, Computation computation) {
        Narrative rule = ruleEnhancer.enhance(userId, campaignId, computation);
        try {
            PromptRenderResult prompt = promptRenderService.render(
                    CampaignPulseAiScene.NAME,
                    defaultPrompt(),
                    variables(computation));
            AiCallContext context = new AiCallContext();
            context.setScene(CampaignPulseAiScene.NAME);
            context.setUserId(userId);
            context.setBusinessId(String.valueOf(campaignId));
            context.setPrompt(prompt.getRenderedPrompt());
            context.setPromptTemplateId(prompt.getPromptTemplateId());
            context.setPromptTemplateVersionId(prompt.getPromptTemplateVersionId());
            context.setPromptVersion(prompt.getPromptVersion());
            context.setInputVariablesJson(null);
            context.setModelParamsJson(prompt.getModelParamsJson());
            context.setPromptHash(prompt.getPromptHash());
            context.setResponseFormat("JSON");
            context.setCheckQuota(true);
            RouteResult route = aiCallLogService.callAndLog(context);
            Narrative result = parse(route.getContent(), computation, rule);
            result.setAiCallLogId(route.getAiCallLogId());
            return result;
        } catch (RuntimeException ex) {
            log.warn("Campaign pulse AI scene failed; using rule fallback campaignId={}",
                    campaignId, ex);
            return fallback(rule, "AI 周期简报不可用，已使用规则结果。");
        }
    }

    private Narrative parse(String raw, Computation computation, Narrative rule) {
        JsonNode json = readObject(raw);
        String summary = text(json.path("summary"), MAX_SUMMARY_LENGTH);
        if (!StringUtils.hasText(summary) || !CHINESE.matcher(summary).find()
                || FORBIDDEN_OUTCOME_CLAIM.matcher(summary).find()) {
            return fallback(rule, "AI 摘要为空或不符合中文约束，已使用规则结果。");
        }
        Set<String> allowed = computation.getActionSeeds().stream()
                .map(ActionItem::getSemanticKey)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> selections = textList(json.path("actionSelections"), allowed.size(), 255);
        if (!allowed.containsAll(selections) || new LinkedHashSet<>(selections).size() != selections.size()) {
            return fallback(rule, "AI 选择了规则白名单之外的行动，已使用规则结果。");
        }
        int selectedMinutes = computation.getActionSeeds().stream()
                .filter(item -> selections.contains(item.getSemanticKey()))
                .map(ActionItem::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int allowedMinutes = computation.getActionSeeds().stream()
                .map(ActionItem::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int weeklyBudgetMinutes = metric(
                computation, "weeklyBudgetMinutes", allowedMinutes);
        if (selectedMinutes > Math.min(allowedMinutes, weeklyBudgetMinutes)) {
            return fallback(rule, "AI 行动时间超过规则预算，已使用规则结果。");
        }
        Narrative result = new Narrative();
        result.setSummary(summary);
        result.setFacts(new ArrayList<>(rule.getFacts()));
        result.setChanges(new ArrayList<>(rule.getChanges()));
        result.setDriftReasons(new ArrayList<>(rule.getDriftReasons()));
        result.setFocusAreas(computation.getActionSeeds().stream()
                .filter(item -> selections.contains(item.getSemanticKey()))
                .map(ActionItem::getTitle)
                .filter(StringUtils::hasText)
                .limit(8)
                .toList());
        result.setActionSelections(selections);
        result.setLimits(mergeLimits(computation.getLimits(),
                textList(json.path("limits"), 20, 500)));
        result.setConfidenceLevel(capConfidence(
                text(json.path("confidenceLevel"), 16), computation.getConfidenceLevel()));
        result.setFallback(false);
        return result;
    }

    private Map<String, String> variables(Computation computation) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("factsJson", json(computation.getFacts()));
        result.put("metricsJson", json(computation.getMetrics()));
        result.put("changesJson", json(computation.getChanges()));
        result.put("driftSignalsJson", json(computation.getDriftSignals()));
        result.put("limitsJson", json(computation.getLimits()));
        result.put("allowedActionsJson", json(computation.getActionSeeds().stream()
                .map(item -> Map.of(
                        "semanticKey", item.getSemanticKey(),
                        "title", Objects.toString(item.getTitle(), ""),
                        "priority", Objects.toString(item.getPriority(), "LOW"),
                        "estimatedMinutes", item.getEstimatedMinutes() == null
                                ? 30 : item.getEstimatedMinutes(),
                        "confidenceLevel", Objects.toString(item.getConfidenceLevel(), "LOW")))
                .toList()));
        result.put("confidenceCeiling", computation.getConfidenceLevel());
        return result;
    }

    private String defaultPrompt() {
        return """
                你是求职周期简报助手。只能基于下面的规则事实生成 JSON，不得补充外部事实。

                事实：{{factsJson}}
                指标：{{metricsJson}}
                变化：{{changesJson}}
                漂移信号：{{driftSignalsJson}}
                已知限制：{{limitsJson}}
                规则白名单行动：{{allowedActionsJson}}
                置信度上限：{{confidenceCeiling}}

                硬约束：
                1. 所有自然语言必须为中文，可保留岗位名、技术名和规范枚举。
                2. 不预测录用概率、面试通过率、招聘方意图或机会成功率。
                3. 低样本只能描述弱信号，不得输出因果结论。
                4. actionSelections 只能返回白名单中的 semanticKey，不得新增行动。
                5. 不得提高规则优先级，不得扩大行动总预计时间。
                6. limits 必须保留来源缺失、截断、过期和低样本限制。
                7. 只返回 JSON 对象：
                {
                  "summary": "中文摘要",
                  "facts": ["中文事实"],
                  "changes": ["中文变化"],
                  "driftReasons": ["中文偏离原因"],
                  "focusAreas": ["中文关注点"],
                  "actionSelections": ["白名单 semanticKey"],
                  "limits": ["中文限制"],
                  "confidenceLevel": "LOW 或 MEDIUM"
                }
                """;
    }

    private JsonNode readObject(String raw) {
        try {
            String text = raw == null ? "" : raw.trim();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("AI 周期简报不是 JSON 对象");
            }
            JsonNode result = objectMapper.readTree(text.substring(start, end + 1));
            if (result == null || !result.isObject()) {
                throw new IllegalArgumentException("AI 周期简报不是 JSON 对象");
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI 周期简报解析失败", ex);
        }
    }

    private Narrative fallback(Narrative rule, String reason) {
        rule.setFallback(true);
        rule.setFallbackReason(reason);
        rule.setConfidenceLevel("LOW");
        return rule;
    }

    private List<String> textList(JsonNode node, int maxItems, int maxLength) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (result.size() >= maxItems) {
                break;
            }
            String value = text(item, maxLength);
            if (StringUtils.hasText(value) && CHINESE_OR_CODE(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private boolean CHINESE_OR_CODE(String value) {
        return CHINESE.matcher(value).find()
                || value.matches("[A-Z0-9_:\\-]+");
    }

    private String text(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private List<String> mergeLimits(List<String> required, List<String> generated) {
        Set<String> result = new LinkedHashSet<>();
        if (required != null) {
            result.addAll(required);
        }
        if (generated != null) {
            result.addAll(generated);
        }
        return new ArrayList<>(result);
    }

    private String capConfidence(String generated, String ceiling) {
        if ("LOW".equalsIgnoreCase(ceiling)) {
            return "LOW";
        }
        return "MEDIUM".equalsIgnoreCase(generated) ? "MEDIUM" : "LOW";
    }

    private int metric(Computation computation, String key, int fallback) {
        Object value = computation.getMetrics().get(key);
        return value instanceof Number number && number.intValue() >= 0
                ? number.intValue() : fallback;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
