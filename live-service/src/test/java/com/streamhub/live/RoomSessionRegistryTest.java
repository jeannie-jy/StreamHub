package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;

import org.springframework.http.HttpHeaders;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class RoomSessionRegistryTest {
    @Test
    void removesExpiredSessionsWithTheirRoomAndUserIdentity() {
        RoomSessionRegistry registry = new RoomSessionRegistry(10);
        WebSocketSession session = session("session-a");

        assertThat(registry.add(3, 7, session)).isTrue();

        var expired = registry.removeExpired(Long.MAX_VALUE);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).roomId()).isEqualTo(3);
        assertThat(expired.get(0).userId()).isEqualTo(7);
        assertThat(expired.get(0).session()).isSameAs(session);
        assertThat(registry.removeExpired(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void rejectsConnectionsBeyondTheRoomLimit() {
        RoomSessionRegistry registry = new RoomSessionRegistry(1);

        assertThat(registry.add(3, 7, session("session-a"))).isTrue();
        assertThat(registry.add(3, 8, session("session-b"))).isFalse();
    }

    private WebSocketSession session(String id) {
        return (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getAttributes" -> new HashMap<String, Object>();
                    case "getHandshakeHeaders" -> HttpHeaders.EMPTY;
                    case "getExtensions" -> List.of();
                    case "isOpen" -> true;
                    case "getTextMessageSizeLimit", "getBinaryMessageSizeLimit" -> 0;
                    default -> null;
                });
    }
}
