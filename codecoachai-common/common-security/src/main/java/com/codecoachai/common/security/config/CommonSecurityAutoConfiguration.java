package com.codecoachai.common.security.config;

import com.codecoachai.common.security.filter.InternalCallFilter;
import com.codecoachai.common.security.filter.LoginUserContextFilter;
import com.codecoachai.common.security.internal.TrustedRequestVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({InternalAuthProperties.class, AdminPermissionCacheProperties.class})
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<LoginUserContextFilter> loginUserContextFilter(
            InternalAuthProperties internalAuthProperties,
            TrustedRequestVerifier trustedRequestVerifier) {
        FilterRegistrationBean<LoginUserContextFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(
                new LoginUserContextFilter(internalAuthProperties, trustedRequestVerifier));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    @ConditionalOnMissingBean
    public TrustedRequestVerifier trustedRequestVerifier(
            InternalAuthProperties internalAuthProperties,
            StringRedisTemplate stringRedisTemplate) {
        return new TrustedRequestVerifier(internalAuthProperties, stringRedisTemplate);
    }

    @Bean
    public FilterRegistrationBean<InternalCallFilter> internalCallFilter(
            InternalAuthProperties internalAuthProperties,
            TrustedRequestVerifier trustedRequestVerifier) {
        FilterRegistrationBean<InternalCallFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new InternalCallFilter(internalAuthProperties, trustedRequestVerifier));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

}
