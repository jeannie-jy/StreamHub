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
public class GiftOrderRepository {
    private static final String SELECT_ORDER = "SELECT o.id, o.order_no, o.room_id, o.sender_id, o.anchor_id, "
            + "o.gift_id, g.code AS gift_code, o.quantity, o.total_amount, o.status, o.failure_reason, "
            + "o.created_at, o.processed_at FROM gift_order o JOIN gift_catalog g ON g.id = o.gift_id ";

    private final JdbcTemplate jdbcTemplate;

    public GiftOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean createPending(
            String orderNo,
            long roomId,
            long senderId,
            long anchorId,
            long giftId,
            int quantity,
            long totalAmount) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gift_order(order_no, room_id, sender_id, anchor_id, gift_id, quantity, total_amount) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, orderNo);
                statement.setLong(2, roomId);
                statement.setLong(3, senderId);
                statement.setLong(4, anchorId);
                statement.setLong(5, giftId);
                statement.setInt(6, quantity);
                statement.setLong(7, totalAmount);
                return statement;
            }, keyHolder);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<GiftOrder> findByOrderNo(String orderNo) {
        return query(SELECT_ORDER + "WHERE o.order_no = ?", orderNo).stream().findFirst();
    }

    public Optional<GiftOrder> findByOrderNoForUpdate(String orderNo) {
        return query(SELECT_ORDER + "WHERE o.order_no = ? FOR UPDATE", orderNo).stream().findFirst();
    }

    public List<GiftOrder> findPendingOlderThan(Instant createdBefore, int limit) {
        return query(
                SELECT_ORDER + "WHERE o.status = 'PENDING' AND o.created_at < ? "
                        + "ORDER BY o.id LIMIT ?",
                Timestamp.from(createdBefore),
                limit);
    }

    public List<Long> findRankRoomIds() {
        return jdbcTemplate.queryForList("SELECT DISTINCT room_id FROM gift_order", Long.class);
    }

    public List<GiftRankEntry> contributorRank(long roomId) {
        return rankQuery(
                "SELECT sender_id AS user_id, COALESCE(SUM(total_amount), 0) AS amount "
                        + "FROM gift_order WHERE room_id = ? AND status = 'SUCCESS' "
                        + "GROUP BY sender_id ORDER BY amount DESC, sender_id ASC",
                roomId);
    }

    public List<GiftRankEntry> incomeRank(long roomId) {
        return rankQuery(
                "SELECT anchor_id AS user_id, COALESCE(SUM(total_amount), 0) AS amount "
                        + "FROM gift_order WHERE room_id = ? AND status = 'SUCCESS' "
                        + "GROUP BY anchor_id ORDER BY amount DESC, anchor_id ASC",
                roomId);
    }

    public void markFailed(String orderNo, String reason) {
        jdbcTemplate.update(
                "UPDATE gift_order SET status = 'FAILED', failure_reason = ?, processed_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE order_no = ? AND status = 'PENDING'",
                reason,
                orderNo);
    }

    public void markSuccess(String orderNo) {
        jdbcTemplate.update(
                "UPDATE gift_order SET status = 'SUCCESS', processed_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE order_no = ? AND status = 'PENDING'",
                orderNo);
    }

    private List<GiftRankEntry> rankQuery(String sql, long roomId) {
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new GiftRankEntry(
                rowNum + 1,
                resultSet.getLong("user_id"),
                resultSet.getLong("amount")), roomId);
    }

    private List<GiftOrder> query(String sql, Object... args) {
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new GiftOrder(
                resultSet.getLong("id"),
                resultSet.getString("order_no"),
                resultSet.getLong("room_id"),
                resultSet.getLong("sender_id"),
                resultSet.getLong("anchor_id"),
                resultSet.getLong("gift_id"),
                resultSet.getString("gift_code"),
                resultSet.getInt("quantity"),
                resultSet.getLong("total_amount"),
                resultSet.getString("status"),
                resultSet.getString("failure_reason"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("processed_at") == null
                        ? null
                        : resultSet.getTimestamp("processed_at").toInstant()), args);
    }
}
