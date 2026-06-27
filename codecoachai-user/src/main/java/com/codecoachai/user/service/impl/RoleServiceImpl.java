package com.codecoachai.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import com.codecoachai.user.convert.UserConvert;
import com.codecoachai.user.domain.entity.SysRole;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.domain.entity.SysUserRole;
import com.codecoachai.user.domain.vo.AdminRoleVO;
import com.codecoachai.user.mapper.SysRoleMapper;
import com.codecoachai.user.mapper.SysUserMapper;
import com.codecoachai.user.mapper.SysUserRoleMapper;
import com.codecoachai.user.service.RoleService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AdminPermissionCache adminPermissionCache;

    @Override
    public List<String> listRoleCodesByUserId(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (CollectionUtils.isEmpty(userRoles)) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, CommonConstants.YES))
                .stream()
                .map(SysRole::getRoleCode)
                .toList();
    }

    @Override
    public Long getRoleIdByCode(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getStatus, CommonConstants.YES)
                .last("limit 1"));
        if (role == null) {
            throw new BusinessException(ErrorCode.USER_ERROR, "默认角色不存在，请先初始化角色数据");
        }
        return role.getId();
    }

    @Override
    public List<AdminRoleVO> listAdminRoles() {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .orderByAsc(SysRole::getId))
                .stream()
                .map(UserConvert::toAdminRoleVO)
                .toList();
    }

    @Override
    public List<AdminRoleVO> listRolesByUserId(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (CollectionUtils.isEmpty(userRoles)) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds))
                .stream()
                .map(UserConvert::toAdminRoleVO)
                .toList();
    }

    @Override
    public Long createRole(String roleCode, String roleName, String description) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码不能为空");
        }
        Long exists = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setStatus(1);
        sysRoleMapper.insert(role);
        return role.getId();
    }

    @Override
    public void updateRole(Long id, String roleName, String description) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在");
        }
        if (StringUtils.hasText(roleName)) {
            role.setRoleName(roleName);
        }
        if (description != null) {
            role.setDescription(description);
        }
        sysRoleMapper.updateById(role);
    }

    @Override
    public void deleteRole(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在");
        }
        if (isBuiltInRole(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "系统内置角色不能删除，请改用禁用或调整菜单授权");
        }
        Long userRelationCount = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (userRelationCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该角色仍有关联用户，请先调整用户角色后再删除");
        }
        sysRoleMapper.deleteById(id);
    }

    private boolean isBuiltInRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        String normalized = roleCode.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return "ADMIN".equals(normalized) || "USER".equals(normalized);
    }

    @Override
    public void updateRoleStatus(Long id, Integer status) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在");
        }
        if (CommonConstants.NO.equals(status) && isBuiltInRole(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "系统内置角色不能禁用，请改用菜单授权控制访问范围");
        }
        role.setStatus(status);
        sysRoleMapper.updateById(role);
        adminPermissionCache.invalidateUsersByRoleId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户 id 不能为空");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        List<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        List<SysRole> assignableRoles = loadEnabledRolesOrThrow(normalizedRoleIds);
        ensureNotRemovingLastAdmin(userId, assignableRoles);
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        for (Long roleId : normalizedRoleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
        adminPermissionCache.invalidateUserPermissions(userId);
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null) {
            return List.of();
        }
        Set<Long> uniqueRoleIds = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            if (roleId != null) {
                uniqueRoleIds.add(roleId);
            }
        }
        return new ArrayList<>(uniqueRoleIds);
    }

    private List<SysRole> loadEnabledRolesOrThrow(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return List.of();
        }
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .orderByAsc(SysRole::getId));
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在无效角色");
        }
        boolean hasDisabled = roles.stream().anyMatch(role -> !CommonConstants.YES.equals(role.getStatus()));
        if (hasDisabled) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能分配已禁用角色");
        }
        return roles;
    }

    private void ensureNotRemovingLastAdmin(Long userId, List<SysRole> newRoles) {
        boolean currentlyAdmin = listRoleCodesByUserId(userId).stream().anyMatch(this::isAdminRoleCode);
        if (!currentlyAdmin || newRoles.stream().anyMatch(role -> isAdminRoleCode(role.getRoleCode()))) {
            return;
        }
        Long activeOtherAdmins = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
                WHERE u.deleted = 0
                  AND u.status = 1
                  AND r.status = 1
                  AND (UPPER(r.role_code) = UPPER(?) OR UPPER(r.role_code) = CONCAT('ROLE_', UPPER(?)))
                  AND u.id <> ?
                """, Long.class, SecurityConstants.ROLE_ADMIN, SecurityConstants.ROLE_ADMIN, userId);
        if (activeOtherAdmins == null || activeOtherAdmins <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能移除最后一个可用管理员账号的管理员角色");
        }
    }

    private boolean isAdminRoleCode(String roleCode) {
        return SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(normalizeRoleCode(roleCode));
    }

    private String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return "";
        }
        String normalized = roleCode.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        return normalized;
    }
}
