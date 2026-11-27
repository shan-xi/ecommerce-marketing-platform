package com.example.marketingplatform.coupon.presentation;

import com.example.marketingplatform.coupon.application.CouponService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class CouponController {
    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/admin/campaigns/{campaignId}/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID campaignId, @RequestBody CouponService.CouponWrite input) {
        return Map.of("data", service.create(campaignId, input));
    }

    @GetMapping("/api/v1/admin/campaigns/{campaignId}/coupons")
    public Map<String, Object> list(@PathVariable UUID campaignId) {
        return Map.of("data", service.list(campaignId));
    }

    @PutMapping("/api/v1/admin/campaigns/{campaignId}/coupons/{couponId}")
    public Map<String, Object> update(@PathVariable UUID campaignId, @PathVariable UUID couponId, @RequestBody CouponService.CouponWrite input) {
        return Map.of("data", service.update(campaignId, couponId, input));
    }

    @PostMapping("/api/v1/campaigns/{campaignId}/coupons/{couponId}/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> claim(@PathVariable UUID campaignId, @PathVariable UUID couponId, @AuthenticationPrincipal UserPrincipal principal, @RequestBody ClaimRequest input) {
        return Map.of("data", service.claim(campaignId, couponId, principal.userId(), input == null ? null : input.idempotencyKey()));
    }

    @GetMapping("/api/v1/member/coupons")
    public Map<String, Object> memberClaims(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("data", service.memberClaims(principal.userId()));
    }

    public record ClaimRequest(UUID idempotencyKey) {
    }
}
