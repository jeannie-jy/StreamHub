package com.streamhub.live;

import java.time.Instant;

public record GiftOrder(
        long id,
        String orderNo,
        long roomId,
        long senderId,
        long anchorId,
        long giftId,
        String giftCode,
        int quantity,
        long totalAmount,
        String status,
        String failureReason,
        Instant createdAt,
        Instant processedAt) {
}
