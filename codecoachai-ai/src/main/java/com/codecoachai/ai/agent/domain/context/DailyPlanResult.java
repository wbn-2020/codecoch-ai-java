package com.codecoachai.ai.agent.domain.context;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DailyPlanResult {

    private String summary;
    private List<FocusSkill> focusSkills = new ArrayList<>();
    private List<PlanTask> tasks = new ArrayList<>();

    @Data
    public static class FocusSkill {
        private String code;
        private String name;
    }

    @Data
    public static class PlanTask {
        private String candidateId;
        private String type;
        private String title;
        private String description;
        private String reason;
        private String priority;
        private Integer estimatedMinutes;
        private String relatedSkillCode;
        private String relatedSkillName;
        private String relatedBizType;
        private Long relatedBizId;
        private String actionUrl;
    }
}
