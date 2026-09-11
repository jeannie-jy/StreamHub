package com.streamhub.live;

import java.time.Instant;

public record LiveRoomView(
        long id,
        long anchorId,
        String title,
        String coverUrl,
        String category,
        String status,
        long onlineCount,
        String pushUrl,
        String playbackUrl,
        String webrtcPlaybackUrl,
        String anchorNickname,
        String anchorAvatarUrl,
        Instant createdAt,
        Instant updatedAt) {
}
