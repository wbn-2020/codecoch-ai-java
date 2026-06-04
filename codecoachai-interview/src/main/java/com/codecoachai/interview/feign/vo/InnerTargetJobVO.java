package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class InnerTargetJobVO {

    private Long id;
    private Long userId;
    private String jobTitle;
    private String companyName;
    private String jobLevel;
    private Integer currentFlag;
}
