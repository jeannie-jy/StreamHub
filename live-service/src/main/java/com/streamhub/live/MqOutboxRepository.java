package com.streamhub.live;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MqOutboxRepository {
    private static final String SELECT_ENTRY = "SELECT id, event_key, topic, message_tag, message_key, body, "
            + "delay_level, status, attempts, next_attempt_at, last_error, created_at, updated_at, sent_at "
            + "FROM mq_outbox ";
    private final JdbcTemplate jdbcTemplate;

    public MqOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long enqueue(
            String eventKey,
            String topic,
            String tag,
            String messageKey,
            String body,
            int delayLevel) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mq_outbox(event_key, topic, message_tag, message_key, body, delay_level) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, eventKey);
                statement.setString(2, topic);
                statement.setString(3, tag);
                statement.setString(4, messageKey);
                statement.setString(5, body);
                statement.setInt(6, Math.max(0, delayLevel));
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("Outbox 事件创建后未返回主键: " + eventKey);
            }
            return key.longValue();
        } catch (DuplicateKeyException exception) {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM mq_outbox WHERE event_key = ?",
                    Long.class,
                    eventKey);
            if (id == null) {
                throw exception;
            }
            return id;
        }
    }

    public List<OutboxMessage> findDispatchable(int maxAttempts, int limit) {
        return jdbcTemplate.query(
                "SELECT id, event_key, topic, message_tag, message_key, body, delay_level, attempts "
                        + "FROM mq_outbox WHERE status = 'PENDING' AND attempts < ? "
                        + "AND next_attempt_at <= CURRENT_TIMESTAMP(3) ORDER BY id LIMIT ?",
                (resultSet, rowNum) -> new OutboxMessage(
                        resultSet.getLong("id"),
                        resultSet.getString("event_key"),
                        resultSet.getString("topic"),
                        resultSet.getString("message_tag"),
                        resultSet.getString("message_key"),
                        resultSet.getString("body"),
                        resultSet.getInt("delay_level"),
                        resultSet.getInt("attempts")),
                Math.max(1, maxAttempts),
                Math.max(1, limit));
    }

    public boolean claim(long id, int maxAttempts) {
        return jdbcTemplate.update(
                "UPDATE mq_outbox SET status = 'SENDING', attempts = attempts + 1 "
                        + "WHERE id = ? AND status = 'PENDING' AND attempts < ? "
                        + "AND next_attempt_at <= CURRENT_TIMESTAMP(3)",
                id,
                Math.max(1, maxAttempts)) == 1;
    }

    public void markSent(long id) {
        jdbcTemplate.update(
                "UPDATE mq_outbox SET status = 'SENT', sent_at = CURRENT_TIMESTAMP(3), last_error = NULL WHERE id = ?",
                id);
    }

    public void markFailure(long id, int attempts, int maxAttempts, Instant nextAttemptAt, String error) {
        String status = attempts >= maxAttempts ? "FAILED" : "PENDING";
        jdbcTemplate.update(
                "UPDATE mq_outbox SET status = ?, next_attempt_at = ?, last_error = ? WHERE id = ?",
                status,
                Timestamp.from(nextAttemptAt),
                truncate(error, 1000),
                id);
    }

    public void requeue(String eventKey) {
        jdbcTemplate.update(
                "UPDATE mq_outbox SET status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP(3), "
                        + "last_error = NULL WHERE event_key = ? AND status IN ('SENT', 'FAILED')",
                eventKey);
    }

    public int recoverStaleSending(Instant updatedBefore) {
        return jdbcTemplate.update(
                "UPDATE mq_outbox SET status = 'PENDING', next_attempt_at = CURRENT_TIMESTAMP(3), "
                        + "last_error = 'Recovered stale SENDING event' "
                        + "WHERE status = 'SENDING' AND updated_at < ?",
                Timestamp.from(updatedBefore));
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mq_outbox WHERE status IN ('PENDING', 'SENDING')",
                Long.class);
        return count == null ? 0 : count;
    }

    public long countFailed() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mq_outbox WHERE status = 'FAILED'",
                Long.class);
        return count == null ? 0 : count;
    }

    public List<OutboxEntry> page(String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        if (status == null || status.isBlank()) {
            return queryEntries(SELECT_ENTRY + "ORDER BY id DESC LIMIT ? OFFSET ?", safeSize, (safePage - 1) * safeSize);
        }
        return queryEntries(
                SELECT_ENTRY + "WHERE status = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                status.trim().toUpperCase(),
                safeSize,
                (safePage - 1) * safeSize);
    }

    public long count(String status) {
        Long count;
        if (status == null || status.isBlank()) {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mq_outbox", Long.class);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mq_outbox WHERE status = ?",
                    Long.class,
                    status.trim().toUpperCase());
        }
        return count == null ? 0 : count;
    }

    public Optional<OutboxEntry> findByIdForUpdate(long id) {
        return queryEntries(SELECT_ENTRY + "WHERE id = ? FOR UPDATE", id).stream().findFirst();
    }

    public boolean retryFailed(long id) {
        return jdbcTemplate.update(
                "UPDATE mq_outbox SET status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP(3), "
                        + "last_error = NULL WHERE id = ? AND status = 'FAILED'",
                id) == 1;
    }

    public long oldestPendingAgeSeconds() {
        Long age = jdbcTemplate.queryForObject(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), CURRENT_TIMESTAMP(3)), 0) "
                        + "FROM mq_outbox WHERE status IN ('PENDING', 'SENDING')",
                Long.class);
        return age == null ? 0 : Math.max(0, age);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private List<OutboxEntry> queryEntries(String sql, Object... args) {
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new OutboxEntry(
                resultSet.getLong("id"),
                resultSet.getString("event_key"),
                resultSet.getString("topic"),
                resultSet.getString("message_tag"),
                resultSet.getString("message_key"),
                resultSet.getString("body"),
                resultSet.getInt("delay_level"),
                resultSet.getString("status"),
                resultSet.getInt("attempts"),
                resultSet.getTimestamp("next_attempt_at").toInstant(),
                resultSet.getString("last_error"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getTimestamp("sent_at") == null ? null : resultSet.getTimestamp("sent_at").toInstant()), args);
    }

    public record OutboxMessage(
            long id,
            String eventKey,
            String topic,
            String tag,
            String messageKey,
            String body,
            int delayLevel,
            int attempts) {
    }

    public record OutboxEntry(
            long id,
            String eventKey,
            String topic,
            String tag,
            String messageKey,
            String body,
            int delayLevel,
            String status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt) {
    }
}
