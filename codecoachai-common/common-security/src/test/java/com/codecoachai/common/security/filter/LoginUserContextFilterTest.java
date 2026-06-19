package com.codecoachai.common.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginUserContextFilterTest {

    private static final String SECRET = "test-user-context-secret";

    private LoginUserContextFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setSecret(SECRET);
        properties.setAllowedClockSkewSeconds(300);
        filter = new LoginUserContextFilter(properties);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void validSignedUserContextIsAvailableInsideChainAndClearedAfterwards() throws Exception {
        MockHttpServletRequest request = signedUserRequest("GET", "/resume/profile", "10", "alice", "ROLE_ADMIN,USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertEquals(10L, chain.userId());
        assertEquals("alice", chain.username());
        assertEquals(List.of("ROLE_ADMIN", "USER"), chain.roles());
        assertNull(LoginUserContext.getLoginUser());
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingUserContextSignatureFailsClosedAndClearsStaleContext() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(99L).roles(List.of("ADMIN")).build());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resume/profile");
        request.addHeader(HeaderConstants.USER_ID, "10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called());
        assertEquals(403, response.getStatus());
        assertNull(LoginUserContext.getLoginUser());
    }

    @Test
    void missingUserIdClearsStaleContextAndContinuesAsAnonymous() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(99L).roles(List.of("ADMIN")).build());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called());
        assertNull(chain.userId());
        assertNull(LoginUserContext.getLoginUser());
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest signedUserRequest(String method, String path, String userId, String username,
                                                     String roles) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String payload = InternalSignatureUtils.userContextPayload(method, path, timestamp, userId, username, roles);
        String signature = InternalSignatureUtils.hmacSha256Hex(SECRET, payload);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(HeaderConstants.USER_ID, userId);
        request.addHeader(HeaderConstants.USERNAME, username);
        request.addHeader(HeaderConstants.ROLES, roles);
        request.addHeader(HeaderConstants.USER_CONTEXT_TIMESTAMP, timestamp);
        request.addHeader(HeaderConstants.USER_CONTEXT_SIGNATURE, signature);
        return request;
    }

    private static class CapturingFilterChain implements FilterChain {

        private final AtomicBoolean called = new AtomicBoolean(false);
        private final AtomicReference<Long> userId = new AtomicReference<>();
        private final AtomicReference<String> username = new AtomicReference<>();
        private final AtomicReference<List<String>> roles = new AtomicReference<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called.set(true);
            userId.set(LoginUserContext.getUserId());
            username.set(LoginUserContext.getUsername());
            roles.set(LoginUserContext.getRoles());
        }

        boolean called() {
            return called.get();
        }

        Long userId() {
            return userId.get();
        }

        String username() {
            return username.get();
        }

        List<String> roles() {
            return roles.get();
        }
    }
}
