package com.codecoachai.ai.agent.campaignreview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_review_source")
public class CareerCampaignReviewSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long snapshotId;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime sourceTime;
    private LocalDateTime sourceUpdatedAt;
    private String sourceHash;
    private String inclusionStatus;
    private String excludeReason;
    private String safeSummary;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
