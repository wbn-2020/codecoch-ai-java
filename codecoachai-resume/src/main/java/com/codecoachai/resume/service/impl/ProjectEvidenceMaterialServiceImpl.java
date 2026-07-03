package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.ProjectJdCoverageRequestDTO;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerateDTO;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerationQueryDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.ProjectStoryGeneration;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.ProjectJdCoverageVO;
import com.codecoachai.resume.domain.vo.ProjectStoryGenerationVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectStoryGenerationMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.ProjectEvidenceMaterialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectEvidenceMaterialServiceImpl implements ProjectEvidenceMaterialService {

    private static final String TYPE_RESUME_BULLET = "RESUME_BULLET";
    private static final String TYPE_STAR_STORY = "STAR_STORY";
    private static final String TYPE_INTERVIEW_QUESTIONS = "INTERVIEW_QUESTIONS";
    private static final Set<String> GENERATION_TYPES = Set.of(
            TYPE_RESUME_BULLET, TYPE_STAR_STORY, TYPE_INTERVIEW_QUESTIONS);
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String SOURCE_LOCAL_GENERATOR = "LOCAL_GENERATOR";
    private static final String PROMPT_VERSION = "project-evidence-1b-local-v1";

    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectSkillEvidenceMapper skillEvidenceMapper;
    private final ProjectStoryGenerationMapper storyGenerationMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ObjectMapper objectMapper;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectStoryGenerationVO generate(Long projectEvidenceId, ProjectStoryGenerateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        String type = normalizeGenerationType(dto == null ? null : dto.getGenerationType());
        Long targetJobId = dto == null ? null : dto.getTargetJobId();
        TargetJob targetJob = targetJobId == null ? null : getOwnedTargetJob(targetJobId, userId);
        List<ProjectSkillEvidence> skills = listSkills(userId, project.getId());
        String resultText = generateText(type, project, skills, targetJob);
        ProjectStoryGeneration generation = new ProjectStoryGeneration();
        generation.setUserId(userId);
        generation.setProjectEvidenceId(project.getId());
        generation.setGenerationType(type);
        generation.setTargetJobId(targetJobId);
        generation.setPromptVersion(PROMPT_VERSION);
        generation.setResultText(resultText);
        generation.setStructuredResultJson(toJson(structuredResult(type, resultText)));
        generation.setInputSummaryJson(toJson(inputSummary(project, skills, targetJob, type)));
        generation.setTraceId(MDC.get("traceId"));
        generation.setResultSource(SOURCE_LOCAL_GENERATOR);
        generation.setAccepted(CommonConstants.NO);
        generation.setStatus(STATUS_SUCCESS);
        generation.setPromptTokens(estimateTokens(summaryText(project, skills, targetJob)));
        generation.setCompletionTokens(estimateTokens(resultText));
        generation.setTotalTokens(safeSum(generation.getPromptTokens(), generation.getCompletionTokens()));
        storyGenerationMapper.insert(generation);
        agentBusinessActionNotifier.completeProjectEvidence(userId, project.getId(), "Project interview material generated");
        return toVO(generation);
    }

    @Override
    public List<ProjectStoryGenerationVO> listGenerations(Long projectEvidenceId, ProjectStoryGenerationQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        LambdaQueryWrapper<ProjectStoryGeneration> wrapper = new LambdaQueryWrapper<ProjectStoryGeneration>()
                .eq(ProjectStoryGeneration::getUserId, userId)
                .eq(ProjectStoryGeneration::getProjectEvidenceId, project.getId())
                .eq(ProjectStoryGeneration::getDeleted, CommonConstants.NO)
                .orderByDesc(ProjectStoryGeneration::getCreatedAt)
                .orderByDesc(ProjectStoryGeneration::getId);
        if (query != null && StringUtils.hasText(query.getGenerationType())) {
            wrapper.eq(ProjectStoryGeneration::getGenerationType, normalizeGenerationType(query.getGenerationType()));
        }
        if (query != null && query.getAccepted() != null) {
            wrapper.eq(ProjectStoryGeneration::getAccepted,
                    Boolean.TRUE.equals(query.getAccepted()) ? CommonConstants.YES : CommonConstants.NO);
        }
        return storyGenerationMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectStoryGenerationVO accept(Long projectEvidenceId, Long generationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        ProjectStoryGeneration generation = storyGenerationMapper.selectOne(new LambdaQueryWrapper<ProjectStoryGeneration>()
                .eq(ProjectStoryGeneration::getId, generationId)
                .eq(ProjectStoryGeneration::getUserId, userId)
                .eq(ProjectStoryGeneration::getProjectEvidenceId, project.getId())
                .eq(ProjectStoryGeneration::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (generation == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "generation is unavailable");
        }
        ProjectStoryGeneration reset = new ProjectStoryGeneration();
        reset.setAccepted(CommonConstants.NO);
        storyGenerationMapper.update(reset, new LambdaQueryWrapper<ProjectStoryGeneration>()
                .eq(ProjectStoryGeneration::getUserId, userId)
                .eq(ProjectStoryGeneration::getProjectEvidenceId, project.getId())
                .eq(ProjectStoryGeneration::getGenerationType, generation.getGenerationType())
                .eq(ProjectStoryGeneration::getDeleted, CommonConstants.NO));
        generation.setAccepted(CommonConstants.YES);
        storyGenerationMapper.updateById(generation);
        agentBusinessActionNotifier.completeProjectEvidence(userId, project.getId(), "Project interview material accepted");
        return toVO(generation);
    }

    @Override
    public ProjectJdCoverageVO analyzeJdCoverage(Long projectEvidenceId, ProjectJdCoverageRequestDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        Long targetJobId = dto == null ? null : dto.getTargetJobId();
        TargetJob targetJob = targetJobId == null ? null : getOwnedTargetJob(targetJobId, userId);
        JobDescriptionAnalysis analysis = targetJob == null ? null : latestJdAnalysis(targetJob.getId(), userId);
        List<String> jdSkills = extractJdSkills(dto == null ? null : dto.getJdText(), targetJob, analysis);
        List<ProjectSkillEvidence> skills = listSkills(userId, project.getId());
        ProjectJdCoverageVO vo = new ProjectJdCoverageVO();
        vo.setProjectEvidenceId(project.getId());
        vo.setTargetJobId(targetJobId);
        vo.setJdSkills(jdSkills);
        for (String jdSkill : jdSkills) {
            CoverageMatch match = matchSkill(jdSkill, project, skills);
            if (match == CoverageMatch.COVERED) {
                vo.getCoveredSkills().add(jdSkill);
            } else if (match == CoverageMatch.WEAK) {
                vo.getWeakCoveredSkills().add(jdSkill);
            } else {
                vo.getMissingSkills().add(jdSkill);
            }
        }
        int score = jdSkills.isEmpty() ? 0 : Math.round((vo.getCoveredSkills().size() * 100.0f
                + vo.getWeakCoveredSkills().size() * 50.0f) / jdSkills.size());
        vo.setCoverageScore(score);
        vo.setExpressionSuggestions(expressionSuggestions(project, vo));
        return vo;
    }

    private ProjectEvidence getOwnedProject(Long id, Long userId) {
        ProjectEvidence project = projectEvidenceMapper.selectOne(new LambdaQueryWrapper<ProjectEvidence>()
                .eq(ProjectEvidence::getId, id)
                .eq(ProjectEvidence::getUserId, userId)
                .eq(ProjectEvidence::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (project == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "project evidence is unavailable");
        }
        return project;
    }

    private TargetJob getOwnedTargetJob(Long id, Long userId) {
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, id)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "target job is unavailable");
        }
        return targetJob;
    }

    private JobDescriptionAnalysis latestJdAnalysis(Long targetJobId, Long userId) {
        return jobDescriptionAnalysisMapper.selectOne(new LambdaQueryWrapper<JobDescriptionAnalysis>()
                .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                .eq(JobDescriptionAnalysis::getUserId, userId)
                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                .orderByDesc(JobDescriptionAnalysis::getUpdatedAt)
                .orderByDesc(JobDescriptionAnalysis::getId)
                .last("limit 1"));
    }

    private List<ProjectSkillEvidence> listSkills(Long userId, Long projectEvidenceId) {
        return skillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                .eq(ProjectSkillEvidence::getUserId, userId)
                .eq(ProjectSkillEvidence::getProjectEvidenceId, projectEvidenceId)
                .eq(ProjectSkillEvidence::getConfirmed, CommonConstants.YES)
                .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                .orderByDesc(ProjectSkillEvidence::getConfirmed)
                .orderByDesc(ProjectSkillEvidence::getUpdatedAt));
    }

    private String normalizeGenerationType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "generation type is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!GENERATION_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "unsupported generation type");
        }
        return normalized;
    }

    private String generateText(String type, ProjectEvidence project, List<ProjectSkillEvidence> skills, TargetJob targetJob) {
        List<String> skillNames = skillNames(skills);
        String skillText = skillNames.isEmpty() ? firstText(project.getTechStack(), "core stack") : String.join(", ", skillNames);
        String title = firstText(project.getTitle(), "Project");
        return switch (type) {
            case TYPE_RESUME_BULLET -> "- Built " + title + " as " + firstText(project.getRole(), "owner")
                    + ", using " + skillText + " to solve " + firstText(project.getDifficulty(), "the core delivery challenge")
                    + " and deliver " + firstText(project.getResult(), "measurable business impact") + ".";
            case TYPE_STAR_STORY -> String.join("\n",
                    "Situation: " + firstText(project.getBackground(), title + " needed measurable improvement."),
                    "Task: " + firstText(project.getResponsibility(), "I owned the project delivery and technical decisions."),
                    "Action: " + firstText(project.getSolution(), "I designed and shipped the implementation with " + skillText + "."),
                    "Result: " + firstText(project.getResult(), "The project produced measurable progress."),
                    "Reflection: " + firstText(project.getReflection(), "I would keep the evidence measurable and the fallback path explicit."));
            case TYPE_INTERVIEW_QUESTIONS -> interviewQuestions(project, skills, targetJob);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "unsupported generation type");
        };
    }

    private String interviewQuestions(ProjectEvidence project, List<ProjectSkillEvidence> skills, TargetJob targetJob) {
        String focus = firstText(targetJob == null ? null : targetJob.getJobTitle(), project.getTitle(), "this project");
        List<String> names = skillNames(skills);
        String primarySkill = names.isEmpty() ? firstText(project.getTechStack(), "the main technology") : names.get(0);
        return String.join("\n",
                "1. What business problem did " + focus + " solve, and why was it important?",
                "2. What was your specific responsibility, and how can you prove your contribution?",
                "3. How did you use " + primarySkill + ", and what tradeoff did you make?",
                "4. What was the hardest failure mode or risk, and how did you mitigate it?",
                "5. Which metric changed after delivery, and how was it measured?");
    }

    private Map<String, Object> structuredResult(String type, String resultText) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generationType", type);
        result.put("items", List.of(resultText.split("\\n")));
        result.put("schema", "project-evidence-1b");
        return result;
    }

    private Map<String, Object> inputSummary(ProjectEvidence project, List<ProjectSkillEvidence> skills,
                                             TargetJob targetJob, String type) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectEvidenceId", project.getId());
        summary.put("targetJobId", targetJob == null ? null : targetJob.getId());
        summary.put("generationType", type);
        summary.put("titleHash", sha256(project.getTitle()));
        summary.put("techStackHash", sha256(project.getTechStack()));
        summary.put("factLength", summaryText(project, skills, targetJob).length());
        summary.put("completenessScore", project.getCompletenessScore());
        summary.put("missingFields", splitCsv(project.getMissingFields()));
        summary.put("skillNames", skillNames(skills));
        summary.put("confirmedSkillCount", skills.stream().filter(this::confirmed).count());
        return summary;
    }

    private String summaryText(ProjectEvidence project, List<ProjectSkillEvidence> skills, TargetJob targetJob) {
        return String.join("\n",
                firstText(project.getTitle(), ""),
                firstText(project.getTechStack(), ""),
                firstText(project.getCompletenessStatus(), ""),
                skillNames(skills).toString(),
                targetJob == null ? "" : firstText(targetJob.getJobTitle(), ""));
    }

    private List<String> extractJdSkills(String inputJdText, TargetJob targetJob, JobDescriptionAnalysis analysis) {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        addKeywordItems(skills, inputJdText);
        if (skills.isEmpty()) {
            addJsonItems(skills, analysis == null ? null : analysis.getRequiredSkillsJson());
            addJsonItems(skills, analysis == null ? null : analysis.getTechKeywordsJson());
        }
        if (skills.isEmpty()) {
            addKeywordItems(skills, targetJob == null ? null : targetJob.getJdText());
        }
        return new ArrayList<>(skills);
    }

    private void addJsonItems(Set<String> skills, String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item != null && item.isValueNode() && StringUtils.hasText(item.asText())) {
                        skills.add(item.asText().trim());
                    } else if (item != null && item.isObject()) {
                        String name = firstText(textValue(item, "skillName"), textValue(item, "name"),
                                textValue(item, "keyword"), textValue(item, "label"));
                        if (StringUtils.hasText(name)) {
                            skills.add(name.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            addKeywordItems(skills, rawJson);
        }
    }

    private void addKeywordItems(Set<String> skills, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        List<String> known = List.of("Java", "Spring Boot", "Spring Cloud", "Redis", "Kafka", "RocketMQ",
                "MySQL", "MyBatis", "JVM", "Docker", "Kubernetes", "Elasticsearch", "Nacos");
        String normalized = normalize(text);
        for (String keyword : known) {
            if (normalized.contains(normalize(keyword))) {
                skills.add(keyword);
            }
        }
    }

    private CoverageMatch matchSkill(String jdSkill, ProjectEvidence project, List<ProjectSkillEvidence> skills) {
        for (ProjectSkillEvidence skill : skills) {
            if (!matches(jdSkill, skill.getSkillName()) && !matches(jdSkill, skill.getJdKeyword())) {
                continue;
            }
            if (confirmed(skill) && !"WEAK".equalsIgnoreCase(firstText(skill.getStrengthLevel(), ""))) {
                return CoverageMatch.COVERED;
            }
            return CoverageMatch.WEAK;
        }
        String projectText = String.join("\n", firstText(project.getTechStack(), ""), firstText(project.getResponsibility(), ""),
                firstText(project.getDifficulty(), ""), firstText(project.getSolution(), ""));
        return matches(jdSkill, projectText) ? CoverageMatch.WEAK : CoverageMatch.MISSING;
    }

    private List<String> expressionSuggestions(ProjectEvidence project, ProjectJdCoverageVO coverage) {
        List<String> suggestions = new ArrayList<>();
        for (String skill : coverage.getMissingSkills()) {
            suggestions.add("Add project evidence for " + skill + " before using this project for the JD.");
        }
        for (String skill : coverage.getWeakCoveredSkills()) {
            suggestions.add("Strengthen " + skill + " with responsibility, difficulty, solution, and measurable result.");
        }
        if (!StringUtils.hasText(project.getResult())) {
            suggestions.add("Add quantified result so the project expression is interview-ready.");
        }
        if (!StringUtils.hasText(project.getDifficulty()) || !StringUtils.hasText(project.getSolution())) {
            suggestions.add("Add difficulty and solution details to support STAR answers.");
        }
        return suggestions;
    }

    private ProjectStoryGenerationVO toVO(ProjectStoryGeneration generation) {
        ProjectStoryGenerationVO vo = new ProjectStoryGenerationVO();
        vo.setId(generation.getId());
        vo.setUserId(generation.getUserId());
        vo.setProjectEvidenceId(generation.getProjectEvidenceId());
        vo.setGenerationType(generation.getGenerationType());
        vo.setTargetJobId(generation.getTargetJobId());
        vo.setPromptVersion(generation.getPromptVersion());
        vo.setResultText(generation.getResultText());
        vo.setStructuredResultJson(generation.getStructuredResultJson());
        vo.setInputSummaryJson(generation.getInputSummaryJson());
        vo.setAiCallLogId(generation.getAiCallLogId());
        vo.setTraceId(generation.getTraceId());
        vo.setResultSource(generation.getResultSource());
        vo.setAccepted(CommonConstants.YES.equals(generation.getAccepted()));
        vo.setStatus(generation.getStatus());
        vo.setErrorMessage(generation.getErrorMessage());
        vo.setPromptTokens(generation.getPromptTokens());
        vo.setCompletionTokens(generation.getCompletionTokens());
        vo.setTotalTokens(generation.getTotalTokens());
        vo.setCreatedAt(generation.getCreatedAt());
        vo.setUpdatedAt(generation.getUpdatedAt());
        return vo;
    }

    private List<String> skillNames(List<ProjectSkillEvidence> skills) {
        return skills == null ? List.of() : skills.stream()
                .map(ProjectSkillEvidence::getSkillName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean confirmed(ProjectSkillEvidence skill) {
        return skill != null && CommonConstants.YES.equals(skill.getConfirmed());
    }

    private boolean matches(String keyword, String text) {
        return StringUtils.hasText(keyword) && StringUtils.hasText(text)
                && normalize(text).contains(normalize(keyword));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-:/,.;()\\[\\]{}]+", "");
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "json serialization failed");
        }
    }

    private String sha256(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 8); i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private Integer estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, Math.round(text.length() / 4.0f));
    }

    private Integer safeSum(Integer left, Integer right) {
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private enum CoverageMatch {
        COVERED,
        WEAK,
        MISSING
    }
}
