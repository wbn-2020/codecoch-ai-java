package com.codecoachai.resume.careercontact.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerContactVO {
    private Long id;
    private Long applicationId;
    private String displayName;
    private String roleType;
    private String channelType;
    private String maskedContactHint;
    private String relationshipSummary;
    private String relationshipType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
