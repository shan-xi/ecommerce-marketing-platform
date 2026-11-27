package com.example.marketingplatform.member.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class MemberProfileService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private final MemberProfileRepository repository;
    private final Clock clock;

    public MemberProfileService(MemberProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Profile getByUserId(UUID userId) {
        MemberProfileRepository.MemberRecord member = repository.findByUserId(userId)
                .orElseThrow(MemberProfileNotFoundException::new);
        Integer age = member.birthDate() == null ? null
                : Period.between(member.birthDate(), LocalDate.now(clock.withZone(BUSINESS_ZONE))).getYears();
        return new Profile(member.memberId(), member.displayName(), member.birthDate(), age, member.gender(),
                member.registeredAt(), member.membershipTier(), member.orderCount(), member.totalSpending(),
                member.lastOrderAt(), member.lastLoginAt());
    }

    public record Profile(UUID memberId, String displayName, LocalDate birthDate, Integer age, String gender,
                          java.time.Instant registeredAt, String membershipTier, int orderCount,
                          java.math.BigDecimal totalSpending, java.time.Instant lastOrderAt,
                          java.time.Instant lastLoginAt) {
    }

    public static class MemberProfileNotFoundException extends RuntimeException {
    }
}
