package com.codecoachai.interview.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class AiSseControllerMappingTest {

    @Test
    void interviewCommentOnlyAcceptsPostBody() {
        Method[] methods = AiSseController.class.getDeclaredMethods();

        boolean hasLegacyGet = Arrays.stream(methods)
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch("/interview-comment"::equals);
        boolean hasSafePost = Arrays.stream(methods)
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch("/interview-comment"::equals);

        assertFalse(hasLegacyGet);
        assertTrue(hasSafePost);
    }
}
