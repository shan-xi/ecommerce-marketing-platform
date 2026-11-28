# Phase 8 Implementation Report — Member Landing Page, Coupon Claim Experience, and Engagement

**Status:** COMPLETE — FORMALLY OWNER-ACCEPTED (2026-11-28). **Implementation date:** 2026-11-27. **Owner acceptance date:** 2026-11-28. **Baseline:**
Owner-authorized `PHASE_8_PLAN.md`, approved v1.5.0 Design Pack, and formally accepted Phases 1–7. **Database
migration:** None. The existing V1 schema and constraints are reused; no applied migration was edited.

## 1. Delivered scope

### Public campaign landing and coupon reads

- Added optional-authentication `GET /api/v1/campaigns/{campaignId}` for published campaigns only.
- Added optional-authentication `GET /api/v1/campaigns/{campaignId}/coupons` with pagination.
- Public response fields are minimized. Anonymous and ADMIN callers do not receive member-specific state.
- Published PAUSED and effectively ENDED campaigns remain publicly readable; claimability is false and coupon statuses
  are unavailable for new claims.
- MEMBER eligibility is resolved from the immutable `campaign_audience_members` snapshot. Coupon claim state comes from
  `coupon_claims`; inventory comes from `campaign_coupons`.
- Reads do not reserve inventory or mutate claims.

### Claim and authentication flow

- The landing page reuses the accepted Phase 6 claim endpoint; no alternate claim operation or browser-side inventory
  logic was added.
- The frontend retains an idempotency key in component state and per-tab `sessionStorage` for retries of one logical
  claim command, including a lost response followed by page reload, and disables concurrent submissions.
- Anonymous claim actions navigate to the existing sign-in UI with an internal campaign return path. The path is
  validated against a strict `/campaigns/{uuid}` pattern; no automatic claim is submitted after login.

### Engagement

- Added `POST /api/v1/campaigns/{campaignId}/events` for authenticated MEMBER events `IMPRESSION`, `CLICK`, and
  `LANDING_PAGE_VIEW`.
- Member identity is derived from `UserPrincipal`; event IDs are deduplicated by the existing database unique
  constraint.
- `IMPRESSION` and `CLICK` require a member inbox row ID from `/member/messages`; the service verifies that row belongs
  to the current member and campaign, then stores its campaign-wide message ID to satisfy existing foreign keys.
- `LANDING_PAGE_VIEW` rejects `messageId`; anonymous views create no event row.
- Client-supplied `CLAIM_ATTEMPT` and other unsupported event types are rejected. Existing Phase 6 claim audit and Phase
  7 delivery records remain authoritative.
- Message impressions are sent after at least 50% of an inbox message card is visible; clicks are sent on deliberate
  campaign navigation. Telemetry failures do not block page navigation or coupon claims.

### Frontend

- Added `/campaigns/:campaignId` public fixed-template route.
- Added public campaign details, coupon summaries, eligibility and claim states, claim result/error handling, and
  responsive presentation.
- Added minimal MEMBER inbox route `/member/messages`, sourced from existing Phase 7 inbox API.
- Added role-aware navigation and safe login return behavior.

## 2. Files and contract changes

- New backend package: `engagement.application.CampaignExperienceService` and
  `engagement.presentation.CampaignExperienceController`.
- Cross-module reads use application boundaries: `CampaignRepository`, `CouponQuery`, `AudienceSnapshotQuery`,
  `MemberProfileRepository`, and the Phase 7 `Phase7Service` delivered-inbox lookup. Coupon/audience persistence
  adapters remain inside their owning modules; engagement writes only its own event table.
- New frontend feature: `features/engagement/CampaignLandingPage.tsx` and `MemberInbox.tsx`.
- New frontend API client: `shared/lib/engagementApi.ts`.
- Added PostgreSQL integration coverage: `CampaignExperienceIntegrationTest`.
- Added API suite: `phase8-member-experience-verification.zsh`.
- Expanded OpenAPI response schemas for the already-approved public read endpoints and clarified that event `messageId`
  is the member inbox row ID. No endpoint path, database schema, Phase 6 claim semantics, or Phase 7 delivery behavior
  was changed.

## 3.1 Coupon claim JDBC timestamp binding correction

- Corrected the `coupon_claims.claimed_at` INSERT parameter to bind `Timestamp.from(now)` rather than passing
  `java.time.Instant` directly to PostgreSQL JDBC. The inventory reservation and claim insert remain in the same
  transaction, so an insert failure rolls back the reservation.
- Added a PostgreSQL integration regression test covering a successful MEMBER claim, same-key idempotent replay, exactly
  one claim row, exactly one inventory reservation, and the two server-generated claim audit events.
- Reviewed backend named-JDBC parameter bindings for direct `Instant` values. The claim INSERT was the remaining direct
  `Instant` binding found; other timestamp writes use `Timestamp`.

## 3. Verification status and owner acceptance

**Owner-reported verification (2026-11-28):**

- `./mvnw -f backend/pom.xml clean verify` — PASS.
- `./mvnw -f backend/pom.xml spring-boot:run` — PASS.
- `npm run dev` — PASS.
- `zsh scripts/api-tests/suites/phase8-member-experience-verification.zsh all` — PASS (owner reported all checks pass).
- Browser/UI flow — PASS; the owner confirmed a MEMBER can operate the Phase 8 UI and successfully claim a coupon.

The owner explicitly approved the Phase 8 changes on 2026-11-28. Phase 8 is therefore **FORMALLY ACCEPTED** and closed.

**Verification evidence boundary:** these results are recorded as reported by the owner. The assistant did not execute
these commands in the owner's environment. The owner did not separately report `npm test`, `npm run typecheck`,
`npm run build`, or the Phase 1–7 API regression suites in this acceptance message; this report does not mark those
individual commands as independently verified. The successful Maven `clean verify` is recorded as the owner's backend
build/test evidence. The original implementation-environment limitations below remain historical context, not the
current acceptance status.

Historical implementation-environment checks: frontend native Node test suite **16 PASS / 0 FAIL**; OpenAPI YAML
parsing and response-schema reference checks PASS; TypeScript/TSX syntax parsing **17 files / 0 syntax diagnostics**;
Java syntax compilation against minimal API stubs PASS. Stub compilation was a syntax sanity check only, not a
substitute for Maven or Testcontainers execution; the owner subsequently supplied the Maven verification result above.

## 4. Operational constraints

- Preserve the Phase 7 single-backend-instance deployment restriction.
- Do not add or edit Flyway V1/V2 migrations for Phase 8.
- Phase 9 analytics was out of scope for Phase 8. Phase 8 is now accepted; Phase 9 planning is recorded in `PHASE_9_PLAN.md` and implementation remains subject to owner approval.
