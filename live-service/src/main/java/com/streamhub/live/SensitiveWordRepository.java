package com.streamhub.live;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SensitiveWordRepository {
    private final JdbcTemplate jdbcTemplate;

    public SensitiveWordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SensitiveWord> findActive() {
        return jdbcTemplate.query(
                "SELECT id, word, status, created_at FROM sensitive_word WHERE status = 'ACTIVE' ORDER BY id DESC",
                (resultSet, rowNum) -> new SensitiveWord(
                        resultSet.getLong("id"),
                        resultSet.getString("word"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant()));
    }

    public SensitiveWord create(String word) {
        try {
            jdbcTemplate.update("INSERT INTO sensitive_word(word, status) VALUES (?, 'ACTIVE')", word);
            return findByWord(word);
        } catch (DuplicateKeyException exception) {
            return jdbcTemplate.queryForObject(
                    "SELECT id, word, status, created_at FROM sensitive_word WHERE word = ?",
                    (resultSet, rowNum) -> new SensitiveWord(
                            resultSet.getLong("id"),
                            resultSet.getString("word"),
                            resultSet.getString("status"),
                            resultSet.getTimestamp("created_at").toInstant()),
                    word);
        }
    }

    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM sensitive_word WHERE id = ?", id);
    }

    public void logBlocked(long roomId, long userId, String content, String matchedWord) {
        jdbcTemplate.update(
                "INSERT INTO content_moderation_log(room_id, user_id, content, matched_word) VALUES (?, ?, ?, ?)",
                roomId,
                userId,
                content,
                matchedWord);
    }

    private SensitiveWord findById(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT id, word, status, created_at FROM sensitive_word WHERE id = ?",
                (resultSet, rowNum) -> new SensitiveWord(
                        resultSet.getLong("id"),
                        resultSet.getString("word"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant()),
                id);
    }

    private SensitiveWord findByWord(String word) {
        return jdbcTemplate.queryForObject(
                "SELECT id, word, status, created_at FROM sensitive_word WHERE word = ?",
                (resultSet, rowNum) -> new SensitiveWord(
                        resultSet.getLong("id"),
                        resultSet.getString("word"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant()),
                word);
    }
}
