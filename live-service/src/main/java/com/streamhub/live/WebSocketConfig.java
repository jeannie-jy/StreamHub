package com.streamhub.live;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final RoomWebSocketHandler roomWebSocketHandler;
    private final String allowedOrigins;

    public WebSocketConfig(
            RoomWebSocketHandler roomWebSocketHandler,
            @Value("${streamhub.cors.allowed-origins:http://localhost:5173,http://localhost:8089}") String allowedOrigins) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/chat")
                .setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new));
    }
}
