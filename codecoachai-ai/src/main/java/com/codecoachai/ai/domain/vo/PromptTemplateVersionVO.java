package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PromptTemplateVersionVO {

    private Long id;
    private Long templateId;
    private String scene;
    private String versionCode;
    private String versionName;
    private String content;
    private String variablesJson;
    private String modelParamsJson;
    private String status;
    private Integer isActive;
    private Long createdBy;
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private String changeLog;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
