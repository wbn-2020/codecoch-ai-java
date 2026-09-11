package com.codecoachai.common.mq.config;

import com.codecoachai.common.mq.producer.MqProducer;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * common-mq 自动装配：仅当 RocketMQ 开关打开时启用。
 *
 * 2026-09-11 修复：MqProducer 原先自带 @Component + @ConditionalOnBean(RocketMQTemplate)，
 * 但它走组件扫描时，RocketMQTemplate（自动配置创建）尚未注册，条件恒不满足 →
 * bean 被跳过，所有 MQ 同步投递静默降级（线上 24h 出现 1889 次 producer unavailable）。
 * 现在 MqProducer 只由此处注册（自动配置阶段时序正确），并保留 @ConditionalOnMissingBean
 * 防重复注册。
 */
@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
public class MqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MqProducer mqProducer(RocketMQTemplate rocketMQTemplate) {
        return new MqProducer(rocketMQTemplate);
    }
}
