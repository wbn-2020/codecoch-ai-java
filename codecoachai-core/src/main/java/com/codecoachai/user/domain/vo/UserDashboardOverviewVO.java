package com.codecoachai.user.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class UserDashboardOverviewVO {

    private Long resumeCount;
    private RecentResumeParseVO recentResumeParse;
    private RecentResumeOptimizeVO recentResumeOptimize;
    private Long interviewCount;
    private RecentInterviewVO recentInterview;
    private RecentReportVO recentReport;
    private Long studyPlanCount;
    private ActiveStudyPlanVO activeStudyPlan;
    private Long todayTaskCount;
    private Long todayCompletedTaskCount;
    private List<EntryStatusVO> entryStatuses;
    private LocalDateTime generatedAt;

    @Data
    public static class RecentResumeParseVO {
        private Long analysisRecordId;
        private Long resumeId;
        private String fileName;
        private String parseStatus;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class RecentResumeOptimizeVO {
        private Long optimizeRecordId;
        private Long resumeId;
        private String optimizeStatus;
        private Long aiCallLogId;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class RecentInterviewVO {
        private Long interviewId;
        private String title;
        private String status;
        private String reportStatus;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class RecentReportVO {
        private Long reportId;
        private Long interviewId;
        private String status;
        private Integer totalScore;
        private LocalDateTime generatedAt;
    }

    @Data
    public static class ActiveStudyPlanVO {
        private Long planId;
        private String planTitle;
        private String planStatus;
        private Integer totalTaskCount;
        private Integer doneTaskCount;
        private Integer progressPercent;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class EntryStatusVO {
        private String key;
        private String status;
        private String reason;
        private Long relatedId;
    }
}
