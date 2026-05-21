package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.service.PromptRenderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentPromptBuilderImpl implements AgentPromptBuilder {

    public static final String PROMPT_TYPE = "JOB_COACH_DAILY_PLAN";
    private static final String PROMPT_VERSION = "v4.1-static-json";

    private final ObjectMapper objectMapper;

    @Override
    public PromptRenderResult buildDailyPlanPrompt(JobCoachAgentContext context, List<CandidateTask> candidates,
                                                   int taskCount, int maxTotalMinutes) {
        String contextJson = toJson(context);
        String candidatesJson = toJson(candidates);
        String promptText = """
                You are CodeCoachAI JobCoachAgent.
                Generate today's job-preparation plan from the provided user context and candidate tasks.

                Rules:
                - Select only from candidate tasks. Minor title/description edits are allowed.
                - candidateId is required for every task and must match a provided candidate task.
                - Return exactly %d tasks, minimum 1 and maximum 5.
                - Total estimatedMinutes must not exceed %d.
                - Return JSON only. Do not return Markdown or explanatory text.

                User context:
                %s

                Candidate tasks:
                %s

                JSON schema:
                {
                  "summary": "short plan summary",
                  "focusSkills": [
                    { "code": "skill.code", "name": "skill name" }
                  ],
                  "tasks": [
                    {
                      "candidateId": "candidate task id",
                      "type": "QUESTION_PRACTICE|RESUME_OPTIMIZE|INTERVIEW|SKILL_REVIEW|KNOWLEDGE_REVIEW",
                      "title": "task title",
                      "description": "task description",
                      "reason": "why this task matters",
                      "estimatedMinutes": 30,
                      "priority": "HIGH|MEDIUM|LOW",
                      "relatedSkillCode": "skill.code",
                      "relatedSkillName": "skill name",
                      "relatedBizType": "business type",
                      "relatedBizId": 1,
                      "actionUrl": "/path"
                    }
                  ]
                }
                """.formatted(taskCount, maxTotalMinutes, contextJson, candidatesJson);

        return PromptRenderResult.builder()
                .scene(PROMPT_TYPE)
                .promptVersion(PROMPT_VERSION)
                .renderedPrompt(promptText)
                .inputVariablesJson(toJson(java.util.Map.of(
                        "context", context,
                        "candidates", candidates,
                        "taskCount", taskCount,
                        "maxTotalMinutes", maxTotalMinutes)))
                .modelParamsJson("{\"temperature\":0.2,\"responseFormat\":\"json_object\"}")
                .promptHash(hash(promptText))
                .fallbackUsed(false)
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return String.valueOf(text.hashCode());
        }
    }
}
