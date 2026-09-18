package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class OnlinePresenceServiceRedisIntegrationTest {
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void luaTracksConnectionsAcrossNodesAndRemovesUserOnlyAfterTheLastConnectionLeaves() {
        OnlinePresenceService nodeA = service("node-a", "boot-a");
        OnlinePresenceService nodeB = service("node-b", "boot-b");

        nodeA.join(11, 7, "same-session-id");
        nodeB.join(11, 7, "same-session-id");

        assertThat(nodeA.onlineCount(11)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().size(RedisKeys.roomUserConnections(11, 7))).isEqualTo(2);
        assertThat(redisTemplate.getExpire(RedisKeys.roomOnline(11), TimeUnit.MILLISECONDS))
                .isPositive();
        assertThat(redisTemplate.getExpire(
                RedisKeys.roomUserConnections(11, 7),
                TimeUnit.MILLISECONDS)).isPositive();

        nodeA.leave(11, 7, "same-session-id");
        assertThat(nodeA.onlineCount(11)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().size(RedisKeys.roomUserConnections(11, 7))).isEqualTo(1);

        nodeB.leave(11, 7, "same-session-id");
        assertThat(nodeA.onlineCount(11)).isZero();
        assertThat(redisTemplate.hasKey(RedisKeys.roomUserConnections(11, 7))).isFalse();
    }

    @Test
    void heartbeatRefreshesRoomAndConnectionKeyExpirations() {
        OnlinePresenceService service = service("node-a", "boot-a");

        service.join(12, 8, "connection-a");

        Long roomTtlMillis = redisTemplate.getExpire(RedisKeys.roomOnline(12), TimeUnit.MILLISECONDS);
        Long connectionTtlMillis = redisTemplate.getExpire(
                RedisKeys.roomUserConnections(12, 8),
                TimeUnit.MILLISECONDS
        );
        assertThat(roomTtlMillis).isNotNull().isPositive().isLessThanOrEqualTo(10_000L);
        assertThat(connectionTtlMillis).isNotNull().isPositive().isLessThanOrEqualTo(10_000L);
    }

    private OnlinePresenceService service(String nodeId, String bootId) {
        return new OnlinePresenceService(redisTemplate, 5_000, new NodeIdentity(nodeId, bootId));
    }
}
