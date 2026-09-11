package com.streamhub.live;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.streamhub.common.api.PageResult;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public ActivityOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean createPending(String orderNo, long activityId, long userId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO activity_order(order_no, activity_id, user_id) VALUES (?, ?, ?)",
                    orderNo,
                    activityId,
                    userId);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<ActivityOrder> findByOrderNo(String orderNo) {
        return query("WHERE order_no = ?", orderNo).stream().findFirst();
    }

    public Optional<ActivityOrder> findByActivityAndUser(long activityId, long userId) {
        return query("WHERE activity_id = ? AND user_id = ?", activityId, userId).stream().findFirst();
    }

    public List<ActivityOrder> findPendingOlderThan(Instant createdBefore, int limit) {
        return query(
                "WHERE status = 'PENDING' AND created_at < ? ORDER BY id LIMIT ?",
                Timestamp.from(createdBefore),
                limit);
    }

    public List<Long> findReservedUserIds(long activityId) {
        return jdbcTemplate.queryForList(
                "SELECT user_id FROM activity_order "
                        + "WHERE activity_id = ? AND status IN ('PENDING', 'SUCCESS') ORDER BY id",
                Long.class,
                activityId);
    }

    public PageResult<ActivityOrder> findByUserId(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_order WHERE user_id = ?", Long.class, userId);
        List<ActivityOrder> items = query(
                "WHERE user_id = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                userId,
                safePageSize,
                (safePage - 1) * safePageSize);
        return PageResult.of(items, safePage, safePageSize, total == null ? 0 : total);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_order", Long.class);
        return count == null ? 0 : count;
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_order WHERE status = 'PENDING'", Long.class);
        return count == null ? 0 : count;
    }

    public long countByRoom(long roomId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_order o JOIN activity a ON a.id = o.activity_id WHERE a.room_id = ?",
                Long.class,
                roomId);
        return count == null ? 0 : count;
    }

    public boolean markSuccess(String orderNo) {
        return jdbcTemplate.update(
                "UPDATE activity_order SET status = 'SUCCESS', processed_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE order_no = ? AND status = 'PENDING'",
                orderNo) == 1;
    }

    public boolean closeIfPending(String orderNo) {
        return jdbcTemplate.update(
                "UPDATE activity_order SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE order_no = ? AND status = 'PENDING'",
                orderNo) == 1;
    }

    private List<ActivityOrder> query(String condition, Object... args) {
        return jdbcTemplate.query(
                "SELECT id, order_no, activity_id, user_id, status, created_at, processed_at, closed_at "
                        + "FROM activity_order " + condition,
                (resultSet, rowNum) -> new ActivityOrder(
                        resultSet.getLong("id"),
                        resultSet.getString("order_no"),
                        resultSet.getLong("activity_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("processed_at") == null
                                ? null
                                : resultSet.getTimestamp("processed_at").toInstant(),
                        resultSet.getTimestamp("closed_at") == null
                                ? null
                                : resultSet.getTimestamp("closed_at").toInstant()),
                args);
    }
}
