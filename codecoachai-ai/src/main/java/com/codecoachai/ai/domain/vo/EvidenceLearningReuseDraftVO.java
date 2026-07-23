package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class EvidenceLearningReuseDraftVO {
    private String title;
    private String content;
    private String editDeepLink;
    private Boolean requiresUserConfirmation = true;
}
