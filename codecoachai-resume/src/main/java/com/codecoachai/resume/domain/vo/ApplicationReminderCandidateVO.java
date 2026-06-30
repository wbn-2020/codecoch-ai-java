package com.codecoachai.resume.domain.vo;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ApplicationReminderCandidateVO {
    private String type;
    private String bizType;
    private String bizId;
    private String title;
    private String content;
    private String actionUrl;
    private String fallbackPath;
    private String fallbackLabel;
    private LocalDate planDate;
}
