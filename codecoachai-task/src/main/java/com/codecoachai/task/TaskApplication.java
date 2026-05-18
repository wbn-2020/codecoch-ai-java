package com.codecoachai.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 异步任务中心服务启动类。
 *
 * 端口约定：8090
 * 职责：
 *  - 消费 codecoachai-resume / codecoachai-interview / codecoachai-question / codecoachai-search 等 Topic
 *  - 维护 async_task / message_dead_letter 表
 *  - 提供 /admin/tasks 后台管理接口
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.codecoachai")
@MapperScan("com.codecoachai.task.mapper")
public class TaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApplication.class, args);
    }
}
