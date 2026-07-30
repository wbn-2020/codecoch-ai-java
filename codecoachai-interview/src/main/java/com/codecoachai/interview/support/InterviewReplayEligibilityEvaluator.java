package com.codecoachai.interview.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.scenario.InterviewScenarioBindingResolver;
import com.codecoachai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewReplayEligibilityEvaluator {

    public static final String POLICY_VERSION = "REPLAY_ELIGIBILITY_V2";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewScenarioBindingResolver bindingResolver;
    private final InterviewService interviewService;
    private final ObjectMapper objectMapper;

    public Evaluation evaluate(Long userId, Long sourceSessionId) {
        InterviewSession sourceSession = sessionMapper.selectById(sourceSessionId);
        if (sourceSession == null
                || CommonConstants.YES.equals(sourceSession.getDeleted())
                || !userId.equals(sourceSession.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "源面试场次不存在或不可用");
        }
        InterviewReport sourceReport = latestReport(sourceSessionId, userId);
        if (sourceReport == null) {
            return Evaluation.ineligible(
                    sourceSession,
                    null,
                    null,
                    "REPORT_NOT_FOUND",
                    "源面试报告不存在");
        }
        if (!InterviewReportTrustPolicy.isAvailableForRemediation(sourceReport)) {
            return Evaluation.ineligible(
                    sourceSession,
                    sourceReport,
                    null,
                    "REPORT_NOT_GENERATED",
                    "源面试报告尚未生成，不能同配置再练");
        }
        Long scenarioVersionId;
        try {
            scenarioVersionId =
                    bindingResolver.reusableScenarioVersionId(
                            sourceSessionId, userId, true);
        } catch (BusinessException ex) {
            return Evaluation.ineligible(
                    sourceSession,
                    sourceReport,
                    null,
                    "SCENARIO_VERSION_INVALID",
                    "源场次绑定的场景版本不可用于历史克隆");
        }
        CreateInterviewDTO cloneRequest =
                InterviewSessionConfigCopier.copyCreationConfig(
                        sourceSession, objectMapper);
        cloneRequest.setScenarioVersionId(scenarioVersionId);
        try {
            interviewService.validateClone(cloneRequest, sourceSessionId);
        } catch (BusinessException ex) {
            return Evaluation.ineligible(
                    sourceSession,
                    sourceReport,
                    scenarioVersionId,
                    "CLONE_CONTEXT_INVALID",
                    ex.getMessage());
        }
        return Evaluation.eligible(
                sourceSession, sourceReport, scenarioVersionId);
    }

    private InterviewReport latestReport(Long sessionId, Long userId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId)
                .eq(InterviewReport::getUserId, userId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));
    }

    public record Evaluation(
            InterviewSession sourceSession,
            InterviewReport sourceReport,
            Long scenarioVersionId,
            boolean eligible,
            String reasonCode,
            String reasonMessage) {

        private static Evaluation eligible(
                InterviewSession session,
                InterviewReport report,
                Long scenarioVersionId) {
            return new Evaluation(
                    session, report, scenarioVersionId, true, null, null);
        }

        private static Evaluation ineligible(
                InterviewSession session,
                InterviewReport report,
                Long scenarioVersionId,
                String reasonCode,
                String reasonMessage) {
            return new Evaluation(
                    session,
                    report,
                    scenarioVersionId,
                    false,
                    reasonCode,
                    reasonMessage);
        }

        public void requireEligible() {
            if (!eligible) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, reasonMessage);
            }
        }
    }
}
