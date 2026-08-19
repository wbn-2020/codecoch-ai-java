package com.codecoachai.resume.convert;

import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.enums.ResumeContextEligibility;
import com.codecoachai.resume.domain.vo.InnerResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeDetailVO;
import com.codecoachai.resume.domain.vo.ResumeListVO;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import com.codecoachai.resume.support.ResumeCompleteness;
import java.util.List;

public final class ResumeConvert {

    private ResumeConvert() {
    }

    public static ResumeListVO toListVO(Resume resume) {
        return toListVO(resume, null);
    }

    public static ResumeListVO toListVO(Resume resume, Long projectCount) {
        ResumeListVO vo = new ResumeListVO();
        vo.setId(resume.getId());
        vo.setTitle(resume.getTitle());
        vo.setRealName(resume.getRealName());
        vo.setTargetPosition(resume.getTargetPosition());
        vo.setSkillStack(resume.getSkillStack());
        vo.setSummary(resume.getSummary());
        vo.setProjectCount(projectCount);
        vo.setIsDefault(resume.getIsDefault());
        vo.setStatus(resume.getStatus());
        vo.setUpdatedAt(resume.getUpdatedAt());
        applyContextEligibility(vo, ResumeContextEligibility.assess(resume));
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
        applyContextEligibility(vo, ResumeContextEligibility.assess(resume));
        ResumeCompleteness.Assessment completeness = ResumeCompleteness.assess(resume, projects);
        vo.setDraft(completeness.isDraft());
        vo.setCompletionPercent(completeness.completionPercent());
        vo.setMissingSections(completeness.missingSections());
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
        applyContextEligibility(vo, ResumeContextEligibility.assess(resume));
        return vo;
    }

    private static void applyContextEligibility(ResumeListVO vo, ResumeContextEligibility.Assessment assessment) {
        vo.setContextEligibility(assessment.status().name());
        vo.setContextEligibilityReason(assessment.reasonCode());
    }

    private static void applyContextEligibility(ResumeDetailVO vo, ResumeContextEligibility.Assessment assessment) {
        vo.setContextEligibility(assessment.status().name());
        vo.setContextEligibilityReason(assessment.reasonCode());
    }

    private static void applyContextEligibility(InnerResumeDetailVO vo, ResumeContextEligibility.Assessment assessment) {
        vo.setContextEligibility(assessment.status().name());
        vo.setContextEligibilityReason(assessment.reasonCode());
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
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        return vo;
    }
}
