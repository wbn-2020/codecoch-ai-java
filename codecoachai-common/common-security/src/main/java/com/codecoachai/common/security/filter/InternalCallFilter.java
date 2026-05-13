package com.codecoachai.common.security.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalCallFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "codecoachai-gateway",
            "codecoachai-auth",
            "codecoachai-user",
            "codecoachai-question",
            "codecoachai-resume",
            "codecoachai-interview",
            "codecoachai-ai",
            "codecoachai-system"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/inner/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String internalCall = request.getHeader(HeaderConstants.INTERNAL_CALL);
        String serviceName = request.getHeader(HeaderConstants.SERVICE_NAME);
        if (!"true".equalsIgnoreCase(internalCall)
                || !StringUtils.hasText(serviceName)
                || !ALLOWED_SERVICES.contains(serviceName)) {
            writeForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
    }
}
