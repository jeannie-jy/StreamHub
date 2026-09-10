package com.streamhub.common.api;

import java.time.Instant;

import org.slf4j.MDC;

public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message,
        String traceId,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "OK", "success", currentTraceId(), Instant.now());
    }

    public static ApiResponse<Void> successVoid() {
        return success(null);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, code, message, currentTraceId(), Instant.now());
    }

    private static String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return traceId == null ? "-" : traceId;
    }
}
