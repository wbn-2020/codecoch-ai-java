package com.codecoachai.interview.controller;

import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.service.InterviewStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Interview AI SSE", description = "Interview SSE APIs. Synchronous report APIs remain available as fallback.")
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
    @Operation(summary = "Stream existing interview report content",
            description = "Legacy SSE compatibility endpoint. Use /ai/sse/interview-report for staged report generation.")
    public SseEmitter report(@RequestParam Long sessionId) {
        return interviewStreamService.streamReport(sessionId);
    }

    @GetMapping(value = "/interview-report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream interview report generation progress",
            description = "Returns text/event-stream staged events. The synchronous /interviews/{id}/finish, /interviews/{id}/report/retry and /interviews/{id}/report APIs remain fallback APIs.")
    public SseEmitter interviewReport(@RequestParam Long interviewId,
                                      @RequestParam(required = false) Long reportId,
                                      @RequestParam(required = false, defaultValue = "false") Boolean forceRegenerate) {
        return interviewStreamService.streamInterviewReport(interviewId, reportId, forceRegenerate);
    }

    @GetMapping(value = "/study-plan", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter studyPlan(@ModelAttribute StudyPlanGenerateDTO dto) {
        return interviewStreamService.streamStudyPlan(dto);
    }
}
