package com.streamhub.user;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> findById(long userId) {
        List<UserProfile> users = jdbcTemplate.query(
                """
                SELECT u.id, u.username, u.nickname, u.avatar_url, u.role, u.status,
                       (SELECT COUNT(*) FROM user_follow f WHERE f.anchor_id = u.id) AS follower_count
                  FROM sys_user u
                 WHERE u.id = ?
                """,
                (resultSet, rowNum) -> new UserProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("nickname"),
                        resultSet.getString("avatar_url"),
                        resultSet.getString("role"),
                        resultSet.getString("status"),
                        resultSet.getLong("follower_count")),
                userId);
        return users.stream().findFirst();
    }

    public void follow(long userId, long anchorId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO user_follow(user_id, anchor_id) VALUES (?, ?)",
                userId,
                anchorId);
    }

    public void unfollow(long userId, long anchorId) {
        jdbcTemplate.update(
                "DELETE FROM user_follow WHERE user_id = ? AND anchor_id = ?",
                userId,
                anchorId);
    }

    public boolean isFollowing(long userId, long anchorId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE user_id = ? AND anchor_id = ?",
                Integer.class,
                userId,
                anchorId);
        return count != null && count > 0;
    }
}
