package com.example.marketingplatform.campaign;

import com.example.marketingplatform.audience.application.AudienceUseCase;
import com.example.marketingplatform.campaign.application.CampaignRepository;
import com.example.marketingplatform.campaign.application.CampaignService;
import com.example.marketingplatform.campaign.domain.CampaignStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampaignServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T07:00:00Z"), ZoneOffset.UTC);
    private final UUID adminId = UUID.randomUUID();
    private final UUID segmentId = UUID.randomUUID();
    private CampaignRepository repository;
    private AudienceUseCase audience;
    private CampaignService service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignRepository.class);
        audience = mock(AudienceUseCase.class);
        service = new CampaignService(repository, audience, JsonMapper.builder().build(), clock);
    }

    @Test
    void createPersistsDraftAndReturnsPersistedConfiguration() {
        CampaignService.CampaignWrite request = validWrite();
        UUID id = UUID.randomUUID();
        CampaignRepository.CampaignRow row = row(id, "DRAFT");
        when(repository.findById(any())).thenReturn(Optional.of(row));
        CampaignService.Campaign created = service.create(adminId, request);
        assertEquals("DRAFT", created.status());
        assertEquals("Welcome", created.landingPage().headline());
        assertEquals(segmentId, created.audience().segmentId());
        verify(audience).validateComposition(segmentId, null);
        verify(repository).create(any(CampaignRepository.CampaignRow.class));
    }

    @Test
    void createTreatsMissingCampaignRulesAsDisabledWhenSegmentIsSelected() {
        CampaignService.CampaignWrite base = validWrite();
        CampaignService.CampaignWrite request = new CampaignService.CampaignWrite(base.name(), base.description(),
                base.startsAt(), base.endsAt(), base.landingPage(),
                new CampaignService.AudienceWrite(segmentId, MissingNode.getInstance()));
        UUID id = UUID.randomUUID();
        when(repository.findById(any())).thenReturn(Optional.of(row(id, "DRAFT")));

        service.create(adminId, request);

        verify(audience).validateComposition(segmentId, null);
        verify(repository).create(argThat(saved -> saved.rulesJson() == null && segmentId.equals(saved.segmentId())));
    }

    @Test
    void createTreatsExplicitJsonNullCampaignRulesAsDisabledWhenSegmentIsSelected() {
        CampaignService.CampaignWrite base = validWrite();
        CampaignService.CampaignWrite request = new CampaignService.CampaignWrite(base.name(), base.description(),
                base.startsAt(), base.endsAt(), base.landingPage(),
                new CampaignService.AudienceWrite(segmentId, NullNode.getInstance()));
        UUID id = UUID.randomUUID();
        when(repository.findById(any())).thenReturn(Optional.of(row(id, "DRAFT")));

        service.create(adminId, request);

        verify(audience).validateComposition(segmentId, null);
        verify(repository).create(argThat(saved -> saved.rulesJson() == null && segmentId.equals(saved.segmentId())));
    }

    @Test
    void rejectsNonIncreasingTimeWindowBeforeAudienceEvaluation() {
        CampaignService.CampaignWrite valid = validWrite();
        CampaignService.CampaignWrite invalid = new CampaignService.CampaignWrite(valid.name(), valid.description(),
                valid.startsAt(), valid.startsAt(), valid.landingPage(), valid.audience());
        assertThrows(IllegalArgumentException.class, () -> service.create(adminId, invalid));
        verifyNoInteractions(audience);
        verify(repository, never()).create(any());
    }

    @Test
    void updateRejectsNonDraftCampaign() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(row(id, "ACTIVE")));
        assertThrows(CampaignService.CampaignConflictException.class, () -> service.update(id, validWrite()));
        verify(repository, never()).updateDraft(any());
    }


    @Test
    void effectiveEndedIsReadOnlyAndDraftDoesNotExpire() {
        Instant now = Instant.parse("2026-11-27T07:00:00Z");
        Instant past = now.minusSeconds(60);
        assertEquals(CampaignStatus.DRAFT, CampaignStatus.effectiveReadStatus("DRAFT", null, past, now));
        assertEquals(CampaignStatus.ENDED, CampaignStatus.effectiveReadStatus("ACTIVE", now.minusSeconds(3600), past, now));
        assertEquals(CampaignStatus.ENDED, CampaignStatus.effectiveReadStatus("PAUSED", now.minusSeconds(3600), past, now));
        assertEquals(CampaignStatus.PAUSED, CampaignStatus.effectiveReadStatus("PAUSED", now.minusSeconds(3600), now.plusSeconds(60), now));
    }

    private CampaignService.CampaignWrite validWrite() {
        return new CampaignService.CampaignWrite("  Welcome  ", null, "2026-12-01T00:00:00Z", "2026-12-31T00:00:00Z",
                new CampaignService.LandingPageWrite("Welcome", "Body"), new CampaignService.AudienceWrite(segmentId, null));
    }

    private CampaignRepository.CampaignRow row(UUID id, String status) {
        Instant created = Instant.parse("2026-11-27T07:00:00Z");
        return new CampaignRepository.CampaignRow(id, "Welcome", null, "Welcome", "Body", status,
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), segmentId,
                null, status.equals("DRAFT") ? null : created, adminId, created, created);
    }
}
