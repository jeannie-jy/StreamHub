package com.streamhub.live;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OpsAuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public OpsAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(long operatorId, String action, String targetType, String targetId, String reason) {
        jdbcTemplate.update(
                "INSERT INTO ops_audit_log(operator_id, action, target_type, target_id, reason) VALUES (?, ?, ?, ?, ?)",
                operatorId,
                action,
                targetType,
                targetId,
                reason);
    }

    public List<AuditLogView> page(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        return jdbcTemplate.query(
                "SELECT id, operator_id, action, target_type, target_id, reason, created_at "
                        + "FROM ops_audit_log ORDER BY id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> new AuditLogView(
                        resultSet.getLong("id"),
                        resultSet.getLong("operator_id"),
                        resultSet.getString("action"),
                        resultSet.getString("target_type"),
                        resultSet.getString("target_id"),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("created_at").toInstant()),
                safeSize,
                (safePage - 1) * safeSize);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Long.class);
        return count == null ? 0 : count;
    }

    public record AuditLogView(
            long id,
            long operatorId,
            String action,
            String targetType,
            String targetId,
            String reason,
            Instant createdAt) {
    }
}
