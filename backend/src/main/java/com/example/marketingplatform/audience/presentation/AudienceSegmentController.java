package com.example.marketingplatform.audience.presentation;

import com.example.marketingplatform.audience.application.AudienceSegmentService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audience-segments")
public class AudienceSegmentController {
    private final AudienceSegmentService service;

    public AudienceSegmentController(AudienceSegmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SegmentWrite request) {
        return Map.of("data", service.create(principal.userId(), request.name(), request.description(), request.rules()));
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100.");
        var result = service.list(page, size);
        return Map.of("data", result.data(), "page", Map.of("number", result.number(), "size", result.size(), "totalElements", result.totalElements(), "totalPages", result.totalPages()));
    }

    @GetMapping("/{segmentId}")
    public Map<String, Object> get(@PathVariable UUID segmentId) {
        return Map.of("data", service.get(segmentId));
    }

    @PutMapping("/{segmentId}")
    public Map<String, Object> update(@PathVariable UUID segmentId, @Valid @RequestBody SegmentWrite request) {
        return Map.of("data", service.update(segmentId, request.name(), request.description(), request.rules()));
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody JsonNode rules) {
        return Map.of("data", service.preview(rules));
    }

    public record SegmentWrite(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
                               JsonNode rules) {
    }
}
