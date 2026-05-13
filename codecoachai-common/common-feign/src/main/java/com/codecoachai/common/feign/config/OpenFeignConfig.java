package com.codecoachai.common.feign.config;

import com.codecoachai.common.core.constant.HeaderConstants;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.StringUtils;

@Configuration
public class OpenFeignConfig {

    private static final List<String> PASS_HEADERS = List.of(
            HeaderConstants.AUTHORIZATION,
            HeaderConstants.USER_ID,
            HeaderConstants.USERNAME,
            HeaderConstants.ROLES,
            HeaderConstants.TRACE_ID
    );

    @Bean
    public RequestInterceptor codeCoachAiFeignRequestInterceptor(
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                for (String header : PASS_HEADERS) {
                    String value = request.getHeader(header);
                    if (StringUtils.hasText(value)) {
                        template.header(header, value);
                    }
                }
            }
            template.header(HeaderConstants.INTERNAL_CALL, "true");
            template.header(HeaderConstants.SERVICE_NAME, serviceName);
        };
    }
}
