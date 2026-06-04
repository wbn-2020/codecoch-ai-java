package com.codecoachai.search;

import com.codecoachai.common.security.config.CommonSecurityAutoConfiguration;
import com.codecoachai.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 搜索服务启动类。
 *
 * 端口约定：8091
 * 索引：cc_question / cc_resume / cc_interview
 * 同步：监听 codecoachai-search Topic（question / resume / interview tag）
 */
@SpringBootApplication(scanBasePackages = "com.codecoachai")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.codecoachai")
@Import({CommonSecurityAutoConfiguration.class, GlobalExceptionHandler.class})
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
