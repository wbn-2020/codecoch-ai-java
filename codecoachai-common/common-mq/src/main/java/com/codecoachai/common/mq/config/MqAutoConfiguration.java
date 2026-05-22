package com.codecoachai.common.mq.config;

import com.codecoachai.common.mq.producer.MqProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * common-mq 自动装配：仅当存在 RocketMQTemplate 且开关打开时启用。
 */
@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@AutoConfigureAfter(name = "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
public class MqAutoConfiguration {

    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean
    public MqProducer mqProducer(RocketMQTemplate rocketMQTemplate) {
        return new MqProducer(rocketMQTemplate);
    }
}
