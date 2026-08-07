package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobRequirementEvidenceSourceRow {

    private String evidenceType;
    private Long evidenceId;
    private Long evidenceSubId;
    private String title;
    private String excerpt;
    private String resultSource;
    private Integer score;
    private Boolean confirmed;
    private Boolean fallback;
    private LocalDateTime occurredAt;
}
