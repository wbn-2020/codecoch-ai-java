package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resume and target-job match report task payload.
 * Topic: codecoachai-job-match  Tag: analyze
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeJobMatchPayload {

    private Long reportId;
    private Long userId;
}
