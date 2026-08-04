package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class ResumeClaimAuditRouteConfigTest {

    @Test
    void fallbackRouteIsNotRegisteredByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(ResumeClaimAuditRouteConfig.class)
                .run(context -> assertFalse(context.containsBean("resumeClaimAuditRouteLocator")));
    }

    @Test
    void explicitlyEnabledFallbackMatchesClaimAuditPathsAndTargetsCore() {
        routeContextRunner()
                .withPropertyValues("codecoachai.gateway.routes.legacy-fallbacks.enabled=true")
                .run(context -> {
                    assertTrue(context.containsBean("resumeClaimAuditRouteLocator"));
                    RouteLocator routeLocator = context.getBean(
                            "resumeClaimAuditRouteLocator",
                            RouteLocator.class);
                    List<Route> routes = routeLocator.getRoutes().collectList().block();

                    assertEquals(1, routes.size());
                    Route route = routes.get(0);
                    assertEquals(ResumeClaimAuditRouteConfig.ROUTE_ID, route.getId());
                    assertEquals("lb://codecoachai-core", ResumeClaimAuditRouteConfig.TARGET_URI);
                    assertEquals(ResumeClaimAuditRouteConfig.TARGET_URI, route.getUri().toString());
                    assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                            MockServerHttpRequest.get(ResumeClaimAuditRouteConfig.PATH).build()))).block());
                    assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                            MockServerHttpRequest.get(ResumeClaimAuditRouteConfig.PATH + "/17").build()))).block());
                });
    }

    private ApplicationContextRunner routeContextRunner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().registerSingleton(
                            "pathRoutePredicateFactory",
                            new PathRoutePredicateFactory());
                    context.getBeanFactory().registerSingleton(
                            "routeLocatorBuilder",
                            new RouteLocatorBuilder(context));
                })
                .withUserConfiguration(ResumeClaimAuditRouteConfig.class);
    }
}
