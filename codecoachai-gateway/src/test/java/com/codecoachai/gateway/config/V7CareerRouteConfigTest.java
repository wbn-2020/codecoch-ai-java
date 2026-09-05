package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class V7CareerRouteConfigTest {

    private static final Map<String, List<String>> ROUTE_PATHS = Map.of(
            V7CareerRouteConfig.CAMPAIGN_ROUTE_ID,
            List.of(V7CareerRouteConfig.CAMPAIGN_PATH),
            V7CareerRouteConfig.INTERVIEW_ROUTE_ID,
            List.of(
                    V7CareerRouteConfig.INTERVIEW_PROCESS_PATH,
                    V7CareerRouteConfig.INTERVIEW_ROUND_PATH,
                    V7CareerRouteConfig.INTERVIEW_ROUND_CONTACT_PATH),
            V7CareerRouteConfig.OFFER_ROUTE_ID,
            List.of(V7CareerRouteConfig.OFFER_PATH),
            V7CareerRouteConfig.CONTACT_ACTIVITY_ROUTE_ID,
            List.of(V7CareerRouteConfig.CONTACT_PATH, V7CareerRouteConfig.ACTIVITY_PATH),
            V7CareerRouteConfig.RESEARCH_ROUTE_ID,
            List.of(
                    V7CareerRouteConfig.RESEARCH_SOURCE_PATH,
                    V7CareerRouteConfig.RESEARCH_SNAPSHOT_PATH));

    @Test
    void fallbackRoutesAreNotRegisteredByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(V7CareerRouteConfig.class)
                .run(context -> assertFalse(context.containsBean("v7CareerRouteLocator")));
    }

    @Test
    void explicitlyEnabledFallbackMatchesAllV7CareerRootsAndNestedPathsOnCoreService() {
        routeContextRunner()
                .withPropertyValues("codecoachai.gateway.routes.legacy-fallbacks.enabled=true")
                .run(context -> {
                    assertTrue(context.containsBean("v7CareerRouteLocator"));
                    RouteLocator routeLocator = context.getBean(
                            "v7CareerRouteLocator",
                            RouteLocator.class);
                    List<Route> routes = routeLocator.getRoutes().collectList().block();

                    assertEquals(ROUTE_PATHS.size(), routes.size());
                    assertEquals("lb://codecoachai-core", V7CareerRouteConfig.TARGET_URI);
                    for (Route route : routes) {
                        assertEquals(V7CareerRouteConfig.TARGET_URI, route.getUri().toString());
                        for (String root : ROUTE_PATHS.get(route.getId())) {
                            assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                                    MockServerHttpRequest.get(root).build()))).block());
                            assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                                    MockServerHttpRequest.get(root + "/17").build()))).block());
                        }
                    }
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
                .withUserConfiguration(V7CareerRouteConfig.class);
    }
}
