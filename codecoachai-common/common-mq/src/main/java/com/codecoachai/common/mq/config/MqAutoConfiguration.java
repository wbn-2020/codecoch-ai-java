package com.codecoachai.common.mq.config;

import com.codecoachai.common.mq.producer.MqProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * common-mq 自动装配：仅当存在 RocketMQTemplate 且开关打开时启用。
 */
@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@ComponentScan(basePackageClasses = MqProducer.class)
public class MqAutoConfiguration {
}
