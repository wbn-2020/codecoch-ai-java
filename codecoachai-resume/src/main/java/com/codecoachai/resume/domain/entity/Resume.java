package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume")
public class Resume extends BaseEntity {

    private Long userId;
    private String title;
    private String realName;
    private String email;
    private String phone;
    private String targetPosition;
    private String skillStack;
    private String workExperience;
    private String educationExperience;
    private String summary;
    private Integer isDefault;
    private Integer status;
    private Long sourceResumeId;
    private Long sourceOptimizeRecordId;
    private LocalDateTime appliedAt;
}
