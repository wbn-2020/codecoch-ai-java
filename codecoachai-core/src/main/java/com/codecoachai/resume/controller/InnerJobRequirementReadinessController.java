package com.codecoachai.resume.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.domain.vo.InnerJobRequirementReadinessContextVO;
import com.codecoachai.resume.domain.vo.InnerJobRequirementReadinessContextVO.RequirementContextItemVO;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/job-requirements")
public class InnerJobRequirementReadinessController {

    private static final int MIN_REQUIREMENT_SAMPLE = 2;

    private final JobRequirementService jobRequirementService;
    private final JobReadinessService jobReadinessService;
    private final ObjectMapper objectMapper;

    @GetMapping("/users/{userId}/targets/{targetJobId}/agent-context")
    public Result<InnerJobRequirementReadinessContextVO> agentContext(@PathVariable Long userId,
                                                                      @PathVariable Long targetJobId) {
        JobRequirementMatrixVO matrix = jobRequirementService.getMatrixForUser(userId, targetJobId);
        JobReadinessSnapshotVO snapshot = jobReadinessService.latestForUser(userId, targetJobId);
        return Result.success(toContext(targetJobId, matrix, snapshot));
    }

    private InnerJobRequirementReadinessContextVO toContext(Long targetJobId,
                                                             JobRequirementMatrixVO matrix,
                                                             JobReadinessSnapshotVO snapshot) {
        InnerJobRequirementReadinessContextVO context = new InnerJobRequirementReadinessContextVO();
        context.setTargetJobId(targetJobId);
        context.setJdAnalysisId(matrix == null ? null : matrix.getJdAnalysisId());
        context.setRequirementCount(matrix == null ? 0 : matrix.getRequirementCount());
        context.setSampleSufficient(requirementCount(matrix) >= MIN_REQUIREMENT_SAMPLE);
        if (snapshot != null) {
            context.setSnapshotId(snapshot.getId());
            context.setSnapshotHash(snapshot.getSnapshotHash());
            context.setPolicyVersion(snapshot.getPolicyVersion());
            context.setGeneratedAt(snapshot.getGeneratedAt());
            context.setReadinessScore(snapshot.getReadinessScore());
            context.setReadinessLevel(snapshot.getReadinessLevel());
            context.setConfidenceLevel(snapshot.getConfidenceLevel());
        }

        Set<String> warnings = new LinkedHashSet<>();
        boolean matrixCurrent = snapshot != null && matrix != null
                && Objects.equals(targetJobId, snapshot.getTargetJobId())
                && Objects.equals(matrix.getJdAnalysisId(), snapshot.getJdAnalysisId())
                && snapshot.getMatrix() != null
                && snapshot.getMatrix().equals(objectMapper.valueToTree(matrix));
        context.setMatrixCurrent(matrixCurrent);
        if (snapshot == null) {
            warnings.add("READINESS_SNAPSHOT_MISSING");
        } else if (!matrixCurrent) {
            warnings.add("READINESS_SNAPSHOT_STALE");
        }
        if (snapshot != null && Boolean.TRUE.equals(snapshot.getFallback())) {
            warnings.add("READINESS_SNAPSHOT_FALLBACK");
        }
        if (snapshot != null && "LOW".equalsIgnoreCase(snapshot.getConfidenceLevel())) {
            warnings.add("READINESS_LOW_CONFIDENCE");
        }
        if (!Boolean.TRUE.equals(context.getSampleSufficient())) {
            warnings.add("REQUIREMENT_SAMPLE_INSUFFICIENT");
        }
        if (matrix != null && matrix.getRequirements() != null) {
            for (JobRequirementMatrixVO.RequirementItem item : matrix.getRequirements()) {
                if (item == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(item.getRequirementFallback())) {
                    warnings.add("REQUIREMENT_FALLBACK");
                }
                if ("LOW".equalsIgnoreCase(item.getRequirementConfidence())) {
                    warnings.add("REQUIREMENT_LOW_CONFIDENCE");
                }
                if (!trustedStrong(item)) {
                    context.getMissingRequirements().add(toRequirement(item));
                }
            }
        }
        context.setWarnings(new ArrayList<>(warnings));
        context.setFallback(snapshot == null
                || Boolean.TRUE.equals(snapshot.getFallback())
                || !matrixCurrent
                || !Boolean.TRUE.equals(context.getSampleSufficient())
                || warnings.contains("REQUIREMENT_FALLBACK")
                || warnings.contains("REQUIREMENT_LOW_CONFIDENCE"));
        return context;
    }

    private RequirementContextItemVO toRequirement(JobRequirementMatrixVO.RequirementItem item) {
        RequirementContextItemVO result = new RequirementContextItemVO();
        result.setRequirementId(item.getRequirementId());
        result.setRequirementKey(item.getRequirementKey());
        result.setRequirementType(item.getRequirementType());
        result.setRequirementName(item.getRequirementName());
        result.setPriority(item.getPriority());
        result.setCoverageLevel(item.getCoverageLevel());
        result.setConfidenceLevel(item.getRequirementConfidence());
        result.setFallback(item.getRequirementFallback());
        result.setProjectEvidenceIds(item.getEvidences() == null ? List.of() : item.getEvidences().stream()
                .filter(Objects::nonNull)
                .map(JobRequirementMatrixVO.EvidenceItem::getProjectEvidenceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return result;
    }

    private boolean trustedStrong(JobRequirementMatrixVO.RequirementItem item) {
        return item != null
                && "STRONG".equalsIgnoreCase(item.getCoverageLevel())
                && !Boolean.TRUE.equals(item.getRequirementFallback())
                && !"LOW".equalsIgnoreCase(item.getRequirementConfidence())
                && item.getEvidences() != null
                && item.getEvidences().stream().anyMatch(this::trustedEvidence);
    }

    private boolean trustedEvidence(JobRequirementMatrixVO.EvidenceItem evidence) {
        return evidence != null
                && "STRONG".equalsIgnoreCase(evidence.getCoverageLevel())
                && Boolean.TRUE.equals(evidence.getConfirmed())
                && !Boolean.TRUE.equals(evidence.getFallback())
                && !"LOW".equalsIgnoreCase(evidence.getConfidenceLevel());
    }

    private int requirementCount(JobRequirementMatrixVO matrix) {
        if (matrix == null) {
            return 0;
        }
        if (matrix.getRequirementCount() != null) {
            return matrix.getRequirementCount();
        }
        return matrix.getRequirements() == null ? 0 : matrix.getRequirements().size();
    }
}
