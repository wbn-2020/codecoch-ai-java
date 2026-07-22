package com.codecoachai.ai.agent.campaignpulse.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_pulse")
public class CampaignPulse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long campaignId;
    private Long currentSnapshotId;
    private Integer snapshotVersion;
    private LocalDateTime lastGeneratedAt;
    private String generationClaimToken;
    private String generationClaimFingerprint;
    private LocalDateTime generationClaimedAt;
    private Integer lockVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
