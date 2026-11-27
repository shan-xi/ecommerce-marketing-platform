package com.example.marketingplatform.member.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for resolving a member profile from its authenticated user ID.
 */
public interface MemberProfileRepository {
    Optional<MemberRecord> findByUserId(UUID userId);

    record MemberRecord(UUID memberId, String displayName, LocalDate birthDate, String gender,
                        Instant registeredAt, String membershipTier, int orderCount,
                        BigDecimal totalSpending, Instant lastOrderAt, Instant lastLoginAt) {
    }
}
