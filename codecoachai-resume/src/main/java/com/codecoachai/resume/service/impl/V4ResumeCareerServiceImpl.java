package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO;
import com.codecoachai.resume.domain.dto.JobApplicationSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeApplyAiSuggestionDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCopyDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCreateDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeSuggestionAdoption;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.domain.vo.ResumeSuggestionAdoptionVO;
import com.codecoachai.resume.domain.vo.ResumeVersionDiffVO;
import com.codecoachai.resume.domain.vo.ResumeVersionVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionAdoptionMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class V4ResumeCareerServiceImpl implements V4ResumeCareerService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "title", "realName", "email", "phone", "targetPosition", "skillStack",
            "workExperience", "educationExperience", "summary");

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final ResumeSuggestionAdoptionMapper resumeSuggestionAdoptionMapper;
    private final JobApplicationEventMapper jobApplicationEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersionVO createVersion(Long resumeId, ResumeVersionCreateDTO dto) {
        Resume resume = ownedResume(resumeId);
        Integer nextNo = nextVersionNo(resumeId);
        ResumeVersion version = new ResumeVersion();
        version.setUserId(resume.getUserId());
        version.setResumeId(resumeId);
        version.setVersionNo(nextNo);
        version.setVersionName(StringUtils.hasText(dto == null ? null : dto.getVersionName()) ? dto.getVersionName() : "V" + nextNo);
        version.setSourceType(StringUtils.hasText(dto == null ? null : dto.getSourceType()) ? dto.getSourceType() : "MANUAL");
        version.setSourceId(dto == null ? null : dto.getSourceId());
        version.setSnapshotJson(writeJson(snapshot(resume)));
        version.setCurrentFlag(1);
        clearCurrentVersions(resumeId);
        resumeVersionMapper.insert(version);
        return toVersionVO(version);
    }

    @Override
    public ResumeVersionVO copyVersion(Long resumeId, Long versionId, ResumeVersionCopyDTO dto) {
        Resume resume = ownedResume(resumeId);
        ResumeVersion source = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, source);

        Integer nextNo = nextVersionNo(resumeId);
        String sourceName = StringUtils.hasText(source.getVersionName()) ? source.getVersionName() : "V" + source.getVersionNo();
        ResumeVersion copy = new ResumeVersion();
        copy.setUserId(resume.getUserId());
        copy.setResumeId(resumeId);
        copy.setVersionNo(nextNo);
        copy.setVersionName(StringUtils.hasText(dto == null ? null : dto.getVersionName())
                ? dto.getVersionName()
                : "Copy of " + sourceName);
        copy.setSourceType("COPY");
        copy.setSourceId(source.getId());
        copy.setSnapshotJson(StringUtils.hasText(source.getSnapshotJson()) ? source.getSnapshotJson() : writeJson(snapshot(resume)));
        copy.setCurrentFlag(0);
        resumeVersionMapper.insert(copy);
        return toVersionVO(copy);
    }

    @Override
    public List<ResumeVersionVO> listVersions(Long resumeId) {
        ownedResume(resumeId);
        return resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resumeId)
                        .orderByDesc(ResumeVersion::getVersionNo))
                .stream().map(this::toVersionVO).toList();
    }

    @Override
    public ResumeVersionVO getVersion(Long versionId) {
        return toVersionVO(ownedVersion(versionId));
    }

    @Override
    public ResumeVersionDiffVO diffVersion(Long resumeId, Long versionId) {
        Resume resume = ownedResume(resumeId);
        ResumeVersion version = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, version);
        return buildDiff(resumeId, null, versionId, "CURRENT_RESUME", "VERSION",
                snapshot(resume), readMap(version.getSnapshotJson()));
    }

    @Override
    public ResumeVersionDiffVO diffVersions(Long sourceVersionId, Long targetVersionId) {
        ResumeVersion source = ownedVersion(sourceVersionId);
        ResumeVersion target = ownedVersion(targetVersionId);
        if (!Objects.equals(source.getResumeId(), target.getResumeId())) {
            throw new IllegalArgumentException("Versions must belong to the same resume");
        }
        return buildDiff(source.getResumeId(), sourceVersionId, targetVersionId, "SOURCE_VERSION", "TARGET_VERSION",
                readMap(source.getSnapshotJson()), readMap(target.getSnapshotJson()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersionVO rollbackVersion(Long resumeId, Long versionId) {
        Resume resume = ownedResume(resumeId);
        ResumeVersion version = ownedVersion(versionId);
        ensureVersionBelongsToResume(resumeId, version);
        applySnapshot(resume, readMap(version.getSnapshotJson()));
        resumeMapper.updateById(resume);
        clearCurrentVersions(resumeId);
        version.setCurrentFlag(1);
        resumeVersionMapper.updateById(version);
        return toVersionVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSuggestionAdoptionVO applyAiSuggestion(Long versionId, ResumeApplyAiSuggestionDTO dto) {
        ResumeVersion version = ownedVersion(versionId);
        Resume resume = ownedResume(version.getResumeId());
        applySnapshot(resume, readMap(version.getSnapshotJson()));
        resumeMapper.updateById(resume);
        clearCurrentVersions(version.getResumeId());
        version.setCurrentFlag(1);
        resumeVersionMapper.updateById(version);

        ResumeSuggestionAdoption adoption = new ResumeSuggestionAdoption();
        adoption.setUserId(resume.getUserId());
        adoption.setResumeId(resume.getId());
        adoption.setOptimizeRecordId(resolveOptimizeRecordId(version, dto));
        adoption.setResumeVersionId(version.getId());
        adoption.setSuggestionType(StringUtils.hasText(dto == null ? null : dto.getSuggestionType())
                ? dto.getSuggestionType()
                : "AI_RESUME_VERSION");
        adoption.setStatus(StringUtils.hasText(dto == null ? null : dto.getStatus()) ? dto.getStatus() : "ADOPTED");
        adoption.setNote(dto == null ? null : dto.getNote());
        resumeSuggestionAdoptionMapper.insert(adoption);
        return toSuggestionAdoptionVO(adoption);
    }

    @Override
    public List<JobApplicationVO> listApplications(String status) {
        Long userId = LoginUserContext.getUserId();
        return jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(StringUtils.hasText(status), JobApplication::getStatus, status)
                        .orderByDesc(JobApplication::getUpdatedAt))
                .stream().map(this::toApplicationVO).toList();
    }

    @Override
    public JobApplicationVO createApplication(JobApplicationSaveDTO dto) {
        Long userId = LoginUserContext.getUserId();
        if (dto != null && dto.getResumeVersionId() != null) {
            ownedVersion(dto.getResumeVersionId());
        }
        JobApplication app = new JobApplication();
        app.setUserId(userId);
        fillApplication(app, dto);
        jobApplicationMapper.insert(app);
        return toApplicationVO(app);
    }

    @Override
    public JobApplicationVO updateApplication(Long id, JobApplicationSaveDTO dto) {
        JobApplication app = ownedApplication(id);
        if (dto != null && dto.getResumeVersionId() != null) {
            ownedVersion(dto.getResumeVersionId());
        }
        fillApplication(app, dto);
        jobApplicationMapper.updateById(app);
        return toApplicationVO(jobApplicationMapper.selectById(id));
    }

    @Override
    public List<JobApplicationEventVO> listApplicationEvents(Long applicationId) {
        JobApplication app = ownedApplication(applicationId);
        return jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                        .eq(JobApplicationEvent::getUserId, app.getUserId())
                        .eq(JobApplicationEvent::getApplicationId, applicationId)
                        .orderByDesc(JobApplicationEvent::getEventTime)
                        .orderByDesc(JobApplicationEvent::getCreatedAt))
                .stream().map(this::toApplicationEventVO).toList();
    }

    @Override
    public JobApplicationEventVO createApplicationEvent(Long applicationId, JobApplicationEventSaveDTO dto) {
        JobApplication app = ownedApplication(applicationId);
        JobApplicationEvent event = new JobApplicationEvent();
        event.setUserId(app.getUserId());
        event.setApplicationId(app.getId());
        event.setEventType(StringUtils.hasText(dto == null ? null : dto.getEventType()) ? dto.getEventType() : "NOTE");
        event.setEventTime(dto == null || dto.getEventTime() == null ? LocalDateTime.now() : dto.getEventTime());
        event.setSummary(dto == null ? null : dto.getSummary());
        event.setReviewJson(writeReviewJson(dto));
        jobApplicationEventMapper.insert(event);
        return toApplicationEventVO(event);
    }

    private Resume ownedResume(Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        Long userId = LoginUserContext.getUserId();
        if (resume == null || !Objects.equals(userId, resume.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历不存在或无权访问");
        }
        return resume;
    }

    private ResumeVersion ownedVersion(Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        Long userId = LoginUserContext.getUserId();
        if (version == null || !Objects.equals(userId, version.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历版本不存在或无权访问");
        }
        return version;
    }

    private JobApplication ownedApplication(Long applicationId) {
        JobApplication app = jobApplicationMapper.selectById(applicationId);
        Long userId = LoginUserContext.getUserId();
        if (app == null || !Objects.equals(userId, app.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "投递记录不存在或无权访问");
        }
        return app;
    }

    private void ensureVersionBelongsToResume(Long resumeId, ResumeVersion version) {
        if (!Objects.equals(resumeId, version.getResumeId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "简历版本不属于当前简历");
        }
    }

    private void clearCurrentVersions(Long resumeId) {
        resumeVersionMapper.update(null, new LambdaUpdateWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .set(ResumeVersion::getCurrentFlag, 0));
    }

    private Integer nextVersionNo(Long resumeId) {
        ResumeVersion latest = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .orderByDesc(ResumeVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private Map<String, Object> snapshot(Resume resume) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", resume.getTitle());
        map.put("realName", resume.getRealName());
        map.put("email", resume.getEmail());
        map.put("phone", resume.getPhone());
        map.put("targetPosition", resume.getTargetPosition());
        map.put("skillStack", resume.getSkillStack());
        map.put("workExperience", resume.getWorkExperience());
        map.put("educationExperience", resume.getEducationExperience());
        map.put("summary", resume.getSummary());
        return map;
    }

    private void applySnapshot(Resume resume, Map<String, Object> map) {
        resume.setTitle(text(map.get("title")));
        resume.setRealName(text(map.get("realName")));
        resume.setEmail(text(map.get("email")));
        resume.setPhone(text(map.get("phone")));
        resume.setTargetPosition(text(map.get("targetPosition")));
        resume.setSkillStack(text(map.get("skillStack")));
        resume.setWorkExperience(text(map.get("workExperience")));
        resume.setEducationExperience(text(map.get("educationExperience")));
        resume.setSummary(text(map.get("summary")));
    }

    private void fillApplication(JobApplication app, JobApplicationSaveDTO dto) {
        app.setTargetJobId(dto == null ? null : dto.getTargetJobId());
        app.setResumeVersionId(dto == null ? null : dto.getResumeVersionId());
        app.setCompanyName(dto == null ? null : dto.getCompanyName());
        app.setJobTitle(StringUtils.hasText(dto == null ? null : dto.getJobTitle()) ? dto.getJobTitle() : "Untitled Job");
        app.setSource(dto == null ? null : dto.getSource());
        app.setStatus(StringUtils.hasText(dto == null ? null : dto.getStatus()) ? dto.getStatus() : "SAVED");
        app.setAppliedAt(dto == null ? null : dto.getAppliedAt());
        app.setNextFollowUpAt(dto == null ? null : dto.getNextFollowUpAt());
        app.setNote(dto == null ? null : dto.getNote());
    }

    private ResumeVersionDiffVO buildDiff(Long resumeId, Long sourceVersionId, Long targetVersionId, String sourceLabel,
                                          String targetLabel, Map<String, Object> source, Map<String, Object> target) {
        ResumeVersionDiffVO vo = new ResumeVersionDiffVO();
        vo.setResumeId(resumeId);
        vo.setVersionId(targetVersionId);
        vo.setSourceVersionId(sourceVersionId);
        vo.setTargetVersionId(targetVersionId);
        vo.setSourceLabel(sourceLabel);
        vo.setTargetLabel(targetLabel);
        for (String field : SNAPSHOT_FIELDS) {
            Object sourceValue = source.get(field);
            Object targetValue = target.get(field);
            ResumeVersionDiffVO.FieldDiff diff = new ResumeVersionDiffVO.FieldDiff();
            diff.setField(field);
            diff.setCurrentValue(sourceValue);
            diff.setVersionValue(targetValue);
            diff.setSourceValue(sourceValue);
            diff.setTargetValue(targetValue);
            diff.setChanged(!Objects.equals(sourceValue, targetValue));
            vo.getFields().add(diff);
        }
        return vo;
    }

    private ResumeVersionVO toVersionVO(ResumeVersion version) {
        ResumeVersionVO vo = new ResumeVersionVO();
        vo.setId(version.getId());
        vo.setResumeId(version.getResumeId());
        vo.setVersionNo(version.getVersionNo());
        vo.setVersionName(version.getVersionName());
        vo.setSourceType(version.getSourceType());
        vo.setSourceId(version.getSourceId());
        vo.setCurrentFlag(version.getCurrentFlag());
        vo.setSnapshot(readMap(version.getSnapshotJson()));
        vo.setCreatedAt(version.getCreatedAt());
        return vo;
    }

    private JobApplicationVO toApplicationVO(JobApplication app) {
        JobApplicationVO vo = new JobApplicationVO();
        vo.setId(app.getId());
        vo.setTargetJobId(app.getTargetJobId());
        vo.setResumeVersionId(app.getResumeVersionId());
        vo.setCompanyName(app.getCompanyName());
        vo.setJobTitle(app.getJobTitle());
        vo.setSource(app.getSource());
        vo.setStatus(app.getStatus());
        vo.setAppliedAt(app.getAppliedAt());
        vo.setNextFollowUpAt(app.getNextFollowUpAt());
        vo.setNote(app.getNote());
        vo.setCreatedAt(app.getCreatedAt());
        vo.setUpdatedAt(app.getUpdatedAt());
        return vo;
    }

    private ResumeSuggestionAdoptionVO toSuggestionAdoptionVO(ResumeSuggestionAdoption adoption) {
        ResumeSuggestionAdoptionVO vo = new ResumeSuggestionAdoptionVO();
        vo.setId(adoption.getId());
        vo.setResumeId(adoption.getResumeId());
        vo.setOptimizeRecordId(adoption.getOptimizeRecordId());
        vo.setResumeVersionId(adoption.getResumeVersionId());
        vo.setSuggestionType(adoption.getSuggestionType());
        vo.setStatus(adoption.getStatus());
        vo.setNote(adoption.getNote());
        vo.setCreatedAt(adoption.getCreatedAt());
        vo.setUpdatedAt(adoption.getUpdatedAt());
        return vo;
    }

    private JobApplicationEventVO toApplicationEventVO(JobApplicationEvent event) {
        JobApplicationEventVO vo = new JobApplicationEventVO();
        vo.setId(event.getId());
        vo.setApplicationId(event.getApplicationId());
        vo.setEventType(event.getEventType());
        vo.setEventTime(event.getEventTime());
        vo.setSummary(event.getSummary());
        vo.setReviewJson(event.getReviewJson());
        vo.setReview(readMap(event.getReviewJson()));
        vo.setCreatedAt(event.getCreatedAt());
        vo.setUpdatedAt(event.getUpdatedAt());
        return vo;
    }

    private Long resolveOptimizeRecordId(ResumeVersion version, ResumeApplyAiSuggestionDTO dto) {
        if (dto != null && dto.getOptimizeRecordId() != null) {
            return dto.getOptimizeRecordId();
        }
        String sourceType = version.getSourceType();
        if (StringUtils.hasText(sourceType)
                && ("AI".equalsIgnoreCase(sourceType)
                || "OPTIMIZE".equalsIgnoreCase(sourceType)
                || "RESUME_OPTIMIZE".equalsIgnoreCase(sourceType))) {
            return version.getSourceId();
        }
        return null;
    }

    private String writeReviewJson(JobApplicationEventSaveDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getReview() != null) {
            return writeJson(dto.getReview());
        }
        return StringUtils.hasText(dto.getReviewJson()) ? dto.getReviewJson() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
