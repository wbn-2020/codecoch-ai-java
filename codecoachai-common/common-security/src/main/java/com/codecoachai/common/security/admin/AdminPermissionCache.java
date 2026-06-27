package com.codecoachai.common.security.admin;

import com.codecoachai.common.security.config.AdminPermissionCacheProperties;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AdminPermissionCache {

    private static final String KEY_PREFIX = "auth:permissions:user:";
    private static final String VALUE_SEPARATOR = "\n";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final AdminPermissionCacheProperties properties;

    public Set<String> getUserPermissions(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        String cacheKey = buildKey(userId);
        String cached = getQuietly(cacheKey);
        if (cached != null) {
            return deserialize(cached);
        }
        Set<String> permissions = loadUserPermissions(userId);
        putQuietly(cacheKey, serialize(permissions), properties.getTtl());
        return permissions;
    }

    public void invalidateUserPermissions(Long userId) {
        if (userId == null) {
            return;
        }
        deleteQuietly(buildKey(userId));
    }

    public void invalidateUsersByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        List<Long> userIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT ur.user_id
                FROM sys_user_role ur
                WHERE ur.deleted = 0
                  AND ur.role_id = ?
                """, Long.class, roleId);
        userIds.forEach(this::invalidateUserPermissions);
    }

    private Set<String> loadUserPermissions(Long userId) {
        List<String> permissions = jdbcTemplate.queryForList("""
                SELECT DISTINCT m.permission_code
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
                JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0
                JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
                WHERE u.deleted = 0
                  AND u.status = 1
                  AND u.id = ?
                  AND m.permission_code IS NOT NULL
                  AND TRIM(m.permission_code) <> ''
                """, String.class, userId);
        Set<String> result = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (StringUtils.hasText(permission)) {
                result.add(permission.trim());
            }
        }
        return result;
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String getQuietly(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void putQuietly(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException ignored) {
            // Cache write failure should not block permission checks.
        }
    }

    private void deleteQuietly(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException ignored) {
            // Cache invalidation failure should not block the write path.
        }
    }

    private String serialize(Set<String> permissions) {
        return String.join(VALUE_SEPARATOR, permissions);
    }

    private Set<String> deserialize(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (String token : value.split(VALUE_SEPARATOR)) {
            if (StringUtils.hasText(token)) {
                permissions.add(token.trim());
            }
        }
        return permissions;
    }
}
