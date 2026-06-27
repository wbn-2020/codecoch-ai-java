package com.codecoachai.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import com.codecoachai.user.domain.entity.SysRole;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.domain.entity.SysUserRole;
import com.codecoachai.user.mapper.SysRoleMapper;
import com.codecoachai.user.mapper.SysUserMapper;
import com.codecoachai.user.mapper.SysUserRoleMapper;
import com.codecoachai.user.service.impl.RoleServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AdminPermissionCache adminPermissionCache;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(
                sysRoleMapper,
                sysUserRoleMapper,
                sysUserMapper,
                jdbcTemplate,
                adminPermissionCache);
    }

    @Test
    void assignRolesRejectsDisabledRoleBeforeMutation() {
        when(sysUserMapper.selectById(9L)).thenReturn(enabledUser(9L));
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(disabledRole(2L, "USER")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> roleService.assignRolesToUser(9L, List.of(2L)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(sysUserRoleMapper, never()).delete(any());
        verify(sysUserRoleMapper, never()).insert(any());
    }

    @Test
    void assignRolesBlocksRemovingAdminFromLastActiveAdmin() {
        when(sysUserMapper.selectById(9L)).thenReturn(enabledUser(9L));
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole(9L, 7L)));
        when(sysRoleMapper.selectList(any()))
                .thenReturn(List.of(enabledRole(7L, "ROLE_ADMIN")))
                .thenReturn(List.of(enabledRole(1L, "USER")));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq("ADMIN"), eq("ADMIN"), eq(9L)))
                .thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> roleService.assignRolesToUser(9L, List.of(1L)));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), exception.getCode());
        verify(sysUserRoleMapper, never()).delete(any());
        verify(sysUserRoleMapper, never()).insert(any());
    }

    @Test
    void assignRolesAllowsAdminRemovalWhenAnotherActiveAdminExists() {
        when(sysUserMapper.selectById(9L)).thenReturn(enabledUser(9L));
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole(9L, 7L)));
        when(sysRoleMapper.selectList(any()))
                .thenReturn(List.of(enabledRole(7L, "ROLE_ADMIN")))
                .thenReturn(List.of(enabledRole(1L, "USER")));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq("ADMIN"), eq("ADMIN"), eq(9L)))
                .thenReturn(1L);

        assertDoesNotThrow(() -> roleService.assignRolesToUser(9L, List.of(1L)));

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper, times(1)).insert(any(SysUserRole.class));
        verify(adminPermissionCache).invalidateUserPermissions(9L);
    }

    @Test
    void updateRoleStatusInvalidatesPermissionsForAffectedUsers() {
        SysRole role = enabledRole(7L, "REVIEWER");
        when(sysRoleMapper.selectById(7L)).thenReturn(role);

        roleService.updateRoleStatus(7L, CommonConstants.NO);

        verify(sysRoleMapper).updateById(role);
        verify(adminPermissionCache).invalidateUsersByRoleId(7L);
    }

    private static SysUser enabledUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(CommonConstants.YES);
        return user;
    }

    private static SysRole enabledRole(Long id, String roleCode) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setStatus(CommonConstants.YES);
        return role;
    }

    private static SysRole disabledRole(Long id, String roleCode) {
        SysRole role = enabledRole(id, roleCode);
        role.setStatus(CommonConstants.NO);
        return role;
    }

    private static SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }
}
