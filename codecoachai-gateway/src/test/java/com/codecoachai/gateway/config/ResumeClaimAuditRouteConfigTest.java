package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class ResumeClaimAuditRouteConfigTest {

    @Test
    void matchesTheClaimAuditRootAndNestedPathsAndTargetsTheResumeService() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(PathRoutePredicateFactory.class);
            context.refresh();

            RouteLocator routeLocator = new ResumeClaimAuditRouteConfig()
                    .resumeClaimAuditRouteLocator(new RouteLocatorBuilder(context));
            List<Route> routes = routeLocator.getRoutes().collectList().block();

            assertEquals(1, routes.size());
            Route route = routes.get(0);
            assertEquals(ResumeClaimAuditRouteConfig.ROUTE_ID, route.getId());
            assertEquals(ResumeClaimAuditRouteConfig.TARGET_URI, route.getUri().toString());
            assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                    MockServerHttpRequest.get(ResumeClaimAuditRouteConfig.PATH).build()))).block());
            assertTrue(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                    MockServerHttpRequest.get(ResumeClaimAuditRouteConfig.PATH + "/17").build()))).block());
        }
    }
}
