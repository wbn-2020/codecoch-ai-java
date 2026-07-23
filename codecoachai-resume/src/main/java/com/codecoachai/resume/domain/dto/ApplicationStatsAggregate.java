package com.codecoachai.resume.domain.dto;

import lombok.Data;

@Data
public class ApplicationStatsAggregate {

    private Long total;
    private Long activeCount;
    private Long overdueFollowUpCount;
    private Long dueTodayFollowUpCount;
    private Long noFollowUpCount;
    private Long staleActiveCount;
    private Long interviewCount;
    private Long offerCount;
    private Long rejectedCount;
    private Long closedCount;
}
