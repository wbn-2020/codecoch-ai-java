package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.dto.ProjectEvidenceFromResumeProjectDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceQueryDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceSaveDTO;
import com.codecoachai.resume.domain.dto.ProjectSkillEvidenceSaveDTO;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.InnerProjectEvidenceAgentContextVO;
import com.codecoachai.resume.domain.vo.InnerProjectEvidenceTrainingContextVO;
import com.codecoachai.resume.domain.vo.InnerProjectSkillEvidenceSummaryVO;
import com.codecoachai.resume.domain.vo.ProjectEvidenceDetailVO;
import com.codecoachai.resume.domain.vo.ProjectEvidenceListVO;
import com.codecoachai.resume.domain.vo.ProjectSkillEvidenceVO;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.ProjectEvidenceService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectEvidenceServiceImpl implements ProjectEvidenceService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_NEEDS_IMPROVEMENT = "NEEDS_IMPROVEMENT";
    private static final String STATUS_INCOMPLETE = "INCOMPLETE";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_AI_EXTRACTED = "AI_EXTRACTED";
    private static final String SOURCE_RESUME_PROJECT = "RESUME_PROJECT";
    private static final List<String> COMPLETENESS_FIELDS = List.of(
            "background", "responsibility", "difficulty", "solution", "result", "reflection", "skillEvidence");

    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final ProjectSkillEvidenceMapper skillEvidenceMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper resumeProjectMapper;
    private final TargetJobMapper targetJobMapper;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;

    @Override
    public PageResult<ProjectEvidenceListVO> list(ProjectEvidenceQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidenceQueryDTO request = query == null ? new ProjectEvidenceQueryDTO() : query;
        long pageNo = sanitizePageNo(request.getPageNo());
        long pageSize = sanitizePageSize(request.getPageSize());
        LambdaQueryWrapper<ProjectEvidence> wrapper = baseProjectWrapper(userId);
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(w -> w.like(ProjectEvidence::getTitle, keyword)
                    .or()
                    .like(ProjectEvidence::getTechStack, keyword));
        }
        if (StringUtils.hasText(request.getTechStack())) {
            wrapper.like(ProjectEvidence::getTechStack, request.getTechStack().trim());
        }
        if (StringUtils.hasText(request.getCompletenessStatus())) {
            wrapper.eq(ProjectEvidence::getCompletenessStatus, request.getCompletenessStatus().trim());
        }
        if (request.getSourceResumeId() != null) {
            wrapper.eq(ProjectEvidence::getSourceResumeId, request.getSourceResumeId());
        }
        if (request.getTargetJobId() != null) {
            wrapper.eq(ProjectEvidence::getTargetJobId, request.getTargetJobId());
        }
        wrapper.orderByDesc(ProjectEvidence::getUpdatedAt).orderByDesc(ProjectEvidence::getId);
        Page<ProjectEvidence> page = projectEvidenceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ProjectEvidenceListVO> records = page.getRecords().stream()
                .map(project -> toListVO(project, skillCount(project.getId(), project.getUserId()), sourceAvailable(project)))
                .toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectEvidenceDetailVO create(ProjectEvidenceSaveDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        validateTargetJob(dto == null ? null : dto.getTargetJobId(), userId);
        ProjectEvidence project = new ProjectEvidence();
        project.setUserId(userId);
        applyProject(project, dto);
        recalculate(project, 0L);
        projectEvidenceMapper.insert(project);
        return toDetailVO(project, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectEvidenceDetailVO importFromResumeProject(ProjectEvidenceFromResumeProjectDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "import request is required");
        }
        Long userId = SecurityAssert.requireLoginUserId();
        Resume resume = getOwnedResume(dto.getSourceResumeId(), userId);
        ResumeProject resumeProject = resumeProjectMapper.selectById(dto.getSourceResumeProjectId());
        if (resumeProject == null || CommonConstants.YES.equals(resumeProject.getDeleted())
                || !Objects.equals(resume.getId(), resumeProject.getResumeId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume project is unavailable");
        }
        validateTargetJob(dto.getTargetJobId(), userId);
        ProjectEvidence project = new ProjectEvidence();
        project.setUserId(userId);
        project.setTitle(resumeProject.getProjectName());
        project.setRole(resumeProject.getRole());
        project.setBackground(firstText(resumeProject.getProjectBackground(), resumeProject.getDescription()));
        project.setResponsibility(resumeProject.getResponsibility());
        project.setTechStack(resumeProject.getTechStack());
        project.setDifficulty(resumeProject.getTechnicalDifficulties());
        project.setSolution(resumeProject.getCoreFeatures());
        project.setResult(resumeProject.getOptimizationResults());
        project.setSourceResumeId(resume.getId());
        project.setSourceResumeProjectId(resumeProject.getId());
        project.setTargetJobId(dto.getTargetJobId());
        applyProjectPeriod(project, resumeProject.getProjectPeriod());
        recalculate(project, 0L);
        projectEvidenceMapper.insert(project);
        return toDetailVO(project, List.of());
    }

    @Override
    public ProjectEvidenceDetailVO detail(Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(id, userId);
        List<ProjectSkillEvidenceVO> skills = listSkillEvidences(userId, id);
        return toDetailVO(project, skills);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectEvidenceDetailVO update(Long id, ProjectEvidenceSaveDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(id, userId);
        validateTargetJob(dto == null ? null : dto.getTargetJobId(), userId);
        applyProject(project, dto);
        recalculate(project, skillCount(project.getId(), userId));
        projectEvidenceMapper.updateById(project);
        agentBusinessActionNotifier.completeProjectEvidence(userId, project.getId(), "Project evidence updated");
        return toDetailVO(project, listSkillEvidences(userId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(id, userId);
        project.setDeleted(CommonConstants.YES);
        projectEvidenceMapper.updateById(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSkillEvidenceVO addSkillEvidence(Long projectEvidenceId, ProjectSkillEvidenceSaveDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        ProjectSkillEvidence evidence = new ProjectSkillEvidence();
        evidence.setUserId(userId);
        evidence.setProjectEvidenceId(project.getId());
        applySkillEvidence(evidence, dto);
        skillEvidenceMapper.insert(evidence);
        recalculateAndUpdate(project, skillCount(project.getId(), userId));
        agentBusinessActionNotifier.completeProjectEvidence(userId, project.getId(), "Project skill evidence added");
        return toSkillVO(evidence);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSkillEvidenceVO updateSkillEvidence(Long projectEvidenceId, Long evidenceId,
                                                      ProjectSkillEvidenceSaveDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        ProjectSkillEvidence evidence = getOwnedSkillEvidence(evidenceId, userId);
        requireSameProject(project.getId(), evidence);
        applySkillEvidence(evidence, dto);
        skillEvidenceMapper.updateById(evidence);
        recalculateAndUpdate(project, skillCount(project.getId(), userId));
        agentBusinessActionNotifier.completeProjectEvidence(userId, project.getId(), "Project skill evidence updated");
        return toSkillVO(evidence);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSkillEvidence(Long projectEvidenceId, Long evidenceId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ProjectEvidence project = getOwnedProject(projectEvidenceId, userId);
        ProjectSkillEvidence evidence = getOwnedSkillEvidence(evidenceId, userId);
        requireSameProject(project.getId(), evidence);
        evidence.setDeleted(CommonConstants.YES);
        skillEvidenceMapper.updateById(evidence);
        recalculateAndUpdate(project, skillCount(project.getId(), userId));
        agentBusinessActionNotifier.completeProjectEvidence(userId, project.getId(), "Project skill evidence deleted");
    }

    @Override
    public List<InnerProjectEvidenceAgentContextVO> listAgentContextForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return projectEvidenceMapper.selectList(baseProjectWrapper(userId)
                        .orderByAsc(ProjectEvidence::getCompletenessScore)
                        .orderByDesc(ProjectEvidence::getUpdatedAt)
                        .last("limit 20"))
                .stream()
                .map(project -> {
                    long skillCount = skillCount(project.getId(), userId);
                    InnerProjectEvidenceAgentContextVO vo = new InnerProjectEvidenceAgentContextVO();
                    vo.setProjectEvidenceId(project.getId());
                    vo.setTitle(project.getTitle());
                    vo.setTechStack(project.getTechStack());
                    vo.setCompletenessScore(project.getCompletenessScore());
                    vo.setCompletenessStatus(project.getCompletenessStatus());
                    vo.setMissingFields(splitMissingFields(project.getMissingFields()));
                    vo.setSkillEvidenceCount(skillCount);
                    vo.setTopSkillNames(topSkillNames(userId, project.getId()));
                    vo.setTargetJobId(project.getTargetJobId());
                    vo.setSuggestedActionPath("/project-evidence/" + project.getId() + "/edit");
                    return vo;
                })
                .toList();
    }

    @Override
    public List<InnerProjectEvidenceTrainingContextVO> listTrainingContextForUser(Long userId, List<Long> ids) {
        if (userId == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(10)
                .toList();
        if (projectIds.isEmpty()) {
            return List.of();
        }
        List<ProjectEvidence> projects = projectEvidenceMapper.selectList(baseProjectWrapper(userId)
                .in(ProjectEvidence::getId, projectIds));
        Map<Long, ProjectEvidence> projectById = projects.stream()
                .collect(Collectors.toMap(ProjectEvidence::getId, Function.identity(), (left, right) -> left));
        return projectIds.stream()
                .map(projectById::get)
                .filter(Objects::nonNull)
                .map(project -> toTrainingContext(project, listTrainingSkillSummaries(userId, project.getId())))
                .toList();
    }

    private void applyProject(ProjectEvidence project, ProjectEvidenceSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "project title is required");
        }
        project.setTitle(dto.getTitle().trim());
        project.setRole(trim(dto.getRole()));
        project.setStartDate(trim(dto.getStartDate()));
        project.setEndDate(trim(dto.getEndDate()));
        project.setBackground(trim(dto.getBackground()));
        project.setResponsibility(trim(dto.getResponsibility()));
        project.setTechStack(trim(dto.getTechStack()));
        project.setDifficulty(trim(dto.getDifficulty()));
        project.setSolution(trim(dto.getSolution()));
        project.setResult(trim(dto.getResult()));
        project.setReflection(trim(dto.getReflection()));
        project.setTargetJobId(dto.getTargetJobId());
    }

    private void applySkillEvidence(ProjectSkillEvidence evidence, ProjectSkillEvidenceSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSkillName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "skill name is required");
        }
        evidence.setSkillName(dto.getSkillName().trim());
        evidence.setSkillCategory(trim(dto.getSkillCategory()));
        evidence.setEvidenceText(trim(dto.getEvidenceText()));
        evidence.setStrengthLevel(StringUtils.hasText(dto.getStrengthLevel()) ? dto.getStrengthLevel().trim() : "MEDIUM");
        evidence.setJdKeyword(trim(dto.getJdKeyword()));
        evidence.setRiskPoints(trim(dto.getRiskPoints()));
        evidence.setSourceType(StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType().trim() : SOURCE_MANUAL);
        evidence.setConfirmed(confirmedValue(dto));
    }

    private void recalculateAndUpdate(ProjectEvidence project, long skillEvidenceCount) {
        recalculate(project, skillEvidenceCount);
        projectEvidenceMapper.updateById(project);
    }

    private void recalculate(ProjectEvidence project, long skillEvidenceCount) {
        List<String> missing = missingFields(project, skillEvidenceCount);
        int present = COMPLETENESS_FIELDS.size() - missing.size();
        int score = Math.round(present * 100.0f / COMPLETENESS_FIELDS.size());
        project.setCompletenessScore(score);
        project.setCompletenessStatus(score >= 80 ? STATUS_READY : score >= 50 ? STATUS_NEEDS_IMPROVEMENT : STATUS_INCOMPLETE);
        project.setMissingFields(String.join(",", missing));
    }

    private List<String> missingFields(ProjectEvidence project, long skillEvidenceCount) {
        return COMPLETENESS_FIELDS.stream()
                .filter(field -> switch (field) {
                    case "background" -> !StringUtils.hasText(project.getBackground());
                    case "responsibility" -> !StringUtils.hasText(project.getResponsibility());
                    case "difficulty" -> !StringUtils.hasText(project.getDifficulty());
                    case "solution" -> !StringUtils.hasText(project.getSolution());
                    case "result" -> !StringUtils.hasText(project.getResult());
                    case "reflection" -> !StringUtils.hasText(project.getReflection());
                    case "skillEvidence" -> skillEvidenceCount <= 0;
                    default -> false;
                })
                .toList();
    }

    private ProjectEvidence getOwnedProject(Long id, Long userId) {
        ProjectEvidence project = projectEvidenceMapper.selectOne(baseProjectWrapper(userId)
                .eq(ProjectEvidence::getId, id)
                .last("limit 1"));
        if (project == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "project evidence is unavailable");
        }
        return project;
    }

    private ProjectSkillEvidence getOwnedSkillEvidence(Long id, Long userId) {
        ProjectSkillEvidence evidence = skillEvidenceMapper.selectOne(new LambdaQueryWrapper<ProjectSkillEvidence>()
                .eq(ProjectSkillEvidence::getId, id)
                .eq(ProjectSkillEvidence::getUserId, userId)
                .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (evidence == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "skill evidence is unavailable");
        }
        return evidence;
    }

    private Resume getOwnedResume(Long id, Long userId) {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume is unavailable");
        }
        return resume;
    }

    private void validateTargetJob(Long targetJobId, Long userId) {
        if (targetJobId == null) {
            return;
        }
        TargetJob targetJob = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, targetJobId)
                .eq(TargetJob::getUserId, userId)
                .eq(TargetJob::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (targetJob == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "target job is unavailable");
        }
    }

    private void requireSameProject(Long projectEvidenceId, ProjectSkillEvidence evidence) {
        if (!Objects.equals(projectEvidenceId, evidence.getProjectEvidenceId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "skill evidence does not belong to project");
        }
    }

    private LambdaQueryWrapper<ProjectEvidence> baseProjectWrapper(Long userId) {
        return new LambdaQueryWrapper<ProjectEvidence>()
                .eq(ProjectEvidence::getUserId, userId)
                .eq(ProjectEvidence::getDeleted, CommonConstants.NO);
    }

    private List<ProjectSkillEvidenceVO> listSkillEvidences(Long userId, Long projectEvidenceId) {
        return skillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                        .eq(ProjectSkillEvidence::getUserId, userId)
                        .eq(ProjectSkillEvidence::getProjectEvidenceId, projectEvidenceId)
                        .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                        .orderByDesc(ProjectSkillEvidence::getConfirmed)
                        .orderByDesc(ProjectSkillEvidence::getUpdatedAt))
                .stream()
                .map(this::toSkillVO)
                .toList();
    }

    private long skillCount(Long projectEvidenceId, Long userId) {
        Long count = skillEvidenceMapper.selectCount(new LambdaQueryWrapper<ProjectSkillEvidence>()
                .eq(ProjectSkillEvidence::getUserId, userId)
                .eq(ProjectSkillEvidence::getProjectEvidenceId, projectEvidenceId)
                .eq(ProjectSkillEvidence::getConfirmed, CommonConstants.YES)
                .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO));
        return count == null ? 0L : count;
    }

    private List<String> topSkillNames(Long userId, Long projectEvidenceId) {
        return skillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                        .eq(ProjectSkillEvidence::getUserId, userId)
                        .eq(ProjectSkillEvidence::getProjectEvidenceId, projectEvidenceId)
                        .eq(ProjectSkillEvidence::getConfirmed, CommonConstants.YES)
                        .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                        .orderByDesc(ProjectSkillEvidence::getConfirmed)
                        .last("limit 5"))
                .stream()
                .map(ProjectSkillEvidence::getSkillName)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<InnerProjectSkillEvidenceSummaryVO> listTrainingSkillSummaries(Long userId, Long projectEvidenceId) {
        return skillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                        .eq(ProjectSkillEvidence::getUserId, userId)
                        .eq(ProjectSkillEvidence::getProjectEvidenceId, projectEvidenceId)
                        .eq(ProjectSkillEvidence::getConfirmed, CommonConstants.YES)
                        .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                        .orderByDesc(ProjectSkillEvidence::getConfirmed)
                        .orderByDesc(ProjectSkillEvidence::getUpdatedAt)
                        .last("limit 8"))
                .stream()
                .map(this::toSkillSummaryVO)
                .toList();
    }

    private boolean sourceAvailable(ProjectEvidence project) {
        if (project.getSourceResumeId() == null || project.getSourceResumeProjectId() == null) {
            return true;
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, project.getSourceResumeId())
                .eq(Resume::getUserId, project.getUserId())
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        ResumeProject resumeProject = resumeProjectMapper.selectById(project.getSourceResumeProjectId());
        return resume != null
                && resumeProject != null
                && !CommonConstants.YES.equals(resumeProject.getDeleted())
                && Objects.equals(project.getSourceResumeId(), resumeProject.getResumeId());
    }

    private ProjectEvidenceListVO toListVO(ProjectEvidence project, long skillCount, boolean sourceAvailable) {
        ProjectEvidenceListVO vo = new ProjectEvidenceListVO();
        copyProjectBase(vo, project, sourceAvailable);
        vo.setSkillEvidenceCount(skillCount);
        return vo;
    }

    private ProjectEvidenceDetailVO toDetailVO(ProjectEvidence project, List<ProjectSkillEvidenceVO> skills) {
        ProjectEvidenceDetailVO vo = new ProjectEvidenceDetailVO();
        copyProjectBase(vo, project, sourceAvailable(project));
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());
        vo.setBackground(project.getBackground());
        vo.setResponsibility(project.getResponsibility());
        vo.setDifficulty(project.getDifficulty());
        vo.setSolution(project.getSolution());
        vo.setResult(project.getResult());
        vo.setReflection(project.getReflection());
        vo.setSkillEvidences(skills == null ? List.of() : skills);
        return vo;
    }

    private void copyProjectBase(ProjectEvidenceListVO vo, ProjectEvidence project, boolean sourceAvailable) {
        vo.setId(project.getId());
        vo.setUserId(project.getUserId());
        vo.setTitle(project.getTitle());
        vo.setRole(project.getRole());
        vo.setTechStack(project.getTechStack());
        vo.setCompletenessScore(project.getCompletenessScore());
        vo.setCompletenessStatus(project.getCompletenessStatus());
        vo.setMissingFields(splitMissingFields(project.getMissingFields()));
        vo.setSourceResumeId(project.getSourceResumeId());
        vo.setSourceResumeProjectId(project.getSourceResumeProjectId());
        vo.setSourceAvailable(sourceAvailable);
        vo.setTargetJobId(project.getTargetJobId());
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
    }

    private void copyProjectBase(ProjectEvidenceDetailVO vo, ProjectEvidence project, boolean sourceAvailable) {
        vo.setId(project.getId());
        vo.setUserId(project.getUserId());
        vo.setTitle(project.getTitle());
        vo.setRole(project.getRole());
        vo.setTechStack(project.getTechStack());
        vo.setCompletenessScore(project.getCompletenessScore());
        vo.setCompletenessStatus(project.getCompletenessStatus());
        vo.setMissingFields(splitMissingFields(project.getMissingFields()));
        vo.setSourceResumeId(project.getSourceResumeId());
        vo.setSourceResumeProjectId(project.getSourceResumeProjectId());
        vo.setSourceAvailable(sourceAvailable);
        vo.setTargetJobId(project.getTargetJobId());
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
    }

    private ProjectSkillEvidenceVO toSkillVO(ProjectSkillEvidence evidence) {
        ProjectSkillEvidenceVO vo = new ProjectSkillEvidenceVO();
        vo.setId(evidence.getId());
        vo.setUserId(evidence.getUserId());
        vo.setProjectEvidenceId(evidence.getProjectEvidenceId());
        vo.setSkillName(evidence.getSkillName());
        vo.setSkillCategory(evidence.getSkillCategory());
        vo.setEvidenceText(evidence.getEvidenceText());
        vo.setStrengthLevel(evidence.getStrengthLevel());
        vo.setJdKeyword(evidence.getJdKeyword());
        vo.setRiskPoints(evidence.getRiskPoints());
        vo.setSourceType(evidence.getSourceType());
        vo.setConfirmed(CommonConstants.YES.equals(evidence.getConfirmed()));
        vo.setCreatedAt(evidence.getCreatedAt());
        vo.setUpdatedAt(evidence.getUpdatedAt());
        return vo;
    }

    private InnerProjectEvidenceTrainingContextVO toTrainingContext(ProjectEvidence project,
                                                                    List<InnerProjectSkillEvidenceSummaryVO> skillSummaries) {
        InnerProjectEvidenceTrainingContextVO vo = new InnerProjectEvidenceTrainingContextVO();
        vo.setProjectEvidenceId(project.getId());
        vo.setTitle(project.getTitle());
        vo.setRole(project.getRole());
        vo.setTechStack(project.getTechStack());
        vo.setCompletenessScore(project.getCompletenessScore());
        vo.setCompletenessStatus(project.getCompletenessStatus());
        vo.setMissingFields(splitMissingFields(project.getMissingFields()));
        vo.setProjectSummary(buildProjectSummary(project));
        vo.setTopSkillNames(skillSummaries.stream()
                .map(InnerProjectSkillEvidenceSummaryVO::getSkillName)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList());
        vo.setSkillEvidenceSummaries(skillSummaries);
        return vo;
    }

    private InnerProjectSkillEvidenceSummaryVO toSkillSummaryVO(ProjectSkillEvidence evidence) {
        InnerProjectSkillEvidenceSummaryVO vo = new InnerProjectSkillEvidenceSummaryVO();
        vo.setId(evidence.getId());
        vo.setSkillName(evidence.getSkillName());
        vo.setSkillCategory(evidence.getSkillCategory());
        vo.setStrengthLevel(evidence.getStrengthLevel());
        vo.setJdKeyword(evidence.getJdKeyword());
        vo.setEvidenceSummary(abbreviate(evidence.getEvidenceText(), 160));
        vo.setRiskPoints(abbreviate(evidence.getRiskPoints(), 120));
        return vo;
    }

    private String buildProjectSummary(ProjectEvidence project) {
        String joined = Arrays.asList(
                        project.getBackground(),
                        project.getResponsibility(),
                        project.getDifficulty(),
                        project.getSolution(),
                        project.getResult(),
                        project.getReflection())
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return abbreviate(joined, 500);
    }

    private List<String> splitMissingFields(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void applyProjectPeriod(ProjectEvidence project, String period) {
        if (!StringUtils.hasText(period)) {
            return;
        }
        String[] parts = period.split("\\s*-\\s*", 2);
        project.setStartDate(parts[0].trim());
        if (parts.length > 1) {
            project.setEndDate(parts[1].trim());
        }
    }

    private long sanitizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long sanitizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int confirmedValue(ProjectSkillEvidenceSaveDTO dto) {
        if (dto.getConfirmed() != null) {
            return dto.getConfirmed() ? CommonConstants.YES : CommonConstants.NO;
        }
        return SOURCE_AI_EXTRACTED.equalsIgnoreCase(trim(dto.getSourceType()))
                ? CommonConstants.NO
                : CommonConstants.YES;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
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
}
