package com.example.marketingplatform.audience.infrastructure;

import com.example.marketingplatform.audience.application.AudienceSnapshotQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcAudienceSnapshotQuery implements AudienceSnapshotQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAudienceSnapshotQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean containsMember(UUID campaignId, UUID memberId) {
        return !jdbc.query("SELECT 1 FROM campaign_audience_members WHERE campaign_id=:campaign AND member_id=:member",
                Map.of("campaign", campaignId, "member", memberId), (r, n) -> 1).isEmpty();
    }
}
