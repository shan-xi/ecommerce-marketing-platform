package com.example.marketingplatform.coupon.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application boundary for public coupon summaries and the current member's claim state.
 */
public interface CouponQuery {
    List<PublicCouponRow> findPublicCoupons(UUID campaignId, UUID memberId, long offset, int limit);

    long countPublicCoupons(UUID campaignId);

    record PublicCouponRow(UUID id, String name, String description, String discountType,
                           BigDecimal discountValue, BigDecimal minimumOrderAmount,
                           int totalQuantity, int claimedQuantity, Instant startsAt, Instant endsAt,
                           boolean alreadyClaimed) {
    }
}
