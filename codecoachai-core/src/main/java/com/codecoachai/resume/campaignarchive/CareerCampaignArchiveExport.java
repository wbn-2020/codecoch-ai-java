package com.codecoachai.resume.campaignarchive;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_campaign_archive_export")
public class CareerCampaignArchiveExport extends BaseEntity {

    private Long userId;
    private Long campaignId;
    private LocalDateTime dataCutoffAt;
    private String exportFormat;
    private String status;
    private String sourceHash;
    private String manifestHash;
    private Long fileId;
    private Long fileSize;
    private String errorCode;
    private String errorMessage;
    private String idempotencyKeyHash;
}
