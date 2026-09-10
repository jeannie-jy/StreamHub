package com.streamhub.live;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LiveRoomRepository {
    private final JdbcTemplate jdbcTemplate;

    public LiveRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(long anchorId, CreateRoomRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO live_room(anchor_id, title, cover_url, category) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, anchorId);
            statement.setString(2, request.title().trim());
            statement.setString(3, request.coverUrl());
            statement.setString(4, request.category());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建直播间后没有返回房间 ID");
        }
        return key.longValue();
    }

    public Optional<LiveRoom> findById(long roomId) {
        List<LiveRoom> rooms = jdbcTemplate.query(
                "SELECT id, anchor_id, title, cover_url, category, status, created_at, updated_at "
                        + "FROM live_room WHERE id = ?",
                (resultSet, rowNum) -> new LiveRoom(
                        resultSet.getLong("id"),
                        resultSet.getLong("anchor_id"),
                        resultSet.getString("title"),
                        resultSet.getString("cover_url"),
                        resultSet.getString("category"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                roomId);
        return rooms.stream().findFirst();
    }

    public boolean start(long roomId, long anchorId, String streamKeyHash) {
        return jdbcTemplate.update(
                "UPDATE live_room SET status = 'LIVE', stream_key_hash = ?, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND anchor_id = ? AND status = 'OFFLINE'",
                streamKeyHash,
                roomId,
                anchorId) == 1;
    }

    public void createSession(long roomId, String playbackUrl) {
        jdbcTemplate.update(
                "INSERT INTO live_session(room_id, started_at, playback_url) VALUES (?, ?, ?)",
                roomId,
                Timestamp.from(java.time.Instant.now()),
                playbackUrl);
    }

    public boolean end(long roomId, long anchorId) {
        return jdbcTemplate.update(
                "UPDATE live_room SET status = 'OFFLINE', updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND anchor_id = ? AND status = 'LIVE'",
                roomId,
                anchorId) == 1;
    }

    public void endCurrentSession(long roomId) {
        jdbcTemplate.update(
                "UPDATE live_session SET ended_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE room_id = ? AND ended_at IS NULL",
                roomId);
    }
}
