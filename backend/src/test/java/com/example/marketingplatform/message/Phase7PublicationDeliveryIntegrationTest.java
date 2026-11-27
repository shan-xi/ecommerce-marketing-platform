package com.example.marketingplatform.message;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.campaign.application.CampaignRepository;
import com.example.marketingplatform.message.application.Phase7Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Phase7PublicationDeliveryIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired Phase7Service phase7;
    @Autowired CampaignRepository campaigns;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void publishesImmutableAudienceAndDeliversInboxMessagesAsynchronously() throws Exception {
        UUID campaignId=createCampaign("[\"GOLD\",\"SILVER\"]");
        createCoupon(campaignId);
        var message=phase7.saveMessage(campaignId,new Phase7Service.MessageWrite("Phase 7 integration","Delivery body"));
        assertEquals(campaignId,message.campaignId());
        var published=phase7.publish(campaignId);
        assertEquals("ACTIVE",published.status());
        assertEquals(2,published.audienceCount());
        assertTrue(java.util.Set.of("PENDING", "PROCESSING", "COMPLETED").contains(published.deliveryStatus()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM campaign_audience_members WHERE campaign_id=:id",Map.of("id",campaignId),Integer.class));

        long deadline=System.currentTimeMillis()+10_000;
        Phase7Service.DeliveryStatus status;
        do {
            Thread.sleep(100);
            status=phase7.deliveryStatus(campaignId);
        } while (!"COMPLETED".equals(status.status()) && System.currentTimeMillis()<deadline);
        assertEquals("COMPLETED",status.status());
        assertEquals(2,status.totalCount());
        assertEquals(2,status.processedCount());

        var memberInbox=phase7.memberInbox(UUID.fromString("10000000-0000-4000-8000-000000000002"),0,20);
        assertTrue(memberInbox.data().stream().anyMatch(row->row.campaignId().equals(campaignId)));
        UUID deliveredId=memberInbox.data().stream().filter(row->row.campaignId().equals(campaignId)).findFirst().orElseThrow().id();
        assertEquals(message.title(),phase7.memberMessage(UUID.fromString("10000000-0000-4000-8000-000000000002"),deliveredId).title());
        assertThrows(Phase7Service.NotFound.class,()->phase7.memberMessage(UUID.fromString("10000000-0000-4000-8000-000000000004"),deliveredId));

        phase7.pause(campaignId);
        assertEquals("PAUSED",campaigns.findById(campaignId).orElseThrow().status());
        assertEquals("COMPLETED",phase7.deliveryStatus(campaignId).status());
        assertThrows(Phase7Service.Conflict.class, () -> phase7.saveMessage(campaignId,
                new Phase7Service.MessageWrite("Too late", "Published messages are immutable.")));
    }

    @Test
    void publicationWithoutMessageRollsBackWithoutSnapshotOrDeliveryJob() {
        UUID campaignId=createCampaign("[\"GOLD\"]");
        createCoupon(campaignId);
        assertThrows(Phase7Service.Conflict.class,()->phase7.publish(campaignId));
        assertEquals("DRAFT",campaigns.findById(campaignId).orElseThrow().status());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM campaign_audience_members WHERE campaign_id=:id",Map.of("id",campaignId),Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM campaign_delivery_jobs WHERE campaign_id=:id",Map.of("id",campaignId),Integer.class));
    }

    @Test
    void repeatedPublicationCannotCreateAnotherSnapshotOrJob() {
        UUID campaignId=createCampaign("[\"GOLD\"]");
        createCoupon(campaignId);
        phase7.saveMessage(campaignId,new Phase7Service.MessageWrite("One-time publish","Message body"));
        phase7.publish(campaignId);
        assertThrows(Phase7Service.Conflict.class,()->phase7.publish(campaignId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM campaign_delivery_jobs WHERE campaign_id=:id",Map.of("id",campaignId),Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM campaign_audience_members WHERE campaign_id=:id",Map.of("id",campaignId),Integer.class));
    }

    @Test
    void zeroAudiencePublicationCompletesWithoutInboxRows() {
        UUID campaignId=createCampaign("[\"PLATINUM\"]");
        createCoupon(campaignId);
        phase7.saveMessage(campaignId,new Phase7Service.MessageWrite("No audience","No inbox rows"));
        var result=phase7.publish(campaignId);
        assertEquals(0,result.audienceCount());
        assertEquals("COMPLETED",result.deliveryStatus());
        assertEquals("NO_AUDIENCE_MEMBERS",result.warningCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM member_messages WHERE campaign_id=:id",Map.of("id",campaignId),Integer.class));
    }

    private UUID createCampaign(String tiersJson) {
        UUID id=UUID.randomUUID();
        Instant now=Instant.now();
        String rules="{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"membershipTier\",\"operator\":\"IN\",\"value\":"+tiersJson+"}]}";
        campaigns.create(new CampaignRepository.CampaignRow(id,"Phase 7 integration",null,"Headline","Landing body","DRAFT",
                now.minusSeconds(60),now.plusSeconds(3600),null,rules,null,UUID.fromString("10000000-0000-4000-8000-000000000001"),now,now));
        return id;
    }
    private void createCoupon(UUID campaignId) {
        Instant now=Instant.now();
        jdbc.update("""
                INSERT INTO campaign_coupons(id,campaign_id,name,description,discount_type,discount_value,minimum_order_amount,total_quantity,claimed_quantity,starts_at,ends_at,created_at,updated_at)
                VALUES(:id,:campaign,'Phase 7 Coupon','Integration fixture','FIXED_AMOUNT',:value,:minimum,20,0,:starts,:ends,:now,:now)
                """,Map.of("id",UUID.randomUUID(),"campaign",campaignId,"value",new BigDecimal("50.00"),"minimum",new BigDecimal("100.00"),
                "starts",Timestamp.from(now.minusSeconds(60)),"ends",Timestamp.from(now.plusSeconds(3600)),"now",Timestamp.from(now)));
    }
}
