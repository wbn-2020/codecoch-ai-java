package com.codecoachai.core;

import com.codecoachai.auth.AuthApplication;
import com.codecoachai.interview.feign.AgentBusinessActionFeignClient;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.file.FileApplication;
import com.codecoachai.interview.InterviewApplication;
import com.codecoachai.question.feign.AiEmbeddingFeignClient;
import com.codecoachai.question.feign.AiPracticeFeignClient;
import com.codecoachai.question.feign.AiQuestionFeignClient;
import com.codecoachai.question.feign.AiQuestionRecommendationFeignClient;
import com.codecoachai.question.QuestionApplication;
import com.codecoachai.resume.feign.CampaignArchiveAiFeignClient;
import com.codecoachai.resume.ResumeApplication;
import com.codecoachai.system.SystemApplication;
import com.codecoachai.task.TaskApplication;
import com.codecoachai.user.UserApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableDiscoveryClient
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "com.codecoachai")
@EnableFeignClients(clients = {
        com.codecoachai.resume.feign.AgentBusinessActionFeignClient.class,
        com.codecoachai.resume.feign.AiFeignClient.class,
        CampaignArchiveAiFeignClient.class,
        com.codecoachai.question.feign.AgentBusinessActionFeignClient.class,
        AiEmbeddingFeignClient.class,
        AiPracticeFeignClient.class,
        AiQuestionFeignClient.class,
        AiQuestionRecommendationFeignClient.class,
        AgentBusinessActionFeignClient.class,
        AiFeignClient.class,
        com.codecoachai.task.feign.AiFeignClient.class
})
@MapperScan(basePackages = {
        "com.codecoachai.user.mapper",
        "com.codecoachai.system.mapper",
        "com.codecoachai.file.mapper",
        "com.codecoachai.resume.mapper",
        "com.codecoachai.resume.careerinterview.mapper",
        "com.codecoachai.resume.careeroffer.mapper",
        "com.codecoachai.resume.careercontact.mapper",
        "com.codecoachai.resume.careerresearch.mapper",
        "com.codecoachai.resume.careercampaign",
        "com.codecoachai.resume.campaignarchive",
        "com.codecoachai.question.mapper",
        "com.codecoachai.interview.mapper",
        "com.codecoachai.task.mapper"
})
@ComponentScan(
        basePackages = "com.codecoachai",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
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
                        com.codecoachai.interview.controller.HealthController.class
                }))
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
