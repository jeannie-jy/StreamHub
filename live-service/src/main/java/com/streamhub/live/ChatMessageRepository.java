package com.streamhub.live;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {
    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatMessage save(long roomId, long userId, String clientMessageId, String content) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO chat_message(room_id, user_id, client_message_id, content) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, roomId);
                statement.setLong(2, userId);
                statement.setString(3, clientMessageId);
                statement.setString(4, content);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("保存弹幕后没有返回消息 ID");
            }
            return findById(key.longValue());
        } catch (DuplicateKeyException exception) {
            return findByClientMessageId(roomId, clientMessageId);
        }
    }

    public List<ChatMessage> listAfter(long roomId, long afterId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query(
                "SELECT id, room_id, user_id, client_message_id, content, created_at "
                        + "FROM chat_message WHERE room_id = ? AND id > ? ORDER BY id LIMIT ?",
                (resultSet, rowNum) -> map(resultSet),
                roomId,
                afterId,
                safeLimit);
    }

    public long countByRoom(long roomId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message WHERE room_id = ?", Long.class, roomId);
        return count == null ? 0 : count;
    }

    private ChatMessage findById(long messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, room_id, user_id, client_message_id, content, created_at "
                        + "FROM chat_message WHERE id = ?",
                (resultSet, rowNum) -> map(resultSet),
                messageId);
    }

    private ChatMessage findByClientMessageId(long roomId, String clientMessageId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, room_id, user_id, client_message_id, content, created_at "
                        + "FROM chat_message WHERE room_id = ? AND client_message_id = ?",
                (resultSet, rowNum) -> map(resultSet),
                roomId,
                clientMessageId);
    }

    private ChatMessage map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new ChatMessage(
                resultSet.getLong("id"),
                resultSet.getLong("room_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("client_message_id"),
                resultSet.getString("content"),
                createdAt.toInstant());
    }
}
