package com.streamhub.live;

import java.time.Instant;

public record ActivityView(
        long id,
        long roomId,
        String name,
        int stock,
        int remainingStock,
        long unitPrice,
        String status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt) {
}
