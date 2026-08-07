package com.codecoachai.resume.careercontact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_contact_application")
public class CareerContactApplication extends BaseEntity {
    private Long userId;
    private Long contactId;
    private Long applicationId;
    private String relationshipType;
}
