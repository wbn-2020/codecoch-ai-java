package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ProjectEvidenceQueryDTO {

    private String keyword;
    private String techStack;
    private String completenessStatus;
    private Long sourceResumeId;
    private Long targetJobId;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
