package com.codecoachai.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps V7 career resources reachable when a stale or shortened Nacos route snapshot is loaded.
 */
@Configuration
public class V7CareerRouteConfig {

    static final String TARGET_URI = "lb://codecoachai-resume";
    static final String CAMPAIGN_ROUTE_ID = "v7-career-campaigns-fallback";
    static final String INTERVIEW_ROUTE_ID = "v7-career-interviews-fallback";
    static final String OFFER_ROUTE_ID = "v7-career-offers-fallback";
    static final String CONTACT_ACTIVITY_ROUTE_ID = "v7-career-contact-activity-fallback";
    static final String RESEARCH_ROUTE_ID = "v7-career-research-fallback";

    static final String CAMPAIGN_PATH = "/career-campaigns";
    static final String INTERVIEW_PROCESS_PATH = "/interview-processes";
    static final String INTERVIEW_ROUND_PATH = "/interview-rounds";
    static final String INTERVIEW_ROUND_CONTACT_PATH = "/interview-round-contacts";
    static final String OFFER_PATH = "/offers";
    static final String CONTACT_PATH = "/career-contacts";
    static final String ACTIVITY_PATH = "/career-activities";
    static final String RESEARCH_SOURCE_PATH = "/research-sources";
    static final String RESEARCH_SNAPSHOT_PATH = "/research-snapshots";

    @Bean
    RouteLocator v7CareerRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(CAMPAIGN_ROUTE_ID, route -> route
                        .path(CAMPAIGN_PATH, CAMPAIGN_PATH + "/**")
                        .uri(TARGET_URI))
                .route(INTERVIEW_ROUTE_ID, route -> route
                        .path(
                                INTERVIEW_PROCESS_PATH,
                                INTERVIEW_PROCESS_PATH + "/**",
                                INTERVIEW_ROUND_PATH,
                                INTERVIEW_ROUND_PATH + "/**",
                                INTERVIEW_ROUND_CONTACT_PATH,
                                INTERVIEW_ROUND_CONTACT_PATH + "/**")
                        .uri(TARGET_URI))
                .route(OFFER_ROUTE_ID, route -> route
                        .path(OFFER_PATH, OFFER_PATH + "/**")
                        .uri(TARGET_URI))
                .route(CONTACT_ACTIVITY_ROUTE_ID, route -> route
                        .path(
                                CONTACT_PATH,
                                CONTACT_PATH + "/**",
                                ACTIVITY_PATH,
                                ACTIVITY_PATH + "/**")
                        .uri(TARGET_URI))
                .route(RESEARCH_ROUTE_ID, route -> route
                        .path(
                                RESEARCH_SOURCE_PATH,
                                RESEARCH_SOURCE_PATH + "/**",
                                RESEARCH_SNAPSHOT_PATH,
                                RESEARCH_SNAPSHOT_PATH + "/**")
                        .uri(TARGET_URI))
                .build();
    }
}
