package com.codecoachai.resume.convert;

import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import java.util.List;

public final class ResumeConvert {

    private ResumeConvert() {
    }

    public static ResumeListVO toListVO(Resume resume) {
        ResumeListVO vo = new ResumeListVO();
        vo.setId(resume.getId());
        vo.setTitle(resume.getTitle());
        vo.setRealName(resume.getRealName());
        vo.setTargetPosition(resume.getTargetPosition());
        vo.setIsDefault(resume.getIsDefault());
        vo.setStatus(resume.getStatus());
        vo.setUpdatedAt(resume.getUpdatedAt());
        return vo;
    }

    public static ResumeDetailVO toDetailVO(Resume resume, List<ResumeProjectVO> projects) {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId(resume.getId());
        vo.setUserId(resume.getUserId());
        vo.setTitle(resume.getTitle());
        vo.setRealName(resume.getRealName());
        vo.setEmail(resume.getEmail());
        vo.setPhone(resume.getPhone());
        vo.setTargetPosition(resume.getTargetPosition());
        vo.setSkillStack(resume.getSkillStack());
        vo.setWorkExperience(resume.getWorkExperience());
        vo.setEducationExperience(resume.getEducationExperience());
        vo.setSummary(resume.getSummary());
        vo.setIsDefault(resume.getIsDefault());
        vo.setStatus(resume.getStatus());
        vo.setProjects(projects);
        return vo;
    }

    public static InnerResumeDetailVO toInnerVO(Resume resume, List<ResumeProjectVO> projects) {
        InnerResumeDetailVO vo = new InnerResumeDetailVO();
        vo.setId(resume.getId());
        vo.setUserId(resume.getUserId());
        vo.setTitle(resume.getTitle());
        vo.setRealName(resume.getRealName());
        vo.setTargetPosition(resume.getTargetPosition());
        vo.setSkillStack(resume.getSkillStack());
        vo.setWorkExperience(resume.getWorkExperience());
        vo.setEducationExperience(resume.getEducationExperience());
        vo.setSummary(resume.getSummary());
        vo.setProjects(projects);
        return vo;
    }

    public static ResumeProjectVO toProjectVO(ResumeProject project) {
        ResumeProjectVO vo = new ResumeProjectVO();
        vo.setId(project.getId());
        vo.setResumeId(project.getResumeId());
        vo.setProjectName(project.getProjectName());
        vo.setProjectPeriod(project.getProjectPeriod());
        vo.setProjectBackground(project.getProjectBackground());
        vo.setRole(project.getRole());
        vo.setTechStack(project.getTechStack());
        vo.setResponsibility(project.getResponsibility());
        vo.setCoreFeatures(project.getCoreFeatures());
        vo.setTechnicalDifficulties(project.getTechnicalDifficulties());
        vo.setOptimizationResults(project.getOptimizationResults());
        vo.setDescription(project.getDescription());
        vo.setHighlights(project.getHighlights());
        vo.setSort(project.getSort());
        vo.setSortOrder(project.getSortOrder());
        return vo;
    }
}
