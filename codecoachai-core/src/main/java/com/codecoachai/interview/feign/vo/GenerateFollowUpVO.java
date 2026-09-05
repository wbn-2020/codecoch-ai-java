package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class GenerateFollowUpVO {

    private Long aiCallLogId;
    private String followUpQuestion;
    private String reason;
    private Boolean relatedToOriginalQuestion;
    private Boolean followUpValid;
}
