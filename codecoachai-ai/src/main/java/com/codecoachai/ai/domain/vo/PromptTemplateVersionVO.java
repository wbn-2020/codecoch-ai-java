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
    private Integer contentLength;
    private String contentHash;
    private String variablesJson;
    private String modelParamsJson;
    private Integer modelParamsLength;
    private String modelParamsHash;
    private String status;
    private Integer isActive;
    private Long createdBy;
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private String changeLog;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean rawFieldsAvailable;
    private Boolean rawFieldsIncluded;
    private String rawAccessPermission;
}
