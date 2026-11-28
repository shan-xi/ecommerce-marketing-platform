# Phase 8 Plan — Member Landing Page, Coupon Claim Experience, and Engagement

**Document status:** COMPLETE — FORMALLY OWNER-ACCEPTED (2026-11-28). **Planning baseline:**
Owner-approved v1.5.0 Design Pack and formally accepted Phases 1–7. **Preceding phase:** Phase 7 formally accepted by
the owner on 2026-11-27. **Implementation status:** IMPLEMENTED and owner-verified; see `PHASE_8_IMPLEMENTATION_REPORT.md` and
`PHASE_8_ACCEPTANCE.md` for the owner-reported verification and acceptance record.

## 1. Objective

Deliver the member-facing campaign experience on top of the existing campaign publication, audience snapshot, coupon
claim backend, and message delivery foundation. A visitor can read a published campaign landing page without signing in;
an authenticated member can see their eligibility and coupon claim state, claim an available coupon, and receive a clear
result. The Member Portal also records authenticated landing-page views and eligible message impression/click events
with idempotent event ingestion.

The landing page uses one fixed template driven by the existing `landing_page_headline` and `landing_page_body` fields.
No page builder, custom layouts, or campaign authoring expansion is included.

## 2. Authoritative source documents

This plan is subordinate to the following approved sources; it does not change their contracts:

1. `docs/PRD.md` — Sections 5.2, 6.6, 6.7, 8, and the corresponding acceptance scenarios.
2. `docs/api/API_SPECIFICATION.md` — Sections 10 and 11 for public campaign/coupon reads, claim, and event ingestion;
   existing Phase 6 claim semantics remain authoritative.
3. `docs/api/openapi.yaml` — existing campaign landing-page, coupon summary, claim, and event paths and schemas.
4. `docs/api/ERROR_CODES.md` — existing campaign, coupon, eligibility, authentication, idempotency, and event validation
   errors.
5. `docs/database/DATABASE_DESIGN.md` and `docs/database/ERD.md` — existing `campaigns`, `campaign_coupons`,
   `coupon_claims`, `campaign_audience_members`, `member_messages`, and `campaign_events` data model.
6. `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` — existing V1 constraints, event
   deduplication key, and event context constraints. Do not edit an applied migration.
7. `docs/architecture/ARCHITECTURE.md`, `docs/architecture/MODULE_DESIGN.md`, and
   `docs/architecture/SECURITY_DESIGN.md` — modular boundaries, public/member authorization, principal-derived identity,
   and frontend route behavior.
8. `docs/development/TESTING_STRATEGY.md` and `docs/development/IMPLEMENTATION_PLAN.md` — verification and phase gates.
9. `docs/development/PHASE_6_IMPLEMENTATION_REPORT.md` and `docs/development/PHASE_7_IMPLEMENTATION_REPORT.md` —
   accepted claim backend, publication snapshot, delivery, and inbox foundation.

If implementation inspection finds a discrepancy between code and these contracts, stop and document it. Do not silently
change the PRD, API, schema, security model, or phase scope.

## 3. Scope

### 3.1 In scope

**Public campaign landing page**

- Implement the existing optional-authentication `GET /api/v1/campaigns/{campaignId}` contract for published campaigns.
- Render campaign name/description, fixed landing-page headline/body, campaign period, and the associated coupon
  summaries using the existing fixed template.
- Keep the page publicly readable when a published campaign is `PAUSED` or effectively `ENDED`; do not expose private
  member data to anonymous visitors.
- Do not expose DRAFT campaigns through the public endpoint. Preserve the API's documented not-found behavior for
  unavailable/unpublished campaigns.

**Coupon summary and member claim state**

- Implement the existing optional-authentication `GET /api/v1/campaigns/{campaignId}/coupons` contract.
- Display each coupon as available, unavailable, or already claimed for the current member, using server-authoritative
  inventory, campaign/coupon validity, immutable audience snapshot eligibility, and claim state.
- Do not imply that available inventory means a member is eligible. An authenticated member outside the audience
  snapshot sees an explicit not-eligible state.
- For paused/ended campaigns, keep public content visible but display coupons as unavailable and prevent new claims.
- Reuse the existing Phase 6 `POST /api/v1/campaigns/{campaignId}/coupons/{couponId}/claims` operation and idempotency
  contract; do not create a second claim path or move claim business rules into the browser.
- Display a clear success/failure result. Avoid duplicate submissions and preserve the idempotency key for a logical
  claim command/retry as required by the accepted contract.

**Authentication and return-to-campaign flow**

- Anonymous visitors can read public campaign/coupon content.
- If an anonymous visitor initiates Claim, direct them to the existing sign-in experience and preserve the intended
  campaign route through authentication.
- After successful sign-in, return the visitor to the original campaign landing page; do not automatically submit a
  claim without a deliberate user action.
- Member-only claim state and member identity must come from the authenticated session/principal, never a
  client-selected member ID.

**Engagement event ingestion**

- Implement the existing `POST /api/v1/campaigns/{campaignId}/events` contract for authenticated MEMBER clients and only
  the client event types `IMPRESSION`, `CLICK`, and `LANDING_PAGE_VIEW`.
- `IMPRESSION` and `CLICK` require the message identifier returned by the member inbox API (the `member_messages` row
  ID) and are accepted only if that row belongs to the current member and campaign. The server maps it to
  `campaign_messages.id` for the existing event foreign keys.
- `LANDING_PAGE_VIEW` has no message identifier and may be recorded for any authenticated member; anonymous page views
  are not attributed and create no `campaign_events` row.
- Use the client-generated `eventId` and existing `(member_id, event_id)` uniqueness to make retries/double firing
  idempotent. Duplicate submissions must not inflate event totals.
- Derive `memberId` from the authenticated principal. Reject client attempts to submit `CLAIM_ATTEMPT`, `DELIVERED`, or
  other unsupported event types. Claim audit remains server-generated by the Phase 6 claim transaction; delivery remains
  derived from `member_messages`.
- Validate event context and ownership against the existing database constraints and approved API contract. Do not
  accept raw sensitive attributes, tokens, or idempotency keys in event metadata.

**Member Portal UI**

- Add the campaign landing route `/campaigns/:campaignId` to the existing React SPA.
- Implement loading, empty/not-found, unavailable, anonymous, eligible, not-eligible, already-claimed,
  sold-out/unavailable, claim-pending, claim-success, and claim-failure states.
- Add the minimum Member navigation/inbox entry point only where needed to reach the campaign from an existing delivered
  message; do not expand this into a general Member dashboard or inbox redesign unless explicitly approved. The existing
  Phase 7 inbox APIs remain the source of delivered messages.
- Ensure route guards and navigation are role-aware without relying on frontend checks as the security boundary.

### 3.2 Explicitly out of scope

- Analytics APIs, Admin analytics dashboards, funnel reports, and metric aggregation (Phase 9).
- Coupon redemption at checkout, orders, payments, refunds, or inventory settlement.
- Anonymous event attribution, fingerprinting, tracking cookies, third-party analytics, and cross-device identity.
- Page Builder, custom campaign layouts, rich-text editor, arbitrary assets, multiple landing-page templates, or
  multiple campaign messages.
- Scheduled publication, campaign editing after publication, and changes to Phase 7 delivery behavior.
- Changes to coupon eligibility, inventory reservation, idempotency, claim transaction, or claim audit semantics already
  accepted in Phase 6.
- New database migration unless implementation inspection proves the approved V1 schema is insufficient; never modify an
  already-applied migration.
- New API paths or request/response contract changes. If a gap is found, stop and request owner approval before changing
  the contract.

## 4. Existing API contract

Use the existing OpenAPI paths and payloads under `/api/v1`; no alternate paths are authorized by this plan.

| Method | Path                                                       | Auth          | Phase 8 behavior                                                                      |
|--------|------------------------------------------------------------|---------------|---------------------------------------------------------------------------------------|
| GET    | `/api/v1/campaigns/{campaignId}`                           | Optional      | Public fixed-template campaign content; member-specific state only when authenticated |
| GET    | `/api/v1/campaigns/{campaignId}/coupons`                   | Optional      | Public coupon summaries; authenticated claim/eligibility state                        |
| POST   | `/api/v1/campaigns/{campaignId}/coupons/{couponId}/claims` | MEMBER + CSRF | Reuse accepted Phase 6 claim operation; sign-in return flow handled by SPA            |
| POST   | `/api/v1/campaigns/{campaignId}/events`                    | MEMBER + CSRF | Idempotently record IMPRESSION, CLICK, or LANDING_PAGE_VIEW                           |

Preserve response envelopes, status codes, error codes, CSRF behavior, idempotency semantics, and OpenAPI definitions.
The client must not choose the member identity. Anonymous reads must not create member-attributed events.

## 5. Frontend route and interaction behavior

- Route: `/campaigns/:campaignId`.
- Public reads work before login.
- Admin-only routes remain restricted to ADMIN; claim and member-specific states remain MEMBER-only.
- Login return state contains only a validated same-origin internal campaign path; reject external/absolute redirect
  destinations to prevent open redirects.
- A message impression is sent only when the delivered message is actually visible/rendered according to the PRD; avoid
  firing on mere inbox fetch.
- A message click event is sent on deliberate user click, before/alongside navigation without blocking navigation on
  telemetry failure.
- A landing-page view is sent only for authenticated members. Event-ingestion failure must not prevent page display or
  claim actions.
- Claim button state is driven by server response and current coupon/member state; disable duplicate in-flight
  submissions.

## 6. Transaction, security, and data rules

- Reuse Phase 6 ClaimCoupon as the only owner of inventory reservation and claim persistence. Never decrement inventory
  in the frontend or in the landing-page read endpoint.
- Reuse the immutable Phase 7 audience snapshot as the eligibility source; do not re-evaluate current audience rules for
  a published campaign.
- Public endpoints return only approved campaign/coupon fields. They must not expose audience member lists, internal
  delivery job details, member identifiers, or private claim records.
- For authenticated responses, derive member identity from the principal and return only that member's eligibility/claim
  state.
- Enforce `MEMBER` authorization and CSRF for claim/event writes. Event idempotency must be enforced by the database
  uniqueness constraint, not only an in-memory/browser guard.
- `CLAIM_ATTEMPT` is server-generated by the claim use case and is never accepted as a client event. Successful claim
  truth comes from `coupon_claims`; delivered truth comes from `member_messages`.
- No new migration is expected because V1 already defines `campaign_events`, event context checks, event deduplication,
  and the claim/inbox/audience tables. Verify actual schema against the documented contract before implementation.

## 7. Acceptance criteria

**Public landing page**

- Anonymous visitor can open a published campaign and see fixed headline/body, campaign details, and public coupon
  summaries.
- DRAFT/unpublished campaign is not publicly readable.
- PAUSED and effectively ENDED published campaigns remain readable, but all coupons are unavailable for new claims.
- Anonymous responses contain no member-specific eligibility, claim state, or personal data.

**Eligibility and claim experience**

- Eligible authenticated member sees available coupons and can claim once successfully.
- Authenticated member outside the immutable audience snapshot sees explicit not-eligible state even when coupon
  inventory remains.
- Already-claimed member sees already-claimed state; sold-out, not-yet-valid, expired, paused, and ended coupon/campaign
  states are accurately represented.
- Claim success displays the authoritative claim result; business failures display a safe, understandable message.
- Duplicate click/network retry does not create duplicate claims or consume inventory more than once; accepted
  idempotency behavior is preserved.
- Anonymous claim attempt redirects to login and returns to the same campaign route after successful login, without
  auto-claiming.

**Engagement**

- Impression event is recorded only for a visible delivered message belonging to the member.
- Click event is recorded only for a delivered message belonging to the member.
- Authenticated landing-page view is recorded; anonymous page view creates no member event.
- Repeating the same `eventId` does not increase counts; different event IDs can represent distinct valid
  impressions/clicks.
- Wrong campaign/message context, non-delivered message, unsupported event type, anonymous write, ADMIN write, and
  malformed event are rejected according to the API contract.
- Client cannot submit claim audit or delivery events.

**Regression and security**

- Phase 1–7 regression remains green, including Phase 7 single-backend-instance worker operation.
- Maven tests and PostgreSQL integration tests pass; frontend tests, typecheck, and production build pass.
- API suite verifies anonymous/Member/Admin boundaries, CSRF, event idempotency, claim idempotency, and public
  paused/ended reads.
- Browser/E2E tests cover direct URL entry, refresh, login return path, role changes, loading/error/empty states, and
  claim outcomes.
- No secrets or sensitive member attributes appear in event payloads/logs.

## 8. Proposed implementation sequence

1. **Contract and persistence inspection:** compare current campaign/coupon/claim/event code and V1 schema to
   PRD/API/OpenAPI; report any mismatch before coding.
2. **Public read APIs:** implement campaign and coupon public read models with optional principal enrichment and strict
   field minimization.
3. **Engagement API:** implement validation, principal-derived member identity, delivered-message ownership checks, and
   idempotent event persistence.
4. **Member SPA route:** add fixed landing-page view and all anonymous/authenticated/eligibility/availability states.
5. **Claim/login integration:** wire the existing claim API, idempotency handling, and safe same-origin
   return-to-campaign behavior.
6. **Message event integration:** instrument delivered message impression/click at actual visible/click interaction
   points.
7. **Automated verification:** add backend unit/integration tests, API suite, frontend component/route tests, and
   repeatable E2E flows.
8. **Regression and documentation:** run all Phase 1–7 suites and update API/OpenAPI/docs only if approved
   contract-consistent clarifications are required.

## 9. Dependencies and implementation gates

- Phase 7 must remain accepted and its single-instance worker constraint must be respected.
- Phase 6 claim transaction, coupon inventory, and idempotency behavior are reused without redesign.
- The existing V1 `campaign_events` table and constraints are expected to support the approved event contract; no
  migration is presumed.
- Phase 9 analytics consumes the authoritative records created by Phase 8 and the existing Phase 6/7 tables.
- The owner explicitly instructed Phase 8 implementation on 2026-11-27 and formally accepted it on 2026-11-28 after
  owner-reported verification. Phase 9 planning is documented separately in `PHASE_9_PLAN.md`; implementation requires
  approval of that plan.
