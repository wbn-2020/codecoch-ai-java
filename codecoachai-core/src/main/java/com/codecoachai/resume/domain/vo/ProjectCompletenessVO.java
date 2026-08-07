package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class ProjectCompletenessVO {

    private Integer score;
    private String status;
    private List<String> missingFields;
}
