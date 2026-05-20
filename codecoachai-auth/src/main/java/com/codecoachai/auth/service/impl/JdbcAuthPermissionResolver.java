package com.codecoachai.auth.service.impl;

import com.codecoachai.auth.service.AuthPermissionResolver;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcAuthPermissionResolver implements AuthPermissionResolver {

    private static final String QUERY_SQL = """
            SELECT DISTINCT m.permission_code
            FROM sys_role r
            JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0
            JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0
            WHERE r.deleted = 0
              AND r.status = 1
              AND m.status = 1
              AND m.permission_code IS NOT NULL
              AND m.permission_code <> ''
              AND r.role_code IN (%s)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> resolvePermissions(List<String> roleCodes) {
        if (CollectionUtils.isEmpty(roleCodes)) {
            return Collections.emptyList();
        }
        List<String> normalizedRoles = roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase())
                .distinct()
                .toList();
        if (normalizedRoles.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String placeholders = String.join(",", Collections.nCopies(normalizedRoles.size(), "?"));
            List<String> permissions = jdbcTemplate.queryForList(QUERY_SQL.formatted(placeholders),
                    String.class, normalizedRoles.toArray());
            return permissions.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            log.warn("Resolve auth permissions failed roles={}", normalizedRoles, ex);
            return Collections.emptyList();
        }
    }
}
