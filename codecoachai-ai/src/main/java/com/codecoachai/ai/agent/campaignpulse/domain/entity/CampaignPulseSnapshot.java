package com.codecoachai.ai.agent.campaignpulse.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_pulse_snapshot")
public class CampaignPulseSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long pulseId;
    private Long campaignId;
    private Integer snapshotVersion;
    private LocalDateTime dataCutoffAt;
    private String inputHash;
    private String generationFingerprint;
    private String idempotencyKeyHash;
    private String idempotencyPayloadHash;
    private String factsJson;
    private String metricsJson;
    private String changesJson;
    private String driftSignalsJson;
    private String limitsJson;
    private String actionSeedsJson;
    private String narrativeJson;
    private String confidenceLevel;
    private Boolean fallback;
    private Long aiCallLogId;
    private LocalDateTime createdAt;
    private Integer deleted;
}
