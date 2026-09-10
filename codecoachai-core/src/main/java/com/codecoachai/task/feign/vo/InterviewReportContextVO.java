package com.codecoachai.task.feign.vo;

import lombok.Data;

@Data
@lombok.EqualsAndHashCode(callSuper = true)
public class InterviewReportContextVO extends com.codecoachai.interview.feign.dto.GenerateReportDTO {

    private Long sessionId;
}
