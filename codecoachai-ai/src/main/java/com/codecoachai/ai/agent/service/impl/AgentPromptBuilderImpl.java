package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.codecoachai.ai.service.PromptSceneContracts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentPromptBuilderImpl implements AgentPromptBuilder {

    public static final String PROMPT_TYPE = PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE;
    private static final String PROMPT_VERSION = PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION;
    private static final String DEFAULT_MODEL_PARAMS_JSON = "{\"temperature\":0.2,\"responseFormat\":\"json_object\"}";

    private final ObjectMapper objectMapper;
    private final PromptRenderService promptRenderService;

    @Override
    public PromptRenderResult buildDailyPlanPrompt(JobCoachAgentContext context, List<CandidateTask> candidates,
                                                   int taskCount, int maxTotalMinutes) {
        String contextJson = toJson(context);
        String candidatesJson = toJson(candidates);
        String promptText = """
                你是 CodeCoachAI 的求职训练 Agent。请根据用户上下文和候选任务，生成今天的中文求职训练计划。

                输出规则：
                - 只能从 candidate tasks 中选择任务；可以轻微润色标题、描述和原因，但不能新编不存在的任务。
                - 每个任务必须带 candidateId，且必须匹配候选任务中的 candidateId。
                - 返回 1 至 %d 个任务；候选不足或时间不足时可以少选，不得重复选择同一个候选。
                - 全部任务 estimatedMinutes 总和不得超过 %d。
                - summary、title、description、reason 必须是自然中文，可保留 Redis、Spring Cloud、Kafka、MySQL 等技术名。
                - reason 必须说明它和目标岗位、岗位技能、能力短板或最近训练记录的关系。
                - 用户上下文中的 skillGaps 是当前目标岗位下的能力或证据短板；只有候选任务的 relatedBizType 为 SKILL_GAP_ITEM 时才能据此选题。
                - 选择 SKILL_GAP_ITEM 候选任务时，reason 必须基于对应短板说明训练价值，不能虚构未提供的缺口、证据或经历。
                - 当前上下文没有 SUCCESS 匹配报告时，不要写“匹配报告显示/匹配报告指出/报告证明”；只能表述为“目标岗位或岗位要求”。
                - 不要在用户文案中输出 fallback、aiCallLogId、AGENT_、candidateId、candidate task、调用日志等内部运行信息。
                - 只输出 JSON，不要 Markdown、代码块或解释文字。

                用户上下文：
                %s

                候选任务：
                %s

                JSON 结构：
                {
                  "summary": "今天计划摘要",
                  "focusSkills": [
                    { "code": "skill.code", "name": "技能名称" }
                  ],
                  "tasks": [
                    {
                      "candidateId": "candidate task id",
                      "type": "QUESTION_PRACTICE|RESUME_OPTIMIZE|INTERVIEW|SKILL_REVIEW|KNOWLEDGE_REVIEW|APPLICATION_FOLLOW_UP",
                      "title": "任务标题",
                      "description": "任务说明",
                      "reason": "为什么今天要做",
                      "estimatedMinutes": 30,
                      "priority": "HIGH|MEDIUM|LOW",
                      "relatedSkillCode": "skill.code",
                      "relatedSkillName": "技能名称",
                      "relatedBizType": "business type",
                      "relatedBizId": 1,
                      "actionUrl": "/path"
                    }
                  ]
                }
                """.formatted(taskCount, maxTotalMinutes, contextJson, candidatesJson);

        PromptRenderResult result = promptRenderService.render(PROMPT_TYPE, promptText,
                promptVariables(contextJson, candidatesJson, taskCount, maxTotalMinutes));
        // Managed templates may predate the Java contract; enforce current limits after rendering too.
        String contract = """

                本次执行约束（优先于模板中冲突的数量和字段说明）：
                - tasks 数量为 1 至 %d，总 estimatedMinutes 不超过 %d；单项为 5 至 180 分钟。
                - candidateId 必须逐字复制候选 ID，同一 ID 只能选择一次；不得按标题编造或替换 ID。
                - type 必须复制该候选的大写 type；priority 只能是 HIGH、MEDIUM、LOW 中的一个值。
                - relatedSkillCode、relatedSkillName、relatedBizType、relatedBizId、actionUrl 复制候选字段，保留 null。
                - summary、title、description、reason 使用中文句子，技术名可以保留；候选中的英文文案请转成中文。
                - DTO、REST API、DeepSeek 可作为真实学习主题，不得泄露本次模型调用的内部运行信息。
                - 只输出符合上述 JSON 结构的对象；示例中的竖线表示可选值，不是需要原样输出的枚举值。
                """.formatted(Math.min(5, taskCount), maxTotalMinutes);
        result.setRenderedPrompt(result.getRenderedPrompt() + contract);
        result.setPromptHash(sha256(result.getRenderedPrompt()));
        if (Boolean.TRUE.equals(result.getFallbackUsed())) {
            result.setPromptVersion(PROMPT_VERSION);
        }
        if (!StringUtils.hasText(result.getModelParamsJson())) {
            result.setModelParamsJson(DEFAULT_MODEL_PARAMS_JSON);
        }
        return result;
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private Map<String, String> promptVariables(String contextJson, String candidatesJson,
                                                int taskCount, int maxTotalMinutes) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("contextJson", contextJson);
        variables.put("candidatesJson", candidatesJson);
        variables.put("context", contextJson);
        variables.put("candidates", candidatesJson);
        variables.put("taskCount", String.valueOf(taskCount));
        variables.put("maxTotalMinutes", String.valueOf(maxTotalMinutes));
        return variables;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
