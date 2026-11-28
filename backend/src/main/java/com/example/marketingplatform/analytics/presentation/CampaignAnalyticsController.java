package com.example.marketingplatform.analytics.presentation;

import com.example.marketingplatform.analytics.application.CampaignAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/campaigns")
public class CampaignAnalyticsController {
    private final CampaignAnalyticsService service;

    public CampaignAnalyticsController(CampaignAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/{campaignId}/analytics")
    public Map<String, Object> get(@PathVariable UUID campaignId) {
        return Map.of("data", service.get(campaignId));
    }
}
