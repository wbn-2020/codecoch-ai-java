package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Study plan generation task payload.
 * Topic: codecoachai-study-plan  Tag: generate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanGeneratePayload {

    private Long planId;
    private Long userId;
}
