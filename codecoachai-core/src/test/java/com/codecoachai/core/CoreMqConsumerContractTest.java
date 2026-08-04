package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.InterviewFeignClient;
import com.codecoachai.task.feign.QuestionFeignClient;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.consumer.AgentDailyPlanConsumer;
import com.codecoachai.task.consumer.InterviewReportConsumer;
import com.codecoachai.task.consumer.JobTargetParseConsumer;
import com.codecoachai.task.consumer.QuestionGenerateConsumer;
import com.codecoachai.task.consumer.QuestionRecommendationGenerateConsumer;
import com.codecoachai.task.consumer.ResumeJobMatchConsumer;
import com.codecoachai.task.consumer.ResumeOptimizeConsumer;
import com.codecoachai.task.consumer.ResumeParseConsumer;
import com.codecoachai.task.consumer.StudyPlanGenerateConsumer;
import com.codecoachai.task.service.AsyncTaskService;
import com.codecoachai.task.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class CoreMqConsumerContractTest {

    private static final Set<Class<?>> LISTENER_TYPES = Set.of(
            AgentDailyPlanConsumer.class,
            InterviewReportConsumer.class,
            JobTargetParseConsumer.class,
            QuestionGenerateConsumer.class,
            QuestionRecommendationGenerateConsumer.class,
            ResumeJobMatchConsumer.class,
            ResumeOptimizeConsumer.class,
            ResumeParseConsumer.class,
            StudyPlanGenerateConsumer.class);

    @Test
    void taskListenersRetainExistingConsumerGroupsAndOffsets() {
        Set<String> groups = new LinkedHashSet<>();
        for (Class<?> listenerType : LISTENER_TYPES) {
            RocketMQMessageListener listener =
                    listenerType.getAnnotation(RocketMQMessageListener.class);
            groups.add(listener.consumerGroup());
        }

        assertEquals(Set.of(
                "codecoachai-task-agent-daily-plan",
                "codecoachai-task-interview-report",
                "codecoachai-task-job-target-parse",
                "codecoachai-task-question-generate",
                "codecoachai-task-question-recommendation-generate",
                "codecoachai-task-resume-job-match",
                "codecoachai-task-resume-optimize",
                "codecoachai-task-resume-parse",
                "codecoachai-task-study-plan-generate"), groups);
    }

    @Test
    void taskConsumersAreNotBeansWhenTheCutoverSwitchIsDisabled() {
        consumerContext()
                .withPropertyValues("codecoachai.task.consumers.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    for (Class<?> listenerType : LISTENER_TYPES) {
                        assertEquals(0, context.getBeansOfType(listenerType).size(), listenerType.getName());
                    }
                });
    }

    @Test
    void taskConsumersAreBeansWhenTheCutoverSwitchIsEnabled() {
        consumerContext()
                .withPropertyValues("codecoachai.task.consumers.enabled=true")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    for (Class<?> listenerType : LISTENER_TYPES) {
                        assertEquals(1, context.getBeansOfType(listenerType).size(), listenerType.getName());
                    }
                });
    }

    private ApplicationContextRunner consumerContext() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ConsumerConfiguration.class)
                .withBean(AsyncTaskService.class, () -> mock(AsyncTaskService.class))
                .withBean(AiFeignClient.class, () -> mock(AiFeignClient.class))
                .withBean(ResumeFeignClient.class, () -> mock(ResumeFeignClient.class))
                .withBean(QuestionFeignClient.class, () -> mock(QuestionFeignClient.class))
                .withBean(InterviewFeignClient.class, () -> mock(InterviewFeignClient.class))
                .withBean(NotificationService.class, () -> mock(NotificationService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            AgentDailyPlanConsumer.class,
            InterviewReportConsumer.class,
            JobTargetParseConsumer.class,
            QuestionGenerateConsumer.class,
            QuestionRecommendationGenerateConsumer.class,
            ResumeJobMatchConsumer.class,
            ResumeOptimizeConsumer.class,
            ResumeParseConsumer.class,
            StudyPlanGenerateConsumer.class
    })
    static class ConsumerConfiguration {
    }
}
