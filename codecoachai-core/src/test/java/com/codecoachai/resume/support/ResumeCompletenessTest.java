package com.codecoachai.resume.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeCompletenessTest {

    @Test
    void titleOnlyResumeIsARecoverableDraftWithActionableMissingSections() {
        Resume resume = new Resume();
        resume.setTitle("Java 后端简历");

        ResumeCompleteness.Assessment result = ResumeCompleteness.assess(resume, List.of());

        assertTrue(result.isDraft());
        assertEquals(14, result.completionPercent());
        assertEquals(List.of("真实姓名", "求职方向", "核心技术栈", "个人摘要或工作经历", "教育经历", "项目经历"),
                result.missingSections());
    }

    @Test
    void completeResumeIsNotReportedAsDraft() {
        Resume resume = new Resume();
        resume.setTitle("Java 后端简历");
        resume.setRealName("李明");
        resume.setTargetPosition("Java 后端工程师");
        resume.setSkillStack("Java, Spring Boot");
        resume.setSummary("负责订单和库存系统的设计与交付。");
        resume.setEducationExperience("本科，计算机科学与技术");
        ResumeProjectVO project = new ResumeProjectVO();
        project.setProjectName("CodeCoachAI");

        ResumeCompleteness.Assessment result = ResumeCompleteness.assess(resume, List.of(project));

        assertFalse(result.isDraft());
        assertEquals(100, result.completionPercent());
        assertTrue(result.missingSections().isEmpty());
    }
}
