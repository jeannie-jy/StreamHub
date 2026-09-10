package com.streamhub.live;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RedisRoomEventListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisRoomEventListener.class);

    private final ObjectMapper objectMapper;
    private final RoomBroadcastService roomBroadcastService;

    public RedisRoomEventListener(ObjectMapper objectMapper, RoomBroadcastService roomBroadcastService) {
        this.objectMapper = objectMapper;
        this.roomBroadcastService = roomBroadcastService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RoomEvent event = objectMapper.readValue(message.getBody(), RoomEvent.class);
            roomBroadcastService.broadcastLocal(event);
        } catch (Exception exception) {
            log.warn("忽略无法解析的房间广播事件 body={}",
                    new String(message.getBody(), StandardCharsets.UTF_8), exception);
        }
    }
}
