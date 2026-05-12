package com.codecoachai.common.log.util;

import java.util.UUID;

public final class TraceIdUtils {

    private TraceIdUtils() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
