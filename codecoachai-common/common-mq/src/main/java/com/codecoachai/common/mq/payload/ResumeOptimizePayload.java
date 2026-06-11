package com.codecoachai.common.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resume optimization task payload.
 * Topic: codecoachai-resume  Tag: optimize
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeOptimizePayload {

    private Long optimizeRecordId;
    private Long resumeId;
    private Long userId;
}
