package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.dto.ParseJobDescriptionDTO;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateLearningPlanVO;
import com.codecoachai.ai.domain.vo.GenerateQuestionDraftVO;
import com.codecoachai.ai.domain.vo.GenerateReportVO;
import com.codecoachai.ai.domain.vo.ParseResumeVO;
import com.codecoachai.ai.domain.vo.ParseJobDescriptionVO;
import com.codecoachai.ai.domain.vo.PracticeReviewVO;
import com.codecoachai.ai.domain.vo.ResumeOptimizeAiResponseVO;

public interface AiService {

    GenerateInterviewQuestionVO generateQuestion(GenerateInterviewQuestionDTO dto);

    GenerateQuestionDraftVO generateQuestionDrafts(GenerateQuestionDraftDTO dto);

    PracticeReviewVO reviewPractice(PracticeReviewDTO dto);

    EvaluateAnswerVO evaluate(EvaluateAnswerDTO dto);

    GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto);

    GenerateReportVO generateReport(GenerateReportDTO dto);

    ParseResumeVO parseResume(ParseResumeDTO dto);

    ResumeOptimizeAiResponseVO optimizeResume(ResumeOptimizeAiRequestDTO dto);

    GenerateLearningPlanVO generateLearningPlan(GenerateLearningPlanDTO dto);

    ParseJobDescriptionVO parseJobDescription(ParseJobDescriptionDTO dto);

    AnalyzeResumeJobMatchVO analyzeResumeJobMatch(AnalyzeResumeJobMatchDTO dto);
}
