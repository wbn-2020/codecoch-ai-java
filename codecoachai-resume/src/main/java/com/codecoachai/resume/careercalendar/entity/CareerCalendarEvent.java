package com.codecoachai.resume.careercalendar.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_calendar_event")
public class CareerCalendarEvent extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private String title;
    private String eventType;
    private LocalDateTime startsAtUtc;
    private LocalDateTime endsAtUtc;
    private String timezone;
    private Integer allDayFlag;
    private String location;
    private String description;
    private String status;
    private String sourceType;
    private String sourceRef;
    private String externalUid;
    private Long importBatchId;
}
