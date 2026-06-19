package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class PromptTemplateVO {

    private Long id;
    private String scene;
    private String name;
    private String templateName;
    private String description;
    private String content;
    private String templateContent;
    private Integer contentLength;
    private String contentHash;
    private Integer templateContentLength;
    private String templateContentHash;
    private String variables;
    private String version;
    private Long activeVersionId;
    private Integer enabled;
    private Integer status;
    private Boolean rawFieldsAvailable;
    private Boolean rawFieldsIncluded;
    private String rawAccessPermission;
}
