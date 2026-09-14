package com.streamhub.live;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomFavoriteRepository {
    private final JdbcTemplate jdbcTemplate;

    public RoomFavoriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(long userId, long roomId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM live_room_favorite WHERE user_id = ? AND room_id = ?",
                Integer.class,
                userId,
                roomId);
        return count != null && count > 0;
    }

    public void add(long userId, long roomId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO live_room_favorite(user_id, room_id) VALUES (?, ?)",
                userId,
                roomId);
    }

    public void remove(long userId, long roomId) {
        jdbcTemplate.update(
                "DELETE FROM live_room_favorite WHERE user_id = ? AND room_id = ?",
                userId,
                roomId);
    }
}
