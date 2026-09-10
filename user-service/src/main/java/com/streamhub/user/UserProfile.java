package com.streamhub.user;

public record UserProfile(
        long id,
        String username,
        String nickname,
        String avatarUrl,
        String role,
        String status,
        long followerCount) {
}
