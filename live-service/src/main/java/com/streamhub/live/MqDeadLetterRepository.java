package com.streamhub.live;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MqDeadLetterRepository {
    private static final String SELECT_COLUMNS = "SELECT id, consumer_group, original_topic, message_id, "
            + "message_keys, message_tag, body, reconsume_times, failure_reason, status, replay_outbox_id, "
            + "created_at, handled_at FROM mq_dead_letter ";

    private final JdbcTemplate jdbcTemplate;

    public MqDeadLetterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void capture(
            String consumerGroup,
            String originalTopic,
            String messageId,
            String messageKeys,
            String tag,
            String body,
            int reconsumeTimes,
            String failureReason) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO mq_dead_letter(consumer_group, original_topic, message_id, message_keys, "
                        + "message_tag, body, reconsume_times, failure_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                consumerGroup,
                originalTopic,
                messageId,
                messageKeys,
                tag,
                body,
                Math.max(0, reconsumeTimes),
                truncate(failureReason, 1000));
    }

    public List<DeadLetterMessage> page(String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        if (status == null || status.isBlank()) {
            return query(SELECT_COLUMNS + "ORDER BY id DESC LIMIT ? OFFSET ?", safeSize, (safePage - 1) * safeSize);
        }
        return query(
                SELECT_COLUMNS + "WHERE status = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                status.trim().toUpperCase(),
                safeSize,
                (safePage - 1) * safeSize);
    }

    public long count(String status) {
        Long count;
        if (status == null || status.isBlank()) {
            count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mq_dead_letter", Long.class);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mq_dead_letter WHERE status = ?",
                    Long.class,
                    status.trim().toUpperCase());
        }
        return count == null ? 0 : count;
    }

    public long countNew() {
        return count("NEW");
    }

    public Optional<DeadLetterMessage> findByIdForUpdate(long id) {
        return query(SELECT_COLUMNS + "WHERE id = ? FOR UPDATE", id).stream().findFirst();
    }

    public boolean markReplayQueued(long id, long outboxId) {
        return jdbcTemplate.update(
                "UPDATE mq_dead_letter SET status = 'REPLAY_QUEUED', replay_outbox_id = ?, "
                        + "handled_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND status = 'NEW'",
                outboxId,
                id) == 1;
    }

    public boolean markIgnored(long id) {
        return jdbcTemplate.update(
                "UPDATE mq_dead_letter SET status = 'IGNORED', handled_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND status = 'NEW'",
                id) == 1;
    }

    public void markReplayedByOutbox(long outboxId) {
        jdbcTemplate.update(
                "UPDATE mq_dead_letter SET status = 'REPLAYED', handled_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE replay_outbox_id = ? AND status = 'REPLAY_QUEUED'",
                outboxId);
    }

    public int reconcileReplayed() {
        return jdbcTemplate.update(
                "UPDATE mq_dead_letter d JOIN mq_outbox o ON o.id = d.replay_outbox_id "
                        + "SET d.status = 'REPLAYED', d.handled_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE d.status = 'REPLAY_QUEUED' AND o.status = 'SENT'");
    }

    private List<DeadLetterMessage> query(String sql, Object... args) {
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new DeadLetterMessage(
                resultSet.getLong("id"),
                resultSet.getString("consumer_group"),
                resultSet.getString("original_topic"),
                resultSet.getString("message_id"),
                resultSet.getString("message_keys"),
                resultSet.getString("message_tag"),
                resultSet.getString("body"),
                resultSet.getInt("reconsume_times"),
                resultSet.getString("failure_reason"),
                resultSet.getString("status"),
                resultSet.getObject("replay_outbox_id", Long.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("handled_at") == null
                        ? null
                        : resultSet.getTimestamp("handled_at").toInstant()), args);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record DeadLetterMessage(
            long id,
            String consumerGroup,
            String originalTopic,
            String messageId,
            String messageKeys,
            String tag,
            String body,
            int reconsumeTimes,
            String failureReason,
            String status,
            Long replayOutboxId,
            Instant createdAt,
            Instant handledAt) {
    }
}
