package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.constant.CommonConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiCallLogMapper aiCallLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenerateInterviewQuestionVO generateQuestion(GenerateInterviewQuestionDTO dto) {
        long start = System.currentTimeMillis();
        GenerateInterviewQuestionVO vo = new GenerateInterviewQuestionVO();
        vo.setScene("INTERVIEW_QUESTION_GENERATE");
        vo.setQuestionText("Please explain: " + safe(dto.getQuestionTitle(), "this Java backend topic"));
        saveLog("INTERVIEW_QUESTION_GENERATE", dto, vo, start, null);
        return vo;
    }

    @Override
    public EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto) {
        long start = System.currentTimeMillis();
        int answerLength = dto.getAnswerContent() == null ? 0 : dto.getAnswerContent().trim().length();
        EvaluateAnswerVO vo = new EvaluateAnswerVO();
        vo.setScore(Math.min(95, Math.max(50, answerLength / 2)));
        vo.setComment(answerLength >= 80
                ? "Mock evaluation: answer is structured and has enough detail."
                : "Mock evaluation: answer is usable, but should include principle, scenario, and tradeoff.");
        vo.setNextAction(answerLength < 80 && (dto.getFollowUpCount() == null || dto.getFollowUpCount() < 2)
                ? "FOLLOW_UP"
                : "NEXT_QUESTION");
        saveLog("INTERVIEW_ANSWER_EVALUATE", dto, vo, start, null);
        return vo;
    }

    @Override
    public GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto) {
        long start = System.currentTimeMillis();
        GenerateFollowUpVO vo = new GenerateFollowUpVO();
        vo.setFollowUpQuestion("Can you add one production example for " + safe(dto.getQuestionTitle(), "this topic") + "?");
        saveLog("INTERVIEW_FOLLOW_UP_GENERATE", dto, vo, start, null);
        return vo;
    }

    @Override
    public GenerateReportVO generateReport(GenerateReportDTO dto) {
        long start = System.currentTimeMillis();
        GenerateReportVO vo = new GenerateReportVO();
        vo.setTotalScore(82);
        vo.setSummary("本场 V1 模拟面试已完成，综合得分 82。总分由回答完整度、关键知识点覆盖、项目表达和工程权衡四个维度综合给出，用于本地演示和后续针对性复习。");
        vo.setStrengths("回答亮点：能够围绕 Java 后端常见题目给出基本结论，并能结合 Spring、MySQL、Redis 等技术栈说明常见处理思路。项目类问题中能描述业务背景和核心方案。");
        vo.setWeaknesses("主要问题：部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足，项目优化结果缺少量化指标。");
        vo.setSuggestions("复习建议：1. 复盘集合、并发、事务、索引和缓存的高频题；2. 准备 2-3 个带指标的项目优化案例；3. 回答时按结论、原理、项目实践、风险边界的顺序组织。");
        saveLog("INTERVIEW_REPORT_GENERATE", dto, vo, start, null);
        return vo;
    }

    private void saveLog(String scene, Object request, Object response, long startMillis, String errorMessage) {
        AiCallLog log = new AiCallLog();
        log.setScene(scene);
        log.setRequestBody(toJson(request));
        log.setResponseBody(toJson(response));
        log.setCostMillis(System.currentTimeMillis() - startMillis);
        log.setStatus(errorMessage == null ? CommonConstants.YES : CommonConstants.NO);
        log.setErrorMessage(errorMessage);
        aiCallLogMapper.insert(log);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
