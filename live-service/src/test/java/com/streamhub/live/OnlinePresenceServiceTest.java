package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class OnlinePresenceServiceTest {
    @Test
    void closingOneOfSeveralConnectionsKeepsTheUserOnline() {
        OnlinePresenceService service = new OnlinePresenceService(
                new StringRedisTemplate(),
                90_000,
                new NodeIdentity("node-a", "boot-a"));
        service.join(1, 7, "connection-a");
        service.join(1, 7, "connection-b");

        assertThat(service.onlineCount(1)).isEqualTo(1);
        service.leave(1, 7, "connection-a");
        assertThat(service.onlineCount(1)).isEqualTo(1);
        service.leave(1, 7, "connection-b");
        assertThat(service.onlineCount(1)).isZero();
    }
}
