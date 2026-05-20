package com.codecoachai.user.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    @TableField("password")
    private String passwordHash;

    private String nickname;

    @TableField("avatar")
    private String avatarUrl;

    private String email;

    private String phone;

    private Integer status;
}
