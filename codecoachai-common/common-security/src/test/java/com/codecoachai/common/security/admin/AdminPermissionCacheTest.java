package com.codecoachai.common.security.admin;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codecoachai.common.security.config.AdminPermissionCacheProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminPermissionCacheTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void invalidatesImmediatelyOutsideTransaction() {
        AdminPermissionCache cache = cache();

        cache.invalidateUserPermissionsAfterCommit(9L);

        verify(stringRedisTemplate).delete("auth:permissions:user:9");
    }

    @Test
    void defersInvalidationUntilTransactionCommit() {
        AdminPermissionCache cache = cache();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        cache.invalidateUserPermissionsAfterCommit(9L);

        verify(stringRedisTemplate, never()).delete("auth:permissions:user:9");
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(stringRedisTemplate).delete("auth:permissions:user:9");
    }

    private AdminPermissionCache cache() {
        return new AdminPermissionCache(
                jdbcTemplate,
                stringRedisTemplate,
                new AdminPermissionCacheProperties());
    }
}
