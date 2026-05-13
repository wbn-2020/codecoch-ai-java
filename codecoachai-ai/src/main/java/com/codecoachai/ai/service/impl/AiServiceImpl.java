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
        vo.setTotalScore(80);
        vo.setSummary("Mock report: the interview has been completed and the candidate can continue targeted practice.");
        vo.setStrengths("Shows basic understanding of Java backend topics.");
        vo.setWeaknesses("Needs more depth in production troubleshooting and tradeoff analysis.");
        vo.setSuggestions("Review JVM, concurrency, MySQL index design, and Redis cache consistency.");
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
