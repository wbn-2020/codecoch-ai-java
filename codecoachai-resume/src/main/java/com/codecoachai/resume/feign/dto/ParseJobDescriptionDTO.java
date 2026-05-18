package com.codecoachai.resume.feign.dto;

import lombok.Data;

@Data
public class ParseJobDescriptionDTO {

    private Long targetJobId;
    private Long userId;
    private String jobTitle;
    private String companyName;
    private String jobLevel;
    private String jdText;
    private String jdSource;
    private String userTargetDirection;
}
