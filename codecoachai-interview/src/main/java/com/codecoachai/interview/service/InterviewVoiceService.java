package com.codecoachai.interview.service;

import com.codecoachai.interview.domain.dto.InterviewTranscriptConfirmDTO;
import com.codecoachai.interview.domain.dto.InterviewVoiceDiscardDTO;
import com.codecoachai.interview.domain.dto.InterviewVoiceSubmissionCreateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.vo.InterviewTranscriptVO;
import com.codecoachai.interview.domain.vo.InterviewVoiceSubmissionVO;
import com.codecoachai.interview.domain.vo.InterviewVoiceTraceVO;
import java.util.List;

public interface InterviewVoiceService {

    InterviewVoiceSubmissionVO createSubmission(Long sessionId, InterviewVoiceSubmissionCreateDTO dto);

    InterviewVoiceSubmissionVO transcribe(Long sessionId, Long submissionId);

    InterviewVoiceSubmissionVO getSubmission(Long sessionId, Long submissionId);

    InterviewTranscriptVO confirmTranscript(Long sessionId, Long transcriptId, InterviewTranscriptConfirmDTO dto);

    void discardSubmission(Long sessionId, Long submissionId, InterviewVoiceDiscardDTO dto);

    SubmitInterviewAnswerDTO buildSubmitAnswerDTO(Long sessionId, Long transcriptId);

    InterviewTranscriptVO validateConfirmedTranscriptForAnswer(Long sessionId, SubmitInterviewAnswerDTO dto);

    void markTranscriptSubmitted(Long sessionId, Long transcriptId, Long answerMessageId);

    void resetTranscriptSubmitted(Long sessionId, Long transcriptId, Long answerMessageId);

    List<InterviewVoiceTraceVO> listSubmittedVoiceTraces(Long sessionId, Long userId);
}
