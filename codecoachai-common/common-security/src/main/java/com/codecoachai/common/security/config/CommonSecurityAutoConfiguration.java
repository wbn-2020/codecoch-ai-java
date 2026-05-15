package com.codecoachai.common.security.config;

import com.codecoachai.common.security.filter.AdminRoleFilter;
import com.codecoachai.common.security.filter.InternalCallFilter;
import com.codecoachai.common.security.filter.LoginUserContextFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<LoginUserContextFilter> loginUserContextFilter() {
        FilterRegistrationBean<LoginUserContextFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new LoginUserContextFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<InternalCallFilter> internalCallFilter() {
        FilterRegistrationBean<InternalCallFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new InternalCallFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<AdminRoleFilter> adminRoleFilter() {
        FilterRegistrationBean<AdminRoleFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AdminRoleFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}
