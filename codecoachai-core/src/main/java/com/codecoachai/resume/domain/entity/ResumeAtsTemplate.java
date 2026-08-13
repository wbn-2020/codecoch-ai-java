package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_ats_template")
public class ResumeAtsTemplate extends BaseEntity {
    private String templateCode;
    private Integer templateVersion;
    private String templateName;
    private String layoutType;
    private String definitionJson;
    private String definitionHash;
    private String status;
}
