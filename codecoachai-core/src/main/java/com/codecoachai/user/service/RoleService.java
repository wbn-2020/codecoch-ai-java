package com.codecoachai.user.service;

import com.codecoachai.user.domain.vo.AdminRoleVO;
import java.util.List;

public interface RoleService {

    List<String> listRoleCodesByUserId(Long userId);

    Long getRoleIdByCode(String roleCode);

    List<AdminRoleVO> listAdminRoles();

    List<AdminRoleVO> listRolesByUserId(Long userId);

    Long createRole(String roleCode, String roleName, String description);

    void updateRole(Long id, String roleName, String description);

    void deleteRole(Long id);

    void updateRoleStatus(Long id, Integer status);

    void assignRolesToUser(Long userId, List<Long> roleIds);
}
