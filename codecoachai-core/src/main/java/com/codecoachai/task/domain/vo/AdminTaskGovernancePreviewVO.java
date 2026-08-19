package com.codecoachai.task.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class AdminTaskGovernancePreviewVO {
    private Long id;
    private String bizType;
    private String bizId;
    private String taskStatus;
    private String governanceStatus;
    private String recommendedGovernanceStatus;
    private String failureClass;
    private String recommendedOwner;
    private Long ageMinutes;
    private Boolean retryAllowed;
    private String previewHash;
    private String impact;
    private List<String> allowedGovernanceStatuses;
}
