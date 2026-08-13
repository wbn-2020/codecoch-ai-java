package com.codecoachai.resume.careercontact.vo;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CareerContactReminderCandidateVO {
    private String type = "FOLLOW_UP";
    private String bizType = "CAREER_CONTACT";
    private String bizId;
    private String title;
    private String content;
    private String actionUrl;
    private LocalDate planDate;
}
