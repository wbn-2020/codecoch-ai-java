package com.codecoachai.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template_version")
public class PromptTemplateVersion extends BaseEntity {

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
}
