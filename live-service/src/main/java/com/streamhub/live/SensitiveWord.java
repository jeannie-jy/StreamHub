package com.streamhub.live;

import java.time.Instant;

public record SensitiveWord(long id, String word, String status, Instant createdAt) {
}
