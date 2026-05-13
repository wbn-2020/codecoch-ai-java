package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.convert.ResumeConvert;
import com.codecoachai.resume.domain.dto.ResumeProjectSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeSaveDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.service.ResumeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;

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

    private List<ResumeProjectVO> projects(Long resumeId) {
        return projectMapper.selectList(new LambdaQueryWrapper<ResumeProject>()
                        .eq(ResumeProject::getResumeId, resumeId)
                        .orderByAsc(ResumeProject::getSort)
                        .orderByDesc(ResumeProject::getUpdatedAt))
                .stream()
                .map(ResumeConvert::toProjectVO)
                .toList();
    }

    private void applyResume(Resume resume, ResumeSaveDTO dto) {
        resume.setTitle(dto.getTitle());
        resume.setRealName(dto.getRealName());
        resume.setEmail(dto.getEmail());
        resume.setPhone(dto.getPhone());
        resume.setSummary(dto.getSummary());
    }

    private void applyProject(ResumeProject project, ResumeProjectSaveDTO dto) {
        project.setProjectName(dto.getProjectName());
        project.setRole(dto.getRole());
        project.setTechStack(dto.getTechStack());
        project.setDescription(dto.getDescription());
        project.setHighlights(dto.getHighlights());
        project.setSort(dto.getSort() == null ? 0 : dto.getSort());
    }

    private Resume getOwnedResume(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || !requireCurrentUserId().equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume not found");
        }
        return resume;
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
