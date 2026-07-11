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
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class GatewayRouteContractTest {

    private static final List<String> GATEWAY_CONFIGS = List.of(
            "docs/nacos/codecoachai-gateway-dev.yml",
            "config/nacos/codecoachai-gateway-dev.yml");

    private static final String DEDUPE_RESPONSE_HEADER_FILTER =
            "DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE";

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
            "/interview-comparisons",
            "/interview-scenarios",
            "/interview-remediations",
            "/interview-tts",
            "/interview-streaming-asr",
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
            routeFamily("interviews", "/interviews/contract-probe", "interview"),
            routeFamily("interview comparisons", "/interview-comparisons/contract-probe", "interview"),
            routeFamily("interview scenarios", "/interview-scenarios/contract-probe", "interview"),
            routeFamily("interview remediations", "/interview-remediations/contract-probe", "interview"),
            routeFamily("interview TTS", "/interview-tts/contract-probe", "interview"),
            routeFamily("interview streaming ASR", "/interview-streaming-asr/contract-probe", "interview"),
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

    private static final String DEV_ORIGIN = "http://nqx.githubpage.com:30080";

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
                    config.globalCorsAllowedOriginPatterns().contains(DEV_ORIGIN),
                    () -> config.relativePath() + " globalcors must allow " + DEV_ORIGIN);
            assertTrue(
                    config.applicationCorsAllowedOriginPatterns().contains(DEV_ORIGIN),
                    () -> config.relativePath() + " codecoachai.gateway.cors must allow " + DEV_ORIGIN);
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
    void pathTokenDeclarationsStayUniqueAndMirroredWithOccurrenceCounts() throws IOException {
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

        String docsPath = GATEWAY_CONFIGS.get(0);
        String configPath = GATEWAY_CONFIGS.get(1);
        Map<String, Long> docsOccurrences = configs.get(docsPath).routeTokenOccurrences();
        Map<String, Long> configOccurrences = configs.get(configPath).routeTokenOccurrences();
        Set<String> differingTokens = new TreeSet<>();
        differingTokens.addAll(docsOccurrences.keySet());
        differingTokens.addAll(configOccurrences.keySet());
        differingTokens.removeIf(token ->
                docsOccurrences.getOrDefault(token, 0L).equals(configOccurrences.getOrDefault(token, 0L)));

        assertTrue(
                differingTokens.isEmpty(),
                () -> docsPath + " and " + configPath
                        + " Path token occurrence counts must stay equal"
                        + "; differences=" + differingTokens.stream()
                                .collect(Collectors.toMap(
                                        token -> token,
                                        token -> "docs=" + docsOccurrences.getOrDefault(token, 0L)
                                                + ", config=" + configOccurrences.getOrDefault(token, 0L),
                                        (left, right) -> left,
                                        LinkedHashMap::new)));
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

    private static RouteFamily routeFamily(String name, String representativePath, String service) {
        return new RouteFamily(name, representativePath, serviceUri(service));
    }

    private static KnownOverlap knownOverlap(
            String name, String representativePath, String specificService, String broadService) {
        return new KnownOverlap(
                name,
                representativePath,
                serviceUri(specificService),
                serviceUri(broadService));
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

            Map<String, Object> applicationCors =
                    mapValue(mapValue(mapValue(root, "codecoachai"), "gateway"), "cors");
            Set<String> applicationOrigins = Arrays.stream(
                            String.valueOf(applicationCors.get("allowed-origin-patterns")).split(","))
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            return new GatewayConfig(
                    relativePath,
                    routes,
                    Set.copyOf(globalOrigins),
                    Set.copyOf(applicationOrigins),
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
