package com.example.marketingplatform.member.presentation;

import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.member.application.MemberProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/member")
public class MemberProfileController {
    private final MemberProfileService service;

    public MemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("data", service.getByUserId(principal.userId()));
    }
}
