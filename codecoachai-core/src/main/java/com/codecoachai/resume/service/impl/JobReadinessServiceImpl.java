package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobReadinessServiceImpl implements JobReadinessService {

    static final String POLICY_VERSION = "five-dimension-readiness-v2";
    private static final String COVERAGE_STRONG = "STRONG";
    private static final String COVERAGE_WEAK = "WEAK";
    private static final String PRIORITY_MUST = "MUST";

    private final TargetJobMapper targetJobMapper;
    private final JobReadinessSnapshotMapper jobReadinessSnapshotMapper;
    private final JobRequirementService jobRequirementService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobReadinessSnapshotVO createSnapshot(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJob(targetJobId, userId);
        JobRequirementMatrixVO matrix = jobRequirementService.refreshMatrix(targetJobId);
        List<JobReadinessSnapshotVO.DimensionScore> dimensions = dimensions(matrix);
        SnapshotScore score = score(matrix, dimensions);
        String matrixJson = writeJson(matrix);
        ObjectNode summary = summary(matrix, score, dimensions);
        String summaryJson = writeJson(summary);
        String dimensionJson = writeJson(dimensions);
        String snapshotHash = sha256(POLICY_VERSION + "|" + matrixJson);

        JobReadinessSnapshot existing = jobReadinessSnapshotMapper.selectOne(
                new LambdaQueryWrapper<JobReadinessSnapshot>()
                        .eq(JobReadinessSnapshot::getUserId, userId)
                        .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                        .eq(JobReadinessSnapshot::getSnapshotHash, snapshotHash)
                        .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (existing != null) {
            return toVO(existing);
        }

        JobReadinessSnapshot snapshot = new JobReadinessSnapshot();
        snapshot.setUserId(userId);
        snapshot.setTargetJobId(targetJobId);
        snapshot.setJdAnalysisId(matrix.getJdAnalysisId());
        snapshot.setSnapshotHash(snapshotHash);
        snapshot.setPolicyVersion(POLICY_VERSION);
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
            JobReadinessSnapshot concurrent = jobReadinessSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<JobReadinessSnapshot>()
                            .eq(JobReadinessSnapshot::getUserId, userId)
                            .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                            .eq(JobReadinessSnapshot::getSnapshotHash, snapshotHash)
                            .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                            .last("limit 1"));
            if (concurrent == null) {
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
        JobReadinessSnapshot snapshot = jobReadinessSnapshotMapper.selectOne(
                new LambdaQueryWrapper<JobReadinessSnapshot>()
                        .eq(JobReadinessSnapshot::getUserId, userId)
                        .eq(JobReadinessSnapshot::getTargetJobId, targetJobId)
                        .eq(JobReadinessSnapshot::getDeleted, CommonConstants.NO)
                        .orderByDesc(JobReadinessSnapshot::getGeneratedAt)
                        .orderByDesc(JobReadinessSnapshot::getId)
                        .last("limit 1"));
        return snapshot == null ? null : toVO(snapshot);
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
        return toVO(snapshot);
    }

    @Override
    public PageResult<JobReadinessSnapshotVO> page(Long targetJobId, Long pageNo, Long pageSize) {
        Long userId = SecurityAssert.requireLoginUserId();
        long currentPage = requirePageNo(pageNo);
        long currentPageSize = requirePageSize(pageSize);
        getOwnedTargetJob(targetJobId, userId);
        Page<JobReadinessSnapshot> page = jobReadinessSnapshotMapper.selectPage(
                new Page<>(currentPage, currentPageSize),
                readinessHistoryQuery(targetJobId, userId));
        List<JobReadinessSnapshotVO> records = page.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(records, page.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public List<JobReadinessSnapshotVO> list(Long targetJobId, JobReadinessQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJob(targetJobId, userId);
        int limit = sanitizeLimit(query == null ? null : query.getLimit());
        return jobReadinessSnapshotMapper.selectList(
                        readinessHistoryQuery(targetJobId, userId).last("limit " + limit))
                .stream()
                .map(this::toVO)
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
        JobReadinessSnapshotVO vo = new JobReadinessSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setTargetJobId(snapshot.getTargetJobId());
        vo.setJdAnalysisId(snapshot.getJdAnalysisId());
        vo.setSnapshotHash(snapshot.getSnapshotHash());
        vo.setPolicyVersion(snapshot.getPolicyVersion());
        vo.setReadinessScore(snapshot.getReadinessScore());
        vo.setReadinessLevel(snapshot.getReadinessLevel());
        vo.setConfidenceLevel(snapshot.getConfidenceLevel());
        vo.setFallback(CommonConstants.YES.equals(snapshot.getFallback()));
        vo.setRequirementCount(snapshot.getRequirementCount());
        vo.setStrongCount(snapshot.getStrongCount());
        vo.setWeakCount(snapshot.getWeakCount());
        vo.setMissingCount(snapshot.getMissingCount());
        vo.setMustRequirementCount(snapshot.getMustRequirementCount());
        vo.setMustMissingCount(snapshot.getMustMissingCount());
        JsonNode summary = readJson(snapshot.getSummaryJson());
        List<JobReadinessSnapshotVO.DimensionScore> dimensions = readDimensions(snapshot.getDimensionJson());
        dimensions.forEach(dimension -> {
            if (dimension.getSampleInsufficient() == null) {
                dimension.setSampleInsufficient(value(dimension.getSampleCount()) == 0);
            }
        });
        vo.setSampleInsufficient((summary != null && summary.path("sampleInsufficient").asBoolean(false))
                || snapshot.getRequirementCount() == null
                || snapshot.getRequirementCount() < 2
                || dimensions.stream().anyMatch(dimension -> value(dimension.getSampleCount()) == 0));
        vo.setSummary(summary);
        vo.setMatrix(readJson(snapshot.getMatrixJson()));
        vo.setDimensions(dimensions);
        vo.setGeneratedAt(snapshot.getGeneratedAt());
        vo.setCreatedAt(snapshot.getCreatedAt());
        return vo;
    }

    private JsonNode readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "stored readiness snapshot JSON is invalid");
        }
    }

    private List<JobReadinessSnapshotVO.DimensionScore> readDimensions(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(JobReadinessSnapshotVO.DimensionScore.class).readValue(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "stored readiness dimension JSON is invalid");
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
