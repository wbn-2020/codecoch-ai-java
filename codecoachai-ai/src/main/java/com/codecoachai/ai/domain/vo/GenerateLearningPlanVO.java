package com.codecoachai.ai.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class GenerateLearningPlanVO {

    private String planTitle;
    private String planSummary;
    private Integer durationDays;
    private List<StageVO> stages;
    private Long aiCallLogId;

    @Data
    public static class StageVO {
        private Integer stageNo;
        private String stageTitle;
        private List<ItemVO> items;
    }

    @Data
    public static class ItemVO {
        private Integer dayOffset;
        private String knowledgePoint;
        private String skillName;
        private String sourceGapId;
        private String taskTitle;
        private String taskDescription;
        private String taskType;
        private String priority;
        private Integer estimatedHours;
        private Integer estimatedMinutes;
        private String acceptance;
        private List<Long> relatedQuestionIds;
        private List<String> relatedTags;
        private List<String> resources;
    }
}
