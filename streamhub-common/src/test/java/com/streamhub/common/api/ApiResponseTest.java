package com.streamhub.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ApiResponseTest {
    @Test
    void shouldIncludeTraceIdFromMdc() {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "trace-123");
        try {
            ApiResponse<String> response = ApiResponse.success("ready");

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isEqualTo("ready");
            assertThat(response.code()).isEqualTo("OK");
            assertThat(response.traceId()).isEqualTo("trace-123");
            assertThat(response.timestamp()).isNotNull();
        } finally {
            MDC.clear();
        }
    }
}
