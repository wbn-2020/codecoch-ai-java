package com.codecoachai.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the resume claim-audit API reachable when a stale Nacos gateway snapshot is loaded.
 */
@Configuration
public class ResumeClaimAuditRouteConfig {

    static final String ROUTE_ID = "resume-claim-audits-fallback";
    static final String PATH = "/resume-claim-audits";
    static final String TARGET_URI = "lb://codecoachai-resume";

    @Bean
    RouteLocator resumeClaimAuditRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(ROUTE_ID, route -> route.path(PATH, PATH + "/**").uri(TARGET_URI))
                .build();
    }
}
