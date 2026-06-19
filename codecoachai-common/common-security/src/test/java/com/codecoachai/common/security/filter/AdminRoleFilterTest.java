package com.codecoachai.common.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminRoleFilterTest {

    private final AdminRoleFilter filter = new AdminRoleFilter();

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void adminPathRejectsNonAdminUser() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .roles(List.of("USER"))
                .build());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
    }

    @Test
    void adminPathAllowsRoleAdminWithContextPath() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(1L)
                .roles(List.of("ROLE_ADMIN"))
                .build());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(200, response.getStatus());
    }

    @Test
    void optionsAndNonAdminPathsBypassRoleCheck() throws Exception {
        MockHttpServletResponse optionsResponse = new MockHttpServletResponse();
        RecordingFilterChain optionsChain = new RecordingFilterChain();
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/admin/users"), optionsResponse, optionsChain);

        MockHttpServletResponse userResponse = new MockHttpServletResponse();
        RecordingFilterChain userChain = new RecordingFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/agent/today"), userResponse, userChain);

        assertTrue(optionsChain.called());
        assertTrue(userChain.called());
        assertEquals(200, optionsResponse.getStatus());
        assertEquals(200, userResponse.getStatus());
    }

    private static class RecordingFilterChain implements FilterChain {

        private final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called.set(true);
        }

        boolean called() {
            return called.get();
        }
    }
}
