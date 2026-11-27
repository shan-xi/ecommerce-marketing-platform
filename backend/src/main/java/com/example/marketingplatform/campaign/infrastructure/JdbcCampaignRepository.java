package com.example.marketingplatform.campaign.infrastructure;

import com.example.marketingplatform.campaign.application.CampaignRepository;
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
public class JdbcCampaignRepository implements CampaignRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCampaignRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(CampaignRow r) {
        jdbc.update("""
                INSERT INTO campaigns(id,name,description,landing_page_headline,landing_page_body,status,starts_at,ends_at,
                    audience_segment_id,audience_rules,published_at,created_by_user_id,created_at,updated_at)
                VALUES(:id,:name,:description,:headline,:body,:status,:starts,:ends,:segment,CAST(:rules AS jsonb),
                    :published,:admin,:created,:updated)
                """, params(r));
    }

    @Override
    public Optional<CampaignRow> findById(UUID id) {
        List<CampaignRow> rows = jdbc.query("SELECT * FROM campaigns WHERE id=:id", Map.of("id", id), this::map);
        return rows.stream().findFirst();
    }

    @Override
    public List<CampaignRow> findPage(int page, int size, String status, Instant now) {
        Filter f = filter(status, now);
        MapSqlParameterSource p = f.params.addValue("limit", size).addValue("offset", (long) page * size);
        return jdbc.query("SELECT * FROM campaigns WHERE " + f.sql + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset", p, this::map);
    }

    @Override
    public long count(String status, Instant now) {
        Filter f = filter(status, now);
        Long result = jdbc.queryForObject("SELECT count(*) FROM campaigns WHERE " + f.sql, f.params, Long.class);
        return result == null ? 0 : result;
    }

    @Override
    public UpdateResult updateDraft(CampaignRow r) {
        List<String> statuses = jdbc.query("SELECT status FROM campaigns WHERE id=:id FOR UPDATE", Map.of("id", r.id()), (rs, n) -> rs.getString(1));
        if (statuses.isEmpty()) return UpdateResult.NOT_FOUND;
        if (!"DRAFT".equals(statuses.get(0))) return UpdateResult.NOT_DRAFT;
        int updated = jdbc.update("""
                UPDATE campaigns SET name=:name,description=:description,landing_page_headline=:headline,
                    landing_page_body=:body,starts_at=:starts,ends_at=:ends,audience_segment_id=:segment,
                    audience_rules=CAST(:rules AS jsonb),updated_at=:updated
                WHERE id=:id AND status='DRAFT'
                """, params(r));
        return updated == 1 ? UpdateResult.UPDATED : UpdateResult.NOT_DRAFT;
    }

    private Filter filter(String status, Instant now) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("now", Timestamp.from(now));
        if (status == null) return new Filter("TRUE", p);
        return switch (status) {
            case "DRAFT" -> new Filter("status='DRAFT'", p);
            case "ACTIVE" -> new Filter("status='ACTIVE' AND NOT (published_at IS NOT NULL AND ends_at<=:now)", p);
            case "PAUSED" -> new Filter("status='PAUSED' AND NOT (published_at IS NOT NULL AND ends_at<=:now)", p);
            case "ENDED" ->
                    new Filter("status IN ('ACTIVE','PAUSED') AND published_at IS NOT NULL AND ends_at<=:now", p);
            default -> new Filter("FALSE", p);
        };
    }

    private MapSqlParameterSource params(CampaignRow r) {
        return new MapSqlParameterSource().addValue("id", r.id()).addValue("name", r.name()).addValue("description", r.description())
                .addValue("headline", r.headline()).addValue("body", r.body()).addValue("status", r.status())
                .addValue("starts", Timestamp.from(r.startsAt())).addValue("ends", Timestamp.from(r.endsAt()))
                .addValue("segment", r.segmentId()).addValue("rules", r.rulesJson()).addValue("published", ts(r.publishedAt()))
                .addValue("admin", r.createdByUserId()).addValue("created", Timestamp.from(r.createdAt())).addValue("updated", Timestamp.from(r.updatedAt()));
    }

    private Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private CampaignRow map(ResultSet rs, int row) throws SQLException {
        Timestamp published = rs.getTimestamp("published_at");
        return new CampaignRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getString("landing_page_headline"), rs.getString("landing_page_body"), rs.getString("status"),
                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                rs.getObject("audience_segment_id", UUID.class), rs.getString("audience_rules"),
                published == null ? null : published.toInstant(), rs.getObject("created_by_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private record Filter(String sql, MapSqlParameterSource params) {
    }
}
