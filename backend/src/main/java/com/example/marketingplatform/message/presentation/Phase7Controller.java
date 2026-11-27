package com.example.marketingplatform.message.presentation;

import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.message.application.Phase7Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
public class Phase7Controller {
    private final Phase7Service service;
    public Phase7Controller(Phase7Service service) { this.service = service; }

    @PutMapping("/api/v1/admin/campaigns/{campaignId}/message")
    public Map<String,Object> saveMessage(@PathVariable UUID campaignId,@RequestBody Phase7Service.MessageWrite input) { return Map.of("data",service.saveMessage(campaignId,input)); }
    @GetMapping("/api/v1/admin/campaigns/{campaignId}/message")
    public Map<String,Object> getMessage(@PathVariable UUID campaignId) { return Map.of("data",service.getMessage(campaignId)); }
    @PostMapping("/api/v1/admin/campaigns/{campaignId}/publish")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> publish(@PathVariable UUID campaignId) { return Map.of("data",service.publish(campaignId)); }
    @PostMapping("/api/v1/admin/campaigns/{campaignId}/pause")
    public Map<String,Object> pause(@PathVariable UUID campaignId) { service.pause(campaignId); return Map.of("data",Map.of("campaignId",campaignId,"status","PAUSED")); }
    @GetMapping("/api/v1/admin/campaigns/{campaignId}/delivery-status")
    public Map<String,Object> deliveryStatus(@PathVariable UUID campaignId) { return Map.of("data",service.deliveryStatus(campaignId)); }
    @PostMapping("/api/v1/admin/campaigns/{campaignId}/delivery/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> retry(@PathVariable UUID campaignId) { service.retryDelivery(campaignId); return Map.of("data",Map.of("campaignId",campaignId,"status","PENDING")); }
    @GetMapping("/api/v1/member/messages")
    public Map<String,Object> inbox(@AuthenticationPrincipal UserPrincipal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var result=service.memberInbox(principal.userId(),page,size); return Map.of("data",result.data(),"page",Map.of("number",result.number(),"size",result.size(),"totalElements",result.totalElements(),"totalPages",result.totalPages()));
    }
    @GetMapping("/api/v1/member/messages/{messageId}")
    public Map<String,Object> message(@AuthenticationPrincipal UserPrincipal principal,@PathVariable UUID messageId) { return Map.of("data",service.memberMessage(principal.userId(),messageId)); }
}
