package com.codecoachai.common.security.filter;

import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.HeaderUserContextReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginUserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            LoginUserContext.setLoginUser(HeaderUserContextReader.read(request));
            filterChain.doFilter(request, response);
        } finally {
            LoginUserContext.clear();
        }
    }
}
