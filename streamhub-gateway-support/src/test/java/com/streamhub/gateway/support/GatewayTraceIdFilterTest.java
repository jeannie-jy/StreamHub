package com.streamhub.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayTraceIdFilterTest {
    @Test
    void preservesSafeTraceIdAcrossGatewayExchange() {
        GatewayTraceIdFilter filter = new GatewayTraceIdFilter();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/live/rooms")
                        .header("X-Trace-Id", "trace-abc-123")
                        .build());
        AtomicReference<String> downstreamTraceId = new AtomicReference<>();

        filter.filter(exchange, next -> {
            downstreamTraceId.set(next.getRequest().getHeaders().getFirst("X-Trace-Id"));
            return next.getResponse().setComplete();
        }).block();

        assertThat(downstreamTraceId.get()).isEqualTo("trace-abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("trace-abc-123");
    }

    @Test
    void replacesUnsafeTraceId() {
        GatewayTraceIdFilter filter = new GatewayTraceIdFilter();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/live/rooms")
                        .header("X-Trace-Id", "trace value with spaces")
                        .build());

        filter.filter(exchange, next -> next.getResponse().setComplete()).block();

        String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).matches("[A-Za-z0-9._-]{1,64}");
    }
}
