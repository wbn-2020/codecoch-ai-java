package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface InterviewStreamService {

    SseEmitter streamCurrentQuestion(Long sessionId);

    SseEmitter streamAnswer(Long sessionId, SubmitInterviewAnswerDTO dto);
}
