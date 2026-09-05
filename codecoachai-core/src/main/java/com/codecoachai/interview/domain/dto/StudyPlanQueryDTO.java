package com.codecoachai.interview.domain.dto;

import lombok.Data;

@Data
public class StudyPlanQueryDTO {

    private Long pageNo;
    private Long pageSize;
    private String planStatus;
}
