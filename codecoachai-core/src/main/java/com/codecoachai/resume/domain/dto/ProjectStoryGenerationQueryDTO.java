package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ProjectStoryGenerationQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private String generationType;
    private Boolean accepted;
}
