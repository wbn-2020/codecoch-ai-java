package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.TargetJobSnapshot;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CandidateTaskBuilderImpl implements CandidateTaskBuilder {

    @Override
    public List<CandidateTask> build(JobCoachAgentContext context, int taskCount) {
        List<CandidateTask> candidates = new ArrayList<>();
        TargetJobSnapshot target = context.getTargetJob();
        String skillName = inferSkillName(target);
        String skillCode = toSkillCode(skillName);
        Long targetJobId = context.getTargetJobId();

        candidates.add(task("q-practice-1", "QUESTION_PRACTICE", "Practice " + skillName + " interview questions",
                "Complete a focused question-practice set and record weak points.",
                "The target job requires repeated practice around high-frequency skills.", "HIGH", 30,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                "/questions/recommendations?targetJobId=" + targetJobId));
        candidates.add(task("resume-optimize-1", "RESUME_OPTIMIZE", "Improve resume evidence for " + skillName,
                "Review whether project experience clearly proves the target skill and impact.",
                "Resume wording should match the target job requirements more directly.", "MEDIUM", 25,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                "/resumes"));
        candidates.add(task("interview-1", "INTERVIEW", "Run a target-job mock interview",
                "Practice project deep-dive and technical follow-up questions for the target job.",
                "A mock interview can expose expression gaps and knowledge blind spots.", "HIGH", 40,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                "/interviews/create?targetJobId=" + targetJobId));
        candidates.add(task("skill-review-1", "SKILL_REVIEW", "Review core " + skillName + " concepts",
                "Summarize concepts, scenarios, common mistakes, and project-ready explanations.",
                "A short review before practice improves task efficiency.", "MEDIUM", 20,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                "/skill-profile"));
        candidates.add(task("knowledge-review-1", "KNOWLEDGE_REVIEW", "Connect personal notes with " + skillName,
                "Search personal notes and extract reusable project examples or interview talking points.",
                "Personal knowledge should be reused by the Agent plan instead of staying isolated.", "LOW", 20,
                skillCode, skillName, "PERSONAL_KNOWLEDGE", null,
                "/knowledge"));
        int size = Math.min(Math.max(taskCount, 1) + 1, candidates.size());
        return candidates.subList(0, size);
    }

    private CandidateTask task(String candidateId, String type, String title, String description, String reason,
                               String priority, Integer estimatedMinutes, String skillCode, String skillName,
                               String relatedBizType, Long relatedBizId, String actionUrl) {
        CandidateTask task = new CandidateTask();
        task.setCandidateId(candidateId);
        task.setType(type);
        task.setTitle(title);
        task.setDescription(description);
        task.setReason(reason);
        task.setPriority(priority);
        task.setEstimatedMinutes(estimatedMinutes);
        task.setRelatedSkillCode(skillCode);
        task.setRelatedSkillName(skillName);
        task.setRelatedBizType(relatedBizType);
        task.setRelatedBizId(relatedBizId);
        task.setActionUrl(actionUrl);
        return task;
    }

    private String inferSkillName(TargetJobSnapshot target) {
        if (target == null || !StringUtils.hasText(target.getJobTitle())) {
            return "Java Backend";
        }
        String title = target.getJobTitle().toLowerCase();
        if (title.contains("redis")) {
            return "Redis";
        }
        if (title.contains("mysql")) {
            return "MySQL";
        }
        if (title.contains("spring")) {
            return "Spring Boot";
        }
        if (title.contains("java")) {
            return "Java Backend";
        }
        return target.getJobTitle();
    }

    private String toSkillCode(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return "java.backend";
        }
        return skillName.toLowerCase().replace(" ", ".").replace("/", ".");
    }
}
