package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.domain.vo.CurrentInterviewVO;
import com.codecoachai.interview.domain.vo.CurrentQuestionVO;
import com.codecoachai.interview.domain.vo.FinishInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewDetailVO;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.InterviewMessageVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.StartInterviewVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.InterviewStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewStreamService interviewStreamService;

    @PostMapping
    public Result<CreateInterviewVO> create(@Valid @RequestBody CreateInterviewDTO dto) {
        return Result.success(interviewService.create(dto));
    }

    @PostMapping("/create-by-job-target")
    public Result<CreateInterviewVO> createByJobTarget(@Valid @RequestBody CreateInterviewDTO dto) {
        dto.setMode(dto.getMode() == null ? "COMPREHENSIVE" : dto.getMode());
        dto.setInterviewMode(dto.getInterviewMode() == null ? dto.getMode() : dto.getInterviewMode());
        dto.setBasedOnResume(dto.getBasedOnResume() == null ? Boolean.TRUE : dto.getBasedOnResume());
        return Result.success(interviewService.create(dto));
    }

    @PostMapping("/{id}/start")
    public Result<StartInterviewVO> start(@PathVariable Long id) {
        return Result.success(interviewService.start(id));
    }

    @GetMapping("/{id}/current")
    public Result<CurrentInterviewVO> current(@PathVariable Long id) {
        return Result.success(interviewService.current(id));
    }

    @GetMapping("/{id}/current-question")
    public Result<CurrentQuestionVO> currentQuestion(@PathVariable Long id) {
        return Result.success(interviewService.currentQuestion(id));
    }

    @PostMapping(value = "/{id}/questions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter currentQuestionStream(@PathVariable Long id) {
        return interviewStreamService.streamCurrentQuestion(id);
    }

    @PostMapping("/{id}/answer")
    public Result<SubmitInterviewAnswerVO> answer(@PathVariable Long id,
                                                  @Valid @RequestBody SubmitInterviewAnswerDTO dto) {
        return Result.success(interviewService.answer(id, dto));
    }

    @PostMapping(value = "/{id}/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(@PathVariable Long id,
                                   @Valid @RequestBody SubmitInterviewAnswerDTO dto) {
        return interviewStreamService.streamAnswer(id, dto);
    }

    @PostMapping("/{id}/finish")
    public Result<FinishInterviewVO> finish(@PathVariable Long id) {
        return Result.success(interviewService.finish(id));
    }

    @PostMapping("/{id}/report/retry")
    public Result<FinishInterviewVO> retryReport(@PathVariable Long id) {
        return Result.success(interviewService.retryReport(id));
    }

    @GetMapping
    public Result<PageResult<InterviewListVO>> list(@RequestParam(required = false) Long pageNo,
                                                    @RequestParam(required = false) Long pageSize) {
        return Result.success(interviewService.list(pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public Result<InterviewDetailVO> detail(@PathVariable Long id) {
        return Result.success(interviewService.detail(id));
    }

    @GetMapping("/{id}/messages")
    public Result<List<InterviewMessageVO>> messages(@PathVariable Long id) {
        return Result.success(interviewService.messages(id));
    }

    @GetMapping("/{id}/report")
    public Result<InterviewReportVO> report(@PathVariable Long id) {
        return Result.success(interviewService.report(id));
    }
}
