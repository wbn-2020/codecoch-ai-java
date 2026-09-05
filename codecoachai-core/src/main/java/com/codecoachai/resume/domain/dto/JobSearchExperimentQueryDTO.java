package com.codecoachai.resume.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class JobSearchExperimentQueryDTO {

    private String keyword;
    private String status;
    private Boolean demoFlag;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
