package com.codecoachai.resume.careerresearch.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerResearchSnapshotVO {
    private Long id;
    private Long reportId;
    private Long applicationId;
    private String sourceSetHash;
    private CareerResearchDraft research;
    private String fallbackReason;
    private List<Long> sourceVersionIds = new ArrayList<>();
    private LocalDateTime createdAt;
}
