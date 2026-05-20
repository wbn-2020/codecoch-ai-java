package com.codecoachai.user.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class V3DashboardVO {

    private TargetJobCardVO currentTargetJob;
    private MatchSummaryVO latestMatch;
    private SkillProfileSummaryVO skillProfile;
    private StudyProgressVO studyProgress;
    private List<Map<String, Object>> recommendedQuestions;
    private List<TrendItemVO> trainingTrend;
    private List<NextActionVO> nextActions;
    private Boolean degraded;
    private List<String> governanceTips;
    private LocalDateTime generatedAt;

    @Data
    public static class TargetJobCardVO {
        private Long id;
        private String jobTitle;
        private String companyName;
        private String jobLevel;
        private String status;
        private Boolean current;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class MatchSummaryVO {
        private Long reportId;
        private Long resumeId;
        private Long targetJobId;
        private Integer overallScore;
        private String status;
        private String summary;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class SkillProfileSummaryVO {
        private Long profileId;
        private Long targetJobId;
        private Integer overallScore;
        private Integer overallLevel;
        private String summary;
        private List<Map<String, Object>> radar;
        private List<Map<String, Object>> gaps;
    }

    @Data
    public static class StudyProgressVO {
        private Long activePlanId;
        private Long totalTasks;
        private Long completedTasks;
        private Integer completionRate;
    }

    @Data
    public static class TrendItemVO {
        private LocalDate date;
        private Long interviewCount;
        private Long completedCount;
        private Long averageScore;
    }

    @Data
    public static class NextActionVO {
        private String actionType;
        private String title;
        private String description;
        private String targetPath;
        private Integer priority;
    }
}
