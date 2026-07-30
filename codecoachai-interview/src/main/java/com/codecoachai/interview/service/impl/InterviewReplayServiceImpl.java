package com.codecoachai.interview.service.impl;

import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewReplayOptionsVO;
import com.codecoachai.interview.domain.vo.InterviewReplayVO;
import com.codecoachai.interview.service.InterviewReplayService;
import com.codecoachai.interview.support.InterviewReplayEligibilityEvaluator;
import com.codecoachai.interview.support.InterviewSessionConfigCopier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * V12 same-configuration interview replay. Claim acquisition and target-session
 * creation are split into bounded transactions by {@link InterviewCloneTransactionService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewReplayServiceImpl implements InterviewReplayService {

    private final InterviewReplayEligibilityEvaluator eligibilityEvaluator;
    private final InterviewCloneTransactionService cloneTransactionService;
    private final ObjectMapper objectMapper;

    @Override
    public InterviewReplayVO create(
            Long sourceSessionId, InterviewReplayCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        String idempotencyKey = dto.getIdempotencyKey().trim();
        InterviewReplay candidate = new InterviewReplay();
        candidate.setUserId(userId);
        candidate.setSourceSessionId(sourceSessionId);
        candidate.setIdempotencyKey(idempotencyKey);

        InterviewReplay completed =
                cloneTransactionService.recoverCompletedReplay(candidate);
        if (completed != null) {
            return toVO(completed, true, null);
        }

        InterviewReplayEligibilityEvaluator.Evaluation eligibility =
                eligibilityEvaluator.evaluate(userId, sourceSessionId);
        eligibility.requireEligible();

        InterviewSession sourceSession = eligibility.sourceSession();
        InterviewReport sourceReport = eligibility.sourceReport();
        candidate.setSourceReportId(sourceReport.getId());
        candidate.setTargetJobId(sourceSession.getTargetJobId());
        candidate.setScenarioVersionId(eligibility.scenarioVersionId());
        candidate.setRubricVersion(sourceReport.getRubricVersion());

        CreateInterviewDTO request =
                InterviewSessionConfigCopier.copyCreationConfig(
                        sourceSession, objectMapper);
        request.setTitle(replayTitle(sourceSession.getTitle()));
        request.setScenarioVersionId(eligibility.scenarioVersionId());
        request.setPracticeMode("REPLAY");
        request.setRecommendationSource("INTERVIEW_REPLAY");
        request.setRecommendationReason(
                "sourceSessionId=" + sourceSessionId
                        + ", sourceReportId=" + sourceReport.getId());
        InterviewServiceImpl.InterviewClonePreparation clonePreparation =
                cloneTransactionService.prepareCloneTarget(request, sourceSessionId);

        InterviewCloneTransactionService.ReplayClaim claim =
                cloneTransactionService.claimReplay(candidate);
        if (!claim.owner()) {
            return toVO(claim.replay(), true, null);
        }
        try {
            InterviewCloneTransactionService.ReplayCreation creation =
                    cloneTransactionService.createReplayTarget(
                            claim.replay().getId(),
                            claim.claimToken(),
                            clonePreparation);
            return toVO(creation.replay(), false, creation.interview());
        } catch (RuntimeException ex) {
            releaseClaimQuietly(claim);
            throw ex;
        }
    }

    @Override
    public InterviewReplayOptionsVO options(Long sourceSessionId) {
        Long userId = SecurityAssert.requireLoginUserId();
        InterviewReplayEligibilityEvaluator.Evaluation eligibility =
                eligibilityEvaluator.evaluate(userId, sourceSessionId);
        InterviewReplayOptionsVO result = new InterviewReplayOptionsVO();
        result.setInterviewId(sourceSessionId);
        result.setTargetJobId(eligibility.sourceSession().getTargetJobId());
        result.setScenarioVersionId(eligibility.scenarioVersionId());
        result.setPolicyVersion(InterviewReplayEligibilityEvaluator.POLICY_VERSION);
        result.setReplayAvailable(eligibility.eligible());
        result.setState(eligibility.eligible() ? "ELIGIBLE" : "INELIGIBLE");
        result.setReasonCode(eligibility.reasonCode());
        result.setReasonMessage(eligibility.reasonMessage());
        if (eligibility.sourceReport() != null) {
            result.setSourceReportId(eligibility.sourceReport().getId());
            result.setRubricVersion(eligibility.sourceReport().getRubricVersion());
        }
        return result;
    }

    private void releaseClaimQuietly(
            InterviewCloneTransactionService.ReplayClaim claim) {
        try {
            cloneTransactionService.releaseReplayClaim(
                    claim.replay().getId(), claim.claimToken());
        } catch (RuntimeException releaseError) {
            log.error(
                    "failed to release interview replay claim {}: {}",
                    claim.replay().getId(),
                    releaseError.getMessage());
        }
    }

    private String replayTitle(String sourceTitle) {
        String title = "同配置再练：" + (StringUtils.hasText(sourceTitle)
                ? sourceTitle.trim() : "模拟面试");
        return title.length() <= 128 ? title : title.substring(0, 128);
    }

    private InterviewReplayVO toVO(
            InterviewReplay replay,
            boolean idempotentReplay,
            CreateInterviewVO interview) {
        InterviewReplayVO vo = new InterviewReplayVO();
        vo.setId(replay.getId());
        vo.setSourceSessionId(replay.getSourceSessionId());
        vo.setSourceReportId(replay.getSourceReportId());
        vo.setTargetSessionId(replay.getTargetSessionId());
        vo.setTargetJobId(replay.getTargetJobId());
        vo.setScenarioVersionId(replay.getScenarioVersionId());
        vo.setRubricVersion(replay.getRubricVersion());
        vo.setStatus(replay.getStatus());
        vo.setIdempotentReplay(idempotentReplay);
        vo.setInterview(interview);
        return vo;
    }
}
