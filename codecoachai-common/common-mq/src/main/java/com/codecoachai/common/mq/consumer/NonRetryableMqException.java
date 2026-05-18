package com.codecoachai.common.mq.consumer;

/**
 * 不可重试异常：消费失败时抛出，直接进入死信，不重试。
 */
public class NonRetryableMqException extends RuntimeException {

    public NonRetryableMqException(String message) {
        super(message);
    }

    public NonRetryableMqException(String message, Throwable cause) {
        super(message, cause);
    }
}
