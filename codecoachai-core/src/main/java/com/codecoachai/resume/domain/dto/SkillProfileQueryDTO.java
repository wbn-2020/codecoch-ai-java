package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class SkillProfileQueryDTO {

    private Long targetJobId;
    private String status;
    private Long pageNo;
    private Long pageSize;
}
