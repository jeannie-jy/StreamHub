package com.streamhub.auth;

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
public class UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createUser(String username, String nickname, String passwordHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sys_user(username, nickname, password_hash) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, nickname);
            statement.setString(3, passwordHash);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建用户后没有返回用户 ID");
        }
        return key.longValue();
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, status FROM sys_user WHERE username = ? LIMIT 1",
                (resultSet, rowNum) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("status")),
                username);
        return users.stream().findFirst();
    }

    public void createSession(String token, long userId, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO auth_session(token, user_id, expires_at) VALUES (?, ?, ?)",
                token,
                userId,
                Timestamp.from(expiresAt));
    }
}
