package com.codecoachai.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients(basePackages = "com.codecoachai.ai.agent.feign")
@MapperScan({
    "com.codecoachai.ai.mapper",
    "com.codecoachai.ai.agent.mapper",
    "com.codecoachai.ai.agent.campaignreview.mapper"
})
@SpringBootApplication(scanBasePackages = "com.codecoachai")
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
