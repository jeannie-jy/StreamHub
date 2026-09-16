package com.streamhub.gateway.support;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayTraceIdFilter implements WebFilter {
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        String normalizedTraceId = traceId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TRACE_ID_HEADER, normalizedTraceId))
                .build();
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, normalizedTraceId);
        return chain.filter(exchange.mutate().request(request).build());
    }
}
