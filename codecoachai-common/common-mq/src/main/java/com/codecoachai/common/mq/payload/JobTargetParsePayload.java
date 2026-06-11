package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job target JD parse task payload.
 * Topic: codecoachai-resume  Tag: job-target-parse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobTargetParsePayload {

    private Long targetJobId;
    private Long userId;
    private Boolean forceRefresh;
    private String userTargetDirection;
}
