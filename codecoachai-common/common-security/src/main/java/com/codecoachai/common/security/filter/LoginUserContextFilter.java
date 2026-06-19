package com.codecoachai.common.security.filter;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.HeaderUserContextReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginUserContextFilter extends OncePerRequestFilter {

    private static final String JSON_UTF8 = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InternalAuthProperties internalAuthProperties;

    public LoginUserContextFilter(InternalAuthProperties internalAuthProperties) {
        this.internalAuthProperties = internalAuthProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            LoginUserContext.setLoginUser(HeaderUserContextReader.read(request, internalAuthProperties));
            filterChain.doFilter(request, response);
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(JSON_UTF8);
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
        } finally {
            LoginUserContext.clear();
        }
    }
}
