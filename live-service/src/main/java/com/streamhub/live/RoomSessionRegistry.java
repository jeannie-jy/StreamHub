package com.streamhub.live;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RoomSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(RoomSessionRegistry.class);

    private final Map<Long, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public void add(long roomId, WebSocketSession session) {
        sessionsByRoom.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
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
