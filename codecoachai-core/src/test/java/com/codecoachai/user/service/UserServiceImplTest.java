package com.codecoachai.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.admin.AdminPermissionCache;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.user.domain.dto.AdminUserQueryDTO;
import com.codecoachai.user.domain.dto.UpdateUserStatusDTO;
import com.codecoachai.user.domain.entity.SysUser;
import com.codecoachai.user.domain.vo.UserDashboardOverviewVO;
import com.codecoachai.user.mapper.SysUserMapper;
import com.codecoachai.user.mapper.SysUserRoleMapper;
import com.codecoachai.user.service.impl.UserServiceImpl;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
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
        LoginUserContext.setLoginUser(new LoginUser(1002L, "operator", "Operator", List.of("OPERATIONS")));
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
        LoginUserContext.setLoginUser(new LoginUser(1002L, "operator", "Operator", List.of("OPERATIONS")));
        SysUser user = new SysUser();
        user.setId(9L);
        user.setStatus(CommonConstants.YES);
        when(sysUserMapper.selectById(9L)).thenReturn(user);
        when(roleService.listRoleCodesByUserId(9L)).thenReturn(List.of("USER"));
        UpdateUserStatusDTO dto = new UpdateUserStatusDTO();
        dto.setStatus(CommonConstants.NO);

        userService.updateUserStatus(9L, dto);

        verify(sysUserMapper).updateById(user);
        verify(adminPermissionCache).invalidateUserPermissionsAfterCommit(9L);
    }

    @Test
    void pageAdminUsersAllowsAuthenticatedScopedOperator() {
        LoginUserContext.setLoginUser(new LoginUser(1002L, "operator", "Operator", List.of("OPERATIONS")));
        Page<SysUser> page = Page.of(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(sysUserMapper.selectAdminUserPage(any(), any(), any(), any())).thenReturn(page);
        SysUser currentUser = new SysUser();
        currentUser.setId(1002L);
        when(sysUserMapper.selectById(1002L)).thenReturn(currentUser);

        userService.pageAdminUsers(new AdminUserQueryDTO());

        verify(sysUserMapper).selectAdminUserPage(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dashboardUsesLatestActivePlanCumulativeProgressAndShanghaiBusinessDate() {
        List<String> queriedSql = new ArrayList<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    queriedSql.add(sql);
                    if (sql.contains("information_schema.tables")) {
                        return 1L;
                    }
                    if (sql.contains("FROM `study_plan`")) {
                        return 2L;
                    }
                    if (sql.contains("FROM `study_task`")) {
                        if (sql.contains("planned_date")) {
                            return 0L;
                        }
                        return 3L;
                    }
                    return 0L;
                });
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            queriedSql.add(sql);
            ResultSetExtractor extractor = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            if (sql.contains("FROM study_plan")) {
                when(rs.next()).thenReturn(true);
                when(rs.getLong("id")).thenReturn(88L);
                when(rs.getString("plan_title")).thenReturn("Latest active plan");
                when(rs.getString("plan_summary")).thenReturn("Read-only plan summary");
                when(rs.getString("plan_status")).thenReturn("ACTIVE");
                when(rs.getTimestamp("updated_at"))
                        .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 10, 9, 30)));
            } else {
                when(rs.next()).thenReturn(false);
            }
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        UserDashboardOverviewVO overview = userService.getDashboardOverview();

        assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")), overview.getBusinessDate());
        assertEquals("Asia/Shanghai", overview.getBusinessTimezone());
        assertEquals(88L, overview.getActiveStudyPlan().getPlanId());
        assertEquals("Read-only plan summary", overview.getActiveStudyPlan().getPlanSummary());
        assertEquals(3, overview.getActiveStudyPlan().getCumulativeTaskCount());
        assertEquals(3, overview.getActiveStudyPlan().getCumulativeDoneTaskCount());
        assertEquals(100, overview.getActiveStudyPlan().getCumulativeProgressPercent());
        assertEquals(0, overview.getActiveStudyPlan().getTodayTaskCount());
        assertEquals(0, overview.getActiveStudyPlan().getTodayDoneTaskCount());
        assertEquals("NO_SCHEDULE", overview.getActiveStudyPlan().getTodayStatus());
        assertEquals(0L, overview.getTodayTaskCount());
        assertEquals(0L, overview.getTodayCompletedTaskCount());
        assertTrue(queriedSql.stream().anyMatch(sql ->
                sql.contains("plan_status = 'ACTIVE'") && sql.contains("ORDER BY updated_at DESC, id DESC")));
        assertTrue(queriedSql.stream().noneMatch(sql -> sql.contains("CURDATE()")));
    }

    @Test
    void dashboardPropagatesMetadataFailuresInsteadOfReportingAnEmptyOverview() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new RuntimeException("metadata unavailable"));

        assertThrows(RuntimeException.class, () -> userService.getDashboardOverview());
    }

    @Test
    void updateUserStatusLocksAndRejectsDisablingLastActiveAdmin() {
        when(roleService.listRoleCodesByUserId(9L)).thenReturn(List.of("ROLE_ADMIN"));
        when(jdbcTemplate.queryForList(any(String.class), eq(Long.class), eq("ADMIN"), eq("ADMIN")))
                .thenReturn(List.of(7L))
                .thenReturn(List.of(9L));
        UpdateUserStatusDTO dto = new UpdateUserStatusDTO();
        dto.setStatus(CommonConstants.NO);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUserStatus(9L, dto));

        assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), exception.getCode());
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
        verify(adminPermissionCache, never()).invalidateUserPermissionsAfterCommit(9L);
    }
}
