package com.streamhub.live;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OnlinePresenceService {
    private static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> LEAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, ARGV[2])
            if redis.call('ZCARD', KEYS[2]) == 0 then
                redis.call('ZREM', KEYS[1], ARGV[3])
                redis.call('DEL', KEYS[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration heartbeatTtl;
    private final String nodeId;
    private final Map<Long, Map<String, Long>> localConnectionsByRoom = new ConcurrentHashMap<>();

    public OnlinePresenceService(
            StringRedisTemplate redisTemplate,
            @Value("${streamhub.realtime.presence-ttl-ms:90000}") long heartbeatTtlMillis,
            @Value("${streamhub.realtime.node-id:${HOSTNAME:local}}") String nodeId) {
        this.redisTemplate = redisTemplate;
        this.heartbeatTtl = Duration.ofMillis(Math.max(1_000, heartbeatTtlMillis));
        this.nodeId = nodeId;
    }

    public void join(long roomId, long userId, String connectionId) {
        localConnectionsByRoom.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>())
                .put(presenceId(connectionId), userId);
        heartbeat(roomId, userId, connectionId);
    }

    public void heartbeat(long roomId, long userId, String connectionId) {
        long now = System.currentTimeMillis();
        try {
            redisTemplate.execute(
                    HEARTBEAT_SCRIPT,
                    List.of(key(roomId), connectionKey(roomId, userId)),
                    presenceId(connectionId),
                    String.valueOf(now),
                    String.valueOf(now - heartbeatTtl.toMillis()),
                    String.valueOf(heartbeatTtl.toMillis() * 2),
                    String.valueOf(userId));
        } catch (RuntimeException ignored) {
            // Redis is an optimization for presence; local connections still remain usable.
        }
    }

    public void leave(long roomId, long userId, String connectionId) {
        String presenceId = presenceId(connectionId);
        Map<String, Long> connections = localConnectionsByRoom.get(roomId);
        if (connections != null) {
            connections.remove(presenceId);
            if (connections.isEmpty()) {
                localConnectionsByRoom.remove(roomId, connections);
            }
        }
        try {
            redisTemplate.execute(
                    LEAVE_SCRIPT,
                    List.of(key(roomId), connectionKey(roomId, userId)),
                    presenceId,
                    String.valueOf(System.currentTimeMillis() - heartbeatTtl.toMillis()),
                    String.valueOf(userId));
        } catch (RuntimeException ignored) {
            // See heartbeat().
        }
    }

    public long onlineCount(long roomId) {
        long cutoff = System.currentTimeMillis() - heartbeatTtl.toMillis();
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
        long cutoff = System.currentTimeMillis() - heartbeatTtl.toMillis();
        localConnectionsByRoom.keySet().forEach(roomId -> {
            try {
                redisTemplate.opsForZSet().removeRangeByScore(key(roomId), 0, cutoff);
            } catch (RuntimeException ignored) {
                // The next scheduled run retries cleanup.
            }
        });
    }

    private long localCount(long roomId) {
        Map<String, Long> connections = localConnectionsByRoom.get(roomId);
        return connections == null ? 0 : connections.values().stream().distinct().count();
    }

    private String presenceId(String connectionId) {
        return nodeId + ":" + connectionId;
    }

    private String key(long roomId) {
        return "live:room:" + roomId + ":online";
    }

    private String connectionKey(long roomId, long userId) {
        return "live:room:" + roomId + ":user:" + userId + ":connections";
    }
}
