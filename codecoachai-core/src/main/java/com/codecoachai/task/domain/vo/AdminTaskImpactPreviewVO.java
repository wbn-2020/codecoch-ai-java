package com.codecoachai.task.domain.vo;

import lombok.Data;

@Data
public class AdminTaskImpactPreviewVO {
    private Long id;
    private String targetType;
    private String bizType;
    private String bizId;
    private Long userId;
    private String currentStatus;
    private Boolean executable;
    private String impact;
    private String riskLevel;
    private String requiredPermission;
    private String requiredNote;
}
