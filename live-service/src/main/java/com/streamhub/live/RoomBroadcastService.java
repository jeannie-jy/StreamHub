package com.streamhub.live;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class RoomBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(RoomBroadcastService.class);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RoomSessionRegistry roomSessionRegistry;
    private final String channel;
    private final String sourceNodeId;
    private final int maxChatEventsPerSecond;
    private final Counter droppedChatEvents;
    private final Counter publishedEvents;
    private final Counter localFallbackEvents;

    public RoomBroadcastService(
            ObjectMapper objectMapper,
            StringRedisTemplate stringRedisTemplate,
            RoomSessionRegistry roomSessionRegistry,
            @Value("${streamhub.realtime.channel:live:room:events}") String channel,
            NodeIdentity nodeIdentity,
            @Value("${streamhub.realtime.max-chat-events-per-second:1000}") int maxChatEventsPerSecond,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomSessionRegistry = roomSessionRegistry;
        this.channel = channel;
        this.sourceNodeId = nodeIdentity.value();
        this.maxChatEventsPerSecond = Math.max(1, maxChatEventsPerSecond);
        this.droppedChatEvents = Counter.builder("streamhub.realtime.broadcast.dropped")
                .tag("type", "chat")
                .description("被房间广播速率保护丢弃的普通弹幕数量")
                .register(meterRegistry);
        this.publishedEvents = Counter.builder("streamhub.realtime.broadcast.published")
                .description("发布到 Redis Pub/Sub 的房间事件数量")
                .register(meterRegistry);
        this.localFallbackEvents = Counter.builder("streamhub.realtime.broadcast.local.fallback")
                .description("Redis 广播不可用时降级到本地的房间事件数量")
                .register(meterRegistry);
    }

    public void publish(long roomId, Map<String, Object> payload) {
        if (isChat(payload) && !allowChatEvent(roomId)) {
            droppedChatEvents.increment();
            return;
        }
        String body = serialize(new RoomEvent(
                UUID.randomUUID().toString(),
                sourceNodeId,
                roomId,
                payload,
                Instant.now()));
        try {
            Long subscriberCount = stringRedisTemplate.convertAndSend(channel, body);
            publishedEvents.increment();
            if (subscriberCount == null || subscriberCount == 0) {
                localFallbackEvents.increment();
                roomSessionRegistry.broadcast(roomId, payloadBody(payload));
            }
        } catch (RuntimeException exception) {
            localFallbackEvents.increment();
            log.warn("Redis 房间广播不可用，降级为本地广播 roomId={} channel={}", roomId, channel, exception);
            roomSessionRegistry.broadcast(roomId, payloadBody(payload));
        }
    }

    public void broadcastLocal(RoomEvent event) {
        roomSessionRegistry.broadcast(event.roomId(), payloadBody(event.payload()));
    }

    private String payloadBody(Map<String, Object> payload) {
        return serialize(payload);
    }

    private boolean isChat(Map<String, Object> payload) {
        return "CHAT".equals(payload.get("type"));
    }

    private boolean allowChatEvent(long roomId) {
        try {
            String key = "live:room:" + roomId + ":broadcast:rate";
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(1));
            }
            return count == null || count <= maxChatEventsPerSecond;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("房间事件序列化失败", exception);
        }
    }
}
