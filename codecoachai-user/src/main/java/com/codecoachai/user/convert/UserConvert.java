package com.codecoachai.user.convert;

import com.codecoachai.user.domain.entity.SysRole;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.domain.enums.UserStatusEnum;
import com.codecoachai.user.domain.vo.AdminRoleVO;
import com.codecoachai.user.domain.vo.AdminUserPageVO;
import com.codecoachai.user.domain.vo.InnerCreateUserVO;
import com.codecoachai.user.domain.vo.InnerUserAuthVO;
import com.codecoachai.user.domain.vo.InnerUserBasicVO;
import com.codecoachai.user.domain.vo.UserProfileVO;
import java.util.List;

public final class UserConvert {

    private UserConvert() {
    }

    public static UserProfileVO toProfileVO(SysUser user, List<String> roles) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setRoles(roles);
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    public static AdminUserPageVO toAdminUserPageVO(SysUser user, List<String> roles) {
        AdminUserPageVO vo = new AdminUserPageVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setStatusName(UserStatusEnum.labelOf(user.getStatus()));
        vo.setRoles(roles);
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    public static InnerUserAuthVO toInnerUserAuthVO(SysUser user, List<String> roles) {
        InnerUserAuthVO vo = new InnerUserAuthVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setPasswordHash(user.getPasswordHash());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setRoles(roles);
        return vo;
    }

    public static InnerUserBasicVO toInnerUserBasicVO(SysUser user, List<String> roles) {
        InnerUserBasicVO vo = new InnerUserBasicVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setRoles(roles);
        return vo;
    }

    public static InnerCreateUserVO toInnerCreateUserVO(SysUser user) {
        InnerCreateUserVO vo = new InnerCreateUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }

    public static AdminRoleVO toAdminRoleVO(SysRole role) {
        AdminRoleVO vo = new AdminRoleVO();
        vo.setRoleId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setStatus(role.getStatus());
        return vo;
    }
}
