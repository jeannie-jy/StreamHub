package com.streamhub.gateway.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class TokenAuthenticationFilter implements GlobalFilter, Ordered {
    private static final ParameterizedTypeReference<RemoteApiResponse<AuthSession>> SESSION_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient.Builder webClientBuilder;

    public TokenAuthenticationFilter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (isPublic(path, exchange.getRequest().getMethod())
                || (path.startsWith("/ws/") && !StringUtils.hasText(authorization))) {
            return chain.filter(exchange);
        }
        if (!isBearerToken(authorization)) {
            return unauthorized(exchange);
        }

        return webClientBuilder.build()
                .post()
                .uri("http://streamhub-auth/api/v1/auth/introspect")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(SESSION_TYPE)
                .flatMap(response -> {
                    if (response == null || !response.success() || response.data() == null) {
                        return unauthorized(exchange);
                    }
                    return chain.filter(withUserId(exchange, response.data().userId(), path));
                })
                .onErrorResume(ignored -> unauthorized(exchange));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (path.startsWith("/actuator/")
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/ping".equals(path)
                || "/api/v1/gifts".equals(path)
                || "/api/v1/live/ping".equals(path)) {
            return true;
        }
        return HttpMethod.GET.equals(method)
                && ("/api/v1/users/ping".equals(path)
                        || numericIdPath(path, "/api/v1/users/")
                        || numericIdPath(path, "/api/v1/live/rooms/")
                        || path.matches("/api/v1/live/rooms/\\d+/messages")
                        || path.matches("/api/v1/live/rooms/\\d+/gift-rank")
                        || path.matches("/api/v1/live/rooms/\\d+/gift-income-rank")
                        || numericIdPath(path, "/api/v1/activities/"));
    }

    private boolean numericIdPath(String path, String prefix) {
        return path.startsWith(prefix)
                && path.substring(prefix.length()).matches("\\d+");
    }

    private boolean isBearerToken(String authorization) {
        return StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && StringUtils.hasText(authorization.substring(7));
    }

    private ServerWebExchange withUserId(ServerWebExchange exchange, long userId, String path) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.set("X-User-Id", String.valueOf(userId));
                })
                .uri(withWebSocketUserId(exchange.getRequest().getURI(), userId, path))
                .build();
        return exchange.mutate().request(request).build();
    }

    private URI withWebSocketUserId(URI original, long userId, String path) {
        if (!path.startsWith("/ws/")) {
            return original;
        }
        return UriComponentsBuilder.fromUri(original)
                .replaceQueryParam("userId", userId)
                .build(true)
                .toUri();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"success\":false,\"data\":null,\"code\":\"COMMON-401\",\"message\":\"未登录或登录已过期\"}"
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    public record AuthSession(long userId, Instant expiresAt) {
    }

    public record RemoteApiResponse<T>(
            boolean success,
            T data,
            String code,
            String message,
            String traceId,
            Instant timestamp) {
    }
}
