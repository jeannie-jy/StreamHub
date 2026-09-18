package com.streamhub.live;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class NodeIdentity {
    private final String value;

    public NodeIdentity(@Value("${streamhub.realtime.node-id:}") String configuredNodeId) {
        this(configuredNodeId, UUID.randomUUID().toString());
    }

    NodeIdentity(String configuredNodeId, String bootId) {
        String base = StringUtils.hasText(configuredNodeId) ? configuredNodeId.trim() : "live";
        String instanceBootId = StringUtils.hasText(bootId) ? bootId.trim() : UUID.randomUUID().toString();
        this.value = base + ":" + instanceBootId;
    }

    public String value() {
        return value;
    }
}
