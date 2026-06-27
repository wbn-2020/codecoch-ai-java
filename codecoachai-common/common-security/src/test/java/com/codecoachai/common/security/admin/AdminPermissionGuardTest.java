package com.codecoachai.common.security.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.config.AdminPermissionCacheProperties;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminPermissionGuardTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminPermissionGuard guard;

    @BeforeEach
    void setUp() {
        AdminPermissionCacheProperties properties = new AdminPermissionCacheProperties();
        properties.setTtl(Duration.ofMinutes(10));
        AdminPermissionCache cache = new AdminPermissionCache(jdbcTemplate, stringRedisTemplate, properties);
        guard = new AdminPermissionGuard(cache);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void requireUsesCachedPermissionsBeforeJdbcLookup() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(42L)
                .roles(List.of("ADMIN"))
                .build());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:permissions:user:42"))
                .thenReturn("admin:system:overview\nadmin:menu:list");

        guard.require("admin:menu:list");

        verify(valueOperations).get("auth:permissions:user:42");
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class), eq(42L));
    }

    @Test
    void overviewKeepsLegacyAdminBypassForAdminRole() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(42L)
                .roles(List.of("ADMIN"))
                .build());

        guard.require("admin:system:overview");

        verify(stringRedisTemplate, never()).opsForValue();
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class), eq(42L));
    }
}
