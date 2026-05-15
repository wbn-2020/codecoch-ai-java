package com.codecoachai.ai.client;

import com.codecoachai.ai.domain.enums.AiFailureType;

public class AiProviderException extends RuntimeException {

    private final AiFailureType failureType;
    private final Integer httpStatus;

    public AiProviderException(AiFailureType failureType, String message) {
        this(failureType, message, null, null);
    }

    public AiProviderException(AiFailureType failureType, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.httpStatus = httpStatus;
    }

    public AiFailureType getFailureType() {
        return failureType;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
