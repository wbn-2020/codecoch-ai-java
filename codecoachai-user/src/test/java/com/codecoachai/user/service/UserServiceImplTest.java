package com.codecoachai.user.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.mapper.SysUserMapper;
import com.codecoachai.user.mapper.SysUserRoleMapper;
import com.codecoachai.user.service.impl.UserServiceImpl;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private RoleService roleService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AdminPermissionCache adminPermissionCache;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                sysUserMapper,
                sysUserRoleMapper,
                roleService,
                passwordEncoder,
                jdbcTemplate,
                adminPermissionCache);
        LoginUserContext.setLoginUser(new LoginUser(1001L, "admin", "Admin", List.of("ADMIN")));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void resetPasswordGeneratesStrongTemporaryPasswordInsteadOfLegacyWeakPattern() {
        SysUser user = new SysUser();
        user.setId(9L);
        when(sysUserMapper.selectById(9L)).thenReturn(user);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("encoded-password");

        String temporaryPassword = userService.resetPassword(9L);

        assertNotNull(temporaryPassword);
        assertTrue(temporaryPassword.length() >= 16);
        assertTrue(temporaryPassword.chars().anyMatch(Character::isUpperCase));
        assertTrue(temporaryPassword.chars().anyMatch(Character::isLowerCase));
        assertTrue(temporaryPassword.chars().anyMatch(Character::isDigit));
        assertTrue(temporaryPassword.chars().anyMatch(ch -> "@#$%&*!?".indexOf(ch) >= 0));
        assertFalse(temporaryPassword.matches("Cc@\\d{1,5}"));
        assertFalse(temporaryPassword.startsWith("Cc@"));

        verify(passwordEncoder).encode(temporaryPassword);
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(userCaptor.capture());
        assertTrue("encoded-password".equals(userCaptor.getValue().getPasswordHash()));
    }

    @Test
    void updateUserStatusInvalidatesPermissionCache() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setStatus(CommonConstants.YES);
        when(sysUserMapper.selectById(9L)).thenReturn(user);
        when(roleService.listRoleCodesByUserId(9L)).thenReturn(List.of("USER"));
        UpdateUserStatusDTO dto = new UpdateUserStatusDTO();
        dto.setStatus(CommonConstants.NO);

        userService.updateUserStatus(9L, dto);

        verify(sysUserMapper).updateById(user);
        verify(adminPermissionCache).invalidateUserPermissions(9L);
    }
}
