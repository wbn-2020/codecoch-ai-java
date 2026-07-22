package com.codecoachai.ai.agent.campaigncockpit.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_action_decision")
public class CampaignActionDecision {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long campaignId;
    private String semanticKey;
    private String sourceHash;
    private String actionType;
    private String decisionStatus;
    private LocalDateTime snoozedUntil;
    private String reason;
    private String idempotencyKeyHash;
    private String payloadHash;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    private Integer activeGuard;
    private String liveSemanticSource;
}
