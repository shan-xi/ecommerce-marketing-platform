package com.example.marketingplatform.campaign;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.campaign.application.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class CampaignRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    CampaignRepository repository;
    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    @Transactional
    void persistsCampaignAndGuardsDraftUpdateAgainstStatusChange() {
        UUID id = UUID.randomUUID();
        UUID admin = UUID.fromString("10000000-0000-4000-8000-000000000001");
        Instant now = Instant.now();
        CampaignRepository.CampaignRow row = new CampaignRepository.CampaignRow(id, "Phase 5 integration", null, "Headline", "Body",
                "DRAFT", now.plusSeconds(60), now.plusSeconds(3600), null,
                "{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"age\",\"operator\":\">=\",\"value\":18}]}",
                null, admin, now, now);
        repository.create(row);
        var stored = repository.findById(id).orElseThrow();
        assertEquals("DRAFT", stored.status());
        assertEquals("Headline", stored.headline());
        assertNotNull(stored.rulesJson());
        CampaignRepository.CampaignRow updated = new CampaignRepository.CampaignRow(id, "Updated", null, "New headline", "Body",
                "DRAFT", row.startsAt(), row.endsAt(), null, row.rulesJson(), null, admin, now, now.plusSeconds(5));
        assertEquals(CampaignRepository.UpdateResult.UPDATED, repository.updateDraft(updated));
        assertEquals("Updated", repository.findById(id).orElseThrow().name());
        jdbc.update("UPDATE campaigns SET status='ACTIVE',published_at=:published,starts_at=:starts,ends_at=:ends WHERE id=:id",
                Map.of("published", java.sql.Timestamp.from(now.minusSeconds(7200)), "starts", java.sql.Timestamp.from(now.minusSeconds(3600)), "ends", java.sql.Timestamp.from(now.minusSeconds(1)), "id", id));
        CampaignRepository.CampaignRow attempted = new CampaignRepository.CampaignRow(id, "Must not change", null, "x", "y",
                "DRAFT", row.startsAt(), row.endsAt(), null, row.rulesJson(), null, admin, now, now.plusSeconds(10));
        assertEquals(CampaignRepository.UpdateResult.NOT_DRAFT, repository.updateDraft(attempted));
        assertEquals("Updated", repository.findById(id).orElseThrow().name());
        assertEquals(1, repository.count("ENDED", now));
    }
}
