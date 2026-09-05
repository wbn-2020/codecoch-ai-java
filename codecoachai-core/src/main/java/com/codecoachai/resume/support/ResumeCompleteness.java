package com.codecoachai.resume.support;

import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.vo.ResumeProjectVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * A presentation-only completeness contract. Resume persistence remains permissive so
 * candidates can save an incomplete draft and finish it later.
 */
public final class ResumeCompleteness {

    private static final int SECTION_COUNT = 7;

    private ResumeCompleteness() {
    }

    public static Assessment assess(Resume resume, List<ResumeProjectVO> projects) {
        List<String> missingSections = new ArrayList<>();
        int completeCount = 0;
        if (resume != null && StringUtils.hasText(resume.getTitle())) {
            completeCount++;
        } else {
            missingSections.add("简历名称");
        }
        if (resume != null && StringUtils.hasText(resume.getRealName())) {
            completeCount++;
        } else {
            missingSections.add("真实姓名");
        }
        if (resume != null && StringUtils.hasText(resume.getTargetPosition())) {
            completeCount++;
        } else {
            missingSections.add("求职方向");
        }
        if (resume != null && StringUtils.hasText(resume.getSkillStack())) {
            completeCount++;
        } else {
            missingSections.add("核心技术栈");
        }
        if (resume != null && (StringUtils.hasText(resume.getSummary())
                || StringUtils.hasText(resume.getWorkExperience()))) {
            completeCount++;
        } else {
            missingSections.add("个人摘要或工作经历");
        }
        if (resume != null && StringUtils.hasText(resume.getEducationExperience())) {
            completeCount++;
        } else {
            missingSections.add("教育经历");
        }
        if (projects != null && !projects.isEmpty()) {
            completeCount++;
        } else {
            missingSections.add("项目经历");
        }
        return new Assessment(Math.round(completeCount * 100.0f / SECTION_COUNT), missingSections);
    }

    public record Assessment(int completionPercent, List<String> missingSections) {

        public boolean isDraft() {
            return !missingSections.isEmpty();
        }
    }
}
