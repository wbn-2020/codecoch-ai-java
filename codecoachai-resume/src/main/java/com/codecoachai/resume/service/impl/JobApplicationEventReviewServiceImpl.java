package com.codecoachai.resume.service.impl;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.JobApplicationEventReviewGenerateDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.dto.GenerateApplicationEventReviewAiDTO;
import com.codecoachai.resume.feign.vo.GenerateApplicationEventReviewAiVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.service.JobApplicationEventReviewService;
import com.codecoachai.resume.service.support.JobApplicationEventReviewJsonCodec;
import com.codecoachai.resume.service.support.JobApplicationEventReviewPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobApplicationEventReviewServiceImpl implements JobApplicationEventReviewService {

    private static final int MAX_CAS_ATTEMPTS = 3;
    private static final int MAX_REVIEW_JSON_BYTES = 48 * 1024;
    private static final int MAX_CLAIM_JSON_BYTES = 46 * 1024;

    private final JobApplicationMapper applicationMapper;
    private final JobApplicationEventMapper eventMapper;
    private final AiFeignClient aiFeignClient;
    private final JobApplicationEventReviewJsonCodec jsonCodec;
    private final JobApplicationEventReviewPolicy policy;

    @Override
    public JobApplicationEventStructuredReviewVO generate(
            Long applicationId,
            Long eventId,
            JobApplicationEventReviewGenerateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationEventReviewGenerateDTO actual =
                request == null ? new JobApplicationEventReviewGenerateDTO() : request;
        validateRequest(actual);

        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            Claim claim = tryClaim(userId, applicationId, eventId, actual);
            if (claim == null) {
                continue;
            }
            if (!claim.claimed()) {
                return claim.review();
            }
            return generateAndFinalize(claim);
        }
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "投递事件复盘正在更新，请稍后重试");
    }

    private Claim tryClaim(
            Long userId,
            Long applicationId,
            Long eventId,
            JobApplicationEventReviewGenerateDTO request) {
        JobApplication application = ownedApplication(userId, applicationId);
        JobApplicationEvent event = ownedEvent(userId, applicationId, eventId);
        String scenario = policy.requireScenario(event.getEventType());
        Map<String, Object> root = jsonCodec.readRoot(event.getReviewJson());
        JobApplicationEventStructuredReviewVO existing =
                jsonCodec.readStructuredReview(root);
        JobApplicationEventStructuredReviewVO.UserInputVO input =
                policy.mergeUserInput(existing, request);
        String eventScope = policy.resolveEventScope(scenario, root);
        var systemFacts = policy.buildSystemFacts(application, event, root, eventScope);
        GenerateApplicationEventReviewAiDTO aiRequest = policy.toAiRequest(
                userId,
                application,
                event,
                scenario,
                eventScope,
                input,
                systemFacts,
                root);
        String fingerprint = policy.inputFingerprint(aiRequest);
        LocalDateTime now = LocalDateTime.now();
        boolean force = Boolean.TRUE.equals(request.getForce());
        if (policy.shouldReuse(existing, fingerprint, force, now)) {
            return new Claim(false, application, event, null, existing, null);
        }

        String requestId = nextRequestId(request.getRequestId(), existing, force);
        JobApplicationEventStructuredReviewVO claimed = policy.claim(
                existing, scenario, eventScope, input, systemFacts, fingerprint, requestId, now);
        String claimedJson = jsonCodec.mergeStructuredReview(event.getReviewJson(), claimed);
        if (jsonCodec.utf8Size(claimedJson) > MAX_CLAIM_JSON_BYTES) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "原事件复盘数据过大，请精简历史复盘内容后再生成");
        }
        int affectedRows = eventMapper.compareAndSetReviewJson(
                eventId, userId, applicationId, event.getReviewJson(), claimedJson);
        if (affectedRows != 1) {
            JobApplicationEvent winner = ownedEvent(userId, applicationId, eventId);
            JobApplicationEventStructuredReviewVO latest =
                    jsonCodec.readStructuredReview(winner.getReviewJson());
            if (latest != null && policy.shouldReuse(latest, fingerprint, false, now)) {
                return new Claim(false, application, winner, null, latest, null);
            }
            return null;
        }
        event.setReviewJson(claimedJson);
        return new Claim(true, application, event, claimedJson, claimed, aiRequest);
    }

    private JobApplicationEventStructuredReviewVO generateAndFinalize(Claim claim) {
        GenerateApplicationEventReviewAiVO aiResponse = null;
        String fallbackReason = null;
        try {
            aiResponse = FeignResultUtils.unwrap(
                    aiFeignClient.generateApplicationEventReview(claim.aiRequest()));
            if (aiResponse == null) {
                fallbackReason = "AI_INVALID_OUTPUT";
            } else {
                aiResponse.setRawResponse(null);
            }
        } catch (RuntimeException ex) {
            fallbackReason = fallbackReason(ex);
            log.warn("Application event review AI generation failed, eventId={}, fallbackReason={}",
                    claim.event().getId(), fallbackReason, ex);
        }

        JobApplicationEventStructuredReviewVO completed =
                policy.complete(claim.review(), aiResponse, fallbackReason, LocalDateTime.now());
        String completedJson = fitCompletedReview(claim.claimedJson(), completed);
        int affectedRows = eventMapper.compareAndSetReviewJson(
                claim.event().getId(),
                claim.event().getUserId(),
                claim.event().getApplicationId(),
                claim.claimedJson(),
                completedJson);
        if (affectedRows == 1) {
            return completed;
        }

        JobApplicationEvent winner = ownedEvent(
                claim.event().getUserId(),
                claim.event().getApplicationId(),
                claim.event().getId());
        JobApplicationEventStructuredReviewVO latest =
                jsonCodec.readStructuredReview(winner.getReviewJson());
        if (latest != null) {
            return latest;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "投递事件复盘结果已被更新，请刷新后重试");
    }

    private JobApplication ownedApplication(Long userId, Long applicationId) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递 ID 不能为空");
        }
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null
                || !Objects.equals(userId, application.getUserId())
                || Objects.equals(application.getDeleted(), CommonConstants.YES)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递不存在或无权访问");
        }
        return application;
    }

    private JobApplicationEvent ownedEvent(Long userId, Long applicationId, Long eventId) {
        if (eventId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递事件 ID 不能为空");
        }
        JobApplicationEvent event = eventMapper.selectById(eventId);
        if (event == null
                || !Objects.equals(userId, event.getUserId())
                || !Objects.equals(applicationId, event.getApplicationId())
                || Objects.equals(event.getDeleted(), CommonConstants.YES)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递事件不存在、无权访问或不属于当前投递");
        }
        return event;
    }

    private void validateRequest(JobApplicationEventReviewGenerateDTO request) {
        if (request.getObservedFacts() != null && request.getObservedFacts().size() > 10) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户观察最多填写 10 条");
        }
        if (request.getObservedFacts() != null) {
            request.getObservedFacts().forEach(value -> validateLength(value, 300, "用户观察"));
        }
        validateLength(request.getExternalFeedback(), 2000, "外部反馈");
        validateLength(request.getSelfReflection(), 2000, "自我反思");
        validateLength(request.getRequestId(), 64, "请求 ID");
        if (StringUtils.hasText(request.getRequestId())
                && !request.getRequestId().trim().matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求 ID 格式不合法");
        }
    }

    private void validateLength(String value, int limit, String field) {
        if (value != null && value.length() > limit) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    field + " 长度不能超过 " + limit + " 个字符");
        }
    }

    private String fitCompletedReview(
            String claimedJson,
            JobApplicationEventStructuredReviewVO completed) {
        String json = jsonCodec.mergeStructuredReview(claimedJson, completed);
        if (jsonCodec.utf8Size(json) <= MAX_REVIEW_JSON_BYTES) {
            return json;
        }

        JobApplicationEventStructuredReviewVO.ReviewAnalysisVO analysis = completed.getAnalysis();
        if (analysis != null) {
            analysis.setLimits(mutable(analysis.getLimits()));
            analysis.setSignals(mutable(analysis.getSignals()));
            analysis.setAdjustments(mutable(analysis.getAdjustments()));
            analysis.setNextActions(mutable(analysis.getNextActions()));
            while (jsonCodec.utf8Size(json) > MAX_REVIEW_JSON_BYTES
                    && analysis.getNextActions().size() > 1) {
                analysis.getNextActions().remove(analysis.getNextActions().size() - 1);
                json = jsonCodec.mergeStructuredReview(claimedJson, completed);
            }
            while (jsonCodec.utf8Size(json) > MAX_REVIEW_JSON_BYTES
                    && analysis.getAdjustments().size() > 1) {
                analysis.getAdjustments().remove(analysis.getAdjustments().size() - 1);
                json = jsonCodec.mergeStructuredReview(claimedJson, completed);
            }
            while (jsonCodec.utf8Size(json) > MAX_REVIEW_JSON_BYTES
                    && !analysis.getSignals().isEmpty()) {
                analysis.getSignals().remove(analysis.getSignals().size() - 1);
                json = jsonCodec.mergeStructuredReview(claimedJson, completed);
            }
            while (jsonCodec.utf8Size(json) > MAX_REVIEW_JSON_BYTES
                    && analysis.getLimits().size() > 1) {
                analysis.getLimits().remove(analysis.getLimits().size() - 1);
                json = jsonCodec.mergeStructuredReview(claimedJson, completed);
            }
            if (jsonCodec.utf8Size(json) > MAX_REVIEW_JSON_BYTES) {
                analysis.setSummary("复盘分析已按存储上限裁剪。");
                analysis.setLimits(new ArrayList<>(List.of(
                        "为保留用户原始事实和事件顶层证据，系统只裁剪了 AI 分析内容。")));
                analysis.setSignals(new ArrayList<>());
                analysis.setAdjustments(new ArrayList<>());
                analysis.setNextActions(new ArrayList<>(List.of(
                        "请精简历史复盘内容后重新生成。")));
                json = jsonCodec.mergeStructuredReview(claimedJson, completed);
            }
        }
        if (jsonCodec.utf8Size(json) > MAX_REVIEW_JSON_BYTES) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "复盘数据超过 48KB，请精简历史复盘内容后重试");
        }
        return json;
    }

    private <T> ArrayList<T> mutable(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private String nextRequestId(
            String requested,
            JobApplicationEventStructuredReviewVO existing,
            boolean force) {
        String candidate = StringUtils.hasText(requested) ? requested.trim() : UUID.randomUUID().toString();
        String previous = existing == null || existing.getGeneration() == null
                ? null : existing.getGeneration().getRequestId();
        if (force && Objects.equals(candidate, previous)) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    private String fallbackReason(RuntimeException ex) {
        String message = (ex.getClass().getSimpleName() + " " + ex.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return "AI_TIMEOUT";
        }
        if (message.contains("parse") || message.contains("json")) {
            return "AI_PARSE_ERROR";
        }
        if (message.contains("provider") || message.contains("model")) {
            return "AI_PROVIDER_ERROR";
        }
        return "AI_FEIGN_ERROR";
    }

    private record Claim(
            boolean claimed,
            JobApplication application,
            JobApplicationEvent event,
            String claimedJson,
            JobApplicationEventStructuredReviewVO review,
            GenerateApplicationEventReviewAiDTO aiRequest) {
    }
}
