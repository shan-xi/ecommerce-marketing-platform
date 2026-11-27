package com.example.marketingplatform.campaign.application;

import com.example.marketingplatform.audience.application.AudiencePreviewResult;
import com.example.marketingplatform.audience.application.AudienceUseCase;
import com.example.marketingplatform.audience.domain.InvalidAudienceRuleException;
import com.example.marketingplatform.campaign.domain.CampaignStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CampaignService {
    private final CampaignRepository repository;
    private final AudienceUseCase audience;
    private final JsonMapper mapper;
    private final Clock clock;

    public CampaignService(CampaignRepository repository, AudienceUseCase audience, JsonMapper mapper, Clock clock) {
        this.repository = repository;
        this.audience = audience;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Campaign create(UUID adminId, CampaignWrite input) {
        validate(input);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now(clock);
        CampaignRepository.CampaignRow row = row(id, input, "DRAFT", adminId, now, now, null);
        repository.create(row);
        return view(repository.findById(id).orElseThrow(CampaignNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public CampaignPage list(int page, int size, String status) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100.");
        if (status != null && !List.of("DRAFT", "ACTIVE", "PAUSED", "ENDED").contains(status))
            throw new IllegalArgumentException("status must be DRAFT, ACTIVE, PAUSED or ENDED.");
        Instant now = Instant.now(clock);
        long total = repository.count(status, now);
        List<CampaignSummary> rows = repository.findPage(page, size, status, now).stream().map(this::summary).toList();
        return new CampaignPage(rows, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public Campaign get(UUID id) {
        return view(repository.findById(id).orElseThrow(CampaignNotFoundException::new));
    }

    @Transactional
    public Campaign update(UUID id, CampaignWrite input) {
        CampaignRepository.CampaignRow existing = repository.findById(id).orElseThrow(CampaignNotFoundException::new);
        if (!"DRAFT".equals(existing.status()))
            throw new CampaignConflictException("Only DRAFT campaigns can be updated.");
        validate(input);
        CampaignRepository.CampaignRow updated = row(id, input, existing.status(), existing.createdByUserId(),
                existing.createdAt(), Instant.now(clock), existing.publishedAt());
        CampaignRepository.UpdateResult result = repository.updateDraft(updated);
        if (result == CampaignRepository.UpdateResult.NOT_FOUND) throw new CampaignNotFoundException();
        if (result == CampaignRepository.UpdateResult.NOT_DRAFT)
            throw new CampaignConflictException("Only DRAFT campaigns can be updated.");
        return view(repository.findById(id).orElseThrow(CampaignNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public AudiencePreviewResult preview(UUID id) {
        CampaignRepository.CampaignRow row = repository.findById(id).orElseThrow(CampaignNotFoundException::new);
        return audience.previewComposition(row.segmentId(), parseRules(row.rulesJson()));
    }

    private void validate(CampaignWrite input) {
        if (input == null) throw new IllegalArgumentException("Request body is required.");
        if (input.name() == null || input.name().isBlank() || input.name().length() > 160)
            throw new IllegalArgumentException("name is required and must be at most 160 characters.");
        if (input.landingPage() == null || input.landingPage().headline() == null || input.landingPage().headline().isBlank()
                || input.landingPage().headline().length() > 200)
            throw new IllegalArgumentException("landingPage.headline is required and must be at most 200 characters.");
        if (input.landingPage().body() == null || input.landingPage().body().isBlank() || input.landingPage().body().length() > 10000)
            throw new IllegalArgumentException("landingPage.body is required and must be at most 10000 characters.");
        Instant start = parseInstant(input.startsAt(), "startsAt"), end = parseInstant(input.endsAt(), "endsAt");
        if (!end.isAfter(start)) throw new IllegalArgumentException("endsAt must be after startsAt.");
        if (input.audience() == null) throw new InvalidAudienceRuleException("audience is required.");
        audience.validateComposition(input.audience().segmentId(), campaignRules(input.audience()));
    }

    private Instant parseInstant(String value, String field) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ex) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 date-time with offset.");
        }
    }

    private CampaignRepository.CampaignRow row(UUID id, CampaignWrite input, String status, UUID adminId,
                                               Instant createdAt, Instant updatedAt, Instant publishedAt) {
        JsonNode campaignRules = campaignRules(input.audience());
        String rules = campaignRules == null ? null : serialize(campaignRules);
        return new CampaignRepository.CampaignRow(id, input.name().trim(), input.description(),
                input.landingPage().headline().trim(), input.landingPage().body(), status,
                parseInstant(input.startsAt(), "startsAt"), parseInstant(input.endsAt(), "endsAt"),
                input.audience().segmentId(), rules, publishedAt, adminId, createdAt, updatedAt);
    }

    private Campaign view(CampaignRepository.CampaignRow r) {
        String effective = CampaignStatus.effectiveReadStatus(r.status(), r.publishedAt(), r.endsAt(), Instant.now(clock)).name();
        return new Campaign(r.id(), r.name(), r.description(), effective, r.startsAt(), r.endsAt(),
                new LandingPage(r.headline(), r.body()), new Audience(r.segmentId(), parseRules(r.rulesJson())),
                r.publishedAt(), r.createdAt(), r.updatedAt());
    }

    private CampaignSummary summary(CampaignRepository.CampaignRow r) {
        String effective = CampaignStatus.effectiveReadStatus(r.status(), r.publishedAt(), r.endsAt(), Instant.now(clock)).name();
        return new CampaignSummary(r.id(), r.name(), r.description(), effective, r.startsAt(), r.endsAt(), r.publishedAt(), r.createdAt(), r.updatedAt());
    }

    /**
     * An omitted JSON property can be represented by Jackson as a MissingNode,
     * while an explicitly supplied JSON null is represented by a NullNode.
     * Both mean that campaign-specific rules are disabled.
     */
    private JsonNode campaignRules(AudienceWrite audienceWrite) {
        JsonNode rules = audienceWrite.rules();
        return rules == null || rules.isMissingNode() || rules.isNull() ? null : rules;
    }

    private JsonNode parseRules(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception ex) {
            throw new InvalidAudienceRuleException("Stored campaign audience rules are unreadable.");
        }
    }

    private String serialize(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Campaign audience rules could not be serialized.", ex);
        }
    }

    public record CampaignWrite(String name, String description, String startsAt, String endsAt,
                                LandingPageWrite landingPage, AudienceWrite audience) {
    }

    public record LandingPageWrite(String headline, String body) {
    }

    public record AudienceWrite(UUID segmentId, JsonNode rules) {
    }

    public record LandingPage(String headline, String body) {
    }

    public record Audience(UUID segmentId, JsonNode rules) {
    }

    public record Campaign(UUID id, String name, String description, String status, Instant startsAt, Instant endsAt,
                           LandingPage landingPage, Audience audience, Instant publishedAt, Instant createdAt,
                           Instant updatedAt) {
    }

    public record CampaignSummary(UUID id, String name, String description, String status, Instant startsAt,
                                  Instant endsAt,
                                  Instant publishedAt, Instant createdAt, Instant updatedAt) {
    }

    public record CampaignPage(List<CampaignSummary> data, int number, int size, long totalElements, int totalPages) {
    }

    public static class CampaignNotFoundException extends RuntimeException {
    }

    public static class CampaignConflictException extends RuntimeException {
        public CampaignConflictException(String message) {
            super(message);
        }
    }
}
