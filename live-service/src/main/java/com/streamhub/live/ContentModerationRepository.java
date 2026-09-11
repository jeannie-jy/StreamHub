package com.streamhub.live;

import java.time.Instant;
import java.util.List;

import com.streamhub.common.api.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContentModerationRepository {
    private final JdbcTemplate jdbcTemplate;

    public ContentModerationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<ModerationLogView> page(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        List<ModerationLogView> items = jdbcTemplate.query(
                "SELECT id, room_id, user_id, content, matched_word, action, created_at "
                        + "FROM content_moderation_log ORDER BY id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> new ModerationLogView(
                        resultSet.getLong("id"),
                        resultSet.getLong("room_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("content"),
                        resultSet.getString("matched_word"),
                        resultSet.getString("action"),
                        resultSet.getTimestamp("created_at").toInstant()),
                safeSize,
                (safePage - 1) * safeSize);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_moderation_log", Long.class);
        return PageResult.of(items, safePage, safeSize, total == null ? 0 : total);
    }

    public record ModerationLogView(
            long id,
            long roomId,
            long userId,
            String content,
            String matchedWord,
            String action,
            Instant createdAt) {
    }
}
