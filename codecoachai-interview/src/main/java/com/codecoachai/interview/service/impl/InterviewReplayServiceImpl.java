package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.domain.vo.InterviewReplayVO;
import com.codecoachai.interview.mapper.InterviewReplayMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.scenario.InterviewScenarioBindingResolver;
import com.codecoachai.interview.service.InterviewReplayService;
import com.codecoachai.interview.service.InterviewService;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.codecoachai.interview.support.InterviewSessionConfigCopier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * V12: same-configuration interview replay. Clones the source session's creation config
 * verbatim — including the scenario binding, so the new round keeps the source rubric and the
 * pair stays comparable — unlike remediation, which changes the training conditions on purpose.
 */
@Service
@RequiredArgsConstructor
public class InterviewReplayServiceImpl implements InterviewReplayService {

    private static final String STATUS_CREATING = "CREATING";
    private static final String STATUS_CREATED = "CREATED";

    private final InterviewReplayMapper replayMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewScenarioBindingResolver bindingResolver;
    private final InterviewService interviewService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewReplayVO create(Long sourceSessionId, InterviewReplayCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        String idempotencyKey = dto.getIdempotencyKey().trim();

        InterviewReplay existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            validateReplayPayload(existing, sourceSessionId);
            return toVO(existing, true, null);
        }

        InterviewSession sourceSession = sessionMapper.selectById(sourceSessionId);
        if (sourceSession == null
                || CommonConstants.YES.equals(sourceSession.getDeleted())
                || !userId.equals(sourceSession.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "源面试场次不存在或不可用");
        }
        InterviewReport sourceReport = latestReport(sourceSessionId, userId);
        if (!InterviewReportTrustPolicy.isAvailableForRemediation(sourceReport)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "源面试报告尚未生成，不能同配置再练");
        }
        // Strict: a replay whose scenario (and thus rubric) cannot be reproduced would defeat
        // its whole purpose — surface the problem instead of silently degrading.
        Long scenarioVersionId =
                bindingResolver.reusableScenarioVersionId(sourceSessionId, userId, true);

        InterviewReplay replay = new InterviewReplay();
        replay.setUserId(userId);
        replay.setSourceSessionId(sourceSessionId);
        replay.setSourceReportId(sourceReport.getId());
        replay.setTargetJobId(sourceSession.getTargetJobId());
        replay.setScenarioVersionId(scenarioVersionId);
        replay.setRubricVersion(sourceReport.getRubricVersion());
        replay.setStatus(STATUS_CREATING);
        replay.setIdempotencyKey(idempotencyKey);
        try {
            replayMapper.insert(replay);
        } catch (DuplicateKeyException ex) {
            InterviewReplay concurrent = findByIdempotencyKey(userId, idempotencyKey);
            if (concurrent == null) {
                throw ex;
            }
            validateReplayPayload(concurrent, sourceSessionId);
            return toVO(concurrent, true, null);
        }

        CreateInterviewDTO request =
                InterviewSessionConfigCopier.copyCreationConfig(sourceSession, objectMapper);
        request.setTitle(replayTitle(sourceSession.getTitle()));
        request.setScenarioVersionId(scenarioVersionId);
        request.setPracticeMode("REPLAY");
        request.setRecommendationSource("INTERVIEW_REPLAY");
        request.setRecommendationReason("sourceSessionId=" + sourceSessionId
                + ", sourceReportId=" + sourceReport.getId());

        CreateInterviewVO interview = interviewService.create(request);
        if (interview == null || interview.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同配置再练创建失败");
        }

        replay.setTargetSessionId(interview.getId());
        replay.setStatus(STATUS_CREATED);
        if (replayMapper.updateById(replay) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "再练记录保存失败");
        }
        return toVO(replay, false, interview);
    }

    private InterviewReplay findByIdempotencyKey(Long userId, String idempotencyKey) {
        return replayMapper.selectOne(new LambdaQueryWrapper<InterviewReplay>()
                .eq(InterviewReplay::getUserId, userId)
                .eq(InterviewReplay::getIdempotencyKey, idempotencyKey)
                .eq(InterviewReplay::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private InterviewReport latestReport(Long sessionId, Long userId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId)
                .eq(InterviewReport::getUserId, userId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));
    }

    private void validateReplayPayload(InterviewReplay existing, Long sourceSessionId) {
        if (!sourceSessionId.equals(existing.getSourceSessionId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "幂等键已被不同的再练请求占用");
        }
    }

    private String replayTitle(String sourceTitle) {
        String title = "同配置再练：" + (StringUtils.hasText(sourceTitle)
                ? sourceTitle.trim() : "模拟面试");
        return title.length() <= 128 ? title : title.substring(0, 128);
    }

    private InterviewReplayVO toVO(
            InterviewReplay replay, boolean idempotentReplay, CreateInterviewVO interview) {
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
