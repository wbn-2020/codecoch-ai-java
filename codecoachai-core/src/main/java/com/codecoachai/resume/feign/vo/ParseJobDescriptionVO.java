package com.codecoachai.resume.feign.vo;

import lombok.Data;

@Data
public class ParseJobDescriptionVO {

    private String resultJson;
    private Long aiCallLogId;
    private String rawResponse;
}
