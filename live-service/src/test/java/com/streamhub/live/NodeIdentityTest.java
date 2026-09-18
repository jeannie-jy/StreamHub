package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NodeIdentityTest {
    @Test
    void bootIdKeepsInstancesUniqueEvenWhenTheyShareTheSameConfiguredName() {
        NodeIdentity first = new NodeIdentity("live-node", "boot-a");
        NodeIdentity second = new NodeIdentity("live-node", "boot-b");

        assertThat(first.value()).isEqualTo("live-node:boot-a");
        assertThat(second.value()).isEqualTo("live-node:boot-b");
        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void blankConfiguredNameUsesTheLiveFallback() {
        assertThat(new NodeIdentity(" ", "boot-a").value()).isEqualTo("live:boot-a");
    }
}
