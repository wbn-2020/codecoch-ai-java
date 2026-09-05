package com.codecoachai.resume.experimentv2;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.experimentv2.ExperimentV2Models.AssignmentCreate;
import com.codecoachai.resume.experimentv2.entity.ExperimentHypothesis;
import com.codecoachai.resume.experimentv2.entity.ExperimentVariant;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentHypothesisMapper;
import com.codecoachai.resume.mapper.experimentv2.ExperimentVariantMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentV2ApplicationAutoAssignmentService {

    private static final int MAX_CANDIDATE_HYPOTHESES = 200;
    private static final List<String> AUTO_ASSIGNABLE_STATUSES = List.of("DRAFT", "RUNNING");

    private final ExperimentHypothesisMapper hypothesisMapper;
    private final ExperimentVariantMapper variantMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ExperimentV2Service experimentV2Service;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void autoAssign(JobApplication application) {
        if (application == null || application.getId() == null || application.getUserId() == null) {
            return;
        }
        Long userId = SecurityAssert.requireLoginUserId();
        if (!Objects.equals(userId, application.getUserId())) {
            log.warn("Skip experiment auto-assignment for cross-user application: applicationId={}, ownerId={}, userId={}",
                    application.getId(), application.getUserId(), userId);
            return;
        }

        Long resumeId = resolveResumeId(application.getResumeVersionId(), userId);
        if (application.getTargetJobId() == null && resumeId == null) {
            return;
        }

        List<ExperimentHypothesis> hypotheses = hypothesisMapper.selectList(
                new LambdaQueryWrapper<ExperimentHypothesis>()
                        .eq(ExperimentHypothesis::getUserId, userId)
                        .eq(ExperimentHypothesis::getDeleted, CommonConstants.NO)
                        .in(ExperimentHypothesis::getStatus, AUTO_ASSIGNABLE_STATUSES)
                        .orderByDesc(ExperimentHypothesis::getUpdatedAt)
                        .orderByDesc(ExperimentHypothesis::getId)
                        .last("limit " + MAX_CANDIDATE_HYPOTHESES));
        if (hypotheses == null || hypotheses.isEmpty()) {
            return;
        }

        Set<Long> hypothesisIds = hypotheses.stream()
                .map(ExperimentHypothesis::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (hypothesisIds.isEmpty()) {
            return;
        }
        List<ExperimentVariant> variants = variantMapper.selectList(
                new LambdaQueryWrapper<ExperimentVariant>()
                        .eq(ExperimentVariant::getUserId, userId)
                        .in(ExperimentVariant::getHypothesisId, hypothesisIds)
                        .eq(ExperimentVariant::getDeleted, CommonConstants.NO));
        if (variants == null || variants.isEmpty()) {
            return;
        }

        Set<Long> matchedHypothesisIds = variants.stream()
                .filter(variant -> treatmentMatches(variant, application.getTargetJobId(), resumeId))
                .map(ExperimentVariant::getHypothesisId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        hypotheses.stream()
                .filter(hypothesis -> matchedHypothesisIds.contains(hypothesis.getId()))
                .sorted(Comparator
                        .comparing(ExperimentHypothesis::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExperimentHypothesis::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .ifPresent(hypothesis -> assign(hypothesis.getId(), application));
    }

    private Long resolveResumeId(Long resumeVersionId, Long userId) {
        if (resumeVersionId == null) {
            return null;
        }
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, resumeVersionId)
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        return version == null ? null : version.getResumeId();
    }

    private boolean treatmentMatches(ExperimentVariant variant, Long targetJobId, Long resumeId) {
        if (variant == null || variant.getTreatmentJson() == null || variant.getTreatmentJson().isBlank()) {
            return false;
        }
        try {
            JsonNode treatment = objectMapper.readTree(variant.getTreatmentJson());
            return containsExactId(treatment, "targetJobIds", targetJobId)
                    || containsExactId(treatment, "resumeIds", resumeId);
        } catch (Exception exception) {
            log.warn("Ignore invalid experiment treatment JSON during auto-assignment: variantId={}",
                    variant.getId(), exception);
            return false;
        }
    }

    private boolean containsExactId(JsonNode treatment, String field, Long expectedId) {
        if (treatment == null || expectedId == null) {
            return false;
        }
        JsonNode ids = treatment.get(field);
        if (ids == null || !ids.isArray()) {
            return false;
        }
        for (JsonNode id : ids) {
            if (id != null && id.isIntegralNumber() && id.canConvertToLong()
                    && id.longValue() == expectedId) {
                return true;
            }
        }
        return false;
    }

    private void assign(Long hypothesisId, JobApplication application) {
        AssignmentCreate request = new AssignmentCreate();
        request.setApplicationId(application.getId());
        request.setJobFamily(application.getJobTitle());
        request.setChannel(application.getSource());
        experimentV2Service.assign(hypothesisId, request);
    }
}
