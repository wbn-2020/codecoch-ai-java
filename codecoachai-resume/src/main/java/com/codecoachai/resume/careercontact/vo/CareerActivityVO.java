package com.codecoachai.resume.careercontact.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerActivityVO {
    private Long id;
    private Long applicationId;
    private Long contactId;
    private String activityType;
    private String channelType;
    private String subject;
    private String summary;
    private LocalDateTime occurredAt;
    private LocalDateTime nextFollowUpAt;
    private String status;
    private LocalDateTime createdAt;
}
