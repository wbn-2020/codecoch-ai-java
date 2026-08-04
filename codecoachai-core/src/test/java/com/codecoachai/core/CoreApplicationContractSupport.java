package com.codecoachai.core;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

final class CoreApplicationContractSupport {

    private CoreApplicationContractSupport() {
    }

    static Set<Class<?>> componentScanExcludedTypes() {
        ComponentScan componentScan = CoreApplication.class.getAnnotation(ComponentScan.class);
        return Arrays.stream(componentScan.excludeFilters())
                .flatMap(filter -> Arrays.stream(filter.classes()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<String> mapperPackages(MapperScan[] mapperScans, Class<? extends Annotation> annotationClass) {
        return Arrays.stream(mapperScans)
                .filter(mapperScan -> mapperScan.annotationClass().equals(annotationClass))
                .flatMap(mapperScan -> Arrays.stream(mapperScan.basePackages()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
