package com.streamhub.live;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityRepository {
    private final JdbcTemplate jdbcTemplate;

    public ActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(
            long roomId,
            String name,
            int stock,
            long unitPrice,
            Instant startsAt,
            Instant endsAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO activity(room_id, name, stock, unit_price, starts_at, ends_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, roomId);
            statement.setString(2, name);
            statement.setInt(3, stock);
            statement.setLong(4, unitPrice);
            statement.setTimestamp(5, Timestamp.from(startsAt));
            statement.setTimestamp(6, Timestamp.from(endsAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建活动后没有返回活动 ID");
        }
        return key.longValue();
    }

    public Optional<Activity> findById(long activityId) {
        return query("SELECT id, room_id, name, stock, unit_price, status, starts_at, ends_at, "
                + "created_at, updated_at FROM activity WHERE id = ?", activityId).stream().findFirst();
    }

    public boolean activate(long activityId) {
        return jdbcTemplate.update(
                "UPDATE activity SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ? AND status = 'DRAFT'",
                activityId) == 1;
    }

    private List<Activity> query(String sql, Object... args) {
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new Activity(
                resultSet.getLong("id"),
                resultSet.getLong("room_id"),
                resultSet.getString("name"),
                resultSet.getInt("stock"),
                resultSet.getLong("unit_price"),
                resultSet.getString("status"),
                resultSet.getTimestamp("starts_at").toInstant(),
                resultSet.getTimestamp("ends_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()), args);
    }
}
