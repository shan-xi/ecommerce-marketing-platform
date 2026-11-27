package com.example.marketingplatform.campaign.presentation;

import com.example.marketingplatform.campaign.application.CampaignService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/campaigns")
public class CampaignController {
    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal UserPrincipal principal, @RequestBody CampaignService.CampaignWrite request) {
        return Map.of("data", service.create(principal.userId(), request));
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String status) {
        var result = service.list(page, size, status);
        return Map.of("data", result.data(), "page", Map.of("number", result.number(), "size", result.size(), "totalElements", result.totalElements(), "totalPages", result.totalPages()));
    }

    @GetMapping("/{campaignId}")
    public Map<String, Object> get(@PathVariable UUID campaignId) {
        return Map.of("data", service.get(campaignId));
    }

    @PutMapping("/{campaignId}")
    public Map<String, Object> update(@PathVariable UUID campaignId, @RequestBody CampaignService.CampaignWrite request) {
        return Map.of("data", service.update(campaignId, request));
    }

    @PostMapping("/{campaignId}/audience-preview")
    public Map<String, Object> preview(@PathVariable UUID campaignId) {
        return Map.of("data", service.preview(campaignId));
    }
}
