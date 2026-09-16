package com.streamhub.gateway.support;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRateLimitConfig {
    @Bean("clientIpKeyResolver")
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return Mono.just(forwardedFor.split(",", 2)[0].trim());
            }
            if (exchange.getRequest().getRemoteAddress() == null
                    || exchange.getRequest().getRemoteAddress().getAddress() == null) {
                return Mono.just("unknown");
            }
            return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        };
    }
}
