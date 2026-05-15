package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.convert.ResumeConvert;
import com.codecoachai.resume.domain.dto.ParsedResumeStructuredDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeAnalysisResultVO;
import com.codecoachai.resume.domain.vo.ResumeConfirmAnalysisVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeParseStatusVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.domain.vo.ResumeUploadVO;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.service.ResumeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String SOURCE_TYPE_FILE_UPLOAD = "FILE_UPLOAD";
    private static final String DEFAULT_AI_RESUME_TITLE = "AI Parsed Resume";
    private static final int RAW_TEXT_SUMMARY_LENGTH = 500;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "md", "txt");

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final FileFeignClient fileFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<ResumeListVO> listResumes() {
        Long userId = requireCurrentUserId();
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getIsDefault)
                        .orderByDesc(Resume::getUpdatedAt))
                .stream()
                .map(ResumeConvert::toListVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVO createResume(ResumeSaveDTO dto) {
        Long userId = requireCurrentUserId();
        Long count = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        Resume resume = new Resume();
        resume.setUserId(userId);
        applyResume(resume, dto);
        resume.setIsDefault(count == null || count == 0 ? CommonConstants.YES : CommonConstants.NO);
        resume.setStatus(CommonConstants.YES);
        resumeMapper.insert(resume);
        return toDetailVO(resume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadVO uploadResume(MultipartFile file) {
        Long userId = requireCurrentUserId();
        validateUploadFile(file);
        InnerFileUploadVO uploadedFile = FeignResultUtils.unwrap(fileFeignClient.upload(file, BIZ_TYPE_RESUME, userId));
        if (uploadedFile == null || uploadedFile.getFileId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "File upload failed");
        }

        ResumeAnalysisRecord record = new ResumeAnalysisRecord();
        record.setUserId(userId);
        record.setFileId(uploadedFile.getFileId());
        record.setSourceType(SOURCE_TYPE_FILE_UPLOAD);
        record.setParseStatus(ResumeParseStatus.PENDING.getCode());
        analysisRecordMapper.insert(record);

        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setFileId(uploadedFile.getFileId());
        vo.setAnalysisRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setParseStatus(record.getParseStatus());
        vo.setOriginalFilename(uploadedFile.getOriginalFilename());
        vo.setFileSize(uploadedFile.getFileSize());
        vo.setFileExt(uploadedFile.getFileExt());
        vo.setMessage("上传成功，等待解析");
        return vo;
    }

    @Override
    public ResumeParseStatusVO getParseStatus(Long analysisRecordId) {
        return toParseStatusVO(getOwnedAnalysisRecord(analysisRecordId, requireCurrentUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseStatusVO reparse(Long analysisRecordId) {
        Long userId = requireCurrentUserId();
        ResumeAnalysisRecord record = getOwnedAnalysisRecord(analysisRecordId, userId);
        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        if (status == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported parse status");
        }
        if (status == ResumeParseStatus.PENDING) {
            return toParseStatusVO(record);
        }
        if (status == ResumeParseStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume is parsing");
        }
        if (status == ResumeParseStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume has been parsed successfully");
        }
        if (status == ResumeParseStatus.WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis is waiting for confirmation");
        }

        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.PENDING.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, null)
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.FAILED.getCode()));
        ResumeAnalysisRecord latestRecord = getOwnedAnalysisRecord(analysisRecordId, userId);
        if (affectedRows > 0) {
            return toParseStatusVO(latestRecord);
        }

        ResumeParseStatus latestStatus = ResumeParseStatus.of(latestRecord.getParseStatus());
        if (latestStatus == ResumeParseStatus.PENDING) {
            return toParseStatusVO(latestRecord);
        }
        if (latestStatus == ResumeParseStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume is parsing");
        }
        if (latestStatus == ResumeParseStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume has been parsed successfully");
        }
        if (latestStatus == ResumeParseStatus.WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis is waiting for confirmation");
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Reparse status update failed");
    }

    @Override
    public ResumeAnalysisResultVO getAnalysisResult(Long analysisRecordId) {
        ResumeAnalysisRecord record = getOwnedAnalysisRecord(analysisRecordId, requireCurrentUserId());
        ResumeAnalysisResultVO vo = new ResumeAnalysisResultVO();
        vo.setAnalysisRecordId(record.getId());
        vo.setFileId(record.getFileId());
        vo.setResumeId(record.getResumeId());
        vo.setParseStatus(record.getParseStatus());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setUpdatedAt(record.getUpdatedAt());

        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        if (status == ResumeParseStatus.WAIT_CONFIRM || status == ResumeParseStatus.SUCCESS) {
            vo.setStructuredJson(parseStructuredJsonObject(record.getStructuredJson()));
            vo.setRawTextSummary(summarizeRawText(record.getRawText()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeConfirmAnalysisVO confirmAnalysis(Long analysisRecordId) {
        Long userId = requireCurrentUserId();
        ResumeAnalysisRecord record = getOwnedAnalysisRecord(analysisRecordId, userId);
        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        if (status == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported parse status");
        }
        if (status == ResumeParseStatus.SUCCESS) {
            return confirmAnalysisSuccess(record);
        }
        if (status == ResumeParseStatus.PENDING || status == ResumeParseStatus.PARSING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis is not finished");
        }
        if (status == ResumeParseStatus.FAILED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis failed, please reparse first");
        }
        if (status != ResumeParseStatus.WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis cannot be confirmed");
        }

        ParsedResumeStructuredDTO structuredResume = parseStructuredResume(record.getStructuredJson());
        Resume resume = buildResumeFromStructured(record, structuredResume);
        resumeMapper.insert(resume);
        insertProjects(resume.getId(), structuredResume.getProjectExperiences());

        int affectedRows = analysisRecordMapper.update(null, new LambdaUpdateWrapper<ResumeAnalysisRecord>()
                .set(ResumeAnalysisRecord::getResumeId, resume.getId())
                .set(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.SUCCESS.getCode())
                .set(ResumeAnalysisRecord::getErrorMessage, null)
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .eq(ResumeAnalysisRecord::getParseStatus, ResumeParseStatus.WAIT_CONFIRM.getCode()));
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume analysis confirmation failed");
        }
        return toConfirmAnalysisVO(record.getId(), resume.getId(), ResumeParseStatus.SUCCESS.getCode(), toDetailVO(resume));
    }

    @Override
    public ResumeDetailVO getResume(Long id) {
        return toDetailVO(getOwnedResume(id));
    }

    @Override
    public ResumeDetailVO updateResume(Long id, ResumeSaveDTO dto) {
        Resume resume = getOwnedResume(id);
        applyResume(resume, dto);
        resumeMapper.updateById(resume);
        return toDetailVO(resume);
    }

    @Override
    public void deleteResume(Long id) {
        Resume resume = getOwnedResume(id);
        projectMapper.delete(new LambdaQueryWrapper<ResumeProject>().eq(ResumeProject::getResumeId, id));
        resumeMapper.deleteById(resume.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVO setDefault(Long id) {
        Resume resume = getOwnedResume(id);
        Long userId = requireCurrentUserId();
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        for (Resume item : resumes) {
            item.setIsDefault(item.getId().equals(id) ? CommonConstants.YES : CommonConstants.NO);
            resumeMapper.updateById(item);
        }
        resume.setIsDefault(CommonConstants.YES);
        return toDetailVO(resume);
    }

    @Override
    public ResumeProjectVO createProject(Long resumeId, ResumeProjectSaveDTO dto) {
        getOwnedResume(resumeId);
        ResumeProject project = new ResumeProject();
        project.setResumeId(resumeId);
        applyProject(project, dto);
        projectMapper.insert(project);
        return ResumeConvert.toProjectVO(project);
    }

    @Override
    public ResumeProjectVO updateProject(Long resumeId, Long projectId, ResumeProjectSaveDTO dto) {
        getOwnedResume(resumeId);
        ResumeProject project = getProject(resumeId, projectId);
        applyProject(project, dto);
        projectMapper.updateById(project);
        return ResumeConvert.toProjectVO(project);
    }

    @Override
    public void deleteProject(Long resumeId, Long projectId) {
        getOwnedResume(resumeId);
        ResumeProject project = getProject(resumeId, projectId);
        projectMapper.deleteById(project.getId());
    }

    @Override
    public InnerResumeDetailVO getInnerResume(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume not found");
        }
        return ResumeConvert.toInnerVO(resume, projects(id));
    }

    @Override
    public InnerResumeDetailVO getDefaultInnerResume() {
        Long userId = requireCurrentUserId();
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .eq(Resume::getIsDefault, CommonConstants.YES)
                .last("limit 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Default resume not found");
        }
        return ResumeConvert.toInnerVO(resume, projects(resume.getId()));
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        return ResumeConvert.toDetailVO(resume, projects(resume.getId()));
    }

    private ResumeParseStatusVO toParseStatusVO(ResumeAnalysisRecord record) {
        ResumeParseStatus status = ResumeParseStatus.of(record.getParseStatus());
        ResumeParseStatusVO vo = new ResumeParseStatusVO();
        vo.setAnalysisRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setFileId(record.getFileId());
        vo.setParseStatus(record.getParseStatus());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setMessage(status == null ? "Unsupported parse status" : status.getMessage());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private ResumeConfirmAnalysisVO confirmAnalysisSuccess(ResumeAnalysisRecord record) {
        if (record.getResumeId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Confirmed resume id is missing");
        }
        Resume resume = getOwnedResume(record.getResumeId());
        return toConfirmAnalysisVO(record.getId(), resume.getId(), ResumeParseStatus.SUCCESS.getCode(), toDetailVO(resume));
    }

    private ResumeConfirmAnalysisVO toConfirmAnalysisVO(Long analysisRecordId, Long resumeId, String parseStatus,
                                                       ResumeDetailVO resume) {
        ResumeConfirmAnalysisVO vo = new ResumeConfirmAnalysisVO();
        vo.setAnalysisRecordId(analysisRecordId);
        vo.setResumeId(resumeId);
        vo.setParseStatus(parseStatus);
        vo.setResume(resume);
        return vo;
    }

    private ParsedResumeStructuredDTO parseStructuredResume(String structuredJson) {
        JsonNode root = parseStructuredJsonObject(structuredJson);
        requireObjectField(root, "basicInfo");
        requireTextField(root, "targetPosition");
        requireArrayField(root, "skills");
        requireArrayField(root, "workExperiences");
        requireArrayField(root, "projectExperiences");
        requireArrayField(root, "educationExperiences");
        for (JsonNode project : root.path("projectExperiences")) {
            if (project == null || !project.isObject()) {
                throw invalidStructuredJson();
            }
            requireArrayField(project, "techStack");
            requireArrayField(project, "responsibilities");
            requireArrayField(project, "technicalDifficulties");
            requireArrayField(project, "achievements");
        }
        try {
            return objectMapper.treeToValue(root, ParsedResumeStructuredDTO.class);
        } catch (Exception ex) {
            throw invalidStructuredJson();
        }
    }

    private JsonNode parseStructuredJsonObject(String structuredJson) {
        if (!StringUtils.hasText(structuredJson)) {
            throw invalidStructuredJson();
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            if (root == null || !root.isObject()) {
                throw invalidStructuredJson();
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidStructuredJson();
        }
    }

    private void requireObjectField(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.path(fieldName).isObject()) {
            throw invalidStructuredJson();
        }
    }

    private void requireTextField(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.path(fieldName).isTextual()) {
            throw invalidStructuredJson();
        }
    }

    private void requireArrayField(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.path(fieldName).isArray()) {
            throw invalidStructuredJson();
        }
    }

    private BusinessException invalidStructuredJson() {
        return new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis structuredJson is invalid");
    }

    private Resume buildResumeFromStructured(ResumeAnalysisRecord record, ParsedResumeStructuredDTO structuredResume) {
        Long existingCount = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, record.getUserId()));
        ParsedResumeStructuredDTO.BasicInfo basicInfo = structuredResume.getBasicInfo();
        String realName = basicInfo == null ? null : basicInfo.getName();
        String targetPosition = structuredResume.getTargetPosition();
        Resume resume = new Resume();
        resume.setUserId(record.getUserId());
        resume.setTitle(buildResumeTitle(realName, targetPosition));
        resume.setRealName(realName);
        resume.setEmail(basicInfo == null ? null : basicInfo.getEmail());
        resume.setPhone(basicInfo == null ? null : basicInfo.getPhone());
        resume.setTargetPosition(targetPosition);
        resume.setSkillStack(joinValues(structuredResume.getSkills(), "、"));
        resume.setWorkExperience(toCompactJson(structuredResume.getWorkExperiences()));
        resume.setEducationExperience(toCompactJson(structuredResume.getEducationExperiences()));
        resume.setSummary(buildResumeSummary(realName, targetPosition, resume.getSkillStack()));
        resume.setIsDefault(existingCount == null || existingCount == 0 ? CommonConstants.YES : CommonConstants.NO);
        resume.setStatus(CommonConstants.YES);
        return resume;
    }

    private void insertProjects(Long resumeId, List<ParsedResumeStructuredDTO.ProjectExperience> projectExperiences) {
        if (projectExperiences == null || projectExperiences.isEmpty()) {
            return;
        }
        int sort = 0;
        for (ParsedResumeStructuredDTO.ProjectExperience item : projectExperiences) {
            if (item == null || !StringUtils.hasText(item.getProjectName())) {
                continue;
            }
            ResumeProject project = new ResumeProject();
            project.setResumeId(resumeId);
            project.setProjectName(item.getProjectName());
            project.setProjectPeriod(item.getPeriod());
            project.setDescription(item.getDescription());
            project.setTechStack(joinValues(item.getTechStack(), "、"));
            project.setResponsibility(joinValues(item.getResponsibilities(), "\n"));
            project.setTechnicalDifficulties(joinValues(item.getTechnicalDifficulties(), "\n"));
            project.setHighlights(joinValues(item.getAchievements(), "\n"));
            project.setSort(sort);
            project.setSortOrder(sort);
            projectMapper.insert(project);
            sort++;
        }
    }

    private String buildResumeTitle(String realName, String targetPosition) {
        if (StringUtils.hasText(realName) && StringUtils.hasText(targetPosition)) {
            return realName + " - " + targetPosition;
        }
        if (StringUtils.hasText(realName)) {
            return realName + " - " + DEFAULT_AI_RESUME_TITLE;
        }
        if (StringUtils.hasText(targetPosition)) {
            return targetPosition;
        }
        return DEFAULT_AI_RESUME_TITLE;
    }

    private String buildResumeSummary(String realName, String targetPosition, String skillStack) {
        StringBuilder summary = new StringBuilder(DEFAULT_AI_RESUME_TITLE);
        if (StringUtils.hasText(realName)) {
            summary.append(": ").append(realName);
        }
        if (StringUtils.hasText(targetPosition)) {
            summary.append(", target ").append(targetPosition);
        }
        if (StringUtils.hasText(skillStack)) {
            summary.append(", skills ").append(skillStack);
        }
        return summary.toString();
    }

    private String joinValues(List<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + delimiter + right)
                .orElse(null);
        return StringUtils.hasText(joined) ? joined : null;
    }

    private String toCompactJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis structuredJson is invalid");
        }
    }

    private String summarizeRawText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        return rawText.length() <= RAW_TEXT_SUMMARY_LENGTH ? rawText : rawText.substring(0, RAW_TEXT_SUMMARY_LENGTH);
    }

    private List<ResumeProjectVO> projects(Long resumeId) {
        return projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                        .eq(ResumeProject::getResumeId, resumeId)
                        .orderByAsc(ResumeProject::getSortOrder)
                        .orderByAsc(ResumeProject::getSort)
                        .orderByDesc(ResumeProject::getUpdatedAt))
                .stream()
                .map(ResumeConvert::toProjectVO)
                .toList();
    }

    private void applyResume(Resume resume, ResumeSaveDTO dto) {
        String title = StringUtils.hasText(dto.getResumeName()) ? dto.getResumeName() : dto.getTitle();
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resumeName is required");
        }
        resume.setTitle(title);
        resume.setRealName(dto.getRealName());
        resume.setEmail(dto.getEmail());
        resume.setPhone(dto.getPhone());
        resume.setTargetPosition(dto.getTargetPosition());
        resume.setSkillStack(dto.getSkillStack());
        resume.setWorkExperience(dto.getWorkExperience());
        resume.setEducationExperience(dto.getEducationExperience());
        resume.setSummary(dto.getSummary());
        if (dto.getStatus() != null) {
            resume.setStatus(dto.getStatus());
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file is empty");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "filename is required");
        }
        String normalized = filename.replace('\\', '/');
        String simpleName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(simpleName) || simpleName.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid filename");
        }
        int index = simpleName.lastIndexOf('.');
        if (index < 0 || index == simpleName.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file extension is required");
        }
        String ext = simpleName.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file type not allowed");
        }
    }

    private void applyProject(ResumeProject project, ResumeProjectSaveDTO dto) {
        project.setProjectName(dto.getProjectName());
        project.setProjectPeriod(dto.getProjectPeriod());
        project.setProjectBackground(dto.getProjectBackground());
        project.setRole(dto.getRole());
        project.setTechStack(dto.getTechStack());
        project.setResponsibility(dto.getResponsibility());
        project.setCoreFeatures(dto.getCoreFeatures());
        project.setTechnicalDifficulties(dto.getTechnicalDifficulties());
        project.setOptimizationResults(dto.getOptimizationResults());
        project.setDescription(dto.getDescription());
        project.setHighlights(dto.getHighlights());
        project.setSort(dto.getSort() == null ? 0 : dto.getSort());
        project.setSortOrder(dto.getSortOrder() == null ? project.getSort() : dto.getSortOrder());
    }

    private Resume getOwnedResume(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || !requireCurrentUserId().equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume not found");
        }
        return resume;
    }

    private ResumeAnalysisRecord getOwnedAnalysisRecord(Long analysisRecordId, Long userId) {
        ResumeAnalysisRecord record = analysisRecordMapper.selectOne(new LambdaQueryWrapper<ResumeAnalysisRecord>()
                .eq(ResumeAnalysisRecord::getId, analysisRecordId)
                .eq(ResumeAnalysisRecord::getUserId, userId)
                .eq(ResumeAnalysisRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume analysis record not found");
        }
        return record;
    }

    private ResumeProject getProject(Long resumeId, Long projectId) {
        ResumeProject project = projectMapper.selectById(projectId);
        if (project == null || !resumeId.equals(project.getResumeId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume project not found");
        }
        return project;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
