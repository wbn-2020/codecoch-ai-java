package com.codecoachai.system.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SysMenuTreeVO {

    private Long id;
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
    private List<SysMenuTreeVO> children = new ArrayList<>();
}
