package com.codecoachai.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.user.convert.UserConvert;
import com.codecoachai.user.domain.dto.AdminUserQueryDTO;
import com.codecoachai.user.domain.dto.InnerCreateUserDTO;
import com.codecoachai.user.domain.dto.UpdatePasswordDTO;
import com.codecoachai.user.domain.dto.UpdateUserProfileDTO;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.domain.entity.SysUserRole;
import com.codecoachai.user.domain.vo.AdminUserPageVO;
import com.codecoachai.user.domain.vo.InnerCreateUserVO;
import com.codecoachai.user.domain.vo.InnerUserAuthVO;
import com.codecoachai.user.domain.vo.InnerUserBasicVO;
import com.codecoachai.user.domain.vo.InnerUserRoleVO;
import com.codecoachai.user.domain.vo.UserOverviewVO;
import com.codecoachai.user.domain.vo.UserProfileVO;
import com.codecoachai.user.mapper.SysUserMapper;
import com.codecoachai.user.mapper.SysUserRoleMapper;
import com.codecoachai.user.service.RoleService;
import com.codecoachai.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileVO getCurrentUserProfile() {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        return UserConvert.toProfileVO(user, roleService.listRoleCodesByUserId(userId));
    }

    @Override
    public UserProfileVO updateCurrentUserProfile(UpdateUserProfileDTO dto) {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        user.setNickname(dto.getNickname());
        user.setAvatarUrl(dto.getAvatarUrl());
        user.setEmail(dto.getEmail());
        sysUserMapper.updateById(user);
        return getCurrentUserProfile();
    }

    @Override
    public void updateCurrentUserPassword(UpdatePasswordDTO dto) {
        Long userId = requireCurrentUserId();
        SysUser user = getUserOrThrow(userId);
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
    }

    @Override
    public UserOverviewVO getOverview() {
        requireCurrentUserId();
        return UserOverviewVO.builder()
                .resumeCount(0)
                .interviewCount(0)
                .completedInterviewCount(0)
                .questionAnsweredCount(0)
                .wrongQuestionCount(0)
                .favoriteQuestionCount(0)
                .build();
    }

    @Override
    public PageResult<AdminUserPageVO> pageAdminUsers(AdminUserQueryDTO query) {
        requireAdmin();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), condition -> condition
                        .like(SysUser::getUsername, query.getKeyword())
                        .or()
                        .like(SysUser::getNickname, query.getKeyword())
                        .or()
                        .like(SysUser::getEmail, query.getKeyword()))
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> page = sysUserMapper.selectPage(Page.of(query.getPageNo(), query.getPageSize()), wrapper);
        List<AdminUserPageVO> records = page.getRecords().stream()
                .filter(user -> !StringUtils.hasText(query.getRoleCode())
                        || roleService.listRoleCodesByUserId(user.getId()).contains(query.getRoleCode()))
                .map(user -> UserConvert.toAdminUserPageVO(user, roleService.listRoleCodesByUserId(user.getId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void updateUserStatus(Long id, UpdateUserStatusDTO dto) {
        requireAdmin();
        if (!CommonConstants.YES.equals(dto.getStatus()) && !CommonConstants.NO.equals(dto.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 只能是0或1");
        }
        Long currentUserId = requireCurrentUserId();
        if (currentUserId.equals(id) && CommonConstants.NO.equals(dto.getStatus())) {
            throw new BusinessException(ErrorCode.DISABLE_SELF_NOT_ALLOWED);
        }
        SysUser user = getUserOrThrow(id);
        user.setStatus(dto.getStatus());
        sysUserMapper.updateById(user);
    }

    @Override
    public InnerUserAuthVO getInnerUserByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return UserConvert.toInnerUserAuthVO(user, roleService.listRoleCodesByUserId(user.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InnerCreateUserVO createInnerUser(InnerCreateUserDTO dto) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(dto.getPasswordHash());
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setStatus(SecurityConstants.USER_STATUS_ENABLED);
        sysUserMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(roleService.getRoleIdByCode(SecurityConstants.ROLE_USER));
        sysUserRoleMapper.insert(userRole);
        return UserConvert.toInnerCreateUserVO(user);
    }

    @Override
    public InnerUserRoleVO getInnerUserRoles(Long id) {
        getUserOrThrow(id);
        InnerUserRoleVO vo = new InnerUserRoleVO();
        vo.setUserId(id);
        vo.setRoles(roleService.listRoleCodesByUserId(id));
        return vo;
    }

    @Override
    public InnerUserBasicVO getInnerUser(Long id) {
        SysUser user = getUserOrThrow(id);
        return UserConvert.toInnerUserBasicVO(user, roleService.listRoleCodesByUserId(id));
    }

    private SysUser getUserOrThrow(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private Long requireCurrentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void requireAdmin() {
        requireCurrentUserId();
        if (!LoginUserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
