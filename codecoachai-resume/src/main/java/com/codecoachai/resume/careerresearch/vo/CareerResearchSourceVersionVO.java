package com.codecoachai.resume.careerresearch.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerResearchSourceVersionVO {
    private Long id;
    private Long sourceId;
    private String versionToken;
    private String contentHash;
    private String contentSummary;
    private LocalDateTime capturedAt;
    private LocalDateTime createdAt;
}
