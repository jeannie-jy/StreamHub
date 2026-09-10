package com.streamhub.live;

import java.time.Instant;

public record Activity(
        long id,
        long roomId,
        String name,
        int stock,
        long unitPrice,
        String status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt) {
}
