package com.codecoachai.ai.agent.campaignpulse.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_campaign_pulse_source")
public class CampaignPulseSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long snapshotId;
    private String sourceType;
    private Long sourceId;
    private Integer sourceVersion;
    private String sourceHash;
    private Long applicationId;
    private Long campaignId;
    private LocalDateTime observedAt;
    private String fieldPath;
    private String safeSummary;
    private LocalDateTime createdAt;
    private Integer deleted;
}
