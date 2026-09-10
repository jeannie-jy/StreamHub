package com.streamhub.live;

import java.time.Instant;

public record ActivityOrderView(
        String orderNo,
        long activityId,
        long userId,
        String status,
        Instant createdAt,
        Instant processedAt,
        Instant closedAt) {
    public static ActivityOrderView from(ActivityOrder order) {
        return new ActivityOrderView(
                order.orderNo(),
                order.activityId(),
                order.userId(),
                order.status(),
                order.createdAt(),
                order.processedAt(),
                order.closedAt());
    }
}
