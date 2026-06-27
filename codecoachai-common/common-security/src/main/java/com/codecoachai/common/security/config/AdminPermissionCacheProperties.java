package com.codecoachai.common.security.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.security.admin.permission-cache")
public class AdminPermissionCacheProperties {

    private Duration ttl = Duration.ofMinutes(10);
}
