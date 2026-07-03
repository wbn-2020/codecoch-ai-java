package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ability_skill_node")
public class AbilitySkillNode extends BaseEntity {

    private String code;
    private String name;
    private String domainCode;
    private String domainName;
    private String description;
    private Integer sortOrder;
    private Integer enabled;
}
