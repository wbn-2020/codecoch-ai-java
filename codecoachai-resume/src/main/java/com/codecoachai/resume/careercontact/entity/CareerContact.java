package com.codecoachai.resume.careercontact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_contact")
public class CareerContact extends BaseEntity {
    private Long userId;
    private String displayName;
    private String roleType;
    private String channelType;
    private String maskedContactHint;
    private String relationshipSummary;
}
