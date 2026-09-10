package com.streamhub.live;

import java.time.Instant;
import java.util.Map;

public record RoomEvent(
        String eventId,
        String sourceNodeId,
        long roomId,
        Map<String, Object> payload,
        Instant publishedAt) {
}
