package com.streamhub.live;

import java.time.Instant;

public record ActivityOrder(
        long id,
        String orderNo,
        long activityId,
        long userId,
        String status,
        Instant createdAt,
        Instant processedAt,
        Instant closedAt) {
}
