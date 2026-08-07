package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_ability_profile")
public class UserAbilityProfile extends BaseEntity {

    private Long userId;
    private String skillCode;
    private String status;
    private Integer evidenceCount;
    private LocalDateTime lastEvaluatedAt;
    private String confidence;
    private String summary;
    private String sourceType;
}
