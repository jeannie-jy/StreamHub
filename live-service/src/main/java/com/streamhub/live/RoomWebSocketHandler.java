package com.streamhub.live;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {
    private static final long MESSAGE_INTERVAL_MILLIS = 300;

    private final ObjectMapper objectMapper;
    private final LiveRoomRepository liveRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OnlinePresenceService onlinePresenceService;
    private final RoomSessionRegistry roomSessionRegistry;
    private final RoomBroadcastService roomBroadcastService;
    private final UserServiceClient userServiceClient;
    private final SensitiveWordService sensitiveWordService;
    private final RoomMuteRepository roomMuteRepository;
    private final Map<Long, Long> lastMessageAtByUser = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(
            ObjectMapper objectMapper,
            LiveRoomRepository liveRoomRepository,
            ChatMessageRepository chatMessageRepository,
            OnlinePresenceService onlinePresenceService,
            RoomSessionRegistry roomSessionRegistry,
            RoomBroadcastService roomBroadcastService,
            UserServiceClient userServiceClient,
            SensitiveWordService sensitiveWordService,
            RoomMuteRepository roomMuteRepository) {
        this.objectMapper = objectMapper;
        this.liveRoomRepository = liveRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.onlinePresenceService = onlinePresenceService;
        this.roomSessionRegistry = roomSessionRegistry;
        this.roomBroadcastService = roomBroadcastService;
        this.userServiceClient = userServiceClient;
        this.sensitiveWordService = sensitiveWordService;
        this.roomMuteRepository = roomMuteRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Map<String, String> query = queryParameters(session);
        Long roomId = parsePositiveLong(query.get("roomId"));
        Long userId = parsePositiveLong(query.get("userId"));
        UserProfileSnapshot profile = userId == null ? null : activeUserProfile(userId);
        if (roomId == null || userId == null || liveRoomRepository.findById(roomId).isEmpty() || profile == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("userId", userId);
        if (!roomSessionRegistry.add(roomId, session)) {
            return;
        }
        onlinePresenceService.join(roomId, userId);
        send(session, Map.of(
                "type", "CONNECTED",
                "roomId", roomId,
                "userId", userId,
                "serverTime", Instant.now()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Long roomId = attributeAsLong(session, "roomId");
        Long userId = attributeAsLong(session, "userId");
        if (roomId == null || userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        ChatCommand command = objectMapper.readValue(message.getPayload(), ChatCommand.class);
        String type = StringUtils.hasText(command.type()) ? command.type().trim().toUpperCase() : "CHAT";
        if ("HEARTBEAT".equals(type)) {
            onlinePresenceService.heartbeat(roomId, userId);
            send(session, Map.of("type", "HEARTBEAT_ACK", "serverTime", Instant.now()));
            return;
        }
        if (!"CHAT".equals(type)) {
            sendError(session, "只支持 CHAT 和 HEARTBEAT 消息");
            return;
        }
        if (roomMuteRepository.isMuted(roomId, userId, Instant.now())) {
            send(session, Map.of("type", "ERROR", "code", "ROOM_MUTED", "message", "你已被本房间禁言"));
            return;
        }
        if (!StringUtils.hasText(command.clientMessageId()) || command.clientMessageId().length() > 64) {
            sendError(session, "clientMessageId 必填且长度不能超过 64");
            return;
        }
        if (!StringUtils.hasText(command.content()) || command.content().length() > 512) {
            sendError(session, "弹幕内容不能为空且长度不能超过 512");
            return;
        }

        var matchedWord = sensitiveWordService.matchedWord(command.content().trim());
        if (matchedWord.isPresent()) {
            sensitiveWordService.logBlocked(roomId, userId, command.content().trim(), matchedWord.get());
            send(session, Map.of(
                    "type", "ERROR",
                    "code", "CONTENT_BLOCKED",
                    "message", "弹幕包含敏感内容，请修改后重试"));
            return;
        }
        UserProfileSnapshot profile = activeUserProfile(userId);
        if (profile == null) {
            sendError(session, "账号已被封禁");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = lastMessageAtByUser.putIfAbsent(userId, now);
        if (previous != null && now - previous < MESSAGE_INTERVAL_MILLIS) {
            lastMessageAtByUser.put(userId, now);
            sendError(session, "发送过于频繁");
            return;
        }
        lastMessageAtByUser.put(userId, now);

        ChatMessage saved = chatMessageRepository.save(
                roomId,
                userId,
                command.clientMessageId(),
                command.content().trim());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "CHAT");
        payload.put("id", saved.id());
        payload.put("roomId", saved.roomId());
        payload.put("userId", saved.userId());
        payload.put("nickname", StringUtils.hasText(profile.nickname()) ? profile.nickname() : profile.username());
        payload.put("avatarUrl", profile.avatarUrl());
        payload.put("clientMessageId", saved.clientMessageId());
        payload.put("content", saved.content());
        payload.put("createdAt", saved.createdAt());
        roomBroadcastService.publish(roomId, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = attributeAsLong(session, "roomId");
        Long userId = attributeAsLong(session, "userId");
        if (roomId != null) {
            roomSessionRegistry.remove(roomId, session);
        }
        if (roomId != null && userId != null) {
            onlinePresenceService.leave(roomId, userId);
        }
    }

    public void broadcastEvent(long roomId, Map<String, Object> payload) {
        roomBroadcastService.publish(roomId, payload);
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        send(session, Map.of("type", "ERROR", "message", message));
    }

    private Map<String, String> queryParameters(WebSocketSession session) {
        if (session.getUri() == null) {
            return Map.of();
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().toSingleValueMap();
    }

    private Long parsePositiveLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long attributeAsLong(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value instanceof Long ? (Long) value : null;
    }

    private UserProfileSnapshot activeUserProfile(long userId) {
        try {
            var response = userServiceClient.getProfile(userId);
            if (response != null && response.success() && response.data() != null
                    && "ACTIVE".equals(response.data().status())) {
                return response.data();
            }
        } catch (RuntimeException exception) {
            return null;
        }
        return null;
    }

    public record ChatCommand(String type, String clientMessageId, String content) {
    }
}
