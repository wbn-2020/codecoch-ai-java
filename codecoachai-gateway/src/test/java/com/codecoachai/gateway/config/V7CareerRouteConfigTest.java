package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.GenericApplicationContext;
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
    void matchesAllV7CareerRootsAndNestedPathsOnResumeService() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory.class);
            context.refresh();

            RouteLocator routeLocator = new V7CareerRouteConfig()
                    .v7CareerRouteLocator(new RouteLocatorBuilder(context));
            List<Route> routes = routeLocator.getRoutes().collectList().block();

            assertEquals(ROUTE_PATHS.size(), routes.size());
            for (Route route : routes) {
                assertEquals(V7CareerRouteConfig.TARGET_URI, route.getUri().toString());
                for (String root : ROUTE_PATHS.get(route.getId())) {
                    assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                            MockServerHttpRequest.get(root).build()))).block());
                    assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                            MockServerHttpRequest.get(root + "/17").build()))).block());
                }
            }
        }
    }
}
