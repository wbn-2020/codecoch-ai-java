package com.codecoachai.resume.careercontact.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerInterviewRoundContactVO {
    private Long id;
    private Long interviewRoundId;
    private Long contactId;
    private String displayName;
    private String roleType;
    private String relationshipType;
    private LocalDateTime createdAt;
}
