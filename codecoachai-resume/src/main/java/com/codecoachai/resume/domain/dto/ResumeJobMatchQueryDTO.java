package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ResumeJobMatchQueryDTO {

    private Long resumeId;
    private Long targetJobId;
    private String status;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
