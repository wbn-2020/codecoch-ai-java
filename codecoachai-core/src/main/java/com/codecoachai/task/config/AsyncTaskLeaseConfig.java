package com.codecoachai.task.config;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AsyncTaskLeaseProperties.class)
public class AsyncTaskLeaseConfig {

    @Bean(name = "asyncTaskLeaseScheduler")
    public ThreadPoolTaskScheduler asyncTaskLeaseScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("async-task-lease-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public AsyncTaskLeaseRuntime asyncTaskLeaseRuntime() {
        return new AsyncTaskLeaseRuntime() {
            @Override
            public LocalDateTime now() {
                return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            }

            @Override
            public String newLeaseToken() {
                return UUID.randomUUID().toString().replace("-", "");
            }
        };
    }
}
