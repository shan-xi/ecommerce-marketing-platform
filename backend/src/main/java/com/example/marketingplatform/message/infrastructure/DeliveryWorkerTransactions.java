package com.example.marketingplatform.message.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeliveryWorkerTransactions {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    public DeliveryWorkerTransactions(NamedParameterJdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional
    public void recoverInterruptedJobs() {
        jdbc.update("UPDATE campaign_delivery_jobs SET status='PENDING',updated_at=:now WHERE status='PROCESSING'", Map.of("now",Timestamp.from(clock.instant())));
    }

    @Transactional
    public UUID claimNextJob() {
        List<JobCandidate> candidates = jdbc.query("""
                SELECT id,attempt_count
                FROM campaign_delivery_jobs
                WHERE status='PENDING'
                  AND (
                    attempt_count=0
                    OR updated_at + CASE attempt_count
                        WHEN 1 THEN INTERVAL '1 second'
                        WHEN 2 THEN INTERVAL '2 seconds'
                        ELSE INTERVAL '4 seconds'
                    END <= :now
                  )
                ORDER BY created_at,id
                LIMIT 1
                FOR UPDATE
                """, Map.of("now",Timestamp.from(clock.instant())), (r,n)->new JobCandidate(r.getObject("id",UUID.class),r.getInt("attempt_count")));
        if(candidates.isEmpty()) return null;
        UUID id=candidates.getFirst().id();
        int updated=jdbc.update("UPDATE campaign_delivery_jobs SET status='PROCESSING',updated_at=:now WHERE id=:id AND status='PENDING'",
                Map.of("id",id,"now",Timestamp.from(clock.instant())));
        return updated==1?id:null;
    }

    @Transactional
    public void processBatch(UUID jobId) {
        List<Job> jobs=jdbc.query("SELECT campaign_id,cursor_member_id FROM campaign_delivery_jobs WHERE id=:id AND status='PROCESSING'",Map.of("id",jobId),
                (r,n)->new Job(r.getObject("campaign_id",UUID.class),r.getObject("cursor_member_id",UUID.class)));
        if(jobs.isEmpty()) return;
        Job job=jobs.getFirst();
        UUID messageId=jdbc.query("SELECT id FROM campaign_messages WHERE campaign_id=:campaign",Map.of("campaign",job.campaignId()),(r,n)->r.getObject(1,UUID.class)).stream().findFirst().orElseThrow();
        MapSqlParameterSource params=new MapSqlParameterSource().addValue("campaign",job.campaignId()).addValue("cursor",job.cursor()).addValue("limit",100);
        List<UUID> memberIds=jdbc.query("SELECT member_id FROM campaign_audience_members WHERE campaign_id=:campaign AND (CAST(:cursor AS uuid) IS NULL OR member_id>CAST(:cursor AS uuid)) ORDER BY member_id LIMIT :limit",params,(r,n)->r.getObject(1,UUID.class));
        if(memberIds.isEmpty()) {
            jdbc.update("UPDATE campaign_delivery_jobs SET status='COMPLETED',attempt_count=0,last_error=NULL,updated_at=:now WHERE id=:id",Map.of("id",jobId,"now",Timestamp.from(clock.instant())));
            return;
        }
        Timestamp now=Timestamp.from(clock.instant());
        var batch=memberIds.stream().map(member -> new MapSqlParameterSource().addValue("id",UUID.randomUUID()).addValue("message",messageId)
                .addValue("campaign",job.campaignId()).addValue("member",member).addValue("now",now)).toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO member_messages(id,campaign_message_id,campaign_id,member_id,delivered_at,created_at) VALUES(:id,:message,:campaign,:member,:now,:now) ON CONFLICT (campaign_id,member_id) DO NOTHING",batch);
        UUID cursor=memberIds.getLast();
        jdbc.update("UPDATE campaign_delivery_jobs SET status='PENDING',cursor_member_id=:cursor,attempt_count=0,last_error=NULL,updated_at=:now WHERE id=:id",Map.of("id",jobId,"cursor",cursor,"now",now));
    }

    @Transactional
    public void recordFailure(UUID jobId) {
        List<Integer> attempts=jdbc.query("SELECT attempt_count FROM campaign_delivery_jobs WHERE id=:id AND status='PROCESSING'",Map.of("id",jobId),(r,n)->r.getInt(1));
        if(attempts.isEmpty()) return;
        int consumed=attempts.getFirst();
        if(consumed>=3) jdbc.update("UPDATE campaign_delivery_jobs SET status='FAILED',last_error='Delivery failed after automatic retries.',updated_at=:now WHERE id=:id",Map.of("id",jobId,"now",Timestamp.from(clock.instant())));
        else jdbc.update("UPDATE campaign_delivery_jobs SET status='PENDING',attempt_count=:attempt,last_error='Delivery batch failed; automatic retry scheduled.',updated_at=:now WHERE id=:id",Map.of("id",jobId,"attempt",consumed+1,"now",Timestamp.from(clock.instant())));
    }
    private record JobCandidate(UUID id,int attempts) {}
    private record Job(UUID campaignId,UUID cursor) {}
}
