package com.streamhub.live;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GiftCatalogRepository {
    private final JdbcTemplate jdbcTemplate;

    public GiftCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GiftCatalog> findActive() {
        return jdbcTemplate.query(
                "SELECT id, code, name, price, status, created_at, updated_at "
                        + "FROM gift_catalog WHERE status = 'ACTIVE' ORDER BY id",
                (resultSet, rowNum) -> map(resultSet));
    }

    public Optional<GiftCatalog> findActiveByCode(String code) {
        return jdbcTemplate.query(
                "SELECT id, code, name, price, status, created_at, updated_at "
                        + "FROM gift_catalog WHERE code = ? AND status = 'ACTIVE'",
                (resultSet, rowNum) -> map(resultSet),
                code).stream().findFirst();
    }

    private GiftCatalog map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new GiftCatalog(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getLong("price"),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
