package com.streamhub.live;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final Map<Long, Map<String, SessionState>> sessionsByRoom = new ConcurrentHashMap<>();
    private final int maxSessionsPerRoom;

    public RoomSessionRegistry(
            @Value("${streamhub.realtime.max-sessions-per-room:10000}") int maxSessionsPerRoom) {
        this.maxSessionsPerRoom = Math.max(1, maxSessionsPerRoom);
    }

    public boolean add(long roomId, long userId, WebSocketSession session) {
        AtomicBoolean added = new AtomicBoolean();
        sessionsByRoom.compute(roomId, (ignored, current) -> {
            Map<String, SessionState> sessions = current == null ? new ConcurrentHashMap<>() : current;
            if (sessions.size() < maxSessionsPerRoom) {
                sessions.put(session.getId(), new SessionState(roomId, userId, session));
                added.set(true);
            }
            return sessions;
        });
        if (!added.get()) {
            closeQuietly(roomId, session, CloseStatus.SERVICE_OVERLOAD, "关闭超限 WebSocket 连接失败");
        }
        return added.get();
    }

    public void heartbeat(long roomId, WebSocketSession session) {
        Map<String, SessionState> sessions = sessionsByRoom.get(roomId);
        if (sessions == null) {
            return;
        }
        SessionState state = sessions.get(session.getId());
        if (state != null) {
            state.heartbeat();
        }
    }

    public void remove(long roomId, WebSocketSession session) {
        sessionsByRoom.computeIfPresent(roomId, (ignored, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public List<SessionState> removeExpired(long cutoffMillis) {
        List<SessionState> expired = new ArrayList<>();
        sessionsByRoom.forEach((roomId, sessions) -> {
            sessions.forEach((sessionId, state) -> {
                if (state.removeIfExpired(sessions, sessionId, cutoffMillis)) {
                    expired.add(state);
                }
            });
            sessionsByRoom.computeIfPresent(
                    roomId,
                    (ignored, current) -> current == sessions && current.isEmpty() ? null : current);
        });
        return expired;
    }

    public void broadcast(long roomId, String body) {
        Map<String, SessionState> sessions = sessionsByRoom.getOrDefault(roomId, Map.of());
        for (SessionState state : sessions.values()) {
            WebSocketSession session = state.session();
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
                closeQuietly(roomId, session, CloseStatus.SERVER_ERROR, "关闭发送失败的 WebSocket 连接失败");
            }
        }
    }

    private void closeQuietly(long roomId, WebSocketSession session, CloseStatus status, String message) {
        try {
            session.close(status);
        } catch (IOException exception) {
            log.debug("{} roomId={} sessionId={}", message, roomId, session.getId());
        }
    }

    public static final class SessionState {
        private final long roomId;
        private final long userId;
        private final WebSocketSession session;
        private long lastHeartbeatAt;

        private SessionState(long roomId, long userId, WebSocketSession session) {
            this.roomId = roomId;
            this.userId = userId;
            this.session = session;
            this.lastHeartbeatAt = System.currentTimeMillis();
        }

        public long roomId() {
            return roomId;
        }

        public long userId() {
            return userId;
        }

        public WebSocketSession session() {
            return session;
        }

        private synchronized void heartbeat() {
            lastHeartbeatAt = System.currentTimeMillis();
        }

        private synchronized boolean removeIfExpired(
                Map<String, SessionState> sessions,
                String sessionId,
                long cutoffMillis) {
            return lastHeartbeatAt < cutoffMillis && sessions.remove(sessionId, this);
        }
    }
}
