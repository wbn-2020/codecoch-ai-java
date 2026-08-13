package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobRequirement;
import com.codecoachai.resume.domain.entity.JobRequirementEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.vo.JobRequirementMaterializationVO;
import com.codecoachai.resume.domain.vo.JobRequirementEvidenceSourceRow;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.domain.vo.JobRequirementVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobRequirementEvidenceMapper;
import com.codecoachai.resume.mapper.JobRequirementEvidenceSourceMapper;
import com.codecoachai.resume.mapper.JobRequirementMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.JobRequirementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobRequirementServiceImpl implements JobRequirementService {

    private static final String PRIORITY_MUST = "MUST";
    private static final String PRIORITY_NICE_TO_HAVE = "NICE_TO_HAVE";
    private static final String COVERAGE_STRONG = "STRONG";
    private static final String COVERAGE_WEAK = "WEAK";
    private static final String COVERAGE_MISSING = "MISSING";
    private static final Set<String> LOW_CONFIDENCE_VALUES = Set.of(
            "LOW", "UNKNOWN", "UNVERIFIED", "FALLBACK", "LOW_CONFIDENCE");

    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final JobRequirementMapper jobRequirementMapper;
    private final JobRequirementEvidenceMapper jobRequirementEvidenceMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectSkillEvidenceMapper projectSkillEvidenceMapper;
    private final JobRequirementEvidenceSourceMapper evidenceSourceMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobRequirementMaterializationVO materialize(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJob(targetJobId, userId);
        JobDescriptionAnalysis analysis = latestParsedAnalysis(targetJobId, userId);
        List<JobRequirement> parsed = parseRequirements(analysis);
        List<JobRequirement> existing = jobRequirementMapper.selectList(
                new LambdaQueryWrapper<JobRequirement>()
                        .eq(JobRequirement::getUserId, userId)
                        .eq(JobRequirement::getTargetJobId, targetJobId)
                        .eq(JobRequirement::getJdAnalysisId, analysis.getId())
                        .eq(JobRequirement::getDeleted, CommonConstants.NO));
        Map<String, JobRequirement> existingByKey = existing.stream()
                .collect(Collectors.toMap(JobRequirement::getRequirementKey, Function.identity(), (left, right) -> left));
        Set<String> parsedKeys = parsed.stream().map(JobRequirement::getRequirementKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int deactivatedCount = (int) existing.stream()
                .filter(item -> CommonConstants.YES.equals(item.getActiveFlag()))
                .filter(item -> !parsedKeys.contains(item.getRequirementKey()))
                .count();

        jobRequirementMapper.update(null, new LambdaUpdateWrapper<JobRequirement>()
                .set(JobRequirement::getActiveFlag, CommonConstants.NO)
                .eq(JobRequirement::getUserId, userId)
                .eq(JobRequirement::getTargetJobId, targetJobId)
                .eq(JobRequirement::getActiveFlag, CommonConstants.YES)
                .eq(JobRequirement::getDeleted, CommonConstants.NO));

        int insertedCount = 0;
        int updatedCount = 0;
        List<JobRequirement> materialized = new ArrayList<>();
        for (JobRequirement candidate : parsed) {
            JobRequirement stored = existingByKey.get(candidate.getRequirementKey());
            if (stored == null) {
                try {
                    jobRequirementMapper.insert(candidate);
                    stored = candidate;
                    insertedCount++;
                } catch (DuplicateKeyException ex) {
                    stored = findRequirement(candidate);
                    if (stored == null) {
                        throw ex;
                    }
                    copyMaterializedFields(candidate, stored);
                    jobRequirementMapper.updateById(stored);
                    updatedCount++;
                }
            } else {
                copyMaterializedFields(candidate, stored);
                jobRequirementMapper.updateById(stored);
                updatedCount++;
            }
            materialized.add(stored);
        }

        JobRequirementMaterializationVO result = new JobRequirementMaterializationVO();
        result.setTargetJobId(targetJobId);
        result.setJdAnalysisId(analysis.getId());
        result.setRequirementCount(materialized.size());
        result.setInsertedCount(insertedCount);
        result.setUpdatedCount(updatedCount);
        result.setDeactivatedCount(deactivatedCount);
        result.setRequirements(materialized.stream().map(this::toRequirementVO).toList());
        return result;
    }

    @Override
    public List<JobRequirementVO> list(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJob(targetJobId, userId);
        JobDescriptionAnalysis analysis = latestParsedAnalysis(targetJobId, userId);
        return activeRequirements(targetJobId, analysis.getId(), userId).stream()
                .map(this::toRequirementVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobRequirementMatrixVO refreshMatrix(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        getOwnedTargetJob(targetJobId, userId);
        JobRequirementMaterializationVO materialization = materialize(targetJobId);
        List<JobRequirement> requirements = activeRequirements(
                targetJobId, materialization.getJdAnalysisId(), userId);
        List<ProjectEvidence> projects = projectEvidenceMapper.selectList(
                new LambdaQueryWrapper<ProjectEvidence>()
                        .eq(ProjectEvidence::getUserId, userId)
                        .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                        .and(wrapper -> wrapper.eq(ProjectEvidence::getTargetJobId, targetJobId)
                                .or()
                                .isNull(ProjectEvidence::getTargetJobId))
                        .orderByDesc(ProjectEvidence::getUpdatedAt)
                        .orderByDesc(ProjectEvidence::getId));
        List<Long> projectIds = projects.stream().map(ProjectEvidence::getId).filter(Objects::nonNull).toList();
        List<ProjectSkillEvidence> skills = projectIds.isEmpty() ? List.of()
                : projectSkillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                        .eq(ProjectSkillEvidence::getUserId, userId)
                        .in(ProjectSkillEvidence::getProjectEvidenceId, projectIds)
                        .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                        .orderByDesc(ProjectSkillEvidence::getUpdatedAt)
                        .orderByDesc(ProjectSkillEvidence::getId));
        Map<Long, List<ProjectSkillEvidence>> skillsByProject = skills.stream()
                .collect(Collectors.groupingBy(ProjectSkillEvidence::getProjectEvidenceId));

        jobRequirementEvidenceMapper.update(null, new LambdaUpdateWrapper<JobRequirementEvidence>()
                .set(JobRequirementEvidence::getActiveFlag, CommonConstants.NO)
                .eq(JobRequirementEvidence::getUserId, userId)
                .eq(JobRequirementEvidence::getTargetJobId, targetJobId)
                .eq(JobRequirementEvidence::getActiveFlag, CommonConstants.YES)
                .eq(JobRequirementEvidence::getDeleted, CommonConstants.NO));

        List<JobRequirementEvidence> existing = jobRequirementEvidenceMapper.selectList(
                new LambdaQueryWrapper<JobRequirementEvidence>()
                        .eq(JobRequirementEvidence::getUserId, userId)
                        .eq(JobRequirementEvidence::getTargetJobId, targetJobId)
                        .eq(JobRequirementEvidence::getDeleted, CommonConstants.NO));
        Map<String, JobRequirementEvidence> existingByRef = existing.stream()
                .collect(Collectors.toMap(this::evidenceIdentity, Function.identity(), (left, right) -> left));
        List<JobRequirementEvidenceSourceRow> businessEvidence = new ArrayList<>();
        businessEvidence.addAll(safeRows(evidenceSourceMapper.selectResumeMatchEvidence(userId, targetJobId)));
        businessEvidence.addAll(safeRows(evidenceSourceMapper.selectInterviewReportEvidence(userId, targetJobId)));
        businessEvidence.addAll(safeRows(evidenceSourceMapper.selectApplicationResultEvidence(userId, targetJobId)));

        for (JobRequirement requirement : requirements) {
            for (ProjectEvidence project : projects) {
                boolean skillMatched = false;
                for (ProjectSkillEvidence skill : skillsByProject.getOrDefault(project.getId(), List.of())) {
                    MatchType matchType = matchType(requirement, skill);
                    if (matchType == null) {
                        continue;
                    }
                    skillMatched = true;
                    JobRequirementEvidence row = skillEvidenceRow(
                            userId, targetJobId, requirement, project, skill, matchType);
                    upsertEvidence(row, existingByRef);
                }
                if (!skillMatched && matchesProjectText(requirement, project)) {
                    JobRequirementEvidence row = projectTextEvidenceRow(
                            userId, targetJobId, requirement, project);
                    upsertEvidence(row, existingByRef);
                }
            }
            for (JobRequirementEvidenceSourceRow source : businessEvidence) {
                if (!matchesBusinessEvidence(requirement, source)) {
                    continue;
                }
                upsertEvidence(businessEvidenceRow(userId, targetJobId, requirement, source), existingByRef);
            }
        }
        return buildMatrix(targetJobId, materialization.getJdAnalysisId(), userId);
    }

    @Override
    public JobRequirementMatrixVO getMatrix(Long targetJobId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return getMatrixForUser(userId, targetJobId);
    }

    @Override
    public JobRequirementMatrixVO getMatrixForUser(Long userId, Long targetJobId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "user is required");
        }
        getOwnedTargetJob(targetJobId, userId);
        JobDescriptionAnalysis analysis = latestParsedAnalysis(targetJobId, userId);
        return buildMatrix(targetJobId, analysis.getId(), userId);
    }

    private List<JobRequirement> parseRequirements(JobDescriptionAnalysis analysis) {
        JsonNode rawRoot = readOptionalJson(analysis.getRawResultJson(), "rawResultJson");
        boolean rootFallback = isFallback(rawRoot);
        String rootConfidence = confidence(rawRoot, rootFallback ? "LOW" : "MEDIUM");
        LinkedHashMap<String, JobRequirement> requirements = new LinkedHashMap<>();
        addArrayRequirements(requirements, analysis, "requiredSkills", analysis.getRequiredSkillsJson(),
                "SKILL", PRIORITY_MUST, BigDecimal.ONE, rootConfidence, rootFallback);
        addArrayRequirements(requirements, analysis, "responsibilities", analysis.getResponsibilitiesJson(),
                "RESPONSIBILITY", PRIORITY_MUST, BigDecimal.ONE, rootConfidence, rootFallback);
        addTextRequirement(requirements, analysis, "experienceRequirement", analysis.getExperienceRequirement(),
                "EXPERIENCE", PRIORITY_MUST, BigDecimal.ONE, rootConfidence, rootFallback);
        addTextRequirement(requirements, analysis, "projectExperienceRequirement",
                analysis.getProjectExperienceRequirement(), "PROJECT_EXPERIENCE", PRIORITY_MUST,
                BigDecimal.ONE, rootConfidence, rootFallback);
        addArrayRequirements(requirements, analysis, "bonusSkills", analysis.getBonusSkillsJson(),
                "SKILL", PRIORITY_NICE_TO_HAVE, new BigDecimal("0.5000"), rootConfidence, rootFallback);
        if (requirements.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "parsed JD does not contain materializable requirements");
        }
        return new ArrayList<>(requirements.values());
    }

    private void addArrayRequirements(Map<String, JobRequirement> target, JobDescriptionAnalysis analysis,
                                      String sourceField, String rawJson, String type, String priority,
                                      BigDecimal defaultWeight, String rootConfidence, boolean rootFallback) {
        JsonNode array = readOptionalJson(rawJson, sourceField);
        if (array == null || array.isNull()) {
            return;
        }
        if (!array.isArray()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "stored JD analysis field is not an array: " + sourceField);
        }
        for (JsonNode item : array) {
            String name = requirementName(item);
            if (!StringUtils.hasText(name)) {
                continue;
            }
            boolean fallback = rootFallback || isFallback(item);
            String itemConfidence = confidence(item, fallback ? "LOW" : rootConfidence);
            JobRequirement requirement = baseRequirement(
                    analysis, sourceField, type, priority, name, item == null ? null : item.toString());
            requirement.setDescription(description(item, name));
            requirement.setCategory(text(item, "category", "skillCategory", "domain"));
            requirement.setRequiredLevel(text(item, "requiredLevel", "level", "targetLevel"));
            requirement.setWeight(weight(item, defaultWeight));
            requirement.setConfidenceLevel(itemConfidence);
            requirement.setSourceFallback(fallback ? CommonConstants.YES : CommonConstants.NO);
            target.putIfAbsent(requirement.getRequirementKey(), requirement);
        }
    }

    private void addTextRequirement(Map<String, JobRequirement> target, JobDescriptionAnalysis analysis,
                                    String sourceField, String value, String type, String priority,
                                    BigDecimal weight, String confidence, boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String name = value.trim();
        JobRequirement requirement = baseRequirement(
                analysis, sourceField, type, priority, name, objectMapper.valueToTree(name).toString());
        requirement.setDescription(name);
        requirement.setWeight(weight);
        requirement.setConfidenceLevel(fallback ? "LOW" : confidence);
        requirement.setSourceFallback(fallback ? CommonConstants.YES : CommonConstants.NO);
        target.putIfAbsent(requirement.getRequirementKey(), requirement);
    }

    private JobRequirement baseRequirement(JobDescriptionAnalysis analysis, String sourceField,
                                           String type, String priority, String name, String sourceJson) {
        JobRequirement requirement = new JobRequirement();
        requirement.setUserId(analysis.getUserId());
        requirement.setTargetJobId(analysis.getTargetJobId());
        requirement.setJdAnalysisId(analysis.getId());
        requirement.setRequirementType(type);
        requirement.setRequirementName(name.trim());
        requirement.setPriority(priority);
        requirement.setSourceField(sourceField);
        requirement.setSourceJson(sourceJson);
        requirement.setActiveFlag(CommonConstants.YES);
        requirement.setRequirementKey(sha256(String.join("|",
                sourceField, type, priority, normalize(name))));
        return requirement;
    }

    private JobRequirementEvidence skillEvidenceRow(Long userId, Long targetJobId, JobRequirement requirement,
                                                    ProjectEvidence project, ProjectSkillEvidence skill,
                                                    MatchType matchType) {
        boolean fallback = isEvidenceFallback(skill);
        boolean exact = matchType == MatchType.SKILL_EXACT || matchType == MatchType.JD_KEYWORD_EXACT;
        boolean strong = exact
                && !isLowRequirement(requirement)
                && CommonConstants.YES.equals(skill.getConfirmed())
                && !isLowStrength(skill.getStrengthLevel())
                && StringUtils.hasText(skill.getEvidenceText())
                && !fallback;
        JobRequirementEvidence row = new JobRequirementEvidence();
        row.setUserId(userId);
        row.setTargetJobId(targetJobId);
        row.setRequirementId(requirement.getId());
        row.setProjectEvidenceId(project.getId());
        row.setProjectSkillEvidenceId(skill.getId());
        row.setEvidenceType("PROJECT_EVIDENCE");
        row.setEvidenceId(project.getId());
        row.setEvidenceSubId(skill.getId());
        row.setTitle(project.getTitle());
        row.setExcerpt(skill.getEvidenceText());
        row.setResultSource(skill.getSourceType());
        row.setOccurredAt(skill.getUpdatedAt());
        row.setEvidenceRefKey("SKILL:" + skill.getId());
        row.setMatchType(matchType.name());
        row.setCoverageLevel(strong ? COVERAGE_STRONG : COVERAGE_WEAK);
        row.setConfidenceLevel(strong
                ? ("STRONG".equalsIgnoreCase(skill.getStrengthLevel()) ? "HIGH" : "MEDIUM")
                : "LOW");
        row.setEvidenceSourceType(skill.getSourceType());
        row.setConfirmed(CommonConstants.YES.equals(skill.getConfirmed()) ? CommonConstants.YES : CommonConstants.NO);
        row.setFallback(fallback ? CommonConstants.YES : CommonConstants.NO);
        row.setEvidenceText(skill.getEvidenceText());
        row.setMatchReason(matchReason(requirement, skill, matchType, strong, fallback));
        row.setActiveFlag(CommonConstants.YES);
        return row;
    }

    private JobRequirementEvidence projectTextEvidenceRow(Long userId, Long targetJobId,
                                                          JobRequirement requirement, ProjectEvidence project) {
        JobRequirementEvidence row = new JobRequirementEvidence();
        row.setUserId(userId);
        row.setTargetJobId(targetJobId);
        row.setRequirementId(requirement.getId());
        row.setProjectEvidenceId(project.getId());
        row.setEvidenceType("PROJECT_EVIDENCE");
        row.setEvidenceId(project.getId());
        row.setTitle(project.getTitle());
        row.setExcerpt(projectEvidenceText(project));
        row.setResultSource("PROJECT_TEXT");
        row.setOccurredAt(project.getUpdatedAt());
        row.setEvidenceRefKey("PROJECT:" + project.getId());
        row.setMatchType(MatchType.PROJECT_TEXT.name());
        row.setCoverageLevel(COVERAGE_WEAK);
        row.setConfidenceLevel("LOW");
        row.setEvidenceSourceType("PROJECT_TEXT");
        row.setConfirmed(CommonConstants.NO);
        row.setFallback(CommonConstants.NO);
        row.setEvidenceText(projectEvidenceText(project));
        row.setMatchReason("Project text contains the requirement term; explicit confirmed skill evidence is required for strong coverage.");
        row.setActiveFlag(CommonConstants.YES);
        return row;
    }

    private JobRequirementEvidence businessEvidenceRow(
            Long userId, Long targetJobId, JobRequirement requirement,
            JobRequirementEvidenceSourceRow source) {
        boolean fallback = Boolean.TRUE.equals(source.getFallback())
                || !Boolean.TRUE.equals(source.getConfirmed());
        boolean applicationResult = "APPLICATION_RESULT".equals(source.getEvidenceType());
        boolean strong = !applicationResult
                && !fallback
                && !isLowRequirement(requirement)
                && source.getScore() != null
                && source.getScore() >= 70;
        JobRequirementEvidence row = new JobRequirementEvidence();
        row.setUserId(userId);
        row.setTargetJobId(targetJobId);
        row.setRequirementId(requirement.getId());
        row.setEvidenceType(source.getEvidenceType());
        row.setEvidenceId(source.getEvidenceId());
        row.setEvidenceSubId(source.getEvidenceSubId());
        row.setTitle(source.getTitle());
        row.setExcerpt(source.getExcerpt());
        row.setResultSource(source.getResultSource());
        row.setResultScore(source.getScore());
        row.setOccurredAt(source.getOccurredAt());
        row.setEvidenceRefKey(source.getEvidenceType() + ":" + source.getEvidenceId()
                + ":" + Objects.toString(source.getEvidenceSubId(), "0"));
        row.setMatchType(applicationResult ? "BUSINESS_OUTCOME" : "BUSINESS_TEXT");
        row.setCoverageLevel(strong ? COVERAGE_STRONG : COVERAGE_WEAK);
        row.setConfidenceLevel(strong ? "HIGH" : fallback ? "LOW" : "MEDIUM");
        row.setEvidenceSourceType(source.getEvidenceType());
        row.setConfirmed(Boolean.TRUE.equals(source.getConfirmed())
                ? CommonConstants.YES : CommonConstants.NO);
        row.setFallback(fallback ? CommonConstants.YES : CommonConstants.NO);
        row.setEvidenceText(source.getExcerpt());
        row.setMatchReason(strong
                ? "Owned trusted business evidence matches this requirement with a sufficient score."
                : applicationResult
                ? "Application outcome is retained as contextual evidence and cannot prove a requirement alone."
                : "Business evidence is low-trust, low-score, or unconfirmed and is retained as weak coverage.");
        row.setActiveFlag(CommonConstants.YES);
        return row;
    }

    private boolean matchesBusinessEvidence(JobRequirement requirement, JobRequirementEvidenceSourceRow source) {
        if (source == null || !StringUtils.hasText(source.getEvidenceType())
                || source.getEvidenceId() == null) {
            return false;
        }
        if ("APPLICATION_RESULT".equals(source.getEvidenceType())) {
            return true;
        }
        String requirementText = normalize(requirement.getRequirementName());
        if (requirementText.length() < 2) {
            return false;
        }
        String evidenceText = normalize(String.join(" ",
                textOrEmpty(source.getTitle()), textOrEmpty(source.getExcerpt())));
        return evidenceText.contains(requirementText);
    }

    private List<JobRequirementEvidenceSourceRow> safeRows(List<JobRequirementEvidenceSourceRow> rows) {
        return rows == null ? List.of() : rows;
    }

    private void upsertEvidence(JobRequirementEvidence candidate,
                                Map<String, JobRequirementEvidence> existingByRef) {
        String identity = evidenceIdentity(candidate);
        JobRequirementEvidence stored = existingByRef.get(identity);
        if (stored == null) {
            try {
                jobRequirementEvidenceMapper.insert(candidate);
                stored = candidate;
            } catch (DuplicateKeyException ex) {
                stored = findEvidence(candidate);
                if (stored == null) {
                    throw ex;
                }
                copyEvidenceFields(candidate, stored);
                jobRequirementEvidenceMapper.updateById(stored);
            }
            existingByRef.put(identity, stored);
            return;
        }
        copyEvidenceFields(candidate, stored);
        jobRequirementEvidenceMapper.updateById(stored);
    }

    private JobRequirementMatrixVO buildMatrix(Long targetJobId, Long jdAnalysisId, Long userId) {
        List<JobRequirement> requirements = activeRequirements(targetJobId, jdAnalysisId, userId);
        List<Long> requirementIds = requirements.stream().map(JobRequirement::getId)
                .filter(Objects::nonNull).toList();
        List<JobRequirementEvidence> evidenceRows = requirementIds.isEmpty() ? List.of()
                : jobRequirementEvidenceMapper.selectList(new LambdaQueryWrapper<JobRequirementEvidence>()
                        .eq(JobRequirementEvidence::getUserId, userId)
                        .eq(JobRequirementEvidence::getTargetJobId, targetJobId)
                        .in(JobRequirementEvidence::getRequirementId, requirementIds)
                        .eq(JobRequirementEvidence::getActiveFlag, CommonConstants.YES)
                        .eq(JobRequirementEvidence::getDeleted, CommonConstants.NO)
                        .orderByAsc(JobRequirementEvidence::getRequirementId)
                        .orderByAsc(JobRequirementEvidence::getId));
        Map<Long, List<JobRequirementEvidence>> evidenceByRequirement = evidenceRows.stream()
                .collect(Collectors.groupingBy(JobRequirementEvidence::getRequirementId));
        Set<Long> projectIds = evidenceRows.stream().map(JobRequirementEvidence::getProjectEvidenceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProjectEvidence> projects = projectIds.isEmpty() ? Map.of()
                : projectEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectEvidence>()
                        .eq(ProjectEvidence::getUserId, userId)
                        .in(ProjectEvidence::getId, projectIds)
                        .eq(ProjectEvidence::getDeleted, CommonConstants.NO))
                .stream().collect(Collectors.toMap(ProjectEvidence::getId, Function.identity()));
        Set<Long> skillIds = evidenceRows.stream().map(JobRequirementEvidence::getProjectSkillEvidenceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProjectSkillEvidence> skills = skillIds.isEmpty() ? Map.of()
                : projectSkillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                        .eq(ProjectSkillEvidence::getUserId, userId)
                        .in(ProjectSkillEvidence::getId, skillIds)
                        .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO))
                .stream().collect(Collectors.toMap(ProjectSkillEvidence::getId, Function.identity()));

        JobRequirementMatrixVO matrix = new JobRequirementMatrixVO();
        matrix.setTargetJobId(targetJobId);
        matrix.setJdAnalysisId(jdAnalysisId);
        for (JobRequirement requirement : requirements) {
            JobRequirementMatrixVO.RequirementItem item = new JobRequirementMatrixVO.RequirementItem();
            item.setRequirementId(requirement.getId());
            item.setRequirementKey(requirement.getRequirementKey());
            item.setRequirementType(requirement.getRequirementType());
            item.setRequirementName(requirement.getRequirementName());
            item.setPriority(requirement.getPriority());
            item.setWeight(requirement.getWeight());
            item.setRequirementConfidence(requirement.getConfidenceLevel());
            item.setRequirementFallback(CommonConstants.YES.equals(requirement.getSourceFallback()));
            List<JobRequirementEvidence> rows = evidenceByRequirement.getOrDefault(requirement.getId(), List.of());
            item.setCoverageLevel(rows.stream().anyMatch(row -> COVERAGE_STRONG.equals(row.getCoverageLevel()))
                    ? COVERAGE_STRONG : rows.isEmpty() ? COVERAGE_MISSING : COVERAGE_WEAK);
            item.setEvidences(rows.stream()
                    .sorted(Comparator.comparing(this::coverageRank)
                            .thenComparing(JobRequirementEvidence::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(row -> toEvidenceVO(row,
                            row.getProjectEvidenceId() == null
                                    ? null : projects.get(row.getProjectEvidenceId()),
                            row.getProjectSkillEvidenceId() == null
                                    ? null : skills.get(row.getProjectSkillEvidenceId())))
                    .toList());
            item.setNextActions(nextActions(targetJobId, item));
            matrix.getRequirements().add(item);
        }
        matrix.setRequirementCount(matrix.getRequirements().size());
        matrix.setStrongCount((int) matrix.getRequirements().stream()
                .filter(item -> COVERAGE_STRONG.equals(item.getCoverageLevel())).count());
        matrix.setWeakCount((int) matrix.getRequirements().stream()
                .filter(item -> COVERAGE_WEAK.equals(item.getCoverageLevel())).count());
        matrix.setMissingCount((int) matrix.getRequirements().stream()
                .filter(item -> COVERAGE_MISSING.equals(item.getCoverageLevel())).count());
        matrix.getWarnings().add("QUESTION_PRACTICE_UNAVAILABLE");
        return matrix;
    }

    private List<JobRequirementMatrixVO.NextAction> nextActions(
            Long targetJobId, JobRequirementMatrixVO.RequirementItem item) {
        if (COVERAGE_STRONG.equals(item.getCoverageLevel())
                && !Boolean.TRUE.equals(item.getRequirementFallback())
                && !"LOW".equalsIgnoreCase(item.getRequirementConfidence())) {
            return List.of(nextAction("VALIDATE_APPLICATION_RESULT", "在真实投递中验证",
                    "/job-targets/" + targetJobId + "/applications"));
        }
        List<JobRequirementMatrixVO.NextAction> actions = new ArrayList<>();
        actions.add(nextAction("IMPROVE_RESUME_MATCH", "补强简历匹配证据",
                "/job-targets/" + targetJobId + "/resume-match"));
        actions.add(nextAction("ADD_PROJECT_EVIDENCE", "补充项目证据",
                "/job-targets/" + targetJobId + "/project-evidence"));
        actions.add(nextAction("PRACTICE_INTERVIEW", "进行针对性模拟面试",
                "/job-targets/" + targetJobId + "/interviews"));
        return actions;
    }

    private JobRequirementMatrixVO.NextAction nextAction(String code, String title, String path) {
        JobRequirementMatrixVO.NextAction action = new JobRequirementMatrixVO.NextAction();
        action.setActionCode(code);
        action.setTitle(title);
        action.setPath(path);
        return action;
    }

    private List<JobRequirement> activeRequirements(Long targetJobId, Long jdAnalysisId, Long userId) {
        return jobRequirementMapper.selectList(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getUserId, userId)
                .eq(JobRequirement::getTargetJobId, targetJobId)
                .eq(JobRequirement::getJdAnalysisId, jdAnalysisId)
                .eq(JobRequirement::getActiveFlag, CommonConstants.YES)
                .eq(JobRequirement::getDeleted, CommonConstants.NO)
                .orderByAsc(JobRequirement::getPriority)
                .orderByAsc(JobRequirement::getId));
    }

    private TargetJob getOwnedTargetJob(Long targetJobId, Long userId) {
        if (targetJobId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "target job is required");
        }
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "target job is unavailable");
        }
        return targetJob;
    }

    private JobDescriptionAnalysis latestParsedAnalysis(Long targetJobId, Long userId) {
        JobDescriptionAnalysis analysis = jobDescriptionAnalysisMapper.selectOne(
                new LambdaQueryWrapper<JobDescriptionAnalysis>()
                        .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                        .eq(JobDescriptionAnalysis::getUserId, userId)
                        .eq(JobDescriptionAnalysis::getParseStatus, JobDescriptionParseStatus.PARSED.getCode())
                        .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                        .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                        .orderByDesc(JobDescriptionAnalysis::getId)
                        .last("limit 1"));
        if (analysis == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "a parsed JD analysis is required before materializing requirements");
        }
        return analysis;
    }

    private MatchType matchType(JobRequirement requirement, ProjectSkillEvidence skill) {
        if (sameNormalized(requirement.getRequirementName(), skill.getSkillName())) {
            return MatchType.SKILL_EXACT;
        }
        if (sameNormalized(requirement.getRequirementName(), skill.getJdKeyword())) {
            return MatchType.JD_KEYWORD_EXACT;
        }
        if (related(requirement.getRequirementName(), skill.getSkillName())
                || related(requirement.getRequirementName(), skill.getJdKeyword())) {
            return MatchType.RELATED;
        }
        return null;
    }

    private boolean matchesProjectText(JobRequirement requirement, ProjectEvidence project) {
        String needle = normalize(requirement.getRequirementName());
        if (needle.length() < 3) {
            return false;
        }
        return normalize(projectEvidenceText(project)).contains(needle);
    }

    private String projectEvidenceText(ProjectEvidence project) {
        return String.join("\n",
                textOrEmpty(project.getTitle()),
                textOrEmpty(project.getRole()),
                textOrEmpty(project.getTechStack()),
                textOrEmpty(project.getResponsibility()),
                textOrEmpty(project.getDifficulty()),
                textOrEmpty(project.getSolution()),
                textOrEmpty(project.getResult()));
    }

    private String matchReason(JobRequirement requirement, ProjectSkillEvidence skill, MatchType matchType,
                               boolean strong, boolean fallback) {
        if (strong) {
            return matchType == MatchType.JD_KEYWORD_EXACT
                    ? "Confirmed evidence has an exact JD keyword match and sufficient strength."
                    : "Confirmed evidence has an exact skill match and sufficient strength.";
        }
        List<String> reasons = new ArrayList<>();
        if (isLowRequirement(requirement)) {
            reasons.add("requirement source is fallback or low confidence");
        }
        if (!CommonConstants.YES.equals(skill.getConfirmed())) {
            reasons.add("evidence is not user confirmed");
        }
        if (isLowStrength(skill.getStrengthLevel())) {
            reasons.add("evidence strength is low");
        }
        if (!StringUtils.hasText(skill.getEvidenceText())) {
            reasons.add("evidence text is missing");
        }
        if (fallback) {
            reasons.add("evidence source is fallback");
        }
        if (matchType == MatchType.RELATED) {
            reasons.add("match is related text rather than exact");
        }
        return reasons.isEmpty() ? "Matched evidence is retained as weak coverage." : String.join("; ", reasons) + ".";
    }

    private boolean isLowRequirement(JobRequirement requirement) {
        return CommonConstants.YES.equals(requirement.getSourceFallback())
                || "LOW".equalsIgnoreCase(requirement.getConfidenceLevel());
    }

    private boolean isLowStrength(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "WEAK".equals(normalized) || "LOW".equals(normalized)
                || "UNKNOWN".equals(normalized) || "FALLBACK".equals(normalized);
    }

    private boolean isEvidenceFallback(ProjectSkillEvidence evidence) {
        String sourceType = textOrEmpty(evidence.getSourceType()).toUpperCase(Locale.ROOT);
        return sourceType.contains("FALLBACK")
                || sourceType.contains("LOW_CONFIDENCE")
                || sourceType.contains("UNVERIFIED");
    }

    private String requirementName(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isValueNode()) {
            return item.asText(null);
        }
        return text(item, "name", "skillName", "title", "requirement", "label", "text", "description");
    }

    private String description(JsonNode item, String fallback) {
        if (item == null || item.isNull() || item.isValueNode()) {
            return fallback;
        }
        String description = text(item, "evidence", "description", "details", "requirement", "text");
        return StringUtils.hasText(description) ? description : fallback;
    }

    private BigDecimal weight(JsonNode item, BigDecimal fallback) {
        if (item == null || !item.isObject()) {
            return fallback;
        }
        JsonNode value = item.get("weight");
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            BigDecimal weight = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
            return weight.compareTo(BigDecimal.ZERO) > 0 ? weight : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String confidence(JsonNode node, String fallback) {
        if (node == null || node.isNull() || !node.isObject()) {
            return fallback;
        }
        JsonNode value = firstNode(node, "confidenceLevel", "confidence", "trustStatus");
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            double confidence = value.asDouble();
            return confidence < 0.6 ? "LOW" : confidence < 0.8 ? "MEDIUM" : "HIGH";
        }
        String normalized = value.asText("").trim().toUpperCase(Locale.ROOT);
        if (LOW_CONFIDENCE_VALUES.contains(normalized)) {
            return "LOW";
        }
        if ("HIGH".equals(normalized) || "VERIFIED".equals(normalized)) {
            return "HIGH";
        }
        if ("MEDIUM".equals(normalized) || "PARTIAL".equals(normalized)) {
            return "MEDIUM";
        }
        return fallback;
    }

    private boolean isFallback(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return false;
        }
        if (node.path("fallback").asBoolean(false)) {
            return true;
        }
        String trustStatus = text(node, "trustStatus", "sourceType");
        return StringUtils.hasText(trustStatus)
                && LOW_CONFIDENCE_VALUES.contains(trustStatus.trim().toUpperCase(Locale.ROOT));
    }

    private JsonNode readOptionalJson(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "stored JD analysis JSON is invalid: " + fieldName);
        }
    }

    private String text(JsonNode node, String... fieldNames) {
        JsonNode value = firstNode(node, fieldNames);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private JsonNode firstNode(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private boolean sameNormalized(String left, String right) {
        String normalizedLeft = normalize(left);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalize(right));
    }

    private boolean related(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.length() < 3 || normalizedRight.length() < 3) {
            return false;
        }
        if (normalizedLeft.startsWith("java") && normalizedRight.startsWith("javascript")
                || normalizedRight.startsWith("java") && normalizedLeft.startsWith("javascript")) {
            return false;
        }
        return normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(textOrEmpty(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String evidenceIdentity(JobRequirementEvidence evidence) {
        return evidence.getRequirementId() + "|" + evidence.getEvidenceRefKey();
    }

    private JobRequirement findRequirement(JobRequirement requirement) {
        return jobRequirementMapper.selectOne(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getUserId, requirement.getUserId())
                .eq(JobRequirement::getTargetJobId, requirement.getTargetJobId())
                .eq(JobRequirement::getJdAnalysisId, requirement.getJdAnalysisId())
                .eq(JobRequirement::getRequirementKey, requirement.getRequirementKey())
                .eq(JobRequirement::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private JobRequirementEvidence findEvidence(JobRequirementEvidence evidence) {
        return jobRequirementEvidenceMapper.selectOne(new LambdaQueryWrapper<JobRequirementEvidence>()
                .eq(JobRequirementEvidence::getUserId, evidence.getUserId())
                .eq(JobRequirementEvidence::getTargetJobId, evidence.getTargetJobId())
                .eq(JobRequirementEvidence::getRequirementId, evidence.getRequirementId())
                .eq(JobRequirementEvidence::getEvidenceRefKey, evidence.getEvidenceRefKey())
                .eq(JobRequirementEvidence::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private int coverageRank(JobRequirementEvidence evidence) {
        return COVERAGE_STRONG.equals(evidence.getCoverageLevel()) ? 0 : 1;
    }

    private void copyMaterializedFields(JobRequirement source, JobRequirement target) {
        target.setRequirementType(source.getRequirementType());
        target.setRequirementName(source.getRequirementName());
        target.setDescription(source.getDescription());
        target.setCategory(source.getCategory());
        target.setRequiredLevel(source.getRequiredLevel());
        target.setPriority(source.getPriority());
        target.setWeight(source.getWeight());
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setSourceField(source.getSourceField());
        target.setSourceJson(source.getSourceJson());
        target.setSourceFallback(source.getSourceFallback());
        target.setActiveFlag(CommonConstants.YES);
    }

    private void copyEvidenceFields(JobRequirementEvidence source, JobRequirementEvidence target) {
        target.setProjectEvidenceId(source.getProjectEvidenceId());
        target.setProjectSkillEvidenceId(source.getProjectSkillEvidenceId());
        target.setEvidenceType(source.getEvidenceType());
        target.setEvidenceId(source.getEvidenceId());
        target.setEvidenceSubId(source.getEvidenceSubId());
        target.setTitle(source.getTitle());
        target.setExcerpt(source.getExcerpt());
        target.setResultSource(source.getResultSource());
        target.setResultScore(source.getResultScore());
        target.setOccurredAt(source.getOccurredAt());
        target.setMatchType(source.getMatchType());
        target.setCoverageLevel(source.getCoverageLevel());
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setEvidenceSourceType(source.getEvidenceSourceType());
        target.setConfirmed(source.getConfirmed());
        target.setFallback(source.getFallback());
        target.setEvidenceText(source.getEvidenceText());
        target.setMatchReason(source.getMatchReason());
        target.setActiveFlag(CommonConstants.YES);
    }

    private JobRequirementVO toRequirementVO(JobRequirement requirement) {
        JobRequirementVO vo = new JobRequirementVO();
        vo.setId(requirement.getId());
        vo.setTargetJobId(requirement.getTargetJobId());
        vo.setJdAnalysisId(requirement.getJdAnalysisId());
        vo.setRequirementKey(requirement.getRequirementKey());
        vo.setRequirementType(requirement.getRequirementType());
        vo.setRequirementName(requirement.getRequirementName());
        vo.setDescription(requirement.getDescription());
        vo.setCategory(requirement.getCategory());
        vo.setRequiredLevel(requirement.getRequiredLevel());
        vo.setPriority(requirement.getPriority());
        vo.setWeight(requirement.getWeight());
        vo.setConfidenceLevel(requirement.getConfidenceLevel());
        vo.setSourceField(requirement.getSourceField());
        vo.setSourceFallback(CommonConstants.YES.equals(requirement.getSourceFallback()));
        vo.setCreatedAt(requirement.getCreatedAt());
        vo.setUpdatedAt(requirement.getUpdatedAt());
        return vo;
    }

    private JobRequirementMatrixVO.EvidenceItem toEvidenceVO(JobRequirementEvidence evidence,
                                                              ProjectEvidence project,
                                                              ProjectSkillEvidence skill) {
        JobRequirementMatrixVO.EvidenceItem vo = new JobRequirementMatrixVO.EvidenceItem();
        vo.setId(evidence.getId());
        vo.setEvidenceType(evidence.getEvidenceType());
        vo.setEvidenceId(evidence.getEvidenceId());
        vo.setEvidenceSubId(evidence.getEvidenceSubId());
        vo.setTitle(evidence.getTitle());
        vo.setExcerpt(evidence.getExcerpt());
        vo.setResultSource(evidence.getResultSource());
        vo.setScore(evidence.getResultScore());
        vo.setOccurredAt(evidence.getOccurredAt());
        vo.setProjectEvidenceId(evidence.getProjectEvidenceId());
        vo.setProjectSkillEvidenceId(evidence.getProjectSkillEvidenceId());
        vo.setProjectTitle(project == null ? evidence.getTitle() : project.getTitle());
        vo.setSkillName(skill == null ? null : skill.getSkillName());
        vo.setMatchType(evidence.getMatchType());
        vo.setCoverageLevel(evidence.getCoverageLevel());
        vo.setConfidenceLevel(evidence.getConfidenceLevel());
        vo.setSourceType(evidence.getEvidenceSourceType());
        vo.setConfirmed(CommonConstants.YES.equals(evidence.getConfirmed()));
        vo.setFallback(CommonConstants.YES.equals(evidence.getFallback()));
        vo.setEvidenceText(StringUtils.hasText(evidence.getExcerpt())
                ? evidence.getExcerpt() : evidence.getEvidenceText());
        vo.setMatchReason(evidence.getMatchReason());
        return vo;
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum MatchType {
        SKILL_EXACT,
        JD_KEYWORD_EXACT,
        RELATED,
        PROJECT_TEXT
    }
}
