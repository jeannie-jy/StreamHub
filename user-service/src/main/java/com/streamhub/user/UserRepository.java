package com.streamhub.user;

import java.util.List;
import java.util.Optional;

import com.streamhub.common.api.PageResult;

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

    public List<UserProfile> findByIds(List<Long> userIds) {
        List<Long> safeIds = userIds == null ? List.of() : userIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(100)
                .toList();
        if (safeIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(safeIds.size(), "?"));
        return jdbcTemplate.query(
                "SELECT u.id, u.username, u.nickname, u.avatar_url, u.role, u.status, "
                        + "(SELECT COUNT(*) FROM user_follow f WHERE f.anchor_id = u.id) AS follower_count "
                        + "FROM sys_user u WHERE u.id IN (" + placeholders + ")",
                (resultSet, rowNum) -> new UserProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("nickname"),
                        resultSet.getString("avatar_url"),
                        resultSet.getString("role"),
                        resultSet.getString("status"),
                        resultSet.getLong("follower_count")),
                safeIds.toArray());
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

    public PageResult<UserProfile> following(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow f JOIN sys_user u ON u.id = f.anchor_id WHERE f.user_id = ?",
                Long.class,
                userId);
        List<UserProfile> items = jdbcTemplate.query(
                """
                SELECT u.id, u.username, u.nickname, u.avatar_url, u.role, u.status,
                       (SELECT COUNT(*) FROM user_follow f2 WHERE f2.anchor_id = u.id) AS follower_count
                  FROM user_follow f
                  JOIN sys_user u ON u.id = f.anchor_id
                 WHERE f.user_id = ?
                 ORDER BY f.created_at DESC
                 LIMIT ? OFFSET ?
                """,
                (resultSet, rowNum) -> new UserProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("nickname"),
                        resultSet.getString("avatar_url"),
                        resultSet.getString("role"),
                        resultSet.getString("status"),
                        resultSet.getLong("follower_count")),
                userId,
                safePageSize,
                (safePage - 1) * safePageSize);
        return PageResult.of(items, safePage, safePageSize, total == null ? 0 : total);
    }

    public UserProfile updateProfile(long userId, String nickname, String avatarUrl) {
        jdbcTemplate.update(
                "UPDATE sys_user SET nickname = ?, avatar_url = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                nickname.trim(),
                avatarUrl,
                userId);
        return findById(userId).orElseThrow();
    }

    public PageResult<UserProfile> page(String keyword, String role, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        StringBuilder condition = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            condition.append(" AND (u.username LIKE ? OR u.nickname LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
        }
        if (role != null && !role.isBlank()) {
            condition.append(" AND u.role = ?");
            args.add(role.trim());
        }
        if (status != null && !status.isBlank()) {
            condition.append(" AND u.status = ?");
            args.add(status.trim());
        }
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user u" + condition,
                Long.class,
                args.toArray());
        args.add(safePageSize);
        args.add((safePage - 1) * safePageSize);
        List<UserProfile> items = jdbcTemplate.query(
                "SELECT u.id, u.username, u.nickname, u.avatar_url, u.role, u.status, "
                        + "(SELECT COUNT(*) FROM user_follow f WHERE f.anchor_id = u.id) AS follower_count "
                        + "FROM sys_user u" + condition + " ORDER BY u.id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> new UserProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("nickname"),
                        resultSet.getString("avatar_url"),
                        resultSet.getString("role"),
                        resultSet.getString("status"),
                        resultSet.getLong("follower_count")),
                args.toArray());
        return PageResult.of(items, safePage, safePageSize, total);
    }

    public UserProfile changeStatus(long userId, String status) {
        jdbcTemplate.update(
                "UPDATE sys_user SET status = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                status,
                userId);
        return findById(userId).orElseThrow();
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    public void appendAudit(long operatorId, String action, String targetType, String targetId, String reason) {
        jdbcTemplate.update(
                "INSERT INTO ops_audit_log(operator_id, action, target_type, target_id, reason) VALUES (?, ?, ?, ?, ?)",
                operatorId,
                action,
                targetType,
                targetId,
                reason);
    }
}
