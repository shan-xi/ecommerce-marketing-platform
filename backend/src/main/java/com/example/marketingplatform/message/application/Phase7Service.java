package com.example.marketingplatform.message.application;

import com.example.marketingplatform.audience.application.AudienceUseCase;
import com.example.marketingplatform.campaign.application.CampaignRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class Phase7Service {
    private final NamedParameterJdbcTemplate jdbc;
    private final CampaignRepository campaigns;
    private final AudienceUseCase audience;
    private final JsonMapper mapper;
    private final Clock clock;

    public Phase7Service(NamedParameterJdbcTemplate jdbc, CampaignRepository campaigns, AudienceUseCase audience,
                         JsonMapper mapper, Clock clock) {
        this.jdbc = jdbc; this.campaigns = campaigns; this.audience = audience; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public CampaignMessage saveMessage(UUID campaignId, MessageWrite input) {
        validateMessage(input);
        lockCampaign(campaignId);
        var campaign = campaigns.findById(campaignId).orElseThrow(NotFound::new);
        if (!"DRAFT".equals(campaign.status())) throw new Conflict("Only DRAFT campaigns can configure a message.", "CAMPAIGN_CONFLICT");
        Instant now = clock.instant();
        UUID existing = jdbc.query("SELECT id FROM campaign_messages WHERE campaign_id=:campaign", Map.of("campaign", campaignId), (r,n) -> r.getObject(1, UUID.class)).stream().findFirst().orElse(null);
        UUID id = existing == null ? UUID.randomUUID() : existing;
        if (existing == null) jdbc.update("INSERT INTO campaign_messages(id,campaign_id,title,body,created_at,updated_at) VALUES(:id,:campaign,:title,:body,:now,:now)",
                Map.of("id", id, "campaign", campaignId, "title", input.title().trim(), "body", input.body().trim(), "now", Timestamp.from(now)));
        else jdbc.update("UPDATE campaign_messages SET title=:title,body=:body,updated_at=:now WHERE id=:id AND campaign_id=:campaign",
                Map.of("id", id, "campaign", campaignId, "title", input.title().trim(), "body", input.body().trim(), "now", Timestamp.from(now)));
        return getMessage(campaignId);
    }

    @Transactional(readOnly = true)
    public CampaignMessage getMessage(UUID campaignId) {
        List<CampaignMessage> rows = jdbc.query("SELECT id,campaign_id,title,body,created_at,updated_at FROM campaign_messages WHERE campaign_id=:campaign",
                Map.of("campaign", campaignId), (r,n) -> new CampaignMessage(r.getObject("id", UUID.class), r.getObject("campaign_id", UUID.class), r.getString("title"), r.getString("body"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant()));
        if (rows.isEmpty()) { if (campaigns.findById(campaignId).isEmpty()) throw new NotFound(); throw new NotFound(); }
        return rows.getFirst();
    }

    @Transactional
    public PublishResult publish(UUID campaignId) {
        lockCampaign(campaignId);
        var campaign = campaigns.findById(campaignId).orElseThrow(NotFound::new);
        Instant now = clock.instant();
        if (!"DRAFT".equals(campaign.status()) || now.isBefore(campaign.startsAt()) || !now.isBefore(campaign.endsAt()))
            throw new Conflict("Campaign must be DRAFT and within its active time window to publish.", "CAMPAIGN_NOT_PUBLISHABLE");
        UUID messageId = jdbc.query("SELECT id FROM campaign_messages WHERE campaign_id=:campaign", Map.of("campaign", campaignId), (r,n) -> r.getObject(1, UUID.class)).stream().findFirst().orElse(null);
        Integer coupons = jdbc.queryForObject("SELECT count(*) FROM campaign_coupons WHERE campaign_id=:campaign AND total_quantity>=1", Map.of("campaign", campaignId), Integer.class);
        if (messageId == null || coupons == null || coupons < 1)
            throw new Conflict("Campaign requires exactly one message and at least one coupon with positive quantity.", "CAMPAIGN_NOT_PUBLISHABLE");
        JsonNode rules;
        try { rules = campaign.rulesJson() == null ? null : mapper.readTree(campaign.rulesJson()); }
        catch (Exception ex) { throw new IllegalStateException("Stored audience rules are unreadable.", ex); }
        List<UUID> members = audience.resolveMemberIds(campaign.segmentId(), rules);
        var snapshotRows = members.stream().map(memberId -> new MapSqlParameterSource()
                .addValue("campaign", campaignId).addValue("member", memberId)
                .addValue("at", Timestamp.from(now)).addValue("segment", campaign.segmentId()))
                .toArray(MapSqlParameterSource[]::new);
        if (snapshotRows.length > 0) jdbc.batchUpdate(
                "INSERT INTO campaign_audience_members(campaign_id,member_id,snapshot_at,source_segment_id) VALUES(:campaign,:member,:at,:segment)", snapshotRows);
        jdbc.update("UPDATE campaigns SET status='ACTIVE',published_at=:now,updated_at=:now WHERE id=:campaign AND status='DRAFT'",
                Map.of("campaign", campaignId, "now", Timestamp.from(now)));
        String jobStatus = members.isEmpty() ? "COMPLETED" : "PENDING";
        jdbc.update("INSERT INTO campaign_delivery_jobs(id,campaign_id,status,cursor_member_id,attempt_count,last_error,created_at,updated_at) VALUES(:id,:campaign,:status,NULL,0,NULL,:now,:now)",
                Map.of("id", UUID.randomUUID(), "campaign", campaignId, "status", jobStatus, "now", Timestamp.from(now)));
        return new PublishResult(campaignId, "ACTIVE", members.size(), jobStatus, members.isEmpty() ? "NO_AUDIENCE_MEMBERS" : null, now);
    }

    @Transactional
    public void pause(UUID campaignId) {
        lockCampaign(campaignId);
        var campaign = campaigns.findById(campaignId).orElseThrow(NotFound::new);
        Instant now = clock.instant();
        if (!"ACTIVE".equals(campaign.status()) || !now.isBefore(campaign.endsAt()))
            throw new Conflict("Only currently active campaigns can be paused.", "CAMPAIGN_CONFLICT");
        jdbc.update("UPDATE campaigns SET status='PAUSED',updated_at=:now WHERE id=:campaign AND status='ACTIVE'",
                Map.of("campaign", campaignId, "now", Timestamp.from(now)));
    }

    @Transactional(readOnly = true)
    public DeliveryStatus deliveryStatus(UUID campaignId) {
        if (campaigns.findById(campaignId).isEmpty()) throw new NotFound();
        List<DeliveryRow> rows = jdbc.query("SELECT id,status,cursor_member_id,attempt_count,last_error FROM campaign_delivery_jobs WHERE campaign_id=:campaign", Map.of("campaign", campaignId),
                (r,n) -> new DeliveryRow(r.getObject("id", UUID.class), r.getString("status"), r.getObject("cursor_member_id", UUID.class), r.getInt("attempt_count"), r.getString("last_error")));
        if (rows.isEmpty()) throw new NotFound();
        DeliveryRow job = rows.getFirst();
        Integer total = jdbc.queryForObject("SELECT count(*) FROM campaign_audience_members WHERE campaign_id=:campaign", Map.of("campaign", campaignId), Integer.class);
        Integer processed = jdbc.queryForObject("SELECT count(*) FROM member_messages WHERE campaign_id=:campaign", Map.of("campaign", campaignId), Integer.class);
        return new DeliveryStatus(job.status(), total == null ? 0 : total, processed == null ? 0 : processed, job.cursor(), job.attemptCount(), job.lastError(), "FAILED".equals(job.status()));
    }

    @Transactional
    public void retryDelivery(UUID campaignId) {
        int updated = jdbc.update("UPDATE campaign_delivery_jobs SET status='PENDING',attempt_count=0,last_error=NULL,updated_at=:now WHERE campaign_id=:campaign AND status='FAILED'",
                Map.of("campaign", campaignId, "now", Timestamp.from(clock.instant())));
        if (updated == 1) return;
        if (campaigns.findById(campaignId).isEmpty()) throw new NotFound();
        throw new Conflict("Only FAILED delivery jobs can be retried.", "DELIVERY_JOB_NOT_RETRYABLE");
    }

    @Transactional(readOnly = true)
    public InboxPage memberInbox(UUID userId, int page, int size) {
        validatePage(page, size);
        UUID memberId = memberId(userId);
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT count(*) FROM member_messages WHERE member_id=:member", Map.of("member", memberId), Long.class)).orElse(0L);
        List<InboxMessage> data = jdbc.query("""
                SELECT mm.id,mm.campaign_id,c.name campaign_name,cm.title,cm.body,mm.delivered_at
                FROM member_messages mm JOIN campaigns c ON c.id=mm.campaign_id JOIN campaign_messages cm ON cm.id=mm.campaign_message_id
                WHERE mm.member_id=:member ORDER BY mm.delivered_at DESC,mm.id DESC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource().addValue("member", memberId).addValue("limit", size).addValue("offset", (long)page*size), this::mapInbox);
        return new InboxPage(data, page, size, total, (int)Math.ceil((double)total/size));
    }

    @Transactional(readOnly = true)
    public InboxMessage memberMessage(UUID userId, UUID messageId) {
        UUID memberId = memberId(userId);
        return jdbc.query("""
                SELECT mm.id,mm.campaign_id,c.name campaign_name,cm.title,cm.body,mm.delivered_at
                FROM member_messages mm JOIN campaigns c ON c.id=mm.campaign_id JOIN campaign_messages cm ON cm.id=mm.campaign_message_id
                WHERE mm.member_id=:member AND mm.id=:id
                """, Map.of("member", memberId, "id", messageId), this::mapInbox).stream().findFirst().orElseThrow(NotFound::new);
    }

    private UUID memberId(UUID userId) {
        return jdbc.query("SELECT id FROM members WHERE user_id=:user", Map.of("user", userId), (r,n)->r.getObject(1,UUID.class)).stream().findFirst().orElseThrow(NotFound::new);
    }
    private InboxMessage mapInbox(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new InboxMessage(r.getObject("id",UUID.class),r.getObject("campaign_id",UUID.class),r.getString("campaign_name"),r.getString("title"),r.getString("body"),r.getTimestamp("delivered_at").toInstant());
    }
    private void lockCampaign(UUID campaignId) {
        List<UUID> ids = jdbc.query("SELECT id FROM campaigns WHERE id=:campaign FOR UPDATE", Map.of("campaign", campaignId), (r,n)->r.getObject(1,UUID.class));
        if (ids.isEmpty()) throw new NotFound();
    }
    private void validateMessage(MessageWrite input) {
        if (input == null || input.title() == null || input.title().isBlank() || input.title().length() > 160)
            throw new IllegalArgumentException("title is required and must be at most 160 characters.");
        if (input.body() == null || input.body().isBlank() || input.body().length() > 10000)
            throw new IllegalArgumentException("body is required and must be at most 10000 characters.");
    }
    private void validatePage(int page,int size) { if(page<0 || size<1 || size>100) throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100."); }

    public record MessageWrite(String title,String body) {}
    public record CampaignMessage(UUID id,UUID campaignId,String title,String body,Instant createdAt,Instant updatedAt) {}
    public record PublishResult(UUID campaignId,String status,int audienceCount,String deliveryStatus,String warningCode,Instant publishedAt) {}
    public record DeliveryStatus(String status,int totalCount,int processedCount,UUID cursorMemberId,int attemptCount,String lastError,boolean retryable) {}
    public record InboxMessage(UUID id,UUID campaignId,String campaignName,String title,String body,Instant deliveredAt) {}
    public record InboxPage(List<InboxMessage> data,int number,int size,long totalElements,int totalPages) {}
    private record DeliveryRow(UUID id,String status,UUID cursor,int attemptCount,String lastError) {}
    public static class NotFound extends RuntimeException {}
    public static class Conflict extends RuntimeException { public final String code; public Conflict(String message,String code){super(message);this.code=code;} }
}
