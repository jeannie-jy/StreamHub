package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

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

    @Test
    void springUsesTheConfiguredConstructorWhenMultipleConstructorsExist() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("node-identity-test", Map.of("streamhub.realtime.node-id", "live-e2e")));
            context.register(NodeIdentity.class);
            context.refresh();

            assertThat(context.getBean(NodeIdentity.class).value()).startsWith("live-e2e:");
        }
    }
}
