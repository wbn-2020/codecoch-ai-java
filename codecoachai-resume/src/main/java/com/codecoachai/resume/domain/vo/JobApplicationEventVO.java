package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class JobApplicationEventVO {
    private Long id;
    private Long applicationId;
    private String eventType;
    private LocalDateTime eventTime;
    private String summary;
    private Map<String, Object> review;
    private String reviewJson;
    private JobApplicationEventStructuredReviewVO structuredReview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
