package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 用线上 agent_run.raw_output_text 的真实输出（runId=9704435，HEX 导出）回归 parser。
 *  该输出曾因 focusSkills 为字符串数组导致 MismatchedInput → 整份计划 PARSE_FAILED 降级。 */
class AgentOutputParserRealOutputTest {

    @Test
    void parsesRealDeepSeekOutputWithStringFocusSkills() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("C:/vibe-coding/codecoachai/.tmp/raw-real.json"));
        String raw = new String(bytes, StandardCharsets.UTF_8).trim();
        DailyPlanResult result = new ObjectMapper().readValue(raw, DailyPlanResult.class);
        assertNotNull(result.getSummary());
        assertEquals(3, result.getTasks().size());
        assertEquals("skill-gap-9702251", result.getTasks().get(0).getCandidateId());
        assertEquals(Integer.valueOf(25), result.getTasks().get(0).getEstimatedMinutes());
        assertEquals(3, result.getFocusSkills().size());
        assertEquals("系统设计", result.getFocusSkills().get(0).getName());
    }

    @Test
    void stillParsesObjectFormFocusSkills() throws Exception {
        String raw = "{\"summary\":\"s\",\"focusSkills\":[{\"code\":\"system-design\",\"name\":\"系统设计\"}],"
                + "\"tasks\":[{\"candidateId\":\"c1\",\"type\":\"SKILL_REVIEW\",\"title\":\"t\",\"reason\":\"r 中文\","
                + "\"estimatedMinutes\":25,\"priority\":\"HIGH\"}]}";
        DailyPlanResult result = new ObjectMapper().readValue(raw, DailyPlanResult.class);
        assertEquals("system-design", result.getFocusSkills().get(0).getCode());
        assertEquals("系统设计", result.getFocusSkills().get(0).getName());
    }
}
