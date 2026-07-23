package com.codecoachai.ai.agent.campaignreview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_review_memory_candidate")
public class CareerCampaignReviewMemoryCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reviewId;
    private Long snapshotId;
    private String candidateKey;
    private String semanticHash;
    private String title;
    private String content;
    private String sourceRef;
    private String confidenceLevel;
    private Integer validityDays;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime confirmedAt;
    private String decisionIdempotencyKeyHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
