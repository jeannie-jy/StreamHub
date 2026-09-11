package com.streamhub.auth;

import java.time.Instant;

public record AuthSession(long userId, Instant expiresAt, String role) {
}
