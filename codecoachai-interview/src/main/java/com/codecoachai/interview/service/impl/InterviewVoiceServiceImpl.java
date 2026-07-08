package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.AsrRequest;
import com.codecoachai.interview.domain.dto.InterviewTranscriptConfirmDTO;
import com.codecoachai.interview.domain.dto.InterviewVoiceSubmissionCreateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewTranscript;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.domain.enums.InterviewTranscriptStatusEnum;
import com.codecoachai.interview.domain.enums.InterviewVoiceStatusEnum;
import com.codecoachai.interview.domain.vo.AsrResult;
import com.codecoachai.interview.domain.vo.InterviewTranscriptVO;
import com.codecoachai.interview.domain.vo.InterviewVoiceSubmissionVO;
import com.codecoachai.interview.domain.vo.InterviewVoiceTraceVO;
import com.codecoachai.interview.feign.FileFeignClient;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewTranscriptMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import com.codecoachai.interview.service.AsrService;
import com.codecoachai.interview.service.InterviewVoiceService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewVoiceServiceImpl implements InterviewVoiceService {

    private static final String BIZ_TYPE_INTERVIEW_VOICE = "INTERVIEW_VOICE";
    private static final String ANSWER_SOURCE_VOICE = "VOICE_TRANSCRIPT";
    private static final String ANSWER_SOURCE_MANUAL_TRANSCRIPT = "MANUAL_TRANSCRIPT";
    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.75");
    private static final int MAX_CONFIRMED_TEXT_LENGTH = 5000;
    private static final int TRANSCRIBING_RECLAIM_MINUTES = 15;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    private final InterviewTranscriptMapper transcriptMapper;
    private final FileFeignClient fileFeignClient;
    private final AsrService asrService;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewVoiceSubmissionVO createSubmission(Long sessionId, InterviewVoiceSubmissionCreateDTO dto) {
        Long userId = requireUserId();
        InterviewSession session = requireOwnedSession(sessionId, userId);
        InterviewMessage questionMessage = requireCurrentQuestionMessage(session, dto.getQuestionMessageId(), dto.getQuestionId());
        InnerFileInfoVO file = FeignResultUtils.unwrap(fileFeignClient.detail(dto.getFileId(), userId, BIZ_TYPE_INTERVIEW_VOICE));
        if (file == null || !BIZ_TYPE_INTERVIEW_VOICE.equalsIgnoreCase(file.getBizType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice audio file is unavailable");
        }

        InterviewVoiceSubmission submission = new InterviewVoiceSubmission();
        submission.setUserId(userId);
        submission.setSessionId(session.getId());
        submission.setQuestionMessageId(questionMessage.getId());
        submission.setQuestionId(questionMessage.getQuestionId());
        submission.setFileId(file.getId());
        submission.setAudioDurationMs(normalizePositive(dto.getAudioDurationMs()));
        submission.setMimeType(firstText(dto.getMimeType(), file.getMimeType()));
        submission.setVoiceStatus(InterviewVoiceStatusEnum.UPLOADED.name());
        submission.setTraceId(StringUtils.hasText(dto.getTraceId()) ? dto.getTraceId().trim() : newTraceId());
        submission.setFallback(Boolean.FALSE);
        voiceSubmissionMapper.insert(submission);
        log.info("Interview voice submission created sessionId={} submissionId={} fileId={} traceId={}",
                session.getId(), submission.getId(), file.getId(), submission.getTraceId());
        return toSubmissionVO(submission, null);
    }

    @Override
    public InterviewVoiceSubmissionVO transcribe(Long sessionId, Long submissionId) {
        Long userId = requireUserId();
        InterviewVoiceSubmission current = requireOwnedSubmission(sessionId, submissionId, userId);
        if (InterviewVoiceStatusEnum.TRANSCRIBED.name().equals(current.getVoiceStatus())) {
            return toSubmissionVO(current, latestTranscript(current.getId()));
        }
        InterviewVoiceSubmission submission = transactionTemplate.execute(
                status -> claimTranscribing(sessionId, submissionId, userId));

        AsrResult result;
        try {
            result = asrService.transcribe(AsrRequest.builder()
                    .userId(userId)
                    .sessionId(submission.getSessionId())
                    .voiceSubmissionId(submission.getId())
                    .fileId(submission.getFileId())
                    .bizType(BIZ_TYPE_INTERVIEW_VOICE)
                    .mimeType(submission.getMimeType())
                    .audioDurationMs(submission.getAudioDurationMs())
                    .language("zh-CN")
                    .scene("interview.voice.answer")
                    .requestId("asr-" + submission.getId())
                    .traceId(submission.getTraceId())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Interview ASR invocation failed sessionId={} submissionId={} fileId={} failureType={} reason={}",
                    sessionId, submissionId, submission.getFileId(), ex.getClass().getSimpleName(),
                    safeReason(ex.getMessage(), "asr invocation failed"));
            result = AsrResult.builder()
                    .status(AsrResult.STATUS_FAILED)
                    .errorCode("ASR_FAILED")
                    .fallback(Boolean.TRUE)
                    .fallbackReason("ASR invocation failed; manual transcript confirmation is required.")
                    .build();
        }

        AsrResult finalResult = result;
        try {
            return transactionTemplate.execute(
                    status -> saveTranscriptionResult(sessionId, submissionId, userId, submission, finalResult));
        } catch (RuntimeException ex) {
            markTranscribeFailedIfStillTranscribing(sessionId, submissionId, userId,
                    "ASR result persistence failed; please retry transcription.");
            throw ex;
        }
    }

    private InterviewVoiceSubmissionVO saveTranscriptionResult(Long sessionId, Long submissionId, Long userId,
                                                               InterviewVoiceSubmission submission,
                                                               AsrResult result) {
        InterviewVoiceSubmission latest = requireOwnedSubmission(sessionId, submissionId, userId);
        if (!InterviewVoiceStatusEnum.TRANSCRIBING.name().equals(latest.getVoiceStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission transcribe state has changed");
        }

        InterviewTranscript transcript = new InterviewTranscript();
        transcript.setUserId(userId);
        transcript.setSessionId(latest.getSessionId());
        transcript.setVoiceSubmissionId(latest.getId());
        transcript.setQuestionMessageId(latest.getQuestionMessageId());
        transcript.setQuestionId(latest.getQuestionId());
        transcript.setAsrProvider(result == null ? null : result.getProvider());
        transcript.setTraceId(latest.getTraceId());

        boolean success = result != null
                && AsrResult.STATUS_SUCCESS.equals(result.getStatus())
                && StringUtils.hasText(result.getTranscript());
        if (success) {
            BigDecimal confidence = normalizeConfidence(result.getConfidence());
            boolean lowConfidence = confidence != null && confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;
            transcript.setDraftText(result.getTranscript().trim());
            transcript.setConfidence(confidence);
            transcript.setTranscriptStatus(lowConfidence
                    ? InterviewTranscriptStatusEnum.LOW_CONFIDENCE.name()
                    : InterviewTranscriptStatusEnum.DRAFT.name());
            transcript.setFallback(Boolean.FALSE);
            latest.setVoiceStatus(InterviewVoiceStatusEnum.TRANSCRIBED.name());
            latest.setFallback(Boolean.FALSE);
            latest.setFallbackReason(null);
            log.info("Interview ASR succeeded sessionId={} submissionId={} transcriptLength={} lowConfidence={}",
                    sessionId, submissionId, transcript.getDraftText().length(), lowConfidence);
        } else {
            String fallbackReason = firstText(
                    result == null ? null : result.getFallbackReason(),
                    result == null ? null : result.getErrorMessage(),
                    "ASR unavailable; manual transcript confirmation is required.");
            transcript.setTranscriptStatus(InterviewTranscriptStatusEnum.FAILED.name());
            transcript.setFallback(Boolean.TRUE);
            transcript.setFallbackReason(fallbackReason);
            latest.setVoiceStatus(InterviewVoiceStatusEnum.TRANSCRIBE_FAILED.name());
            latest.setFallback(Boolean.TRUE);
            latest.setFallbackReason(fallbackReason);
            log.info("Interview ASR unavailable sessionId={} submissionId={} fileId={} reason={}",
                    sessionId, submissionId, latest.getFileId(),
                    safeReason(fallbackReason, "asr unavailable"));
        }
        transcriptMapper.insert(transcript);
        voiceSubmissionMapper.updateById(latest);
        return toSubmissionVO(latest, transcript);
    }

    @Override
    public InterviewVoiceSubmissionVO getSubmission(Long sessionId, Long submissionId) {
        Long userId = requireUserId();
        InterviewVoiceSubmission submission = requireOwnedSubmission(sessionId, submissionId, userId);
        return toSubmissionVO(submission, latestTranscript(submission.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewTranscriptVO confirmTranscript(Long sessionId, Long transcriptId, InterviewTranscriptConfirmDTO dto) {
        Long userId = requireUserId();
        InterviewTranscript transcript = requireOwnedTranscript(sessionId, transcriptId, userId);
        InterviewVoiceSubmission submission = requireOwnedSubmission(sessionId, transcript.getVoiceSubmissionId(), userId);
        if (InterviewVoiceStatusEnum.DISCARDED.name().equals(submission.getVoiceStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission has been discarded");
        }
        if (transcript.getSubmittedAnswerMessageId() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript has already been submitted");
        }
        if (InterviewTranscriptStatusEnum.LOW_CONFIDENCE.name().equals(transcript.getTranscriptStatus())
                && !Boolean.TRUE.equals(dto.getLowConfidenceAcknowledged())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Low-confidence transcript must be acknowledged before confirmation");
        }

        String confirmedText = dto.getConfirmedText() == null ? "" : dto.getConfirmedText().trim();
        if (!StringUtils.hasText(confirmedText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Confirmed transcript cannot be empty");
        }
        if (confirmedText.length() > MAX_CONFIRMED_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Confirmed transcript is too long");
        }

        transcript.setConfirmedText(confirmedText);
        transcript.setTranscriptStatus(InterviewTranscriptStatusEnum.CONFIRMED.name());
        transcript.setConfirmedAt(LocalDateTime.now());
        transcriptMapper.updateById(transcript);

        submission.setVoiceStatus(InterviewVoiceStatusEnum.CONFIRMED.name());
        voiceSubmissionMapper.updateById(submission);
        log.info("Interview transcript confirmed sessionId={} submissionId={} transcriptId={} textLength={}",
                sessionId, submission.getId(), transcript.getId(), confirmedText.length());
        return toTranscriptVO(transcript);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discardSubmission(Long sessionId, Long submissionId) {
        Long userId = requireUserId();
        InterviewVoiceSubmission submission = requireOwnedSubmission(sessionId, submissionId, userId);
        if (InterviewVoiceStatusEnum.CONFIRMED.name().equals(submission.getVoiceStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Confirmed voice submission cannot be discarded");
        }
        submission.setVoiceStatus(InterviewVoiceStatusEnum.DISCARDED.name());
        voiceSubmissionMapper.updateById(submission);
        transcriptMapper.update(null, new LambdaUpdateWrapper<InterviewTranscript>()
                .eq(InterviewTranscript::getVoiceSubmissionId, submission.getId())
                .eq(InterviewTranscript::getUserId, userId)
                .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                .ne(InterviewTranscript::getTranscriptStatus, InterviewTranscriptStatusEnum.CONFIRMED.name())
                .set(InterviewTranscript::getTranscriptStatus, InterviewTranscriptStatusEnum.REJECTED.name()));
        log.info("Interview voice submission discarded sessionId={} submissionId={}", sessionId, submissionId);
    }

    @Override
    public SubmitInterviewAnswerDTO buildSubmitAnswerDTO(Long sessionId, Long transcriptId) {
        Long userId = requireUserId();
        InterviewTranscript transcript = requireOwnedTranscript(sessionId, transcriptId, userId);
        requireConfirmedTranscript(transcript);
        SubmitInterviewAnswerDTO dto = new SubmitInterviewAnswerDTO();
        dto.setMessageId(transcript.getQuestionMessageId());
        dto.setQuestionId(transcript.getQuestionId());
        dto.setAnswerContent(transcript.getConfirmedText());
        dto.setVoiceSubmissionId(transcript.getVoiceSubmissionId());
        dto.setTranscriptId(transcript.getId());
        dto.setTranscriptConfidence(transcript.getConfidence());
        dto.setAnswerSource(Boolean.TRUE.equals(transcript.getFallback())
                ? ANSWER_SOURCE_MANUAL_TRANSCRIPT
                : ANSWER_SOURCE_VOICE);
        dto.setNeedFollowUp(Boolean.TRUE);
        return dto;
    }

    @Override
    public InterviewTranscriptVO validateConfirmedTranscriptForAnswer(Long sessionId, SubmitInterviewAnswerDTO dto) {
        if (dto == null || (dto.getTranscriptId() == null && dto.getVoiceSubmissionId() == null)) {
            return null;
        }
        if (dto.getTranscriptId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript id is required for voice answer source");
        }
        Long userId = requireUserId();
        InterviewTranscript transcript = requireOwnedTranscript(sessionId, dto.getTranscriptId(), userId);
        requireConfirmedTranscript(transcript);
        if (dto.getVoiceSubmissionId() != null && !dto.getVoiceSubmissionId().equals(transcript.getVoiceSubmissionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission does not match transcript");
        }
        if (dto.getMessageId() != null && !dto.getMessageId().equals(transcript.getQuestionMessageId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript does not match current question");
        }
        if (dto.getQuestionId() != null && transcript.getQuestionId() != null && !dto.getQuestionId().equals(transcript.getQuestionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript does not match current question");
        }
        String answerText = dto.getAnswerContent() == null ? "" : dto.getAnswerContent().trim();
        if (!answerText.equals(transcript.getConfirmedText().trim())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice transcript must be confirmed again after editing");
        }
        InterviewVoiceSubmission submission = requireOwnedSubmission(sessionId, transcript.getVoiceSubmissionId(), userId);
        if (!InterviewVoiceStatusEnum.CONFIRMED.name().equals(submission.getVoiceStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission is not confirmed");
        }
        return toTranscriptVO(transcript);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTranscriptSubmitted(Long sessionId, Long transcriptId, Long answerMessageId) {
        if (transcriptId == null || answerMessageId == null) {
            return;
        }
        Long userId = requireUserId();
        InterviewTranscript transcript = requireOwnedTranscript(sessionId, transcriptId, userId);
        requireConfirmedTranscript(transcript);
        transcript.setSubmittedAnswerMessageId(answerMessageId);
        transcript.setSubmittedAt(LocalDateTime.now());
        transcriptMapper.updateById(transcript);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetTranscriptSubmitted(Long sessionId, Long transcriptId, Long answerMessageId) {
        if (transcriptId == null || answerMessageId == null) {
            return;
        }
        Long userId = requireUserId();
        transcriptMapper.update(null, new LambdaUpdateWrapper<InterviewTranscript>()
                .eq(InterviewTranscript::getId, transcriptId)
                .eq(InterviewTranscript::getSessionId, sessionId)
                .eq(InterviewTranscript::getUserId, userId)
                .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                .eq(InterviewTranscript::getSubmittedAnswerMessageId, answerMessageId)
                .set(InterviewTranscript::getSubmittedAnswerMessageId, null)
                .set(InterviewTranscript::getSubmittedAt, null));
    }

    @Override
    public List<InterviewVoiceTraceVO> listSubmittedVoiceTraces(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return List.of();
        }
        return transcriptMapper.selectList(new LambdaQueryWrapper<InterviewTranscript>()
                        .eq(InterviewTranscript::getSessionId, sessionId)
                        .eq(InterviewTranscript::getUserId, userId)
                        .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                        .isNotNull(InterviewTranscript::getSubmittedAnswerMessageId)
                        .orderByAsc(InterviewTranscript::getSubmittedAt)
                        .orderByAsc(InterviewTranscript::getId))
                .stream()
                .map(this::toTraceVO)
                .toList();
    }

    private InterviewSession requireOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.selectOne(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview session does not exist or is unavailable");
        }
        return session;
    }

    private InterviewMessage requireCurrentQuestionMessage(InterviewSession session, Long messageId, Long questionId) {
        InterviewMessage current = messageMapper.selectOne(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, session.getId())
                .eq(InterviewMessage::getRole, "AI")
                .in(InterviewMessage::getMessageType, List.of("QUESTION", "FOLLOW_UP"))
                .eq(InterviewMessage::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewMessage::getCreatedAt)
                .orderByDesc(InterviewMessage::getId)
                .last("limit 1"));
        if (current == null || messageId == null || !messageId.equals(current.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission must target the current question");
        }
        if (questionId != null && current.getQuestionId() != null && !questionId.equals(current.getQuestionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission question mismatch");
        }
        return current;
    }

    private InterviewVoiceSubmission requireOwnedSubmission(Long sessionId, Long submissionId, Long userId) {
        InterviewVoiceSubmission submission = voiceSubmissionMapper.selectOne(new LambdaQueryWrapper<InterviewVoiceSubmission>()
                .eq(InterviewVoiceSubmission::getId, submissionId)
                .eq(InterviewVoiceSubmission::getSessionId, sessionId)
                .eq(InterviewVoiceSubmission::getUserId, userId)
                .eq(InterviewVoiceSubmission::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (submission == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission does not exist or is unavailable");
        }
        return submission;
    }

    private InterviewTranscript requireOwnedTranscript(Long sessionId, Long transcriptId, Long userId) {
        InterviewTranscript transcript = transcriptMapper.selectOne(new LambdaQueryWrapper<InterviewTranscript>()
                .eq(InterviewTranscript::getId, transcriptId)
                .eq(InterviewTranscript::getSessionId, sessionId)
                .eq(InterviewTranscript::getUserId, userId)
                .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (transcript == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript does not exist or is unavailable");
        }
        return transcript;
    }

    private InterviewVoiceSubmission claimTranscribing(Long sessionId, Long submissionId, Long userId) {
        InterviewVoiceSubmission submission = requireOwnedSubmission(sessionId, submissionId, userId);
        if (InterviewVoiceStatusEnum.CONFIRMED.name().equals(submission.getVoiceStatus())
                || InterviewVoiceStatusEnum.DISCARDED.name().equals(submission.getVoiceStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission cannot be transcribed in current status");
        }
        LocalDateTime reclaimBefore = LocalDateTime.now().minusMinutes(TRANSCRIBING_RECLAIM_MINUTES);
        int updated = voiceSubmissionMapper.update(null, new LambdaUpdateWrapper<InterviewVoiceSubmission>()
                .eq(InterviewVoiceSubmission::getId, submissionId)
                .eq(InterviewVoiceSubmission::getSessionId, sessionId)
                .eq(InterviewVoiceSubmission::getUserId, userId)
                .eq(InterviewVoiceSubmission::getDeleted, CommonConstants.NO)
                .and(wrapper -> wrapper.in(InterviewVoiceSubmission::getVoiceStatus, List.of(
                                InterviewVoiceStatusEnum.UPLOADED.name(),
                                InterviewVoiceStatusEnum.TRANSCRIBE_FAILED.name()))
                        .or(timeout -> timeout
                                .eq(InterviewVoiceSubmission::getVoiceStatus, InterviewVoiceStatusEnum.TRANSCRIBING.name())
                                .lt(InterviewVoiceSubmission::getUpdatedAt, reclaimBefore)))
                .set(InterviewVoiceSubmission::getVoiceStatus, InterviewVoiceStatusEnum.TRANSCRIBING.name())
                .set(InterviewVoiceSubmission::getFallbackReason, null));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission is already being transcribed");
        }
        submission.setVoiceStatus(InterviewVoiceStatusEnum.TRANSCRIBING.name());
        submission.setFallbackReason(null);
        return submission;
    }

    private void markTranscribeFailedIfStillTranscribing(Long sessionId, Long submissionId, Long userId, String reason) {
        try {
            int updated = voiceSubmissionMapper.update(null, new LambdaUpdateWrapper<InterviewVoiceSubmission>()
                    .eq(InterviewVoiceSubmission::getId, submissionId)
                    .eq(InterviewVoiceSubmission::getSessionId, sessionId)
                    .eq(InterviewVoiceSubmission::getUserId, userId)
                    .eq(InterviewVoiceSubmission::getDeleted, CommonConstants.NO)
                    .eq(InterviewVoiceSubmission::getVoiceStatus, InterviewVoiceStatusEnum.TRANSCRIBING.name())
                    .set(InterviewVoiceSubmission::getVoiceStatus, InterviewVoiceStatusEnum.TRANSCRIBE_FAILED.name())
                    .set(InterviewVoiceSubmission::getFallback, Boolean.TRUE)
                    .set(InterviewVoiceSubmission::getFallbackReason, reason));
            log.warn("Interview ASR result persistence failed; transcribing state compensation updated sessionId={} submissionId={} updated={}",
                    sessionId, submissionId, updated);
        } catch (RuntimeException compensationEx) {
            log.warn("Interview ASR transcribing compensation failed sessionId={} submissionId={} failureType={}",
                    sessionId, submissionId, compensationEx.getClass().getSimpleName());
        }
    }

    private void requireConfirmedTranscript(InterviewTranscript transcript) {
        if (transcript == null
                || !InterviewTranscriptStatusEnum.CONFIRMED.name().equals(transcript.getTranscriptStatus())
                || !StringUtils.hasText(transcript.getConfirmedText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript is not confirmed");
        }
        if (transcript.getSubmittedAnswerMessageId() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Transcript has already been submitted");
        }
    }

    private InterviewTranscript latestTranscript(Long submissionId) {
        if (submissionId == null) {
            return null;
        }
        return transcriptMapper.selectOne(new LambdaQueryWrapper<InterviewTranscript>()
                .eq(InterviewTranscript::getVoiceSubmissionId, submissionId)
                .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewTranscript::getId)
                .last("limit 1"));
    }

    private InterviewVoiceSubmissionVO toSubmissionVO(InterviewVoiceSubmission submission, InterviewTranscript transcript) {
        InterviewVoiceSubmissionVO vo = new InterviewVoiceSubmissionVO();
        vo.setVoiceSubmissionId(submission.getId());
        vo.setSessionId(submission.getSessionId());
        vo.setQuestionMessageId(submission.getQuestionMessageId());
        vo.setQuestionId(submission.getQuestionId());
        vo.setFileId(submission.getFileId());
        vo.setAudioDurationMs(submission.getAudioDurationMs());
        vo.setMimeType(submission.getMimeType());
        vo.setVoiceStatus(submission.getVoiceStatus());
        vo.setTraceId(submission.getTraceId());
        vo.setFallback(Boolean.TRUE.equals(submission.getFallback()));
        vo.setFallbackReason(submission.getFallbackReason());
        vo.setTranscript(toTranscriptVO(transcript));
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setUpdatedAt(submission.getUpdatedAt());
        return vo;
    }

    private InterviewTranscriptVO toTranscriptVO(InterviewTranscript transcript) {
        if (transcript == null) {
            return null;
        }
        InterviewTranscriptVO vo = new InterviewTranscriptVO();
        vo.setTranscriptId(transcript.getId());
        vo.setVoiceSubmissionId(transcript.getVoiceSubmissionId());
        vo.setSessionId(transcript.getSessionId());
        vo.setQuestionMessageId(transcript.getQuestionMessageId());
        vo.setQuestionId(transcript.getQuestionId());
        vo.setDraftText(transcript.getDraftText());
        vo.setConfirmedText(transcript.getConfirmedText());
        vo.setConfidence(transcript.getConfidence());
        vo.setConfidenceLevel(confidenceLevel(transcript.getConfidence()));
        vo.setLowConfidence(isLowConfidence(transcript.getConfidence(), transcript.getTranscriptStatus()));
        vo.setTranscriptStatus(transcript.getTranscriptStatus());
        vo.setAsrProvider(transcript.getAsrProvider());
        vo.setFallback(Boolean.TRUE.equals(transcript.getFallback()));
        vo.setFallbackReason(transcript.getFallbackReason());
        vo.setTraceId(transcript.getTraceId());
        vo.setConfirmedAt(transcript.getConfirmedAt());
        vo.setSubmittedAnswerMessageId(transcript.getSubmittedAnswerMessageId());
        vo.setSubmittedAt(transcript.getSubmittedAt());
        vo.setCreatedAt(transcript.getCreatedAt());
        vo.setUpdatedAt(transcript.getUpdatedAt());
        return vo;
    }

    private InterviewVoiceTraceVO toTraceVO(InterviewTranscript transcript) {
        InterviewVoiceTraceVO vo = new InterviewVoiceTraceVO();
        vo.setVoiceSubmissionId(transcript.getVoiceSubmissionId());
        vo.setTranscriptId(transcript.getId());
        vo.setAnswerMessageId(transcript.getSubmittedAnswerMessageId());
        vo.setQuestionMessageId(transcript.getQuestionMessageId());
        vo.setQuestionId(transcript.getQuestionId());
        vo.setAnswerSource(Boolean.TRUE.equals(transcript.getFallback()) ? ANSWER_SOURCE_MANUAL_TRANSCRIPT : ANSWER_SOURCE_VOICE);
        vo.setTranscriptStatus(transcript.getTranscriptStatus());
        vo.setConfidence(transcript.getConfidence());
        vo.setLowConfidence(isLowConfidence(transcript.getConfidence(), transcript.getTranscriptStatus()));
        vo.setFallback(Boolean.TRUE.equals(transcript.getFallback()));
        vo.setFallbackReason(transcript.getFallbackReason());
        vo.setTraceId(transcript.getTraceId());
        vo.setConfirmedAt(transcript.getConfirmedAt());
        vo.setSubmittedAt(transcript.getSubmittedAt());
        return vo;
    }

    private Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String newTraceId() {
        return "interview-voice-" + UUID.randomUUID().toString().replace("-", "");
    }

    private Long normalizePositive(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private BigDecimal normalizeConfidence(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    private boolean isLowConfidence(BigDecimal confidence, String status) {
        return InterviewTranscriptStatusEnum.LOW_CONFIDENCE.name().equals(status)
                || (confidence != null && confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0);
    }

    private String confidenceLevel(BigDecimal confidence) {
        if (confidence == null) {
            return "UNKNOWN";
        }
        if (confidence.compareTo(new BigDecimal("0.85")) >= 0) {
            return "HIGH";
        }
        if (confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "voice failure";
        if (!StringUtils.hasText(reason)) {
            return base;
        }
        return base + "; reasonLength=" + reason.length() + "; reasonHash=" + shortHash(reason);
    }

    private String shortHash(String value) {
        String hash = TextFingerprintUtils.sha256Hex(value);
        return hash == null ? null : hash.substring(0, Math.min(hash.length(), 12));
    }
}
