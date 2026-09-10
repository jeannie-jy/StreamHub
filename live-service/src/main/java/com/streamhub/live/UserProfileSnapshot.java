package com.streamhub.live;

public record UserProfileSnapshot(
        long id,
        String username,
        String nickname,
        String avatarUrl,
        String role,
        String status,
        long followerCount) {
}
