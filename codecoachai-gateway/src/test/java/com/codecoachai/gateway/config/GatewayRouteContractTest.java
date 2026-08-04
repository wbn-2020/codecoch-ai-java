package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class GatewayRouteContractTest {

    private static final List<String> GATEWAY_CONFIGS = List.of(
            "docs/nacos/codecoachai-gateway-dev.yml");

    private static final String DEDUPE_RESPONSE_HEADER_FILTER =
            "DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE";

    private static final String CORE_SERVICE_URI = serviceUri("core");
    private static final Set<String> STANDALONE_SERVICE_NAMES = Set.of("ai", "search");
    private static final Map<String, String> STANDALONE_ROUTE_TARGETS = Map.of(
            "codecoachai-ai-admin", serviceUri("ai"),
            "codecoachai-search", serviceUri("search"));

    private static final List<String> REQUIRED_PUBLIC_ROUTES = routeGroupTokens(
            "/resume-versions",
            "/resume-suggestions",
            "/resume-ats-templates",
            "/resume-exports",
            "/resume-artifacts",
            "/resume-claim-audits",
            "/applications",
            "/application-packages",
            "/project-evidence",
            "/ability-map",
            "/job-experiments",
            "/job-experiments-v2",
            "/career-calendar",
            "/career-imports",
            "/portfolio-demo",
            "/evidence-assets/overview",
            "/evidence-assets/usages",
            "/evidence-assets/results",
            "/evidence-assets/candidates",
            "/evidence-usages",
            "/evidence-usage-results",
            "/career-campaigns",
            "/career-campaign-archive-exports",
            "/offers",
            "/career-contacts",
            "/career-activities",
            "/research-sources",
            "/research-snapshots",
            "/interview-processes",
            "/interview-rounds",
            "/interview-round-contacts",
            "/interview-comparisons",
            "/interview-scenarios",
            "/interview-remediations",
            "/interview-tts",
            "/interview-streaming-asr",
            "/ai/feedback",
            "/notifications");

    private static final Set<String> REQUIRED_WILDCARD_PUBLIC_ROUTES = Set.of(
            "/growth/**",
            "/analytics/**");

    private static final List<String> REPRESENTATIVE_EXISTING_ROUTES = List.of(
            "/auth/**",
            "/users/**",
            "/admin/users/**",
            "/questions/**",
            "/resumes/**",
            "/job-targets/**",
            "/interviews/**",
            "/agent/**",
            "/admin/ai/**",
            "/files/**",
            "/tasks/**",
            "/search/**",
            "/admin/system/**",
            "/admin/config/**");

    private static final List<RouteFamily> EXPECTED_ROUTE_FAMILIES = List.of(
            routeFamily("auth", "/auth/contract-probe", "auth"),
            routeFamily("users", "/users/contract-probe", "user"),
            routeFamily("admin users", "/admin/users/contract-probe", "user"),
            routeFamily("questions", "/questions/contract-probe", "question"),
            routeFamily("resumes", "/resumes/contract-probe", "resume"),
            routeFamily("job targets", "/job-targets/contract-probe", "resume"),
            routeFamily("resume suggestions", "/resume-suggestions/contract-probe", "resume"),
            routeFamily("resume ATS templates", "/resume-ats-templates/contract-probe", "resume"),
            routeFamily("resume exports", "/resume-exports/contract-probe", "resume"),
            routeFamily("resume artifacts", "/resume-artifacts/contract-probe", "resume"),
            routeFamily("resume claim audits", "/resume-claim-audits/contract-probe", "resume"),
            routeFamily("applications", "/applications/contract-probe", "resume"),
            routeFamily("application packages", "/application-packages/contract-probe", "resume"),
            routeFamily("project evidence", "/project-evidence/contract-probe", "resume"),
            routeFamily("ability map", "/ability-map/contract-probe", "resume"),
            routeFamily("job experiments", "/job-experiments/contract-probe", "resume"),
            routeFamily("job experiments v2", "/job-experiments-v2/contract-probe", "resume"),
            routeFamily("career calendar", "/career-calendar/contract-probe", "resume"),
            routeFamily("career imports", "/career-imports/contract-probe", "resume"),
            routeFamily("portfolio demo", "/portfolio-demo/contract-probe", "resume"),
            routeFamily("evidence assets overview", "/evidence-assets/overview/contract-probe", "resume"),
            routeFamily("evidence assets usages", "/evidence-assets/usages/contract-probe", "resume"),
            routeFamily("evidence assets results", "/evidence-assets/results/contract-probe", "resume"),
            routeFamily("evidence assets candidates", "/evidence-assets/candidates/contract-probe", "ai"),
            routeFamily("evidence usages", "/evidence-usages/contract-probe", "resume"),
            routeFamily("evidence usage results", "/evidence-usage-results/contract-probe", "resume"),
            routeFamily("career campaigns", "/career-campaigns/contract-probe", "resume"),
            routeFamily("career campaign archive exports", "/career-campaign-archive-exports/contract-probe", "resume"),
            routeFamily("offers", "/offers/contract-probe", "resume"),
            routeFamily("career contacts", "/career-contacts/contract-probe", "resume"),
            routeFamily("career activities", "/career-activities/contract-probe", "resume"),
            routeFamily("research sources", "/research-sources/contract-probe", "resume"),
            routeFamily("research snapshots", "/research-snapshots/contract-probe", "resume"),
            routeFamily("interview processes", "/interview-processes/contract-probe", "resume"),
            routeFamily("interview rounds", "/interview-rounds/contract-probe", "resume"),
            routeFamily("interview round contacts", "/interview-round-contacts/contract-probe", "resume"),
            routeFamily("interviews", "/interviews/contract-probe", "interview"),
            routeFamily("interview comparisons", "/interview-comparisons/contract-probe", "interview"),
            routeFamily("interview scenarios", "/interview-scenarios/contract-probe", "interview"),
            routeFamily("interview remediations", "/interview-remediations/contract-probe", "interview"),
            routeFamily("interview TTS", "/interview-tts/contract-probe", "interview"),
            routeFamily("interview streaming ASR", "/interview-streaming-asr/contract-probe", "interview"),
            routeFamily("ai result feedback", "/ai/feedback/contract-probe", "ai"),
            routeFamily("growth", "/growth/contract-probe", "ai"),
            routeFamily("analytics", "/analytics/contract-probe", "ai"),
            routeFamily("files", "/files/contract-probe", "file"),
            routeFamily("tasks", "/tasks/contract-probe", "task"),
            routeFamily("notifications", "/notifications/contract-probe", "task"),
            routeFamily("search", "/search/contract-probe", "search"),
            routeFamily("system", "/admin/system/contract-probe", "system"),
            routeFamily("system config", "/admin/config/contract-probe", "system"));

    private static final List<KnownOverlap> KNOWN_OVERLAPS = List.of(
            knownOverlap(
                    "role menus",
                    "/admin/roles/42/menus",
                    "system",
                    "user"),
            knownOverlap(
                    "admin AI questions",
                    "/admin/ai/questions/42",
                    "question",
                    "ai"));

    private static final List<String> INNER_PATH_PROBES = List.of(
            "/inner",
            "/inner/contract-probe",
            "/inner/contract-probe/deep",
            "/inner/users/42",
            "/inner/questions/42",
            "/inner/resumes/42",
            "/inner/interviews/42/report-context",
            "/inner/ai/interview/question",
            "/inner/files/42",
            "/inner/notifications/resolve-by-biz",
            "/inner/job-targets/users/42/current",
            "/inner/agent/reminders/candidates");

    private static final Set<String> DEV_ORIGINS = Set.of(
            "http://nqx.githubpage.com:30080",
            "http://103.236.97.252:30080");

    @Test
    void devGatewayConfigsExposeTheReleaseRoutesAndCorsOriginWithoutInnerRoutes() throws IOException {
        for (GatewayConfig config : readGatewayConfigs().values()) {
            Set<String> routeTokens = config.routeTokens();

            for (String route : REQUIRED_PUBLIC_ROUTES) {
                assertTrue(
                        routeTokens.contains(route),
                        () -> config.relativePath() + " must expose exact route token " + route);
            }

            for (String route : REQUIRED_WILDCARD_PUBLIC_ROUTES) {
                assertTrue(
                        routeTokens.contains(route),
                        () -> config.relativePath() + " must expose " + route);
            }

            for (String route : REPRESENTATIVE_EXISTING_ROUTES) {
                assertTrue(
                        routeTokens.contains(route),
                        () -> config.relativePath() + " must retain existing route " + route);
            }

            for (GatewayRoute route : config.routes()) {
                for (String pathPattern : route.pathPatterns()) {
                    for (String innerPath : INNER_PATH_PROBES) {
                        assertFalse(
                                GatewayRoute.matches(pathPattern, innerPath),
                                () -> config.relativePath() + " Path token " + pathPattern
                                        + " on target URI " + route.uri()
                                        + " must not expose internal path " + innerPath);
                    }
                }
            }

            assertTrue(
                    config.globalCorsAllowedOriginPatterns().containsAll(DEV_ORIGINS),
                    () -> config.relativePath() + " globalcors must allow " + DEV_ORIGINS);
            assertTrue(
                    config.applicationCorsAllowedOriginPatterns().containsAll(DEV_ORIGINS),
                    () -> config.relativePath() + " codecoachai.gateway.cors must allow " + DEV_ORIGINS);
            assertTrue(
                    config.globalCorsExposedHeaders().contains("X-Trace-Id"),
                    () -> config.relativePath() + " globalcors must expose X-Trace-Id");
            assertTrue(
                    config.applicationCorsExposedHeaders().contains("X-Trace-Id"),
                    () -> config.relativePath()
                            + " codecoachai.gateway.cors must expose X-Trace-Id");
        }
    }

    @Test
    void routeFamiliesHaveOneExpectedOwner() throws IOException {
        for (GatewayConfig config : readGatewayConfigs().values()) {
            for (RouteFamily family : EXPECTED_ROUTE_FAMILIES) {
                List<GatewayRoute> owners = config.routesMatching(family.representativePath());
                assertEquals(
                        1,
                        owners.size(),
                        () -> config.relativePath() + " route family " + family.name()
                                + " must have exactly one owner for " + family.representativePath()
                                + "; matching routes=" + owners);

                GatewayRoute owner = owners.get(0);
                assertEquals(
                        family.targetUri(),
                        owner.uri(),
                        () -> config.relativePath() + " route family " + family.name()
                                + " must belong to target URI " + family.targetUri()
                                + "; actual owner=" + owner);
            }
        }
    }

    @Test
    void onlyAiAndSearchRoutesRemainSeparateFromTheCoreService() throws IOException {
        for (GatewayConfig config : readGatewayConfigs().values()) {
            assertEquals(
                    39,
                    config.routes().size(),
                    () -> config.relativePath() + " must retain its complete route set");

            long coreRouteCount = 0;
            for (GatewayRoute route : config.routes()) {
                String standaloneTarget = STANDALONE_ROUTE_TARGETS.get(route.id());
                if (standaloneTarget != null) {
                    assertEquals(
                            standaloneTarget,
                            route.uri(),
                            () -> config.relativePath() + " route " + route.id()
                                    + " must retain its standalone target");
                    continue;
                }

                assertEquals(
                        CORE_SERVICE_URI,
                        route.uri(),
                        () -> config.relativePath() + " business route " + route.id()
                                + " must target the Core service");
                coreRouteCount++;
            }

            assertEquals(
                    37,
                    coreRouteCount,
                    () -> config.relativePath() + " must direct 37 business routes to Core");
            assertEquals(
                    Set.of(CORE_SERVICE_URI, serviceUri("ai"), serviceUri("search")),
                    config.routes().stream().map(GatewayRoute::uri).collect(Collectors.toSet()),
                    () -> config.relativePath() + " may target only Core, AI, and Search");
        }
    }

    @Test
    void resumeClaimAuditsUseTheirDedicatedGatewayRoute() throws IOException {
        for (GatewayConfig config : readGatewayConfigs().values()) {
            GatewayRoute route = config.routesMatching("/resume-claim-audits/contract-probe").get(0);
            assertTrue(
                    route.id().contains("claim-audit"),
                    () -> config.relativePath()
                            + " must keep resume claim audits on a dedicated route; actual route="
                            + route.id());
            assertEquals(
                    List.of("/resume-claim-audits", "/resume-claim-audits/**"),
                    route.pathPatterns(),
                    () -> config.relativePath()
                            + " must keep resume claim audits isolated from the aggregate resume route");
        }
    }

    @Test
    void resumeEvidenceResultsUseTheirDedicatedGatewayRoute() throws IOException {
        List<String> expectedPatterns = routeGroupTokens(
                "/evidence-assets/overview",
                "/evidence-assets/usages",
                "/evidence-assets/results",
                "/evidence-usages",
                "/evidence-usage-results");
        for (GatewayConfig config : readGatewayConfigs().values()) {
            GatewayRoute route = config.routesMatching("/evidence-usages/contract-probe").get(0);
            assertTrue(
                    route.id().contains("evidence-results"),
                    () -> config.relativePath()
                            + " must keep evidence result routes on a dedicated route; actual route="
                            + route.id());
            assertEquals(
                    expectedPatterns,
                    route.pathPatterns(),
                    () -> config.relativePath()
                            + " must keep evidence result routes isolated from the aggregate resume route");
        }
    }

    @Test
    void knownOverlapsKeepSpecificRoutesBeforeBroadRoutes() throws IOException {
        for (GatewayConfig config : readGatewayConfigs().values()) {
            for (KnownOverlap overlap : KNOWN_OVERLAPS) {
                List<String> matchingUris = config.routesMatching(overlap.representativePath()).stream()
                        .map(GatewayRoute::uri)
                        .toList();

                assertEquals(
                        List.of(overlap.specificTargetUri(), overlap.broadTargetUri()),
                        matchingUris,
                        () -> config.relativePath() + " overlap " + overlap.name()
                                + " must be owned by the specific target before the broad target for "
                                + overlap.representativePath()
                                + "; matching target URIs=" + matchingUris);
            }
        }
    }

    @Test
    void pathTokenDeclarationsStayUnique() throws IOException {
        Map<String, GatewayConfig> configs = readGatewayConfigs();
        for (GatewayConfig config : configs.values()) {
            Map<String, Long> duplicates = config.routeTokenOccurrences().entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (left, right) -> left,
                            LinkedHashMap::new));
            assertTrue(
                    duplicates.isEmpty(),
                    () -> config.relativePath() + " must not declare an exact Path token more than once"
                            + "; duplicate occurrence counts=" + duplicates);
        }
    }

    @Test
    void eachConfigUsesExactlyOneDedupeResponseHeaderDefaultFilter() throws IOException {
        for (GatewayConfig config : readGatewayConfigs().values()) {
            long occurrenceCount = config.defaultFilters().stream()
                    .filter(DEDUPE_RESPONSE_HEADER_FILTER::equals)
                    .count();
            assertEquals(
                    1,
                    occurrenceCount,
                    () -> config.relativePath() + " must configure exactly one default filter "
                            + DEDUPE_RESPONSE_HEADER_FILTER);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("docs/nacos/codecoachai-gateway-dev.yml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        return current;
    }

    private static Map<String, GatewayConfig> readGatewayConfigs() throws IOException {
        Map<String, GatewayConfig> configs = new LinkedHashMap<>();
        for (String relativePath : GATEWAY_CONFIGS) {
            String yaml = Files.readString(repositoryRoot().resolve(relativePath));
            configs.put(relativePath, GatewayConfig.parse(relativePath, yaml));
        }
        return configs;
    }

    private static List<String> routeGroupTokens(String... roots) {
        List<String> tokens = new ArrayList<>(roots.length * 2);
        for (String root : roots) {
            tokens.add(root);
            tokens.add(root + "/**");
        }
        return List.copyOf(tokens);
    }

    private static RouteFamily routeFamily(String name, String representativePath, String logicalService) {
        return new RouteFamily(name, representativePath, deployableServiceUri(logicalService));
    }

    private static KnownOverlap knownOverlap(
            String name, String representativePath, String specificLogicalService, String broadLogicalService) {
        return new KnownOverlap(
                name,
                representativePath,
                deployableServiceUri(specificLogicalService),
                deployableServiceUri(broadLogicalService));
    }

    private static String deployableServiceUri(String logicalService) {
        return STANDALONE_SERVICE_NAMES.contains(logicalService)
                ? serviceUri(logicalService)
                : CORE_SERVICE_URI;
    }

    private static String serviceUri(String service) {
        return "lb://codecoachai-" + service;
    }

    private static Map<String, Object> mapValue(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected YAML map at key " + key + ", got " + value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((mapKey, mapValue) -> result.put(String.valueOf(mapKey), mapValue));
        return result;
    }

    private static List<Object> listValue(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected YAML list at key " + key + ", got " + value);
        }
        return new ArrayList<>(list);
    }

    private static List<String> stringListValue(Map<String, Object> parent, String key) {
        return listValue(parent, key).stream().map(String::valueOf).toList();
    }

    private static List<String> optionalStringListValue(Map<String, Object> parent, String key) {
        return parent.containsKey(key) ? stringListValue(parent, key) : List.of();
    }

    private record RouteFamily(String name, String representativePath, String targetUri) {}

    private record KnownOverlap(
            String name,
            String representativePath,
            String specificTargetUri,
            String broadTargetUri) {}

    private record GatewayRoute(String id, String uri, List<String> pathPatterns) {

        private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

        private boolean matches(String requestPath) {
            return pathPatterns.stream()
                    .anyMatch(pathPattern -> matches(pathPattern, requestPath));
        }

        private static boolean matches(String pathPattern, String requestPath) {
            return PATH_PATTERN_PARSER.parse(pathPattern).matches(PathContainer.parsePath(requestPath));
        }
    }

    private record GatewayConfig(
            String relativePath,
            List<GatewayRoute> routes,
            Set<String> globalCorsAllowedOriginPatterns,
            Set<String> applicationCorsAllowedOriginPatterns,
            Set<String> globalCorsExposedHeaders,
            Set<String> applicationCorsExposedHeaders,
            List<String> defaultFilters) {

        private static GatewayConfig parse(String relativePath, String yamlText) {
            LoaderOptions loaderOptions = new LoaderOptions();
            Object loaded = new Yaml(new SafeConstructor(loaderOptions)).load(yamlText);
            if (!(loaded instanceof Map<?, ?> loadedMap)) {
                throw new IllegalArgumentException(relativePath + " must contain a YAML mapping");
            }

            Map<String, Object> root = new LinkedHashMap<>();
            loadedMap.forEach((key, value) -> root.put(String.valueOf(key), value));
            Map<String, Object> gateway = mapValue(mapValue(mapValue(root, "spring"), "cloud"), "gateway");

            List<GatewayRoute> routes = listValue(gateway, "routes").stream()
                    .map(GatewayConfig::parseRoute)
                    .toList();

            Map<String, Object> corsConfigurations =
                    mapValue(mapValue(gateway, "globalcors"), "cors-configurations");
            Map<String, Object> allPathsCors = mapValue(corsConfigurations, "[/**]");
            Set<String> globalOrigins =
                    new LinkedHashSet<>(stringListValue(allPathsCors, "allowedOriginPatterns"));
            Set<String> globalExposedHeaders =
                    new LinkedHashSet<>(optionalStringListValue(allPathsCors, "exposedHeaders"));

            Map<String, Object> applicationCors =
                    mapValue(mapValue(mapValue(root, "codecoachai"), "gateway"), "cors");
            Set<String> applicationOrigins = Arrays.stream(
                            String.valueOf(applicationCors.get("allowed-origin-patterns")).split(","))
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> applicationExposedHeaders = Arrays.stream(
                            String.valueOf(applicationCors.getOrDefault("exposed-headers", "")).split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            return new GatewayConfig(
                    relativePath,
                    routes,
                    Set.copyOf(globalOrigins),
                    Set.copyOf(applicationOrigins),
                    Set.copyOf(globalExposedHeaders),
                    Set.copyOf(applicationExposedHeaders),
                    List.copyOf(optionalStringListValue(gateway, "default-filters")));
        }

        private static GatewayRoute parseRoute(Object value) {
            if (!(value instanceof Map<?, ?> routeMap)) {
                throw new IllegalArgumentException("Expected route mapping, got " + value);
            }
            Map<String, Object> route = new LinkedHashMap<>();
            routeMap.forEach((key, routeValue) -> route.put(String.valueOf(key), routeValue));

            List<String> pathPatterns = stringListValue(route, "predicates").stream()
                    .filter(predicate -> predicate.startsWith("Path="))
                    .flatMap(predicate -> Arrays.stream(predicate.substring("Path=".length()).split(",")))
                    .map(String::trim)
                    .filter(pattern -> !pattern.isEmpty())
                    .toList();

            return new GatewayRoute(
                    String.valueOf(route.get("id")),
                    String.valueOf(route.get("uri")),
                    pathPatterns);
        }

        private Set<String> routeTokens() {
            return routeTokenOccurrences().keySet();
        }

        private Map<String, Long> routeTokenOccurrences() {
            return routes.stream()
                    .flatMap(route -> route.pathPatterns().stream())
                    .collect(Collectors.groupingBy(
                            pathPattern -> pathPattern,
                            LinkedHashMap::new,
                            Collectors.counting()));
        }

        private List<GatewayRoute> routesMatching(String requestPath) {
            return routes.stream().filter(route -> route.matches(requestPath)).toList();
        }
    }
}
