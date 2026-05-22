package com.codecoachai.task.feign.vo;

import lombok.Data;

@Data
public class ResumeJobMatchSubmitVO {

    private Long reportId;
    private Long resumeId;
    private Long targetJobId;
    private Long aiCallLogId;
    private String status;
    private String errorMessage;
}
