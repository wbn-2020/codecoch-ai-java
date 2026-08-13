package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class JobSearchExperimentRelationVO {

    private Long id;
    private Long experimentId;
    private String relationType;
    private Long relationId;
    private String relationSummary;
    private Map<String, Object> metadata;
    private Integer demoFlag;
    private LocalDateTime createdAt;
}
