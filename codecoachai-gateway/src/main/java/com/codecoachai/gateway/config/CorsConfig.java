package com.codecoachai.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${codecoachai.gateway.cors.allowed-origin-patterns:"
                    + "http://localhost:5173,http://127.0.0.1:5173,"
                    + "http://localhost:3000,http://127.0.0.1:3000}")
            String allowedOriginPatterns) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = parseOrigins(allowedOriginPatterns);
        if (origins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException(
                    "codecoachai.gateway.cors.allowed-origin-patterns cannot contain * when credentials are allowed");
        }
        origins.forEach(configuration::addAllowedOriginPattern);
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }

    private List<String> parseOrigins(String allowedOriginPatterns) {
        List<String> origins = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("codecoachai.gateway.cors.allowed-origin-patterns must not be empty");
        }
        return origins;
    }
}
