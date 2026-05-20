package com.codecoachai.resume.feign.vo;

import lombok.Data;

@Data
public class AnalyzeSkillGapVO {

    private String resultJson;
    private Long aiCallLogId;
    private String rawResponse;
}
