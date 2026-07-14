package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.interview.domain.dto.InterviewComparisonCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewComparison;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.InterviewComparisonPointVO;
import com.codecoachai.interview.domain.vo.InterviewComparisonReasonVO;
import com.codecoachai.interview.domain.vo.InterviewComparisonRoundVO;
import com.codecoachai.interview.domain.vo.InterviewComparisonVO;
import com.codecoachai.interview.domain.vo.InterviewDimensionComparisonVO;
import com.codecoachai.interview.mapper.InterviewComparisonMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.service.InterviewComparisonService;
import com.codecoachai.interview.support.InterviewReportComparabilityPolicy;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InterviewComparisonServiceImpl implements InterviewComparisonService {

    private static final String STATUS_COMPARABLE = "COMPARABLE";
    private static final String STATUS_NOT_COMPARABLE = "NOT_COMPARABLE";

    private final InterviewComparisonMapper comparisonMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;
    private final InterviewReportComparabilityPolicy comparabilityPolicy;

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = DuplicateKeyException.class)
    public InterviewComparisonVO compare(InterviewComparisonCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        String idempotencyKey = dto.getIdempotencyKey().trim();
        List<Long> canonicalRequestedIds = canonicalizeRequestedReportIds(dto.getReportIds());
        List<Long> analysisIds = canonicalRequestedIds.stream().distinct().toList();

        InterviewComparison existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            validateReplayPayload(existing, canonicalRequestedIds);
            return replay(existing);
        }

        InterviewComparisonVO result = analyze(userId, dto.getReportIds(), analysisIds);
        InterviewComparison comparison = new InterviewComparison();
        comparison.setUserId(userId);
        comparison.setTargetJobId(result.getTargetJobId());
        comparison.setReportIds(writeJson(canonicalRequestedIds));
        comparison.setReportKey(hashReportIds(canonicalRequestedIds));
        comparison.setRubricVersion(result.getRubricVersion());
        comparison.setStatus(Boolean.TRUE.equals(result.getComparable())
                ? STATUS_COMPARABLE : STATUS_NOT_COMPARABLE);
        comparison.setReasonCodes(writeJson(result.getUnavailableReasons().stream()
                .map(InterviewComparisonReasonVO::getCode)
                .toList()));
        comparison.setResultJson(writeJson(result));
        comparison.setIdempotencyKey(idempotencyKey);
        try {
            comparisonMapper.insert(comparison);
        } catch (DuplicateKeyException ex) {
            InterviewComparison concurrent = findByIdempotencyKeyForUpdate(userId, idempotencyKey);
            if (concurrent == null) {
                throw ex;
            }
            validateReplayPayload(concurrent, canonicalRequestedIds);
            return replay(concurrent);
        }
        result.setId(comparison.getId());
        result.setIdempotentReplay(false);
        return result;
    }

    @Override
    public List<InterviewComparisonVO> list(Integer limit) {
        Long userId = SecurityAssert.requireLoginUserId();
        int normalizedLimit = requireListLimit(limit);
        return comparisonMapper.selectList(new LambdaQueryWrapper<InterviewComparison>()
                        .eq(InterviewComparison::getUserId, userId)
                        .eq(InterviewComparison::getDeleted, CommonConstants.NO)
                        .orderByDesc(InterviewComparison::getCreatedAt)
                        .orderByDesc(InterviewComparison::getId)
                        .last("limit " + normalizedLimit))
                .stream()
                .map(comparison -> readSnapshot(comparison, false))
                .toList();
    }

    @Override
    public InterviewComparisonVO detail(Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "比较记录不存在或不可用");
        }
        InterviewComparison comparison = comparisonMapper.selectOne(new LambdaQueryWrapper<InterviewComparison>()
                .eq(InterviewComparison::getId, id)
                .eq(InterviewComparison::getUserId, userId)
                .eq(InterviewComparison::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (comparison == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "比较记录不存在或不可用");
        }
        return readSnapshot(comparison, false);
    }

    private InterviewComparisonVO analyze(Long userId, List<Long> requestedIds, List<Long> normalizedIds) {
        InterviewComparisonVO result = emptyResult(normalizedIds);
        List<InterviewComparisonReasonVO> reasons = result.getUnavailableReasons();
        if (requestedIds == null || requestedIds.size() < 2) {
            addReason(reasons, "REPORT_COUNT_INSUFFICIENT", "至少需要两份面试报告");
        }
        if (requestedIds != null && requestedIds.size() != normalizedIds.size()) {
            addReason(reasons, "DUPLICATE_REPORT_ID", "比较请求包含重复报告");
        }

        Map<Long, InterviewReport> reportsById = ownedReportsById(userId, normalizedIds);
        List<Long> sessionIds = reportsById.values().stream()
                .map(InterviewReport::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Map<Long, InterviewSession> sessionsById = ownedSessionsById(userId, sessionIds);

        List<ReportContext> contexts = new ArrayList<>();
        for (Long reportId : normalizedIds) {
            InterviewReport report = reportsById.get(reportId);
            if (report == null) {
                addReason(reasons, "REPORT_UNAVAILABLE", "面试报告不存在或不可用");
                continue;
            }
            InterviewSession session = sessionsById.get(report.getSessionId());
            if (session == null) {
                addReason(reasons, "REPORT_UNAVAILABLE", "面试报告不存在或不可用");
                continue;
            }
            contexts.add(new ReportContext(report, session));
        }

        List<EvaluatedReportContext> evaluatedContexts = contexts.stream()
                .map(context -> new EvaluatedReportContext(
                        context, comparabilityPolicy.evaluate(context.report(), context.session())))
                .toList();
        evaluatedContexts.stream()
                .map(EvaluatedReportContext::evaluation)
                .filter(evaluation -> !evaluation.comparable())
                .forEach(evaluation -> addReason(reasons, evaluation.reasonCode(), evaluation.message()));
        InterviewReportComparabilityPolicy.Result groupEvaluation = comparabilityPolicy.evaluateGroup(
                evaluatedContexts.stream().map(EvaluatedReportContext::evaluation).toList());
        if (!groupEvaluation.comparable()) {
            addReason(reasons, groupEvaluation.reasonCode(), groupEvaluation.message());
        } else {
            result.setTargetJobId(groupEvaluation.targetJobId());
            result.setRubricVersion(groupEvaluation.rubricVersion());
        }

        List<InterviewComparisonRoundVO> rounds = evaluatedContexts.stream()
                .sorted(Comparator
                        .comparing((EvaluatedReportContext context) -> generatedAt(context.context().report()),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(context -> createdAt(context.context().report()),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(context -> context.context().report().getId()))
                .map(this::toRound)
                .toList();
        result.setRounds(rounds);
        if (rounds.stream().anyMatch(round -> Boolean.TRUE.equals(round.getSampleInsufficient()))) {
            result.getWarnings().add(new InterviewComparisonReasonVO(
                    "SAMPLE_INSUFFICIENT_REPORT",
                    "部分报告样本不足，比较结果只能作为弱信号"));
        }

        result.setComparable(reasons.isEmpty());
        if (Boolean.TRUE.equals(result.getComparable())) {
            populateScoreComparison(result, rounds);
        }
        return result;
    }

    private Map<Long, InterviewReport> ownedReportsById(Long userId, List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InterviewReport> reports = new LinkedHashMap<>();
        reportMapper.selectList(new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getUserId, userId)
                        .in(InterviewReport::getId, reportIds)
                        .eq(InterviewReport::getDeleted, CommonConstants.NO))
                .forEach(report -> {
                    if (report != null && report.getId() != null) {
                        reports.putIfAbsent(report.getId(), report);
                    }
                });
        return reports;
    }

    private Map<Long, InterviewSession> ownedSessionsById(Long userId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, InterviewSession> sessions = new LinkedHashMap<>();
        sessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .in(InterviewSession::getId, sessionIds)
                        .eq(InterviewSession::getDeleted, CommonConstants.NO))
                .forEach(session -> {
                    if (session != null && session.getId() != null) {
                        sessions.putIfAbsent(session.getId(), session);
                    }
                });
        return sessions;
    }

    private InterviewComparisonVO emptyResult(List<Long> reportIds) {
        InterviewComparisonVO result = new InterviewComparisonVO();
        result.setComparable(false);
        result.setReportIds(reportIds);
        result.setUnavailableReasons(new ArrayList<>());
        result.setWarnings(new ArrayList<>());
        result.setRounds(List.of());
        result.setDimensions(List.of());
        result.setIdempotentReplay(false);
        return result;
    }

    private InterviewComparisonRoundVO toRound(EvaluatedReportContext evaluatedContext) {
        InterviewReport report = evaluatedContext.context().report();
        InterviewComparisonRoundVO round = new InterviewComparisonRoundVO();
        round.setReportId(report.getId());
        round.setSessionId(report.getSessionId());
        round.setTotalScore(report.getTotalScore());
        round.setGeneratedAt(report.getGeneratedAt());
        round.setSampleInsufficient(InterviewReportTrustPolicy.isSampleInsufficient(report));
        round.setTrustStatus(InterviewReportTrustPolicy.isTrustedForFormalAction(report)
                ? "VERIFIED" : InterviewReportTrustPolicy.isFallbackOrUntrusted(report) ? "FALLBACK" : "PARTIAL");
        round.setRubricScores(evaluatedContext.evaluation().normalizedDimensions());
        return round;
    }

    private void populateScoreComparison(InterviewComparisonVO result, List<InterviewComparisonRoundVO> rounds) {
        if (rounds.isEmpty()) {
            return;
        }
        InterviewComparisonRoundVO first = rounds.get(0);
        InterviewComparisonRoundVO latest = rounds.get(rounds.size() - 1);
        result.setFirstTotalScore(first.getTotalScore());
        result.setLatestTotalScore(latest.getTotalScore());
        result.setTotalScoreDelta(delta(first.getTotalScore(), latest.getTotalScore()));

        Set<String> dimensions = new LinkedHashSet<>();
        rounds.forEach(round -> dimensions.addAll(round.getRubricScores().keySet()));
        List<InterviewDimensionComparisonVO> comparisons = new ArrayList<>();
        for (String dimension : dimensions) {
            InterviewDimensionComparisonVO comparison = new InterviewDimensionComparisonVO();
            comparison.setDimension(dimension);
            List<InterviewComparisonPointVO> points = new ArrayList<>();
            BigDecimal previous = null;
            for (InterviewComparisonRoundVO round : rounds) {
                BigDecimal score = round.getRubricScores().get(dimension);
                InterviewComparisonPointVO point = new InterviewComparisonPointVO();
                point.setReportId(round.getReportId());
                point.setScore(score);
                point.setDeltaFromPrevious(previous == null || score == null ? null : score.subtract(previous));
                points.add(point);
                if (score != null) {
                    previous = score;
                }
            }
            BigDecimal firstScore = first.getRubricScores().get(dimension);
            BigDecimal latestScore = latest.getRubricScores().get(dimension);
            comparison.setFirstScore(firstScore);
            comparison.setLatestScore(latestScore);
            comparison.setDelta(decimalDelta(firstScore, latestScore));
            comparison.setPoints(points);
            comparisons.add(comparison);
        }
        result.setDimensions(comparisons);
    }

    private InterviewComparison findByIdempotencyKey(Long userId, String idempotencyKey) {
        return comparisonMapper.selectOne(new LambdaQueryWrapper<InterviewComparison>()
                .eq(InterviewComparison::getUserId, userId)
                .eq(InterviewComparison::getIdempotencyKey, idempotencyKey)
                .eq(InterviewComparison::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private InterviewComparison findByIdempotencyKeyForUpdate(Long userId, String idempotencyKey) {
        return comparisonMapper.selectOne(new LambdaQueryWrapper<InterviewComparison>()
                .eq(InterviewComparison::getUserId, userId)
                .eq(InterviewComparison::getIdempotencyKey, idempotencyKey)
                .eq(InterviewComparison::getDeleted, CommonConstants.NO)
                .last("limit 1 for update"));
    }

    private void validateReplayPayload(InterviewComparison existing, List<Long> reportIds) {
        List<Long> savedIds = readReportIds(existing.getReportIds());
        if (!savedIds.equals(reportIds)) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "幂等键已被不同的报告比较请求占用");
        }
    }

    private InterviewComparisonVO replay(InterviewComparison comparison) {
        return readSnapshot(comparison, true);
    }

    private InterviewComparisonVO readSnapshot(InterviewComparison comparison, boolean idempotentReplay) {
        try {
            InterviewComparisonVO result = objectMapper.readValue(
                    comparison.getResultJson(), InterviewComparisonVO.class);
            result.setId(comparison.getId());
            result.setIdempotentReplay(idempotentReplay);
            result.setCreatedAt(comparison.getCreatedAt());
            return result;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "历史比较结果读取失败");
        }
    }

    private int requireListLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "limit 必须在 1 到 50 之间");
        }
        return limit;
    }

    private List<Long> canonicalizeRequestedReportIds(List<Long> reportIds) {
        if (reportIds == null) {
            return List.of();
        }
        return reportIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .sorted()
                .toList();
    }

    private List<Long> readReportIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {
            });
            return canonicalizeRequestedReportIds(ids);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String hashReportIds(List<Long> reportIds) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(reportIds.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告比较键生成失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告比较结果序列化失败");
        }
    }

    private void addReason(List<InterviewComparisonReasonVO> reasons, String code, String message) {
        if (reasons.stream().noneMatch(reason -> code.equals(reason.getCode()))) {
            reasons.add(new InterviewComparisonReasonVO(code, message));
        }
    }

    private Integer delta(Integer first, Integer latest) {
        return first == null || latest == null ? null : latest - first;
    }

    private BigDecimal decimalDelta(BigDecimal first, BigDecimal latest) {
        return first == null || latest == null ? null : latest.subtract(first);
    }

    private LocalDateTime generatedAt(InterviewReport report) {
        return report == null ? null : report.getGeneratedAt();
    }

    private LocalDateTime createdAt(InterviewReport report) {
        return report == null ? null : report.getCreatedAt();
    }

    private record ReportContext(InterviewReport report, InterviewSession session) {
    }

    private record EvaluatedReportContext(
            ReportContext context,
            InterviewReportComparabilityPolicy.Result evaluation) {
    }
}
