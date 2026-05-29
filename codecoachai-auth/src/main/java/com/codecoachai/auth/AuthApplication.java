package com.codecoachai.auth;

import com.codecoachai.auth.config.PasswordResetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.codecoachai.auth.feign")
@EnableConfigurationProperties(PasswordResetProperties.class)
@SpringBootApplication(scanBasePackages = "com.codecoachai")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
