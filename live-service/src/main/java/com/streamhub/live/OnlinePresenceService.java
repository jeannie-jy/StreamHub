package com.streamhub.live;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OnlinePresenceService {
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(35);

    private final StringRedisTemplate redisTemplate;
    private final Map<Long, Set<Long>> localUsersByRoom = new ConcurrentHashMap<>();

    public OnlinePresenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void join(long roomId, long userId) {
        localUsersByRoom.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(userId);
        heartbeat(roomId, userId);
    }

    public void heartbeat(long roomId, long userId) {
        try {
            redisTemplate.opsForZSet().add(key(roomId), String.valueOf(userId), System.currentTimeMillis());
        } catch (RuntimeException ignored) {
            // Redis is an optimization for presence; local connections still remain usable.
        }
    }

    public void leave(long roomId, long userId) {
        Set<Long> users = localUsersByRoom.get(roomId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                localUsersByRoom.remove(roomId, users);
            }
        }
        try {
            redisTemplate.opsForZSet().remove(key(roomId), String.valueOf(userId));
        } catch (RuntimeException ignored) {
            // See heartbeat().
        }
    }

    public long onlineCount(long roomId) {
        long cutoff = System.currentTimeMillis() - HEARTBEAT_TTL.toMillis();
        try {
            redisTemplate.opsForZSet().removeRangeByScore(key(roomId), 0, cutoff);
            Long count = redisTemplate.opsForZSet().count(key(roomId), cutoff, Double.MAX_VALUE);
            return count == null ? localCount(roomId) : count;
        } catch (RuntimeException ignored) {
            return localCount(roomId);
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void cleanExpiredPresence() {
        long cutoff = System.currentTimeMillis() - HEARTBEAT_TTL.toMillis();
        localUsersByRoom.keySet().forEach(roomId -> {
            try {
                redisTemplate.opsForZSet().removeRangeByScore(key(roomId), 0, cutoff);
            } catch (RuntimeException ignored) {
                // The next scheduled run retries cleanup.
            }
        });
    }

    private long localCount(long roomId) {
        Set<Long> users = localUsersByRoom.get(roomId);
        return users == null ? 0 : users.size();
    }

    private String key(long roomId) {
        return "live:room:" + roomId + ":online";
    }
}
