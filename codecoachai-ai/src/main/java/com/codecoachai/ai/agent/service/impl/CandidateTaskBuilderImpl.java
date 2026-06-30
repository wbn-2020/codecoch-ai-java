package com.codecoachai.ai.agent.service.impl;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.ApplicationSnapshot;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext.TargetJobSnapshot;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CandidateTaskBuilderImpl implements CandidateTaskBuilder {

    private static final List<String> PRIORITY_SKILLS = List.of(
            "Spring Cloud", "Spring Boot", "Redis", "Kafka", "RocketMQ", "MySQL", "MyBatis",
            "JVM", "线程池", "并发", "Nacos", "Seata", "Elasticsearch", "Docker", "Kubernetes",
            "分布式事务", "分布式锁", "缓存一致性", "接口幂等", "微服务");

    @Override
    public List<CandidateTask> build(JobCoachAgentContext context, int taskCount) {
        List<CandidateTask> candidates = new ArrayList<>();
        candidates.addAll(applicationFollowUpTasks(context));
        TargetJobSnapshot target = context.getTargetJob();
        List<String> skills = inferSkillNames(target);
        String skillName = skills.get(0);
        String secondarySkill = skills.size() > 1 ? skills.get(1) : skillName;
        String skillCode = toSkillCode(skillName);
        String secondarySkillCode = toSkillCode(secondarySkill);
        String targetTitle = target == null ? null : target.getJobTitle();
        Long targetJobId = context.getTargetJobId();

        candidates.add(task("q-practice-1", "QUESTION_PRACTICE", "练习 " + skillName + " 高频面试题",
                "完成一组围绕 " + skillName + " 的题目练习，并记录没有答完整的知识点。",
                "目标岗位或岗位分析中出现了 " + skillName + "，先用题目练习找出当前短板。", "HIGH", 30,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                practiceActionUrl(targetJobId, skillName)));
        candidates.add(task("resume-optimize-1", "RESUME_OPTIMIZE", "补齐 " + skillName + " 的项目表达",
                "检查项目经历里是否写清了 " + skillName + " 的使用场景、个人职责、难点和结果。",
                "目标岗位和岗位要求需要清晰的项目经历支撑；把岗位技能转成简历里的项目表达。", "MEDIUM", 25,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                "/resumes"));
        candidates.add(task("interview-1", "INTERVIEW", "围绕" + firstText(targetTitle, "当前目标岗位") + "做模拟面试",
                "重点练项目深挖、技术追问和岗位匹配表达，结束后根据报告继续补短板。",
                "模拟面试能验证你是否能把 " + skillName + " 和项目经历讲清楚。", "HIGH", 40,
                skillCode, skillName, "TARGET_JOB", targetJobId,
                "/interviews/create?targetJobId=" + targetJobId));
        candidates.add(task("skill-review-1", "SKILL_REVIEW", "复盘 " + secondarySkill + " 场景题",
                "整理核心概念、常见追问、项目落地场景和容易说错的边界。",
                secondarySkill.equals(skillName)
                        ? "练习前先做短复盘，可以让回答更有结构。"
                        : "岗位要求同时关注 " + secondarySkill + "，今天顺手补一块次优先技能。",
                "MEDIUM", 20,
                secondarySkillCode, secondarySkill, "TARGET_JOB", targetJobId,
                "/skill-profile"));
        candidates.add(task("knowledge-review-1", "KNOWLEDGE_REVIEW", "整理 " + skillName + " 的可复用回答素材",
                "从项目经历、历史训练或面试工具里提取 2-3 条能直接用于面试表达的例子。",
                "把零散记录沉淀成面试可复用素材，后续练习和模拟面试都能继续使用。", "LOW", 20,
                skillCode, skillName, "TRAINING_MATERIAL", null,
                "/tools"));
        int size = Math.min(Math.max(taskCount, 1) + 1, candidates.size());
        return candidates.subList(0, size);
    }

    private List<CandidateTask> applicationFollowUpTasks(JobCoachAgentContext context) {
        if (context == null || context.getApplications() == null || context.getApplications().isEmpty()) {
            return List.of();
        }
        return context.getApplications().stream()
                .filter(this::isActionableApplication)
                .sorted(Comparator.comparingInt(this::applicationFollowUpRank)
                        .thenComparing(ApplicationSnapshot::getNextFollowUpAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ApplicationSnapshot::getId))
                .limit(2)
                .map(this::applicationFollowUpTask)
                .toList();
    }

    private boolean isActionableApplication(ApplicationSnapshot application) {
        if (application == null || application.getId() == null) {
            return false;
        }
        String status = normalizeStatus(application.getStatus());
        return !"REJECTED".equals(status) && !"CLOSED".equals(status);
    }

    private int applicationFollowUpRank(ApplicationSnapshot application) {
        if (Boolean.TRUE.equals(application.getFollowUpOverdue())) {
            return 0;
        }
        if (Boolean.TRUE.equals(application.getFollowUpDueToday())) {
            return 1;
        }
        if ("INTERVIEWING".equals(normalizeStatus(application.getStatus()))) {
            return 2;
        }
        if (application.getNextFollowUpAt() != null) {
            return 3;
        }
        return 4;
    }

    private CandidateTask applicationFollowUpTask(ApplicationSnapshot application) {
        String companyName = firstText(application.getCompanyName(), "目标公司");
        String jobTitle = firstText(application.getJobTitle(), "投递岗位");
        return task("application-follow-up-" + application.getId(),
                AgentTaskTypeEnum.APPLICATION_FOLLOW_UP.name(),
                "跟进" + companyName + "的" + jobTitle + "投递",
                "查看这条投递的最新状态，补充沟通记录，并安排下一次跟进。",
                applicationFollowUpReason(application),
                applicationFollowUpPriority(application),
                15,
                null,
                null,
                "JOB_APPLICATION",
                application.getId(),
                "/applications");
    }

    private String applicationFollowUpReason(ApplicationSnapshot application) {
        String evidence = applicationEvidence(application);
        String baseReason;
        if (Boolean.TRUE.equals(application.getFollowUpOverdue())) {
            baseReason = "这条投递的跟进时间已经逾期，今天优先补一次沟通记录。";
        } else if (Boolean.TRUE.equals(application.getFollowUpDueToday())) {
            baseReason = "这条投递今天需要跟进，及时确认进展可以减少遗漏。";
        } else if ("INTERVIEWING".equals(normalizeStatus(application.getStatus()))) {
            baseReason = "当前处于面试流程，适合同步准备进展和下一步安排。";
        } else if (application.getNextFollowUpAt() != null) {
            baseReason = "这条投递已有下次跟进时间，提前整理沟通要点。";
        } else {
            baseReason = "这条投递仍在流程中，保持轻量跟进有助于推动反馈。";
        }
        return StringUtils.hasText(evidence) ? baseReason + " " + evidence : baseReason;
    }

    private String applicationEvidence(ApplicationSnapshot application) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(application.getLatestEventSummary())) {
            parts.add("最近记录：" + trimToLength(application.getLatestEventSummary(), 48));
        } else if (StringUtils.hasText(application.getLatestEventType())) {
            parts.add("最近事件：" + application.getLatestEventType());
        }
        String versionName = firstText(application.getResumeVersionName(),
                application.getResumeVersionNo() == null ? null : "v" + application.getResumeVersionNo());
        if (StringUtils.hasText(versionName)) {
            parts.add("关联简历版本：" + trimToLength(versionName, 32));
        }
        return String.join("；", parts);
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String applicationFollowUpPriority(ApplicationSnapshot application) {
        return Boolean.TRUE.equals(application.getFollowUpOverdue())
                || Boolean.TRUE.equals(application.getFollowUpDueToday()) ? "HIGH" : "MEDIUM";
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "";
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

    private String practiceActionUrl(Long targetJobId, String skillName) {
        String encodedSkill = URLEncoder.encode(firstText(skillName, "Java 后端"), StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder("/questions/practice?mode=category")
                .append("&keyword=").append(encodedSkill)
                .append("&skillName=").append(encodedSkill)
                .append("&topic=").append(encodedSkill)
                .append("&sourceType=TARGET_JOB")
                .append("&trustStatus=VERIFIED")
                .append("&fallback=false");
        if (targetJobId != null) {
            builder.append("&targetJobId=").append(targetJobId);
        }
        return builder.toString();
    }

    private List<String> inferSkillNames(TargetJobSnapshot target) {
        String evidence = evidenceText(target);
        List<String> skills = new ArrayList<>();
        for (String skill : PRIORITY_SKILLS) {
            if (containsNormalized(evidence, skill) && !skills.contains(skill)) {
                skills.add(skill);
            }
        }
        if (skills.isEmpty()) {
            if (containsNormalized(evidence, "Java")) {
                skills.add("Java 后端");
            } else if (target != null && StringUtils.hasText(target.getJobTitle())) {
                skills.add(target.getJobTitle().trim());
            } else {
                skills.add("Java 后端");
            }
        }
        return skills;
    }

    private String toSkillCode(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return "java.backend";
        }
        return switch (skillName) {
            case "Java 后端" -> "java.backend";
            case "线程池" -> "java.thread-pool";
            case "并发" -> "java.concurrent";
            case "分布式事务" -> "distributed.transaction";
            case "分布式锁" -> "distributed.lock";
            case "缓存一致性" -> "cache.consistency";
            case "接口幂等" -> "api.idempotency";
            case "微服务" -> "microservice";
            default -> skillName.toLowerCase(Locale.ROOT).replace(" ", ".").replace("/", ".");
        };
    }

    private String evidenceText(TargetJobSnapshot target) {
        if (target == null) {
            return "";
        }
        return String.join("\n",
                firstText(target.getJobTitle(), ""),
                firstText(target.getAnalysisSummary(), ""),
                target.getRequiredSkills() == null ? "" : target.getRequiredSkills().toString(),
                target.getInterviewFocusPoints() == null ? "" : target.getInterviewFocusPoints().toString());
    }

    private boolean containsNormalized(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return normalize(text).contains(normalize(keyword));
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .replace("/", "");
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
