package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.domain.vo.ParseResumeVO;

public interface AiService {

    GenerateInterviewQuestionVO generateQuestion(GenerateInterviewQuestionDTO dto);

    EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto);

    GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto);

    GenerateReportVO generateReport(GenerateReportDTO dto);

    ParseResumeVO parseResume(ParseResumeDTO dto);
}
