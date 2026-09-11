package com.streamhub.live;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import com.streamhub.common.api.PageResult;
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

    public boolean update(long roomId, long anchorId, CreateRoomRequest request) {
        return jdbcTemplate.update(
                "UPDATE live_room SET title = ?, cover_url = ?, category = ?, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND anchor_id = ?",
                request.title().trim(),
                request.coverUrl(),
                request.category(),
                roomId,
                anchorId) == 1;
    }

    public PageResult<LiveRoom> page(String status, String category, String keyword, int page, int pageSize, Long anchorId) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        StringBuilder condition = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            condition.append(" AND status = ?");
            args.add(status.trim());
        }
        if (category != null && !category.isBlank()) {
            condition.append(" AND category = ?");
            args.add(category.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            condition.append(" AND title LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (anchorId != null) {
            condition.append(" AND anchor_id = ?");
            args.add(anchorId);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM live_room" + condition, Long.class, args.toArray());
        args.add(safePageSize);
        args.add((safePage - 1) * safePageSize);
        List<LiveRoom> items = jdbcTemplate.query(
                "SELECT id, anchor_id, title, cover_url, category, status, created_at, updated_at "
                        + "FROM live_room" + condition + " ORDER BY status = 'LIVE' DESC, updated_at DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> new LiveRoom(
                        resultSet.getLong("id"),
                        resultSet.getLong("anchor_id"),
                        resultSet.getString("title"),
                        resultSet.getString("cover_url"),
                        resultSet.getString("category"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                args.toArray());
        return PageResult.of(items, safePage, safePageSize, total == null ? 0 : total);
    }

    public boolean adminStop(long roomId) {
        return jdbcTemplate.update(
                "UPDATE live_room SET status = 'OFFLINE', updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND status = 'LIVE'",
                roomId) == 1;
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM live_room WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM live_room", Long.class);
        return count == null ? 0 : count;
    }

    public void appendAudit(long operatorId, String action, String targetType, String targetId, String reason) {
        jdbcTemplate.update(
                "INSERT INTO ops_audit_log(operator_id, action, target_type, target_id, reason) VALUES (?, ?, ?, ?, ?)",
                operatorId,
                action,
                targetType,
                targetId,
                reason);
    }
}
