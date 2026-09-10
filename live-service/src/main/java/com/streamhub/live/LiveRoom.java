package com.streamhub.live;

import java.time.Instant;

public record LiveRoom(
        long id,
        long anchorId,
        String title,
        String coverUrl,
        String category,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
