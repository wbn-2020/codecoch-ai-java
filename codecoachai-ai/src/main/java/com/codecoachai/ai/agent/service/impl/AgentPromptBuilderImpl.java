package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentPromptBuilderImpl implements AgentPromptBuilder {

    public static final String PROMPT_TYPE = "JOB_COACH_DAILY_PLAN";
    private static final String PROMPT_VERSION = "v4.2-zh-evidence-json";
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
                - 必须返回 %d 个任务，最少 1 个，最多 5 个。
                - 全部任务 estimatedMinutes 总和不得超过 %d。
                - summary、title、description、reason 必须是自然中文，可保留 Redis、Spring Cloud、Kafka、MySQL 等技术名。
                - reason 必须说明它和目标岗位、岗位技能、能力短板或最近训练记录的关系。
                - 当前上下文没有 SUCCESS 匹配报告时，不要写“匹配报告显示/匹配报告指出/报告证明”；只能表述为“目标岗位或岗位要求”。
                - 不要输出 fallback、aiCallLogId、DTO、REST API、后端接口、具体模型名、AGENT_、candidate task 等内部词。
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
        if (Boolean.TRUE.equals(result.getFallbackUsed())) {
            result.setPromptVersion(PROMPT_VERSION);
        }
        if (!StringUtils.hasText(result.getModelParamsJson())) {
            result.setModelParamsJson(DEFAULT_MODEL_PARAMS_JSON);
        }
        return result;
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
