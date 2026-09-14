package com.streamhub.live;

import java.time.Instant;

public record ChatMessageView(
        long id,
        long roomId,
        long userId,
        String nickname,
        String avatarUrl,
        String clientMessageId,
        String content,
        Instant createdAt) {

    public static ChatMessageView from(ChatMessage message, UserProfileSnapshot profile) {
        return new ChatMessageView(
                message.id(),
                message.roomId(),
                message.userId(),
                profile == null ? null : profile.nickname(),
                profile == null ? null : profile.avatarUrl(),
                message.clientMessageId(),
                message.content(),
                message.createdAt());
    }
}
