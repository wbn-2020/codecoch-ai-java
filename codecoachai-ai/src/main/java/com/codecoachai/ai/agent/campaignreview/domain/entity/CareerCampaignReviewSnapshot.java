package com.codecoachai.ai.agent.campaignreview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_review_snapshot")
public class CareerCampaignReviewSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reviewId;
    private Long campaignId;
    private Integer snapshotVersion;
    private LocalDateTime dataCutoffAt;
    private String inputHash;
    private String generationFingerprint;
    private String idempotencyKeyHash;
    private String idempotencyPayloadHash;
    private String summary;
    private String confidenceLevel;
    private String factsJson;
    private String coverageJson;
    private String limitsJson;
    private String signalsJson;
    private String memoryCandidatesJson;
    private String experimentCandidatesJson;
    private String nextCycleActionsJson;
    private String resultSource;
    private Integer fallback;
    private String fallbackReason;
    private Long aiCallLogId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
