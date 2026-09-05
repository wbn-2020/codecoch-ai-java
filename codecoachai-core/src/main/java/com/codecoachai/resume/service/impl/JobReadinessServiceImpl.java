package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.JobReadinessQueryDTO;
import com.codecoachai.resume.domain.entity.JobReadinessSnapshot;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.mapper.JobReadinessSnapshotMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import com.codecoachai.resume.service.support.ReadinessDimensionCodec;
import com.codecoachai.resume.service.support.ReadinessDimensionCodec.DecodeResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobReadinessServiceImpl implements JobReadinessService {

    static final String POLICY_VERSION = "five-dimension-readiness-v3";
    private static final int LATEST_CANDIDATE_LIMIT = 50;
    private static final String COVERAGE_STRONG = "STRONG";
    private static final String COVERAGE_WEAK = "WEAK";
    private static final String PRIORITY_MUST = "MUST";
    private static final Pattern REPAIR_BATCH_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private final TargetJobMapper targetJobMapper;
    private final JobReadinessSnapshotMapper jobReadinessSnapshotMapper;
    private final JobRequirementService jobRequirementService;
    private final ObjectMapper objectMapper;
    private final ReadinessDimensionCodec dimensionCodec;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobReadinessSnapshotVO createSnapshot(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return createSnapshotForUser(userId, targetJobId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobReadinessSnapshotVO regenerateForRepair(Long userId, Long targetJobId, String repairBatchId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "user is required");
        }
        if (!StringUtils.hasText(repairBatchId)
                || !REPAIR_BATCH_ID_PATTERN.matcher(repairBatchId.trim()).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "repairBatchId must be 8-64 letters, digits, dot, underscore, colon, or dash");
        }
        return createSnapshotForUser(userId, targetJobId, repairBatchId.trim());
    }

    private JobReadinessSnapshotVO createSnapshotForUser(Long userId, Long targetJobId) {
        return createSnapshotForUser(userId, targetJobId, null);
    }

    private JobReadinessSnapshotVO createSnapshotForUser(Long userId,
                                                          Long targetJobId,
                                                          String requestedRepairBatchId) {
        getOwnedTargetJob(targetJobId, userId);
        JobRequirementMatrixVO matrix = jobRequirementService.refreshMatrix(targetJobId);
        List<JobReadinessSnapshotVO.DimensionScore> dimensions = dimensions(matrix);
        SnapshotScore score = score(matrix, dimensions);
        String matrixJson = writeJson(matrix);
        ObjectNode summary = summary(matrix, score, dimensions);
        String summaryJson = writeJson(summary);
        String dimensionJson = dimensionCodec.encode(dimensions);
        String sourceHash = sha256(matrixJson);
        String snapshotHash = sha256(POLICY_VERSION + "|"
                + ReadinessDimensionCodec.SCHEMA_VERSION + "|" + sourceHash);

        JobReadinessSnapshot existing = findSnapshotByHash(userId, targetJobId, snapshotHash);
        if (validDimensions(existing)) {
            return toVO(existing);
        }
        String repairBatchId = null;
        if (existing != null) {
            repairBatchId = StringUtils.hasText(requestedRepairBatchId)
                    ? requestedRepairBatchId
                    : "auto-readiness-" + existing.getId();
            snapshotHash = sha256(snapshotHash + "|repair|" + existing.getId());
            JobReadinessSnapshot repaired = findSnapshotByHash(userId, targetJobId, snapshotHash);
            if (validDimensions(repaired)) {
                return toVO(repaired);
            }
        }

        JobReadinessSnapshot snapshot = new JobReadinessSnapshot();
        snapshot.setUserId(userId);
        snapshot.setTargetJobId(targetJobId);
        snapshot.setJdAnalysisId(matrix.getJdAnalysisId());
        snapshot.setSnapshotHash(snapshotHash);
        snapshot.setSourceHash(sourceHash);
        snapshot.setPolicyVersion(POLICY_VERSION);
        snapshot.setSchemaVersion(ReadinessDimensionCodec.SCHEMA_VERSION);
        snapshot.setValidationStatus("VALID");
        snapshot.setRepairBatchId(repairBatchId);
        snapshot.setReadinessScore(score.readinessScore());
        snapshot.setReadinessLevel(score.readinessLevel());
        snapshot.setConfidenceLevel(score.confidenceLevel());
        snapshot.setFallback(score.fallback() ? CommonConstants.YES : CommonConstants.NO);
        snapshot.setRequirementCount(matrix.getRequirementCount());
        snapshot.setStrongCount(matrix.getStrongCount());
        snapshot.setWeakCount(matrix.getWeakCount());
        snapshot.setMissingCount(matrix.getMissingCount());
        snapshot.setMustRequirementCount(score.mustRequirementCount());
        snapshot.setMustMissingCount(score.mustMissingCount());
        snapshot.setSummaryJson(summaryJson);
        snapshot.setMatrixJson(matrixJson);
        snapshot.setDimensionJson(dimensionJson);
        snapshot.setGeneratedAt(LocalDateTime.now());
        try {
            jobReadinessSnapshotMapper.insert(snapshot);
            return toVO(snapshot);
        } catch (DuplicateKeyException ex) {
            JobReadinessSnapshot concurrent = findSnapshotByHash(userId, targetJobId, snapshotHash);
            if (!validDimensions(concurrent)) {
                throw ex;
            }
            return toVO(concurrent);
        }
    }

    @Override
    public JobReadinessSnapshotVO latest(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return latestForUser(userId, targetJobId);
    }

    @Override
    public JobReadinessSnapshotVO latestForUser(Long userId, Long targetJobId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "user is required");
        }
        getOwnedTargetJob(targetJobId, userId);
        List<JobReadinessSnapshot> candidates = jobReadinessSnapshotMapper.selectList(
                readinessHistoryQuery(targetJobId, userId).last("limit " + LATEST_CANDIDATE_LIMIT));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        JobReadinessSnapshot invalidLatest = null;
        for (JobReadinessSnapshot candidate : candidates) {
            SnapshotValidation validation = validateSnapshot(candidate);
            if (validation.valid()) {
                JobReadinessSnapshotVO vo = toVO(candidate, validation.dimensionResult());
                if (invalidLatest != null) {
                    vo.setHistoryFallback(true);
                    vo.setInvalidLatestSnapshotId(invalidLatest.getId());
                    vo.getWarnings().add("READINESS_LATEST_SNAPSHOT_INVALID");
                    meterRegistry.counter("readiness_snapshot_fallback_to_legacy_total").increment();
                }
                return vo;
            }
            if (invalidLatest == null) {
                invalidLatest = candidate;
            }
            recordInvalidSnapshot(validation);
        }
        try {
            JobReadinessSnapshotVO regenerated = createSnapshotForUser(userId, targetJobId);
            regenerated.setRegenerated(true);
            regenerated.setInvalidLatestSnapshotId(invalidLatest == null ? null : invalidLatest.getId());
            regenerated.getWarnings().add("READINESS_SNAPSHOT_REGENERATED");
            meterRegistry.counter("readiness_snapshot_regenerated_total").increment();
            return regenerated;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR,
                    "readiness history is invalid and could not be regenerated; retry after refreshing job evidence");
        }
    }

    @Override
    public JobReadinessSnapshotVO getSnapshot(Long targetJobId, Long snapshotId) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJobForRead(targetJobId, userId);
        if (snapshotId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "snapshot id is required");
        }
        JobReadinessSnapshot snapshot = jobReadinessSnapshotMapper.selectOne(
                new LambdaQueryWrapper<JobReadinessSnapshot>()
                        .eq(JobReadinessSnapshot::getId, snapshotId)
                        .eq(JobReadinessSnapshot::getUserId, userId)
                        .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                        .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "readiness snapshot is unavailable");
        }
        SnapshotValidation validation = validateSnapshot(snapshot);
        if (!validation.valid()) {
            recordInvalidSnapshot(validation);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "readiness snapshot is unavailable");
        }
        return toVO(snapshot, validation.dimensionResult());
    }

    @Override
    public PageResult<JobReadinessSnapshotVO> page(Long targetJobId, Long pageNo, Long pageSize) {
        Long userId = SecurityAssert.requireLoginUserId();
        long currentPage = requirePageNo(pageNo);
        long currentPageSize = requirePageSize(pageSize);
        getOwnedTargetJob(targetJobId, userId);
        List<JobReadinessSnapshotVO> validRecords = validSnapshotVOs(
                jobReadinessSnapshotMapper.selectList(readinessHistoryQuery(targetJobId, userId)));
        long offset = (currentPage - 1) * currentPageSize;
        if (offset >= validRecords.size()) {
            return PageResult.of(List.of(), validRecords.size(), currentPage, currentPageSize);
        }
        int fromIndex = Math.toIntExact(offset);
        int toIndex = (int) Math.min((long) validRecords.size(), offset + currentPageSize);
        return PageResult.of(validRecords.subList(fromIndex, toIndex),
                validRecords.size(), currentPage, currentPageSize);
    }

    @Override
    public List<JobReadinessSnapshotVO> list(Long targetJobId, JobReadinessQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJob(targetJobId, userId);
        int limit = sanitizeLimit(query == null ? null : query.getLimit());
        return validSnapshotVOs(jobReadinessSnapshotMapper.selectList(
                        readinessHistoryQuery(targetJobId, userId)))
                .stream()
                .limit(limit)
                .toList();
    }

    private LambdaQueryWrapper<JobReadinessSnapshot> readinessHistoryQuery(Long targetJobId, Long userId) {
        return new LambdaQueryWrapper<JobReadinessSnapshot>()
                .eq(JobReadinessSnapshot::getUserId, userId)
                .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                .orderByDesc(JobReadinessSnapshot::getGeneratedAt)
                .orderByDesc(JobReadinessSnapshot::getId);
    }

    private JobReadinessSnapshot findSnapshotByHash(Long userId, Long targetJobId, String snapshotHash) {
        return jobReadinessSnapshotMapper.selectOne(
                new LambdaQueryWrapper<JobReadinessSnapshot>()
                        .eq(JobReadinessSnapshot::getUserId, userId)
                        .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                        .eq(JobReadinessSnapshot::getSnapshotHash, snapshotHash)
                        .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
    }

    private boolean validDimensions(JobReadinessSnapshot snapshot) {
        return snapshot != null && validateSnapshot(snapshot).valid();
    }

    private DecodeResult decodeDimensions(JobReadinessSnapshot snapshot) {
        if (snapshot == null) {
            return dimensionCodec.decode(null, null);
        }
        return dimensionCodec.decode(snapshot.getDimensionJson(), snapshot.getSchemaVersion());
    }

    private SnapshotValidation validateSnapshot(JobReadinessSnapshot snapshot) {
        DecodeResult dimensionResult = decodeDimensions(snapshot);
        if (!dimensionResult.valid()) {
            return new SnapshotValidation(dimensionResult, "DIMENSIONS");
        }
        if (snapshot == null) {
            return new SnapshotValidation(dimensionResult, "MISSING");
        }
        if (!POLICY_VERSION.equals(snapshot.getPolicyVersion())) {
            return new SnapshotValidation(dimensionResult, "POLICY_VERSION");
        }
        if (!scoreInRange(snapshot.getReadinessScore())) {
            return new SnapshotValidation(dimensionResult, "READINESS_SCORE");
        }
        if (!isAllowed(snapshot.getReadinessLevel(), "READY", "NEAR_READY", "NEEDS_WORK")) {
            return new SnapshotValidation(dimensionResult, "READINESS_LEVEL");
        }
        if (!isAllowed(snapshot.getConfidenceLevel(), "LOW", "MEDIUM", "HIGH")) {
            return new SnapshotValidation(dimensionResult, "CONFIDENCE_LEVEL");
        }
        if (!validCounts(snapshot)) {
            return new SnapshotValidation(dimensionResult, "COUNTS");
        }
        JsonNode summary = readObject(snapshot.getSummaryJson());
        if (summary == null || !matchesSummary(summary, snapshot, dimensionResult.dimensions())) {
            return new SnapshotValidation(dimensionResult, "SUMMARY");
        }
        JsonNode matrix = readObject(snapshot.getMatrixJson());
        if (matrix == null || !matchesMatrix(matrix, snapshot)) {
            return new SnapshotValidation(dimensionResult, "MATRIX");
        }
        return new SnapshotValidation(dimensionResult, null);
    }

    private boolean validCounts(JobReadinessSnapshot snapshot) {
        int requirementCount = value(snapshot.getRequirementCount());
        int strongCount = value(snapshot.getStrongCount());
        int weakCount = value(snapshot.getWeakCount());
        int missingCount = value(snapshot.getMissingCount());
        int mustRequirementCount = value(snapshot.getMustRequirementCount());
        int mustMissingCount = value(snapshot.getMustMissingCount());
        return snapshot.getRequirementCount() != null
                && snapshot.getStrongCount() != null
                && snapshot.getWeakCount() != null
                && snapshot.getMissingCount() != null
                && snapshot.getMustRequirementCount() != null
                && snapshot.getMustMissingCount() != null
                && requirementCount >= 0
                && strongCount >= 0
                && weakCount >= 0
                && missingCount >= 0
                && strongCount + weakCount + missingCount <= requirementCount
                && mustRequirementCount >= 0
                && mustRequirementCount <= requirementCount
                && mustMissingCount >= 0
                && mustMissingCount <= mustRequirementCount;
    }

    private boolean matchesSummary(JsonNode summary, JobReadinessSnapshot snapshot,
                                   List<JobReadinessSnapshotVO.DimensionScore> dimensions) {
        return summary.isObject()
                && sameInt(summary, "readinessScore", snapshot.getReadinessScore())
                && sameText(summary, "readinessLevel", snapshot.getReadinessLevel())
                && sameText(summary, "confidenceLevel", snapshot.getConfidenceLevel())
                && sameBoolean(summary, "fallback", CommonConstants.YES.equals(snapshot.getFallback()))
                && sameInt(summary, "requirementCount", snapshot.getRequirementCount())
                && sameInt(summary, "strongCount", snapshot.getStrongCount())
                && sameInt(summary, "weakCount", snapshot.getWeakCount())
                && sameInt(summary, "missingCount", snapshot.getMissingCount())
                && sameInt(summary, "mustRequirementCount", snapshot.getMustRequirementCount())
                && sameInt(summary, "mustMissingCount", snapshot.getMustMissingCount())
                && summary.path("dimensions").isArray()
                && summary.path("dimensions").size() == dimensions.size();
    }

    private boolean matchesMatrix(JsonNode matrix, JobReadinessSnapshot snapshot) {
        return matrix.isObject()
                && matrix.path("requirements").isArray()
                && matrix.path("requirements").size() == value(snapshot.getRequirementCount())
                && sameInt(matrix, "requirementCount", snapshot.getRequirementCount())
                && sameInt(matrix, "strongCount", snapshot.getStrongCount())
                && sameInt(matrix, "weakCount", snapshot.getWeakCount())
                && sameInt(matrix, "missingCount", snapshot.getMissingCount());
    }

    private JsonNode readObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean scoreInRange(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }

    private boolean isAllowed(String value, String... allowed) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean sameInt(JsonNode node, String field, Integer expected) {
        return expected != null && node.path(field).canConvertToInt()
                && node.path(field).intValue() == expected;
    }

    private boolean sameText(JsonNode node, String field, String expected) {
        return StringUtils.hasText(expected) && expected.equalsIgnoreCase(node.path(field).asText());
    }

    private boolean sameBoolean(JsonNode node, String field, boolean expected) {
        return node.has(field) && node.path(field).isBoolean() && node.path(field).asBoolean() == expected;
    }

    private void recordInvalidSnapshot(SnapshotValidation validation) {
        DecodeResult result = validation.dimensionResult();
        meterRegistry.counter(
                "readiness_snapshot_invalid_total",
                "validation_status",
                result.status().name()).increment();
        if (validation.contractIssue() != null) {
            meterRegistry.counter(
                    "readiness_snapshot_contract_invalid_total",
                    "reason",
                    validation.contractIssue()).increment();
        }
    }

    private List<JobReadinessSnapshotVO> validSnapshotVOs(List<JobReadinessSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<JobReadinessSnapshotVO> result = new ArrayList<>();
        for (JobReadinessSnapshot snapshot : snapshots) {
            SnapshotValidation validation = validateSnapshot(snapshot);
            if (validation.valid()) {
                result.add(toVO(snapshot, validation.dimensionResult()));
            } else {
                recordInvalidSnapshot(validation);
            }
        }
        return List.copyOf(result);
    }

    private SnapshotScore score(JobRequirementMatrixVO matrix,
                                List<JobReadinessSnapshotVO.DimensionScore> dimensions) {
        int mustRequirementCount = 0;
        int mustMissingCount = 0;
        boolean requirementFallback = false;
        for (JobRequirementMatrixVO.RequirementItem item : matrix.getRequirements()) {
            if (PRIORITY_MUST.equals(item.getPriority())) {
                mustRequirementCount++;
                if (!COVERAGE_STRONG.equals(item.getCoverageLevel())) {
                    mustMissingCount++;
                }
            }
            requirementFallback = requirementFallback
                    || Boolean.TRUE.equals(item.getRequirementFallback())
                    || "LOW".equalsIgnoreCase(item.getRequirementConfidence());
        }
        BigDecimal readiness = BigDecimal.ZERO;
        boolean dimensionFallback = false;
        int emptyDimensions = 0;
        for (JobReadinessSnapshotVO.DimensionScore dimension : dimensions) {
            readiness = readiness.add(BigDecimal.valueOf(value(dimension.getScore()))
                    .multiply(Dimension.valueOf(dimension.getDimension()).weight));
            dimensionFallback = dimensionFallback || Boolean.TRUE.equals(dimension.getFallback());
            if (value(dimension.getSampleCount()) == 0) {
                emptyDimensions++;
            }
        }
        int readinessScore = readiness.setScale(0, RoundingMode.HALF_UP).intValue();
        boolean fallback = requirementFallback || dimensionFallback;
        String readinessLevel;
        if (readinessScore >= 80 && mustMissingCount == 0 && !fallback && emptyDimensions == 0) {
            readinessLevel = "READY";
        } else if (readinessScore >= 60 && mustMissingCount <= 1 && emptyDimensions <= 1) {
            readinessLevel = "NEAR_READY";
        } else {
            readinessLevel = "NEEDS_WORK";
        }
        String confidenceLevel;
        if (fallback || matrix.getRequirementCount() == null || matrix.getRequirementCount() < 2
                || emptyDimensions > 0) {
            confidenceLevel = "LOW";
        } else if (dimensions.stream().allMatch(item -> "HIGH".equals(item.getConfidenceLevel()))) {
            confidenceLevel = "HIGH";
        } else {
            confidenceLevel = "MEDIUM";
        }
        boolean sampleInsufficient = matrix.getRequirementCount() == null
                || matrix.getRequirementCount() < 2
                || emptyDimensions > 0;
        return new SnapshotScore(readinessScore, readinessLevel, confidenceLevel, fallback, sampleInsufficient,
                mustRequirementCount, mustMissingCount);
    }

    private List<JobReadinessSnapshotVO.DimensionScore> dimensions(JobRequirementMatrixVO matrix) {
        Map<Dimension, List<JobRequirementMatrixVO.EvidenceItem>> grouped = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) {
            grouped.put(dimension, new ArrayList<>());
        }
        if (matrix.getRequirements() != null) {
            for (JobRequirementMatrixVO.RequirementItem requirement : matrix.getRequirements()) {
                if (requirement == null || requirement.getEvidences() == null) {
                    continue;
                }
                for (JobRequirementMatrixVO.EvidenceItem evidence : requirement.getEvidences()) {
                    Dimension dimension = dimensionOf(evidence);
                    if (dimension != null) {
                        grouped.get(dimension).add(evidence);
                    }
                }
            }
        }
        List<JobReadinessSnapshotVO.DimensionScore> result = new ArrayList<>();
        for (Dimension dimension : Dimension.values()) {
            List<JobRequirementMatrixVO.EvidenceItem> samples = grouped.get(dimension);
            int dimensionScore = samples.isEmpty() ? 0 : (int) Math.round(samples.stream()
                    .mapToInt(this::evidenceScore)
                    .average()
                    .orElse(0));
            boolean fallback = samples.isEmpty() || samples.stream().anyMatch(evidence ->
                    Boolean.TRUE.equals(evidence.getFallback())
                            || !Boolean.TRUE.equals(evidence.getConfirmed())
                            || "LOW".equalsIgnoreCase(evidence.getConfidenceLevel()));
            JobReadinessSnapshotVO.DimensionScore item = new JobReadinessSnapshotVO.DimensionScore();
            item.setDimension(dimension.name());
            item.setScore(dimensionScore);
            item.setSampleCount(samples.size());
            item.setFallback(fallback);
            item.setSampleInsufficient(samples.isEmpty());
            item.setConfidenceLevel(samples.isEmpty() || fallback
                    ? "LOW" : samples.size() >= 2 ? "HIGH" : "MEDIUM");
            result.add(item);
        }
        return result;
    }

    private Dimension dimensionOf(JobRequirementMatrixVO.EvidenceItem evidence) {
        if (evidence == null || !StringUtils.hasText(evidence.getEvidenceType())) {
            return null;
        }
        return switch (evidence.getEvidenceType().trim().toUpperCase(Locale.ROOT)) {
            case "RESUME_MATCH" -> Dimension.RESUME;
            case "PROJECT_EVIDENCE", "PROJECT_TEXT" -> Dimension.PROJECT_EVIDENCE;
            case "QUESTION_PRACTICE" -> Dimension.KNOWLEDGE;
            case "INTERVIEW_REPORT" -> Dimension.INTERVIEW;
            case "APPLICATION_RESULT" -> Dimension.APPLICATION;
            default -> null;
        };
    }

    private int evidenceScore(JobRequirementMatrixVO.EvidenceItem evidence) {
        if (evidence.getScore() != null) {
            return Math.max(0, Math.min(100, evidence.getScore()));
        }
        if (COVERAGE_STRONG.equals(evidence.getCoverageLevel())) {
            return 85;
        }
        return COVERAGE_WEAK.equals(evidence.getCoverageLevel()) ? 50 : 0;
    }

    private ObjectNode summary(JobRequirementMatrixVO matrix, SnapshotScore score,
                               List<JobReadinessSnapshotVO.DimensionScore> dimensions) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("policyVersion", POLICY_VERSION);
        summary.put("readinessScore", score.readinessScore());
        summary.put("readinessLevel", score.readinessLevel());
        summary.put("confidenceLevel", score.confidenceLevel());
        summary.put("fallback", score.fallback());
        summary.put("sampleInsufficient", score.sampleInsufficient());
        summary.put("requirementCount", value(matrix.getRequirementCount()));
        summary.put("strongCount", value(matrix.getStrongCount()));
        summary.put("weakCount", value(matrix.getWeakCount()));
        summary.put("missingCount", value(matrix.getMissingCount()));
        summary.put("mustRequirementCount", score.mustRequirementCount());
        summary.put("mustMissingCount", score.mustMissingCount());
        summary.set("dimensions", objectMapper.valueToTree(dimensions));
        summary.put("strongCoverageRule",
                "trusted requirement + exact skill/JD keyword + confirmed non-fallback evidence + medium-or-strong strength");
        summary.put("weakCoverageWeight", 0.5);
        return summary;
    }

    private TargetJob getOwnedTargetJob(Long targetJobId, Long userId) {
        return requireOwnedTargetJob(targetJobId, userId, ErrorCode.PARAM_ERROR);
    }

    private TargetJob getOwnedTargetJobForRead(Long targetJobId, Long userId) {
        return requireOwnedTargetJob(targetJobId, userId, ErrorCode.RESOURCE_NOT_FOUND);
    }

    private TargetJob requireOwnedTargetJob(Long targetJobId, Long userId, ErrorCode missingErrorCode) {
        if (targetJobId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "target job is required");
        }
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(missingErrorCode, "target job is unavailable");
        }
        return targetJob;
    }

    private JobReadinessSnapshotVO toVO(JobReadinessSnapshot snapshot) {
        return toVO(snapshot, decodeDimensions(snapshot));
    }

    private JobReadinessSnapshotVO toVO(JobReadinessSnapshot snapshot, DecodeResult dimensionResult) {
        JobReadinessSnapshotVO vo = new JobReadinessSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setTargetJobId(snapshot.getTargetJobId());
        vo.setJdAnalysisId(snapshot.getJdAnalysisId());
        vo.setSnapshotHash(snapshot.getSnapshotHash());
        vo.setSourceHash(snapshot.getSourceHash());
        vo.setPolicyVersion(snapshot.getPolicyVersion());
        vo.setSchemaVersion(dimensionResult.schemaVersion());
        vo.setValidationStatus(dimensionResult.status().name());
        vo.setRepairBatchId(snapshot.getRepairBatchId());
        vo.setReadinessScore(snapshot.getReadinessScore());
        vo.setReadinessLevel(snapshot.getReadinessLevel());
        vo.setConfidenceLevel(snapshot.getConfidenceLevel());
        vo.setFallback(CommonConstants.YES.equals(snapshot.getFallback()) || !dimensionResult.valid());
        vo.setRequirementCount(snapshot.getRequirementCount());
        vo.setStrongCount(snapshot.getStrongCount());
        vo.setWeakCount(snapshot.getWeakCount());
        vo.setMissingCount(snapshot.getMissingCount());
        vo.setMustRequirementCount(snapshot.getMustRequirementCount());
        vo.setMustMissingCount(snapshot.getMustMissingCount());
        JsonNode summary = readJson(snapshot.getSummaryJson(), vo.getWarnings(), "SUMMARY");
        List<JobReadinessSnapshotVO.DimensionScore> dimensions = dimensionResult.dimensions();
        if (!dimensionResult.valid()) {
            vo.getWarnings().add("READINESS_DIMENSIONS_" + dimensionResult.status().name());
            recordInvalidSnapshot(new SnapshotValidation(dimensionResult, "DIMENSIONS"));
        } else if (dimensionResult.status()
                == ReadinessDimensionCodec.ValidationStatus.VALID_LEGACY) {
            vo.getWarnings().add("READINESS_DIMENSIONS_LEGACY_SCHEMA");
        }
        dimensions.forEach(dimension -> {
            if (dimension.getSampleInsufficient() == null) {
                dimension.setSampleInsufficient(value(dimension.getSampleCount()) == 0);
            }
        });
        vo.setSampleInsufficient(!dimensionResult.valid()
                || (summary != null && summary.path("sampleInsufficient").asBoolean(false))
                || snapshot.getRequirementCount() == null
                || snapshot.getRequirementCount() < 2
                || dimensions.stream().anyMatch(dimension -> value(dimension.getSampleCount()) == 0));
        vo.setSummary(summary);
        vo.setMatrix(readJson(snapshot.getMatrixJson(), vo.getWarnings(), "MATRIX"));
        vo.setDimensions(dimensions);
        vo.setGeneratedAt(snapshot.getGeneratedAt());
        vo.setCreatedAt(snapshot.getCreatedAt());
        return vo;
    }

    private JsonNode readJson(String raw, List<String> warnings, String field) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            warnings.add("READINESS_" + field + "_JSON_INVALID");
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "readiness snapshot could not be serialized");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private int sanitizeLimit(Integer value) {
        if (value == null) {
            return 20;
        }
        return Math.max(1, Math.min(value, 100));
    }

    private long requirePageNo(Long pageNo) {
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNo must be at least 1");
        }
        return pageNo;
    }

    private long requirePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageSize must be between 1 and 100");
        }
        return pageSize;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private record SnapshotScore(
            int readinessScore,
            String readinessLevel,
            String confidenceLevel,
            boolean fallback,
            boolean sampleInsufficient,
            int mustRequirementCount,
            int mustMissingCount) {
    }

    private record SnapshotValidation(
            DecodeResult dimensionResult,
            String contractIssue) {

        private boolean valid() {
            return dimensionResult.valid() && contractIssue == null;
        }
    }

    private enum Dimension {
        RESUME("0.25"),
        PROJECT_EVIDENCE("0.25"),
        KNOWLEDGE("0.20"),
        INTERVIEW("0.20"),
        APPLICATION("0.10");

        private final BigDecimal weight;

        Dimension(String weight) {
            this.weight = new BigDecimal(weight);
        }
    }
}
