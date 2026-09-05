package com.codecoachai.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String permissionCode;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;
    private String remark;
}
