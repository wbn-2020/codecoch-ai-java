package com.codecoachai.resume.careerresearch.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerResearchSourceVO {
    private Long id;
    private Long applicationId;
    private String sourceType;
    private String title;
    private String officialUrl;
    private String externalRef;
    private String status;
    private Long currentVersionId;
    private CareerResearchSourceVersionVO currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
