package com.streamhub.live;

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

@Service
public class RoomBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(RoomBroadcastService.class);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RoomSessionRegistry roomSessionRegistry;
    private final String channel;
    private final String sourceNodeId;

    public RoomBroadcastService(
            ObjectMapper objectMapper,
            StringRedisTemplate stringRedisTemplate,
            RoomSessionRegistry roomSessionRegistry,
            @Value("${streamhub.realtime.channel:live:room:events}") String channel,
            @Value("${streamhub.realtime.node-id:local}") String sourceNodeId) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomSessionRegistry = roomSessionRegistry;
        this.channel = channel;
        this.sourceNodeId = sourceNodeId;
    }

    public void publish(long roomId, Map<String, Object> payload) {
        String body = serialize(new RoomEvent(
                UUID.randomUUID().toString(),
                sourceNodeId,
                roomId,
                payload,
                Instant.now()));
        try {
            stringRedisTemplate.convertAndSend(channel, body);
        } catch (RuntimeException exception) {
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

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("房间事件序列化失败", exception);
        }
    }
}
