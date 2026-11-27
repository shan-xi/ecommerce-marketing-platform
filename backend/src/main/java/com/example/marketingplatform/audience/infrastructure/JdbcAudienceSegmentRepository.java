package com.example.marketingplatform.audience.infrastructure;

import com.example.marketingplatform.audience.application.AudienceSegmentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAudienceSegmentRepository implements AudienceSegmentRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAudienceSegmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID id, String name, String description, String rulesJson, UUID createdByUserId, Instant now) {
        jdbc.update("""
                INSERT INTO audience_segments(id,name,description,rules,created_by_user_id,created_at,updated_at)
                VALUES(:id,:name,:description,CAST(:rules AS jsonb),:admin,:now,:now)
                """, new MapSqlParameterSource().addValue("id", id).addValue("name", name)
                .addValue("description", description).addValue("rules", rulesJson).addValue("admin", createdByUserId)
                .addValue("now", Timestamp.from(now)));
    }

    @Override
    public boolean update(UUID id, String name, String description, String rulesJson, Instant now) {
        int changed = jdbc.update("""
                UPDATE audience_segments SET name=:name, description=:description,
                rules=CAST(:rules AS jsonb), updated_at=:now WHERE id=:id AND archived_at IS NULL
                """, new MapSqlParameterSource().addValue("name", name).addValue("description", description)
                .addValue("rules", rulesJson).addValue("now", Timestamp.from(now)).addValue("id", id));
        return changed > 0;
    }

    @Override
    public Optional<SegmentRow> findById(UUID id) {
        List<SegmentRow> rows = jdbc.query("""
                SELECT id,name,description,rules,created_by_user_id,created_at,updated_at
                FROM audience_segments WHERE id=:id AND archived_at IS NULL
                """, Map.of("id", id), this::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public long countActive() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM audience_segments WHERE archived_at IS NULL", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<SegmentRow> findPage(int page, int size) {
        return jdbc.query("""
                SELECT id,name,description,rules,created_by_user_id,created_at,updated_at
                FROM audience_segments WHERE archived_at IS NULL ORDER BY created_at DESC LIMIT :limit OFFSET :offset
                """, Map.of("limit", size, "offset", (long) page * size), this::mapRow);
    }

    private SegmentRow mapRow(ResultSet rs, int row) throws SQLException {
        return new SegmentRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getString("rules"), rs.getObject("created_by_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
