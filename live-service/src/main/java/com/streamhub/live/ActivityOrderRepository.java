package com.streamhub.live;

import java.util.List;
import java.util.Optional;

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
