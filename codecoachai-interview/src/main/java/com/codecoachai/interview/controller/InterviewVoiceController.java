package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.interview.domain.dto.InterviewTranscriptConfirmDTO;
import com.codecoachai.interview.domain.dto.InterviewVoiceSubmissionCreateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.InterviewTranscriptVO;
import com.codecoachai.interview.domain.vo.InterviewVoiceSubmissionVO;
import com.codecoachai.interview.domain.vo.SubmitInterviewAnswerVO;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.service.InterviewVoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interviews/{id}/voice")
public class InterviewVoiceController {

    private final InterviewVoiceService interviewVoiceService;
    private final InterviewService interviewService;

    @PostMapping("/submissions")
    public Result<InterviewVoiceSubmissionVO> createSubmission(@PathVariable Long id,
                                                               @Valid @RequestBody InterviewVoiceSubmissionCreateDTO dto) {
        return Result.success(interviewVoiceService.createSubmission(id, dto));
    }

    @PostMapping("/submissions/{submissionId}/transcribe")
    public Result<InterviewVoiceSubmissionVO> transcribe(@PathVariable Long id,
                                                         @PathVariable Long submissionId) {
        return Result.success(interviewVoiceService.transcribe(id, submissionId));
    }

    @GetMapping("/submissions/{submissionId}")
    public Result<InterviewVoiceSubmissionVO> getSubmission(@PathVariable Long id,
                                                            @PathVariable Long submissionId) {
        return Result.success(interviewVoiceService.getSubmission(id, submissionId));
    }

    @PostMapping("/transcripts/{transcriptId}/confirm")
    public Result<InterviewTranscriptVO> confirmTranscript(@PathVariable Long id,
                                                           @PathVariable Long transcriptId,
                                                           @Valid @RequestBody InterviewTranscriptConfirmDTO dto) {
        return Result.success(interviewVoiceService.confirmTranscript(id, transcriptId, dto));
    }

    @PostMapping("/submissions/{submissionId}/discard")
    public Result<Void> discardSubmission(@PathVariable Long id,
                                          @PathVariable Long submissionId) {
        interviewVoiceService.discardSubmission(id, submissionId);
        return Result.success();
    }

    @PostMapping("/transcripts/{transcriptId}/submit-answer")
    public Result<SubmitInterviewAnswerVO> submitTranscriptAnswer(@PathVariable Long id,
                                                                  @PathVariable Long transcriptId) {
        SubmitInterviewAnswerDTO dto = interviewVoiceService.buildSubmitAnswerDTO(id, transcriptId);
        return Result.success(interviewService.answer(id, dto));
    }
}
