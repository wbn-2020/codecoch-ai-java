package com.codecoachai.user.service;

import com.codecoachai.user.domain.vo.AdminRoleVO;
import java.util.List;

public interface RoleService {

    List<String> listRoleCodesByUserId(Long userId);

    Long getRoleIdByCode(String roleCode);

    List<AdminRoleVO> listAdminRoles();
}
