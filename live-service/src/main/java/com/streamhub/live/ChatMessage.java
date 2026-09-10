package com.streamhub.live;

import java.time.Instant;

public record ChatMessage(
        long id,
        long roomId,
        long userId,
        String clientMessageId,
        String content,
        Instant createdAt) {
}
