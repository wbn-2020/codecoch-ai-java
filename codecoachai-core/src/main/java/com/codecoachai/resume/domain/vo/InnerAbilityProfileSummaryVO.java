package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InnerAbilityProfileSummaryVO {

    private String skillCode;
    private String skillName;
    private String domainCode;
    private String domainName;
    private String status;
    private Integer evidenceCount;
    private LocalDateTime lastEvaluatedAt;
    private String confidence;
    private String summary;
}
