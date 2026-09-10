package com.streamhub.live;

import java.time.Instant;

public record GiftOrderView(
        String orderNo,
        String status,
        long roomId,
        long senderId,
        long anchorId,
        String giftCode,
        int quantity,
        long totalAmount,
        Instant createdAt,
        Instant processedAt,
        String failureReason) {
    public static GiftOrderView from(GiftOrder order) {
        return new GiftOrderView(
                order.orderNo(),
                order.status(),
                order.roomId(),
                order.senderId(),
                order.anchorId(),
                order.giftCode(),
                order.quantity(),
                order.totalAmount(),
                order.createdAt(),
                order.processedAt(),
                order.failureReason());
    }
}
