package com.streamhub.live;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RoomSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(RoomSessionRegistry.class);

    private final Map<Long, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();
    private final int maxSessionsPerRoom;

    public RoomSessionRegistry(
            @Value("${streamhub.realtime.max-sessions-per-room:10000}") int maxSessionsPerRoom) {
        this.maxSessionsPerRoom = Math.max(1, maxSessionsPerRoom);
    }

    public boolean add(long roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByRoom.computeIfAbsent(
                roomId,
                ignored -> ConcurrentHashMap.newKeySet());
        synchronized (sessions) {
            if (sessions.size() >= maxSessionsPerRoom) {
                try {
                    session.close(CloseStatus.SERVICE_OVERLOAD);
                } catch (IOException exception) {
                    log.debug("关闭超限 WebSocket 连接失败 roomId={} sessionId={}", roomId, session.getId());
                }
                return false;
            }
            sessions.add(session);
            return true;
        }
    }

    public void remove(long roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByRoom.remove(roomId, sessions);
        }
    }

    public void broadcast(long roomId, String body) {
        Set<WebSocketSession> sessions = sessionsByRoom.getOrDefault(roomId, Set.of());
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                remove(roomId, session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(body));
                }
            } catch (IOException exception) {
                log.debug("房间广播发送失败，移除断开的连接 roomId={} sessionId={}", roomId, session.getId());
                remove(roomId, session);
            }
        }
    }
}
