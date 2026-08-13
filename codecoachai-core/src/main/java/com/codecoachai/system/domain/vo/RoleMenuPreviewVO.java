package com.codecoachai.system.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoleMenuPreviewVO {

    private Long roleId;
    private Integer assignedMenuCount;
    private Integer unavailableAssignmentCount;
    private Integer permissionCount;
    private List<String> permissionCodes = new ArrayList<>();
    private List<SysMenuTreeVO> menuTree = new ArrayList<>();
}
