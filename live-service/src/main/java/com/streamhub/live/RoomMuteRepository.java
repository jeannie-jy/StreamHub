package com.streamhub.live;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomMuteRepository {
    private final JdbcTemplate jdbcTemplate;

    public RoomMuteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void mute(long roomId, long userId, long operatorId, String reason, Instant expiresAt) {
        revoke(roomId, userId);
        jdbcTemplate.update(
                "INSERT INTO room_mute(room_id, user_id, operator_id, reason, expires_at) VALUES (?, ?, ?, ?, ?)",
                roomId,
                userId,
                operatorId,
                reason,
                expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
    }

    public void revoke(long roomId, long userId) {
        jdbcTemplate.update(
                "UPDATE room_mute SET revoked_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE room_id = ? AND user_id = ? AND revoked_at IS NULL",
                roomId,
                userId);
    }

    public boolean isMuted(long roomId, long userId, Instant now) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_mute "
                        + "WHERE room_id = ? AND user_id = ? AND revoked_at IS NULL "
                        + "AND (expires_at IS NULL OR expires_at > ?)",
                Integer.class,
                roomId,
                userId,
                java.sql.Timestamp.from(now));
        return count != null && count > 0;
    }
}
