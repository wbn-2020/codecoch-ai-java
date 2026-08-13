package com.codecoachai.user.domain.vo;

import lombok.Data;

@Data
public class AdminRoleVO {

    private Long roleId;
    private String roleCode;
    private String roleName;
    private Integer status;
}
