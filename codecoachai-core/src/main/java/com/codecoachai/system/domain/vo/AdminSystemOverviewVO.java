package com.codecoachai.system.domain.vo;

import lombok.Data;

@Data
public class AdminSystemOverviewVO {

    private Long userCount;
    private Long questionCount;
    private Long resumeCount;
    private Long interviewCount;
    private Long aiCallCount;
}
