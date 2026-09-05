package com.codecoachai.common.security.internal;

import java.util.Set;

public final class TrustedServiceNames {

    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "codecoachai-gateway",
            "codecoachai-core",
            "codecoachai-ai",
            "codecoachai-search"
    );

    private TrustedServiceNames() {
    }

    public static boolean contains(String serviceName) {
        return serviceName != null && ALLOWED_SERVICES.contains(serviceName);
    }
}
