package com.example.marketingplatform.engagement.application;

import com.example.marketingplatform.audience.application.AudienceSnapshotQuery;
import com.example.marketingplatform.campaign.application.CampaignRepository;
import com.example.marketingplatform.coupon.application.CouponQuery;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.member.application.MemberProfileRepository;
import com.example.marketingplatform.message.application.Phase7Service;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CampaignExperienceService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final CampaignRepository campaigns;
    private final CouponQuery couponQuery;
    private final AudienceSnapshotQuery audienceSnapshot;
    private final MemberProfileRepository members;
    private final Phase7Service messaging;

    public CampaignExperienceService(NamedParameterJdbcTemplate jdbc, Clock clock, CampaignRepository campaigns,
                                     CouponQuery couponQuery, AudienceSnapshotQuery audienceSnapshot,
                                     MemberProfileRepository members, Phase7Service messaging) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.campaigns = campaigns;
        this.couponQuery = couponQuery;
        this.audienceSnapshot = audienceSnapshot;
        this.members = members;
        this.messaging = messaging;
    }

    @Transactional(readOnly = true)
    public LandingCampaign landing(UUID campaignId, UserPrincipal principal) {
        CampaignRepository.CampaignRow campaign = publishedCampaign(campaignId);
        Instant now = clock.instant();
        String status = !now.isBefore(campaign.endsAt()) ? "ENDED"
                : ("PAUSED".equals(campaign.status()) ? "PAUSED" : "ACTIVE");
        MemberState member = memberState(campaignId, principal);
        boolean claimable = "ACTIVE".equals(status) && !now.isBefore(campaign.startsAt());
        return new LandingCampaign(campaign.id(), campaign.name(), campaign.description(), status,
                campaign.startsAt(), campaign.endsAt(), new LandingPage(campaign.headline(), campaign.body()),
                claimable, member);
    }

    @Transactional(readOnly = true)
    public CouponPage coupons(UUID campaignId, UserPrincipal principal, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100.");
        CampaignRepository.CampaignRow campaign = publishedCampaign(campaignId);
        Instant now = clock.instant();
        UUID memberId = memberId(principal);
        boolean eligible = memberId != null && audienceSnapshot.containsMember(campaignId, memberId);
        boolean campaignActive = "ACTIVE".equals(campaign.status())
                && !now.isBefore(campaign.startsAt()) && now.isBefore(campaign.endsAt());
        List<CouponSummary> data = couponQuery.findPublicCoupons(campaignId, memberId, (long) page * size, size).stream()
                .map(coupon -> mapCoupon(coupon, memberId, eligible, campaignActive, now)).toList();
        MemberState member = memberId == null ? null : new MemberState(eligible);
        long total = couponQuery.countPublicCoupons(campaignId);
        return new CouponPage(data, member, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional
    public void recordEvent(UUID campaignId, UserPrincipal principal, EventWrite input) {
        if (input == null || input.eventId() == null || input.eventType() == null || input.occurredAt() == null)
            throw new IllegalArgumentException("eventId, eventType and occurredAt are required.");
        UUID memberId = memberId(principal);
        if (memberId == null) throw new IllegalArgumentException("An authenticated MEMBER is required.");
        if (!List.of("IMPRESSION", "CLICK", "LANDING_PAGE_VIEW").contains(input.eventType()))
            throw new IllegalArgumentException("eventType must be IMPRESSION, CLICK or LANDING_PAGE_VIEW.");
        Instant occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(input.occurredAt()).toInstant();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("occurredAt must be an ISO-8601 date-time with an offset.");
        }
        publishedCampaign(campaignId);
        UUID campaignMessageId = null;
        if ("IMPRESSION".equals(input.eventType()) || "CLICK".equals(input.eventType())) {
            if (input.messageId() == null)
                throw new IllegalArgumentException("messageId is required for IMPRESSION and CLICK.");
            campaignMessageId = messaging.findCampaignMessageIdForMemberInboxRow(campaignId, input.messageId(), memberId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "messageId must identify a delivered inbox message belonging to the current member and campaign."));
        } else if (input.messageId() != null) {
            throw new IllegalArgumentException("messageId must be omitted for LANDING_PAGE_VIEW.");
        }
        jdbc.update("""
                INSERT INTO campaign_events(id,event_id,campaign_id,member_id,campaign_message_id,campaign_coupon_id,event_type,occurred_at,received_at,metadata)
                VALUES(:id,:eventId,:campaign,:member,:message,NULL,:eventType,:occurred,:received,NULL)
                ON CONFLICT (member_id,event_id) DO NOTHING
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("eventId", input.eventId())
                .addValue("campaign", campaignId).addValue("member", memberId).addValue("message", campaignMessageId)
                .addValue("eventType", input.eventType()).addValue("occurred", Timestamp.from(occurredAt))
                .addValue("received", Timestamp.from(clock.instant())));
    }

    private CampaignRepository.CampaignRow publishedCampaign(UUID campaignId) {
        CampaignRepository.CampaignRow campaign = campaigns.findById(campaignId).orElseThrow(ResourceNotFound::new);
        if (campaign.publishedAt() == null) throw new ResourceNotFound();
        return campaign;
    }

    private UUID memberId(UserPrincipal principal) {
        if (principal == null || !"MEMBER".equals(principal.role())) return null;
        return members.findByUserId(principal.userId()).map(MemberProfileRepository.MemberRecord::memberId)
                .orElseThrow(ResourceNotFound::new);
    }

    private MemberState memberState(UUID campaignId, UserPrincipal principal) {
        UUID member = memberId(principal);
        return member == null ? null : new MemberState(audienceSnapshot.containsMember(campaignId, member));
    }

    private CouponSummary mapCoupon(CouponQuery.PublicCouponRow coupon, UUID memberId, boolean eligible,
                                    boolean campaignActive, Instant now) {
        String reason = null;
        String state;
        if (coupon.alreadyClaimed()) state = "ALREADY_CLAIMED";
        else if (!campaignActive) {
            state = "UNAVAILABLE";
            reason = "CAMPAIGN_INACTIVE";
        } else if (now.isBefore(coupon.startsAt())) {
            state = "UNAVAILABLE";
            reason = "COUPON_NOT_STARTED";
        } else if (!now.isBefore(coupon.endsAt())) {
            state = "UNAVAILABLE";
            reason = "COUPON_EXPIRED";
        } else if (coupon.claimedQuantity() >= coupon.totalQuantity()) {
            state = "UNAVAILABLE";
            reason = "COUPON_SOLD_OUT";
        } else if (memberId != null && !eligible) {
            state = "NOT_ELIGIBLE";
            reason = "MEMBER_NOT_ELIGIBLE";
        } else state = "AVAILABLE";
        return new CouponSummary(coupon.id(), coupon.name(), coupon.description(), coupon.discountType(),
                coupon.discountValue(), coupon.minimumOrderAmount(), coupon.totalQuantity(), coupon.claimedQuantity(),
                Math.max(0, coupon.totalQuantity() - coupon.claimedQuantity()), coupon.startsAt(), coupon.endsAt(), state, reason);
    }

    public record LandingPage(String headline, String body) {
    }

    public record MemberState(boolean eligible) {
    }

    public record LandingCampaign(UUID id, String name, String description, String status, Instant startsAt,
                                  Instant endsAt,
                                  LandingPage landingPage, boolean claimable, MemberState memberState) {
    }

    public record CouponSummary(UUID id, String name, String description, String discountType, BigDecimal discountValue,
                                BigDecimal minimumOrderAmount, int totalQuantity, int claimedQuantity,
                                int remainingQuantity,
                                Instant startsAt, Instant endsAt, String memberStatus, String unavailableReason) {
    }

    public record CouponPage(List<CouponSummary> data, MemberState memberState, int number, int size,
                             long totalElements, int totalPages) {
    }

    public record EventWrite(UUID eventId, String eventType, UUID messageId, String occurredAt) {
    }

    public static class ResourceNotFound extends RuntimeException {
    }
}
