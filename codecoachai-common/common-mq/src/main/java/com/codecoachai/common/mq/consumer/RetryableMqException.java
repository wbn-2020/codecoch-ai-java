package com.codecoachai.common.mq.consumer;

/**
 * 可重试异常：消费失败时抛出，RocketMQ 会自动重试。
 */
public class RetryableMqException extends RuntimeException {

    public RetryableMqException(String message) {
        super(message);
    }

    public RetryableMqException(String message, Throwable cause) {
        super(message, cause);
    }
}
