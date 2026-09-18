package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;

class RedisKeysTest {
    @Test
    void roomPresenceKeysShareAClusterSlot() {
        assertThat(SlotHash.getSlot(RedisKeys.roomOnline(123)))
                .isEqualTo(SlotHash.getSlot(RedisKeys.roomUserConnections(123, 7)));
    }

    @Test
    void activityReservationKeysShareAClusterSlot() {
        assertThat(SlotHash.getSlot(RedisKeys.activityStock(456)))
                .isEqualTo(SlotHash.getSlot(RedisKeys.activityUsers(456)));
    }
}
