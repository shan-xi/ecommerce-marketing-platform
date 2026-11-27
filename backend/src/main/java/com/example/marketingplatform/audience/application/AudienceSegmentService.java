package com.example.marketingplatform.audience.application;

import com.example.marketingplatform.audience.domain.AudienceRuleSet;
import com.example.marketingplatform.audience.domain.InvalidAudienceRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AudienceSegmentService implements AudienceUseCase {
    private final AudienceSegmentRepository repository;
    private final AudienceRuleValidator validator;
    private final AudienceEvaluator evaluator;
    private final JsonMapper mapper;
    private final Clock clock;

    public AudienceSegmentService(AudienceSegmentRepository repository, AudienceRuleValidator validator,
                                  AudienceEvaluator evaluator, JsonMapper mapper, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.evaluator = evaluator;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Segment create(UUID adminId, String name, String description, JsonNode rules) {
        validateName(name);
        AudienceRuleSet canonical = validator.validate(rules);
        UUID id = UUID.randomUUID();
        repository.create(id, name.trim(), description, serialize(canonical), adminId, Instant.now(clock));
        return get(id);
    }

    @Transactional
    public Segment update(UUID id, String name, String description, JsonNode rules) {
        validateName(name);
        AudienceRuleSet canonical = validator.validate(rules);
        boolean changed = repository.update(id, name.trim(), description, serialize(canonical), Instant.now(clock));
        if (!changed) throw new AudienceResourceNotFoundException();
        return get(id);
    }

    @Transactional(readOnly = true)
    public Segment get(UUID id) {
        return repository.findById(id).map(this::toSegment).orElseThrow(AudienceResourceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Segment> list(int page, int size) {
        int p = Math.max(0, page), s = Math.max(1, Math.min(100, size));
        long total = repository.countActive();
        List<Segment> rows = repository.findPage(p, s).stream().map(this::toSegment).toList();
        return new Page<>(rows, p, s, total, (int) Math.ceil((double) total / s));
    }

    @Override
    @Transactional(readOnly = true)
    public AudiencePreviewResult preview(JsonNode rules) {
        return evaluator.preview(validator.validate(rules), null, 10);
    }

    @Override
    @Transactional(readOnly = true)
    public AudiencePreviewResult previewComposition(UUID segmentId, JsonNode campaignRules) {
        AudienceRuleSet saved = segmentId == null ? null : validator.validate(get(segmentId).rules());
        AudienceRuleSet specific = campaignRules == null ? null : validator.validate(campaignRules);
        if (saved == null && specific == null)
            throw new InvalidAudienceRuleException("At least one audience definition is required.");
        return evaluator.preview(saved, specific, 10);
    }

    private Segment toSegment(AudienceSegmentRepository.SegmentRow row) {
        try {
            return new Segment(row.id(), row.name(), row.description(), mapper.readTree(row.rulesJson()),
                    row.createdByUserId(), row.createdAt(), row.updatedAt());
        } catch (Exception ex) {
            throw new IllegalStateException("Stored audience rules are unreadable.", ex);
        }
    }

    private String serialize(AudienceRuleSet rules) {
        try {
            return mapper.writeValueAsString(rules);
        } catch (Exception ex) {
            throw new IllegalStateException("Audience rules could not be serialized.", ex);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 120)
            throw new IllegalArgumentException("name is required and must be at most 120 characters.");
    }

    public record Segment(UUID id, String name, String description, JsonNode rules, UUID createdByUserId,
                          Instant createdAt, Instant updatedAt) {
    }

    public record Page<T>(List<T> data, int number, int size, long totalElements, int totalPages) {
    }

    public static class AudienceResourceNotFoundException extends RuntimeException {
    }
}
