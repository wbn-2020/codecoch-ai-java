package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.service.AgentOutputParser;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentOutputParserImpl implements AgentOutputParser {

    private final ObjectMapper objectMapper;

    @Override
    public DailyPlanResult parseDailyPlan(String rawOutput) {
        if (!StringUtils.hasText(rawOutput)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, AgentErrorCode.OUTPUT_PARSE_FAILED);
        }
        try {
            return objectMapper.readValue(stripCodeFence(rawOutput), DailyPlanResult.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, AgentErrorCode.OUTPUT_PARSE_FAILED);
        }
    }

    private String stripCodeFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return value.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return value;
    }
}
