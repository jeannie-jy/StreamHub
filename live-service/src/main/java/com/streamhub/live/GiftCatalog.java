package com.streamhub.live;

import java.time.Instant;

public record GiftCatalog(
        long id,
        String code,
        String name,
        long price,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
