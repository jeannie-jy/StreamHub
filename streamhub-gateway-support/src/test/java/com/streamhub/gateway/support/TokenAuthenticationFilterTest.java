package com.streamhub.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

class TokenAuthenticationFilterTest {
    private final TokenAuthenticationFilter filter = new TokenAuthenticationFilter(WebClient.builder());

    @Test
    void roomCollectionIsPublicOnlyForReads() {
        assertThat(filter.isPublic("/api/v1/live/rooms", HttpMethod.GET)).isTrue();
        assertThat(filter.isPublic("/api/v1/live/rooms", HttpMethod.POST)).isFalse();
    }

    @Test
    void onlineCountIsPublicOnlyForNumericRoomReads() {
        assertThat(filter.isPublic("/api/v1/live/rooms/88/online-count", HttpMethod.GET)).isTrue();
        assertThat(filter.isPublic("/api/v1/live/rooms/88/online-count", HttpMethod.POST)).isFalse();
        assertThat(filter.isPublic("/api/v1/live/rooms/mine/online-count", HttpMethod.GET)).isFalse();
    }

    @Test
    void publicGiftCatalogDoesNotMakeWritesPublic() {
        assertThat(filter.isPublic("/api/v1/gifts", HttpMethod.GET)).isTrue();
        assertThat(filter.isPublic("/api/v1/gifts", HttpMethod.POST)).isFalse();
    }
}
