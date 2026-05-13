package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class GenerateReportVO {

    private Integer totalScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String suggestions;
}
