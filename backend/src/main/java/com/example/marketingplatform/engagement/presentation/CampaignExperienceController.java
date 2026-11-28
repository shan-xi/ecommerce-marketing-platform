package com.example.marketingplatform.engagement.presentation;

import com.example.marketingplatform.engagement.application.CampaignExperienceService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class CampaignExperienceController {
    private final CampaignExperienceService service;

    public CampaignExperienceController(CampaignExperienceService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/campaigns/{campaignId}")
    public Map<String, Object> landing(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal) {
        var landing = service.landing(campaignId, principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", landing.id());
        data.put("name", landing.name());
        data.put("description", landing.description());
        data.put("status", landing.status());
        data.put("startsAt", landing.startsAt());
        data.put("endsAt", landing.endsAt());
        data.put("landingPage", landing.landingPage());
        data.put("claimable", landing.claimable());
        if (landing.memberState() != null) data.put("memberState", landing.memberState());
        return Map.of("data", data);
    }

    @GetMapping("/api/v1/campaigns/{campaignId}/coupons")
    public Map<String, Object> coupons(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.coupons(campaignId, principal, page, size);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.data());
        if (result.memberState() != null) response.put("memberState", result.memberState());
        response.put("page", Map.of("number", result.number(), "size", result.size(), "totalElements", result.totalElements(), "totalPages", result.totalPages()));
        return response;
    }

    @PostMapping("/api/v1/campaigns/{campaignId}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> event(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody CampaignExperienceService.EventWrite input) {
        service.recordEvent(campaignId, principal, input);
        return Map.of("data", Map.of("accepted", true));
    }
}
