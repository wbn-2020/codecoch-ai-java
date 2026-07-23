package com.codecoachai.resume.careerinterview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_interview_round")
public class CareerInterviewRound extends BaseEntity {
    private Long processId;
    private Integer roundNo;
    private String roundType;
    private String title;
    private String timezone;
    private LocalDateTime scheduledStartsAtUtc;
    private LocalDateTime scheduledEndsAtUtc;
    private Long calendarEventId;
    private String preparationSourceHash;
    private Long rescheduledFromRoundId;
    private String status;
    private String resultSummary;
    private String nextStep;
    private Integer lockVersion;
}
