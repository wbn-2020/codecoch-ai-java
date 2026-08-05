package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.auth.AuthApplication;
import com.codecoachai.file.FileApplication;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.InterviewApplication;
import com.codecoachai.question.QuestionApplication;
import com.codecoachai.question.feign.AiQuestionFeignClient;
import com.codecoachai.resume.ResumeApplication;
import com.codecoachai.system.SystemApplication;
import com.codecoachai.task.TaskApplication;
import com.codecoachai.user.UserApplication;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.ComponentScan;

class CoreApplicationContractTest {

    @Test
    void excludesEveryLegacyBootApplicationAndHealthController() {
        ComponentScan componentScan = CoreApplication.class.getAnnotation(ComponentScan.class);

        assertEquals(Set.of("com.codecoachai"), Set.of(componentScan.basePackages()));

        Set<Class<?>> excludedTypes = CoreApplicationContractSupport.componentScanExcludedTypes();

        assertTrue(excludedTypes.containsAll(Set.of(
                CoreApplication.class,
                AuthApplication.class,
                UserApplication.class,
                SystemApplication.class,
                FileApplication.class,
                ResumeApplication.class,
                QuestionApplication.class,
                InterviewApplication.class,
                TaskApplication.class,
                com.codecoachai.auth.controller.HealthController.class,
                com.codecoachai.user.controller.HealthController.class,
                com.codecoachai.system.controller.HealthController.class,
                com.codecoachai.file.controller.HealthController.class,
                com.codecoachai.resume.controller.HealthController.class,
                com.codecoachai.question.controller.HealthController.class,
                com.codecoachai.interview.controller.HealthController.class)));
    }

    @Test
    void registersOnlyCrossProcessAiFeignClients() {
        EnableFeignClients enableFeignClients = CoreApplication.class.getAnnotation(EnableFeignClients.class);

        assertEquals(11, enableFeignClients.clients().length);
        Set<String> contextIds = new LinkedHashSet<>();
        for (Class<?> clientType : enableFeignClients.clients()) {
            FeignClient feignClient = clientType.getAnnotation(FeignClient.class);
            assertEquals("codecoachai-ai", feignClient.name(), clientType.getName());
            assertTrue(!feignClient.contextId().isBlank(), clientType.getName());
            assertTrue(contextIds.add(feignClient.contextId()), feignClient.contextId());
        }
    }

    @Test
    void interviewAiFeignClientUsesItsLongRunningConfigurationContext() {
        FeignClient feignClient = AiFeignClient.class.getAnnotation(FeignClient.class);

        assertEquals("interviewAiFeignClient", feignClient.contextId());
    }

    @Test
    void questionAiFeignClientUsesItsNamedConfigurationContext() {
        FeignClient feignClient = AiQuestionFeignClient.class.getAnnotation(FeignClient.class);

        assertEquals("aiQuestionFeignClient", feignClient.contextId());
    }

    @Test
    void scansEveryCoreBusinessMapperPackage() {
        MapperScan[] mapperScans = CoreApplication.class.getAnnotationsByType(MapperScan.class);
        Set<String> mapperPackages = CoreApplicationContractSupport.mapperPackages(mapperScans, Annotation.class);
        Set<String> annotatedMapperPackages = CoreApplicationContractSupport.mapperPackages(mapperScans, Mapper.class);

        assertTrue(mapperPackages.containsAll(Set.of(
                "com.codecoachai.user.mapper",
                "com.codecoachai.system.mapper",
                "com.codecoachai.file.mapper",
                "com.codecoachai.resume.mapper",
                "com.codecoachai.question.mapper",
                "com.codecoachai.interview.mapper",
                "com.codecoachai.task.mapper")));
        assertEquals(Set.of(
                "com.codecoachai.resume.careercampaign",
                "com.codecoachai.resume.campaignarchive"), annotatedMapperPackages);
    }
}
