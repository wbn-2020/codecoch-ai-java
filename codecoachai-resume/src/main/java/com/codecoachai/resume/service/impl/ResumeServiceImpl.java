package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.convert.ResumeConvert;
import com.codecoachai.resume.domain.dto.ApplyResumeOptimizeResultDTO;
import com.codecoachai.resume.domain.dto.ParsedResumeStructuredDTO;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeOptimizeRecord;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.ApplyResumeOptimizeResultVO;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.InnerResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeAnalysisResultVO;
import com.codecoachai.resume.domain.vo.ResumeConfirmAnalysisVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordVO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.domain.vo.ResumeParseStatusVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.domain.vo.ResumeUploadVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.dto.ResumeOptimizeAiRequestDTO;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.feign.vo.ResumeOptimizeAiResponseVO;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeOptimizeRecordMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mq.ResumeMqDispatcher;
import com.codecoachai.resume.service.ResumeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String SOURCE_TYPE_FILE_UPLOAD = "FILE_UPLOAD";
    private static final String DEFAULT_AI_RESUME_TITLE = "AI Parsed Resume";
    private static final String APPLY_MODE_CREATE_DRAFT = "CREATE_DRAFT";
    private static final int RAW_TEXT_SUMMARY_LENGTH = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "md", "txt");

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeAnalysisRecordMapper analysisRecordMapper;
    private final ResumeOptimizeRecordMapper optimizeRecordMapper;
    private final FileFeignClient fileFeignClient;
    private final AiFeignClient aiFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Optional<ResumeMqDispatcher> resumeMqDispatcher;

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
        boolean dispatched = dispatchResumeParse(record, uploadedFile);

        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setFileId(uploadedFile.getFileId());
        vo.setAnalysisRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setParseStatus(record.getParseStatus());
        vo.setOriginalFilename(uploadedFile.getOriginalFilename());
        vo.setFileSize(uploadedFile.getFileSize());
        vo.setFileExt(uploadedFile.getFileExt());
        vo.setMessage(dispatched ? "上传成功，已提交解析" : "上传成功，等待解析补偿");
        return vo;
    }

    @Override
    public ResumeParseStatusVO getParseStatus(Long analysisRecordId) {
        return toParseStatusVO(getOwnedAnalysisRecord(analysisRecordId, requireCurrentUserId()));
    }

    private boolean dispatchResumeParse(ResumeAnalysisRecord record, InnerFileUploadVO uploadedFile) {
        ResumeParsePayload payload = ResumeParsePayload.builder()
                .resumeId(record.getId())
                .fileId(record.getFileId())
                .ossKey(uploadedFile.getStoragePath())
                .mimeType(uploadedFile.getMimeType())
                .userId(record.getUserId())
                .mode("deep")
                .build();
        boolean dispatched = resumeMqDispatcher.map(dispatcher -> dispatcher.dispatchParse(payload)).orElse(false);
        if (!dispatched) {
            record.setErrorMessage("MQ dispatch unavailable; scheduled compensation will retry");
            analysisRecordMapper.updateById(record);
        }
        return dispatched;
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
    public ResumeOptimizeSubmitVO optimizeResume(Long resumeId, ResumeOptimizeRequestDTO dto) {
        ResumeOptimizeRequestDTO request = dto == null ? new ResumeOptimizeRequestDTO() : dto;
        OptimizeContext context = transactionTemplate.execute(status -> createOptimizeRecord(resumeId, request));
        try {
            ResumeOptimizeAiResponseVO response = FeignResultUtils.unwrap(aiFeignClient.optimizeResume(context.aiRequest()));
            JsonNode resultJson = parseResultJson(response == null ? null : response.getResultJson());
            ResumeOptimizeRecord latestRecord = transactionTemplate.execute(status ->
                    markOptimizeSuccess(context.record().getId(), resultJson.toString(),
                            response == null ? null : response.getAiCallLogId()));
            return toOptimizeSubmitVO(latestRecord, resultJson);
        } catch (RuntimeException ex) {
            ResumeOptimizeRecord failedRecord = transactionTemplate.execute(status ->
                    markOptimizeFailed(context.record().getId(), ex));
            return toOptimizeSubmitVO(failedRecord, null);
        }
    }

    @Override
    public List<ResumeOptimizeRecordVO> listOptimizeRecords(Long resumeId) {
        Long userId = requireCurrentUserId();
        getOwnedResume(resumeId, userId);
        return optimizeRecordMapper.selectList(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                        .eq(ResumeOptimizeRecord::getResumeId, resumeId)
                        .eq(ResumeOptimizeRecord::getUserId, userId)
                        .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                        .orderByDesc(ResumeOptimizeRecord::getCreatedAt))
                .stream()
                .map(this::toOptimizeRecordVO)
                .toList();
    }

    @Override
    public ResumeOptimizeDetailVO getOptimizeRecordDetail(Long recordId) {
        ResumeOptimizeRecord record = getOwnedOptimizeRecord(recordId, requireCurrentUserId());
        return toOptimizeDetailVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyResumeOptimizeResultVO applyOptimizeResult(Long recordId, ApplyResumeOptimizeResultDTO dto) {
        Long userId = requireCurrentUserId();
        String applyMode = dto == null || !StringUtils.hasText(dto.getApplyMode())
                ? APPLY_MODE_CREATE_DRAFT : dto.getApplyMode();
        if (!APPLY_MODE_CREATE_DRAFT.equals(applyMode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only CREATE_DRAFT applyMode is supported");
        }
        ResumeOptimizeRecord record = getOwnedOptimizeRecord(recordId, userId);
        if (!ResumeOptimizeStatus.SUCCESS.getCode().equals(record.getOptimizeStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only successful optimize record can be applied");
        }
        if (record.getResumeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Optimize record source resume is missing");
        }
        parseResultJson(record.getResultJson());
        Resume sourceResume = getOwnedResume(record.getResumeId(), userId);
        LocalDateTime appliedAt = LocalDateTime.now();
        Resume draft = copyResumeDraft(sourceResume, record.getId(), appliedAt);
        resumeMapper.insert(draft);
        copyProjects(sourceResume.getId(), draft.getId());

        ApplyResumeOptimizeResultVO vo = new ApplyResumeOptimizeResultVO();
        vo.setSourceResumeId(sourceResume.getId());
        vo.setSourceOptimizeRecordId(record.getId());
        vo.setNewResumeId(draft.getId());
        vo.setAppliedAt(appliedAt);
        vo.setApplyMode(APPLY_MODE_CREATE_DRAFT);
        vo.setMessage("AI optimization suggestions are linked to a new draft copy. Please edit and confirm manually.");
        vo.setWarnings(List.of("Optimization result is suggestion JSON, not a stable resume patch; resume fields were copied from the original resume."));
        vo.setResumeDetail(toDetailVO(draft));
        return vo;
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
    public ResumeProjectVO updateProject(Long projectId, ResumeProjectSaveDTO dto) {
        ResumeProject project = getOwnedProject(projectId);
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
    public void deleteProject(Long projectId) {
        ResumeProject project = getOwnedProject(projectId);
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

    @Override
    public InnerResumeOptimizeRecordVO getInnerOptimizeRecord(Long recordId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectOne(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                .eq(ResumeOptimizeRecord::getId, recordId)
                .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume optimize record not found");
        }
        InnerResumeOptimizeRecordVO vo = new InnerResumeOptimizeRecordVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetPosition(record.getTargetPosition());
        vo.setExperienceYears(record.getExperienceYears());
        vo.setIndustryDirection(record.getIndustryDirection());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        vo.setResultJson(record.getResultJson());
        vo.setErrorMessage(record.getErrorMessage());
        return vo;
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        return ResumeConvert.toDetailVO(resume, projects(resume.getId()));
    }

    private Resume copyResumeDraft(Resume source, Long optimizeRecordId, LocalDateTime appliedAt) {
        Resume draft = new Resume();
        draft.setUserId(source.getUserId());
        draft.setTitle(buildDraftTitle(source.getTitle()));
        draft.setRealName(source.getRealName());
        draft.setEmail(source.getEmail());
        draft.setPhone(source.getPhone());
        draft.setTargetPosition(source.getTargetPosition());
        draft.setSkillStack(source.getSkillStack());
        draft.setWorkExperience(source.getWorkExperience());
        draft.setEducationExperience(source.getEducationExperience());
        draft.setSummary(source.getSummary());
        draft.setIsDefault(CommonConstants.NO);
        draft.setStatus(source.getStatus() == null ? CommonConstants.YES : source.getStatus());
        draft.setSourceResumeId(source.getId());
        draft.setSourceOptimizeRecordId(optimizeRecordId);
        draft.setAppliedAt(appliedAt);
        return draft;
    }

    private String buildDraftTitle(String sourceTitle) {
        String title = StringUtils.hasText(sourceTitle) ? sourceTitle : DEFAULT_AI_RESUME_TITLE;
        String suffix = " - AI优化草稿";
        int maxLength = 128;
        if (title.endsWith(suffix)) {
            return title;
        }
        int allowedTitleLength = maxLength - suffix.length();
        if (title.length() > allowedTitleLength) {
            title = title.substring(0, allowedTitleLength);
        }
        return title + suffix;
    }

    private void copyProjects(Long sourceResumeId, Long draftResumeId) {
        List<ResumeProject> projects = projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, sourceResumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO)
                .orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
        for (ResumeProject source : projects) {
            ResumeProject draft = new ResumeProject();
            draft.setResumeId(draftResumeId);
            draft.setProjectName(source.getProjectName());
            draft.setProjectPeriod(source.getProjectPeriod());
            draft.setProjectBackground(source.getProjectBackground());
            draft.setRole(source.getRole());
            draft.setTechStack(source.getTechStack());
            draft.setResponsibility(source.getResponsibility());
            draft.setCoreFeatures(source.getCoreFeatures());
            draft.setTechnicalDifficulties(source.getTechnicalDifficulties());
            draft.setOptimizationResults(source.getOptimizationResults());
            draft.setDescription(source.getDescription());
            draft.setHighlights(source.getHighlights());
            draft.setSort(source.getSort());
            draft.setSortOrder(source.getSortOrder());
            projectMapper.insert(draft);
        }
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

    private OptimizeContext createOptimizeRecord(Long resumeId, ResumeOptimizeRequestDTO dto) {
        Long userId = requireCurrentUserId();
        Resume resume = getOwnedResume(resumeId, userId);
        List<ResumeProject> selectedProjects = selectProjects(resumeId, dto.getSelectedProjectIds());
        String targetPosition = firstText(dto.getTargetPosition(), resume.getTargetPosition());

        ResumeOptimizeRecord record = new ResumeOptimizeRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setTargetPosition(targetPosition);
        record.setExperienceYears(dto.getExperienceYears());
        record.setIndustryDirection(dto.getIndustryDirection());
        record.setOptimizeStatus(ResumeOptimizeStatus.PROCESSING.getCode());
        optimizeRecordMapper.insert(record);

        ResumeOptimizeAiRequestDTO aiRequest = buildOptimizeAiRequest(record.getId(), userId, resume, selectedProjects,
                targetPosition, dto);
        record.setRequestJson(toJson(aiRequest));
        optimizeRecordMapper.updateById(record);
        return new OptimizeContext(resume, selectedProjects, record, aiRequest);
    }

    private List<ResumeProject> selectProjects(Long resumeId, List<Long> selectedProjectIds) {
        LambdaQueryWrapper<ResumeProject> query = new LambdaQueryWrapper<ResumeProject>()
                .eq(ResumeProject::getResumeId, resumeId)
                .eq(ResumeProject::getDeleted, CommonConstants.NO);
        if (selectedProjectIds != null && !selectedProjectIds.isEmpty()) {
            Set<Long> distinctIds = new LinkedHashSet<>(selectedProjectIds);
            query.in(ResumeProject::getId, distinctIds);
            List<ResumeProject> projects = projectMapper.selectList(query.orderByAsc(ResumeProject::getSortOrder)
                    .orderByAsc(ResumeProject::getSort)
                    .orderByDesc(ResumeProject::getUpdatedAt));
            if (projects.size() != distinctIds.size()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Selected projects do not belong to the resume");
            }
            return projects;
        }
        return projectMapper.selectList(query.orderByAsc(ResumeProject::getSortOrder)
                .orderByAsc(ResumeProject::getSort)
                .orderByDesc(ResumeProject::getUpdatedAt));
    }

    private ResumeOptimizeAiRequestDTO buildOptimizeAiRequest(Long optimizeRecordId, Long userId, Resume resume,
                                                             List<ResumeProject> projects, String targetPosition,
                                                             ResumeOptimizeRequestDTO dto) {
        ResumeOptimizeAiRequestDTO request = new ResumeOptimizeAiRequestDTO();
        request.setOptimizeRecordId(optimizeRecordId);
        request.setUserId(userId);
        request.setResumeId(resume.getId());
        request.setTargetPosition(targetPosition);
        request.setExperienceYears(dto.getExperienceYears());
        request.setIndustryDirection(dto.getIndustryDirection());
        request.setTargetCompany(dto.getTargetCompany());
        request.setExtraRequirements(dto.getExtraRequirements());
        request.setOptimizeFocus(dto.getOptimizeFocus());
        request.setResume(toResumeSnapshot(resume));
        request.setProjects(projects.stream().map(this::toProjectSnapshot).toList());
        return request;
    }

    private ResumeOptimizeAiRequestDTO.ResumeSnapshot toResumeSnapshot(Resume resume) {
        ResumeOptimizeAiRequestDTO.ResumeSnapshot snapshot = new ResumeOptimizeAiRequestDTO.ResumeSnapshot();
        snapshot.setRealName(resume.getRealName());
        snapshot.setTargetPosition(resume.getTargetPosition());
        snapshot.setSkillStack(resume.getSkillStack());
        snapshot.setWorkExperience(resume.getWorkExperience());
        snapshot.setEducationExperience(resume.getEducationExperience());
        snapshot.setSummary(resume.getSummary());
        return snapshot;
    }

    private ResumeOptimizeAiRequestDTO.ProjectSnapshot toProjectSnapshot(ResumeProject project) {
        ResumeOptimizeAiRequestDTO.ProjectSnapshot snapshot = new ResumeOptimizeAiRequestDTO.ProjectSnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setProjectName(project.getProjectName());
        snapshot.setProjectBackground(project.getProjectBackground());
        snapshot.setRole(project.getRole());
        snapshot.setTechStack(project.getTechStack());
        snapshot.setResponsibility(project.getResponsibility());
        snapshot.setTechnicalDifficulties(project.getTechnicalDifficulties());
        snapshot.setOptimizationResults(project.getOptimizationResults());
        snapshot.setDescription(project.getDescription());
        snapshot.setHighlights(project.getHighlights());
        return snapshot;
    }

    private ResumeOptimizeRecord markOptimizeSuccess(Long recordId, String resultJson, Long aiCallLogId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectById(recordId);
        record.setResultJson(resultJson);
        record.setAiCallLogId(aiCallLogId);
        record.setOptimizeStatus(ResumeOptimizeStatus.SUCCESS.getCode());
        record.setErrorMessage(null);
        optimizeRecordMapper.updateById(record);
        return optimizeRecordMapper.selectById(recordId);
    }

    private ResumeOptimizeRecord markOptimizeFailed(Long recordId, RuntimeException ex) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectById(recordId);
        record.setOptimizeStatus(ResumeOptimizeStatus.FAILED.getCode());
        record.setErrorMessage(truncateErrorMessage(ex == null ? null : ex.getMessage()));
        optimizeRecordMapper.updateById(record);
        return optimizeRecordMapper.selectById(recordId);
    }

    private ResumeOptimizeSubmitVO toOptimizeSubmitVO(ResumeOptimizeRecord record, JsonNode resultJson) {
        ResumeOptimizeSubmitVO vo = new ResumeOptimizeSubmitVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setAiCallLogId(record.getAiCallLogId());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        vo.setResultJson(resultJson == null ? parseNullableJson(record.getResultJson()) : resultJson);
        vo.setErrorMessage(record.getErrorMessage());
        return vo;
    }

    private ResumeOptimizeRecordVO toOptimizeRecordVO(ResumeOptimizeRecord record) {
        JsonNode resultJson = parseNullableJson(record.getResultJson());
        ResumeOptimizeRecordVO vo = new ResumeOptimizeRecordVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetPosition(record.getTargetPosition());
        vo.setExperienceYears(record.getExperienceYears());
        vo.setIndustryDirection(record.getIndustryDirection());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        vo.setSummary(textField(resultJson, "overallComment"));
        vo.setOverallComment(textField(resultJson, "overallComment"));
        vo.setErrorMessage(record.getErrorMessage());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private ResumeOptimizeDetailVO toOptimizeDetailVO(ResumeOptimizeRecord record) {
        ResumeOptimizeDetailVO vo = new ResumeOptimizeDetailVO();
        vo.setOptimizeRecordId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setTargetPosition(record.getTargetPosition());
        vo.setExperienceYears(record.getExperienceYears());
        vo.setIndustryDirection(record.getIndustryDirection());
        vo.setOptimizeStatus(record.getOptimizeStatus());
        vo.setResultJson(parseNullableJson(record.getResultJson()));
        vo.setErrorMessage(record.getErrorMessage());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private JsonNode parseResultJson(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume optimize result is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            if (root == null || !root.isObject()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume optimize result must be a JSON object");
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume optimize result is not valid JSON");
        }
    }

    private JsonNode parseNullableJson(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            return root == null || !root.isObject() ? null : root;
        } catch (Exception ex) {
            return null;
        }
    }

    private String textField(JsonNode json, String fieldName) {
        if (json == null || !json.has(fieldName) || json.path(fieldName).isNull()) {
            return null;
        }
        return json.path(fieldName).isTextual() ? json.path(fieldName).asText() : json.path(fieldName).toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON serialization failed");
        }
    }

    private String truncateErrorMessage(String message) {
        String value = StringUtils.hasText(message) ? message : "Resume optimize failed";
        return value.length() <= MAX_ERROR_MESSAGE_LENGTH ? value : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
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
        return getOwnedResume(id, requireCurrentUserId());
    }

    private Resume getOwnedResume(Long id, Long userId) {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (resume == null) {
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
        if (project == null || !resumeId.equals(project.getResumeId())
                || CommonConstants.YES.equals(project.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume project not found");
        }
        return project;
    }

    private ResumeProject getOwnedProject(Long projectId) {
        ResumeProject project = projectMapper.selectById(projectId);
        if (project == null || CommonConstants.YES.equals(project.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume project not found");
        }
        getOwnedResume(project.getResumeId());
        return project;
    }

    private ResumeOptimizeRecord getOwnedOptimizeRecord(Long recordId, Long userId) {
        ResumeOptimizeRecord record = optimizeRecordMapper.selectOne(new LambdaQueryWrapper<ResumeOptimizeRecord>()
                .eq(ResumeOptimizeRecord::getId, recordId)
                .eq(ResumeOptimizeRecord::getUserId, userId)
                .eq(ResumeOptimizeRecord::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume optimize record not found");
        }
        return record;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
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

    private record OptimizeContext(Resume resume, List<ResumeProject> projects, ResumeOptimizeRecord record,
                                   ResumeOptimizeAiRequestDTO aiRequest) {
    }
}
