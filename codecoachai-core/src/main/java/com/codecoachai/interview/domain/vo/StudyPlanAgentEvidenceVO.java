package com.codecoachai.interview.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StudyPlanAgentEvidenceVO {

    private Long id;

    private Long userId;

    private String sourceType;

    private Long sourceId;

    private Long targetJobId;

    private Long skillProfileId;

    private Long matchReportId;

    private Long reportId;

    private String planStatus;

    private LocalDate startDate;

    private LocalDateTime generatedAt;

    private LocalDateTime createdAt;
}
