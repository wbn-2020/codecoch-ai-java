package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.annotation.AnnotatedElementUtils;

class CoreControllerRouteContractTest {

    @Test
    void consolidatedControllersHaveNoExactDuplicateRoutes() throws ClassNotFoundException {
        Set<Class<?>> excludedTypes = CoreApplicationContractSupport.componentScanExcludedTypes();
        Map<String, Set<String>> routeOwners = new LinkedHashMap<>();

        for (Class<?> controllerType : controllerTypes()) {
            if (excludedTypes.contains(controllerType)) {
                continue;
            }
            RequestMapping classMapping =
                    AnnotatedElementUtils.findMergedAnnotation(controllerType, RequestMapping.class);
            Set<String> classPaths = paths(classMapping);

            for (Method method : controllerType.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())
                        || method.isBridge()
                        || method.isSynthetic()) {
                    continue;
                }
                RequestMapping methodMapping =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping == null) {
                    continue;
                }
                for (String classPath : classPaths) {
                    for (String methodPath : paths(methodMapping)) {
                        for (RequestMethod requestMethod : methods(methodMapping)) {
                            String route = requestMethod + " " + join(classPath, methodPath);
                            routeOwners
                                    .computeIfAbsent(route, ignored -> new LinkedHashSet<>())
                                    .add(controllerType.getName() + "#" + method.getName());
                        }
                    }
                }
            }
        }

        Map<String, Set<String>> duplicates = new LinkedHashMap<>();
        routeOwners.forEach((route, owners) -> {
            if (owners.size() > 1) {
                duplicates.put(route, owners);
            }
        });
        assertTrue(duplicates.isEmpty(), () -> "Duplicate Core controller routes: " + duplicates);
    }

    private static Set<Class<?>> controllerTypes() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Set<Class<?>> controllers = new LinkedHashSet<>();
        for (var beanDefinition : scanner.findCandidateComponents("com.codecoachai")) {
            controllers.add(Class.forName(beanDefinition.getBeanClassName()));
        }
        return controllers;
    }

    private static Set<String> paths(RequestMapping mapping) {
        if (mapping == null) {
            return Set.of("");
        }
        String[] configuredPaths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return configuredPaths.length == 0
                ? Set.of("")
                : new LinkedHashSet<>(Arrays.asList(configuredPaths));
    }

    private static Set<RequestMethod> methods(RequestMapping mapping) {
        return mapping.method().length == 0
                ? Set.of(
                        RequestMethod.GET,
                        RequestMethod.HEAD,
                        RequestMethod.POST,
                        RequestMethod.PUT,
                        RequestMethod.PATCH,
                        RequestMethod.DELETE,
                        RequestMethod.OPTIONS,
                        RequestMethod.TRACE)
                : new LinkedHashSet<>(Arrays.asList(mapping.method()));
    }

    private static String join(String classPath, String methodPath) {
        String joined = ("/" + classPath + "/" + methodPath).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/")
                ? joined.substring(0, joined.length() - 1)
                : joined;
    }
}
