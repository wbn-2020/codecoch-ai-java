package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface InterviewStreamService {

    SseEmitter streamCurrentQuestion(Long sessionId);

    SseEmitter streamAnswer(Long sessionId, SubmitInterviewAnswerDTO dto);

    SseEmitter streamReport(Long sessionId);

    SseEmitter streamStudyPlan(StudyPlanGenerateDTO dto);
}
