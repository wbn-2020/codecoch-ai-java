package com.codecoachai.interview.controller;

import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.service.InterviewStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/sse")
public class AiSseController {

    private final InterviewStreamService interviewStreamService;

    @GetMapping(value = "/interview-question", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter interviewQuestion(@RequestParam Long sessionId) {
        return interviewStreamService.streamCurrentQuestion(sessionId);
    }

    @GetMapping(value = "/interview-comment", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter interviewComment(@RequestParam Long sessionId,
                                       @RequestParam String answerContent) {
        SubmitInterviewAnswerDTO dto = new SubmitInterviewAnswerDTO();
        dto.setAnswerContent(answerContent);
        return interviewStreamService.streamAnswer(sessionId, dto);
    }

    @PostMapping(value = "/interview-comment", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter interviewCommentByBody(@RequestParam Long sessionId,
                                             @RequestBody SubmitInterviewAnswerDTO dto) {
        return interviewStreamService.streamAnswer(sessionId, dto);
    }

    @GetMapping(value = "/report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter report(@RequestParam Long sessionId) {
        return interviewStreamService.streamReport(sessionId);
    }

    @GetMapping(value = "/study-plan", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter studyPlan(@ModelAttribute StudyPlanGenerateDTO dto) {
        return interviewStreamService.streamStudyPlan(dto);
    }
}
