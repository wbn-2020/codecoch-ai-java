package com.codecoachai.ai.agent.campaignreview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_review")
public class CareerCampaignReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long campaignId;
    private Long currentSnapshotId;
    private String reviewStatus;
    private Integer snapshotVersion;
    private String generationClaimFingerprint;
    private String generationClaimToken;
    private String generationClaimIdempotencyKeyHash;
    private String generationClaimPayloadHash;
    private LocalDateTime generationClaimedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    private Integer lockVersion;
}
