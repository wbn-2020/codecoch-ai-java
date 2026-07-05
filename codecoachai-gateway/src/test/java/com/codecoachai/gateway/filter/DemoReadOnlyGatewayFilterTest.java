package com.codecoachai.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class DemoReadOnlyGatewayFilterTest {

    @Test
    void authRegisterRefreshAndLogoutAreWritableInDemoMode() throws Exception {
        DemoReadOnlyGatewayFilter filter = new DemoReadOnlyGatewayFilter();
        Method isWriteWhitePath = DemoReadOnlyGatewayFilter.class.getDeclaredMethod("isWriteWhitePath", String.class);
        isWriteWhitePath.setAccessible(true);

        assertTrue((Boolean) isWriteWhitePath.invoke(filter, "/auth/login"));
        assertTrue((Boolean) isWriteWhitePath.invoke(filter, "/auth/register"));
        assertTrue((Boolean) isWriteWhitePath.invoke(filter, "/auth/refresh-token"));
        assertTrue((Boolean) isWriteWhitePath.invoke(filter, "/auth/logout"));
        assertTrue((Boolean) isWriteWhitePath.invoke(filter, "/portfolio-demo/load"));
        assertTrue((Boolean) isWriteWhitePath.invoke(filter, "/portfolio-demo/reset"));
        assertFalse((Boolean) isWriteWhitePath.invoke(filter, "/user/profile"));
    }
}
