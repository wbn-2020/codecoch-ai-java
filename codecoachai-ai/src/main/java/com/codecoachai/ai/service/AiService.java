package com.codecoachai.ai.service;

import com.codecoachai.ai.domain.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.ai.domain.dto.AnalyzeSkillGapDTO;
import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.dto.GenerateAgentReviewDTO;
import com.codecoachai.ai.domain.dto.GenerateAgentWeeklyReportDTO;
import com.codecoachai.ai.domain.dto.GenerateApplicationEventReviewDTO;
import com.codecoachai.ai.domain.dto.GenerateFollowUpDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewPreparationDTO;
import com.codecoachai.ai.domain.dto.GenerateInterviewQuestionDTO;
import com.codecoachai.ai.domain.dto.GenerateLearningPlanDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionRecommendationDTO;
import com.codecoachai.ai.domain.dto.GenerateQuestionDraftDTO;
import com.codecoachai.ai.domain.dto.GenerateReportDTO;
import com.codecoachai.ai.domain.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.ai.domain.dto.ParseResumeDTO;
import com.codecoachai.ai.domain.dto.ParseJobDescriptionDTO;
import com.codecoachai.ai.domain.dto.PracticeReviewDTO;
import com.codecoachai.ai.domain.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.ai.domain.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.ai.domain.vo.AnalyzeSkillGapVO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.domain.vo.GenerateAgentReviewVO;
import com.codecoachai.ai.domain.vo.GenerateAgentWeeklyReportVO;
import com.codecoachai.ai.domain.vo.GenerateApplicationEventReviewVO;
import com.codecoachai.ai.domain.vo.GenerateFollowUpVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewPreparationVO;
import com.codecoachai.ai.domain.vo.GenerateInterviewQuestionVO;
import com.codecoachai.ai.domain.vo.GenerateLearningPlanVO;
import com.codecoachai.ai.domain.vo.GenerateQuestionRecommendationVO;
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

    EvaluateAnswerVO evaluateStream(EvaluateAnswerDTO dto, java.util.function.Consumer<String> tokenConsumer);

    GenerateFollowUpVO generateFollowUp(GenerateFollowUpDTO dto);

    GenerateReportVO generateReport(GenerateReportDTO dto);

    ParseResumeVO parseResume(ParseResumeDTO dto);

    ResumeOptimizeAiResponseVO optimizeResume(ResumeOptimizeAiRequestDTO dto);

    GenerateLearningPlanVO generateLearningPlan(GenerateLearningPlanDTO dto);

    GenerateLearningPlanVO generateTargetedStudyPlan(GenerateTargetedStudyPlanDTO dto);

    GenerateQuestionRecommendationVO generateQuestionRecommendations(GenerateQuestionRecommendationDTO dto);

    ParseJobDescriptionVO parseJobDescription(ParseJobDescriptionDTO dto);

    AnalyzeResumeJobMatchVO analyzeResumeJobMatch(AnalyzeResumeJobMatchDTO dto);

    AnalyzeSkillGapVO analyzeSkillGap(AnalyzeSkillGapDTO dto);

    GenerateAgentReviewVO generateAgentReview(GenerateAgentReviewDTO dto);

    GenerateApplicationEventReviewVO generateApplicationEventReview(GenerateApplicationEventReviewDTO dto);

    GenerateInterviewPreparationVO generateInterviewPreparation(GenerateInterviewPreparationDTO dto);

    GenerateAgentWeeklyReportVO generateWeeklyCareerReport(GenerateAgentWeeklyReportDTO dto);
}
