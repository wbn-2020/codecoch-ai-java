package com.codecoachai.ai.security;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.domain.enums.AiFailureType;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.web.client.RestClientResponseException;

public final class AiErrorSanitizer {

    private AiErrorSanitizer() {
    }

    public static String safeFailureSummary(Throwable error) {
        if (error == null) {
            return null;
        }
        AiFailureType failureType = resolveFailureType(error);
        Integer httpStatus = resolveHttpStatus(error);
        String statusPart = httpStatus == null ? "" : "; httpStatus=" + httpStatus;
        return "errorType=" + failureType.name()
                + statusPart
                + "; errorRef=" + errorRef(error)
                + "; summary=" + summary(failureType);
    }

    public static AiFailureType resolveFailureType(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current instanceof AiProviderException providerException
                    && providerException.getFailureType() != null
                    && providerException.getFailureType() != AiFailureType.NONE) {
                return providerException.getFailureType();
            }
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return AiFailureType.TIMEOUT;
            }
            if (current instanceof RestClientResponseException) {
                return AiFailureType.HTTP_ERROR;
            }
            if (current instanceof ConnectException) {
                return AiFailureType.HTTP_ERROR;
            }
            current = current.getCause();
            depth++;
        }
        return AiFailureType.UNKNOWN_ERROR;
    }

    private static Integer resolveHttpStatus(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current instanceof AiProviderException providerException
                    && providerException.getHttpStatus() != null) {
                return providerException.getHttpStatus();
            }
            if (current instanceof RestClientResponseException responseException) {
                return responseException.getRawStatusCode();
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }

    private static String errorRef(Throwable error) {
        String seed = error.getClass().getName() + ":" + String.valueOf(error.getMessage());
        return Integer.toHexString(seed.hashCode());
    }

    private static String summary(AiFailureType failureType) {
        return switch (failureType) {
            case CONFIG_ERROR -> "AI provider configuration is unavailable";
            case TIMEOUT -> "AI provider request timed out";
            case HTTP_ERROR -> "AI provider returned an error response";
            case EMPTY_RESPONSE -> "AI provider returned an empty response";
            case PARSE_ERROR -> "AI response format could not be parsed";
            case NONE, UNKNOWN_ERROR -> "AI provider call failed";
        };
    }
}
