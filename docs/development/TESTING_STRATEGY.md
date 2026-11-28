# Testing Strategy

**Status:** Approved — part of the owner-approved v1.5.0 Development Baseline. Phases 1–8 verification and owner
acceptance are recorded in their implementation reports; Phase 9 analytics verification requirements are defined below.

## 1. Test pyramid

| Level                  | Purpose                                  | Examples                                                                                  |
|------------------------|------------------------------------------|-------------------------------------------------------------------------------------------|
| Unit                   | Fast domain/application correctness      | Campaign lifecycle, audience rule validation, coupon eligibility, conversion calculations |
| Repository/integration | Real PostgreSQL behavior and constraints | Unique claims, atomic inventory update, Flyway migrations, query filters                  |
| API integration        | HTTP contract, auth, CSRF, serialization | Role enforcement, cookie refresh, error envelope, request validation                      |
| Frontend component     | User interaction and state rendering     | Campaign form, audience builder, inbox, coupon claim states                               |
| End-to-end             | Complete user journey                    | Admin publishes → member sees message → opens page → claims coupon → admin sees metrics   |
| Concurrency            | Race conditions and invariants           | Oversubscribed coupon inventory, duplicate claims, duplicate delivery/event submissions   |

## 2. Required backend tests

### Authentication and authorization

- Admin can authenticate and access admin endpoints.
- Member cannot access admin endpoints.
- Member A cannot retrieve Member B's private data.
- Refresh token rotation, expiration, revocation, and logout work. MVP defaults are access 15 minutes and refresh 7 days
  (overridable by environment). Each token generation has its own row and family ID; exactly one concurrent request
  consumes a generation; reuse of a consumed token revokes the family.
- CSRF protection rejects missing/invalid tokens on state-changing cookie requests.

### Audience

- Every field/operator combination in the PRD matrix is accepted; every incompatible combination is rejected. Cover all
  8 fields, enum restrictions, scalar-vs-array value shape, exact value types, enum allowlists, and integer/decimal
  bounds.
- Age is derived from `birth_date` using the `Asia/Taipei` date: preview uses the preview run date, while publication
  uses the actual publication date and creates the authoritative snapshot.
- Unsupported fields/operators and malformed values are rejected.
- AND semantics are correct.
- When both a saved segment and campaign-specific rules are supplied, the result is their intersection; either one alone
  works as the effective audience.
- NULL-valued attributes do not match any supported comparison/set operator; explicit NULL-check operators and NULL rule
  values/array items are rejected. Comparison operators require scalar values; `IN`/`NOT IN` require non-empty arrays.
- Snapshot contains the complete matching members committed with publication and remains unchanged after member
  attributes or segment rules change.
- Snapshot rows and the delivery job are committed atomically; no delivery starts if snapshot creation fails.
- Composite foreign keys reject member_messages or campaign_events that pair a campaign with another campaign's
  message/coupon; impression/click events cannot be recorded by a member without the matching inbox row; a delivery
  cursor cannot reference a member outside its campaign snapshot.
- Impression/click event ingestion rejects a member who does not have the corresponding campaign message in their inbox.
- A zero-member snapshot publishes successfully with a warning and a `COMPLETED` delivery job; no inbox rows are
  created.

### Publication and delivery

- Campaign cannot publish without required setup.
- Campaigns outside their start-inclusive/end-exclusive window are rejected for publication; publication succeeds only
  when `starts_at <= now < ends_at`.
- `ENDED` is derived for a published campaign when `ends_at <= now`; it is not persisted, no background status
  transition is required, and an expired draft remains `DRAFT`.
- Publication rejects a campaign without at least one configured coupon with `total_quantity >= 1`; coupon configuration
  rejects zero or negative total quantity.
- Publish is idempotent or rejects duplicate publish safely.
- Audience snapshot and delivery job are committed consistently.
- Delivery worker processes batches of 100; each batch and cursor update are atomic.
- Injected batch failures retry after 1s/2s/4s; the initial attempt plus three automatic retries (1s/2s/4s backoff) fail
  at the same cursor, the job moves to `FAILED`. A successful batch resets attempt count.
- Admin retry is accepted only for `FAILED`, resets attempt count, preserves cursor, and resumes without duplicate inbox
  records. Retry is rejected for other job statuses.
- Delivery can resume after worker interruption from the last committed cursor; a `PROCESSING` job is recovered to
  `PENDING` on startup.
- The MVP runs one delivery worker; no lease or multi-worker coordination is required.
- A committed delivery job still in `PENDING` when the campaign is paused is processed to completion; the worker does
  not gate on campaign status.

### Phase 7 message, publication, and inbox coverage

- Message configuration is limited to one row per campaign, validates title/body length and required content, and is
  writable only in DRAFT; ADMIN role and CSRF boundaries are verified.
- Publish atomically commits the complete publication-time audience snapshot, ACTIVE transition, and one delivery job;
  missing message/coupon, invalid campaign window, and duplicate publication leave no partial effects.
- Zero-member publication returns `NO_AUDIENCE_MEMBERS`, creates a COMPLETED job, and creates no inbox records.
- Worker batch size is 100; inbox rows and cursor commit together, uniqueness prevents duplicate campaign/member rows,
  automatic retries follow 1s/2s/4s, successful batches reset retries, and a fourth total failure becomes FAILED.
- Worker startup recovers PROCESSING jobs to PENDING; manual retry is FAILED-only and resumes from the committed cursor.
- Pause serializes against coupon claims and does not cancel committed delivery.
- Member inbox pagination and detail are principal-scoped; a member cannot access another member's inbox row.

### Coupon claim correctness

- Claim succeeds for eligible member and available coupon.
- Duplicate claim by same member is rejected without consuming extra inventory.
- Ineligible member, paused/ended campaign, expired coupon, and sold-out coupon are rejected.
- Pausing during a delivery job does not stop it, including when the committed job is still `PENDING`; the worker
  completes the snapshot delivery.
- A concurrency test verifies pause/claim ordering: a claim that acquired the campaign shared lock before Pause may
  complete before Pause commits; claims that acquire the lock after Pause commits are rejected.
- Replaying the same idempotency key for the same campaign/coupon returns the original successful result, including
  after the campaign is paused or has ended.
- Concurrent requests using the same idempotency key re-check after acquiring the campaign lock and return the single
  winning claim result; reuse for a different request returns `IDEMPOTENCY_KEY_CONFLICT`.
- Reusing a key for a different campaign/coupon returns `IDEMPOTENCY_KEY_CONFLICT`; while the campaign is otherwise
  claimable, a duplicate claim with a new key returns `CLAIM_ALREADY_EXISTS`.
- Failure after inventory reservation rolls back the inventory update.

**Concurrency acceptance test:** seed a coupon with quantity 100 and send at least 1,000 concurrent claim attempts from
distinct eligible members. Exactly 100 or fewer claims may succeed (exactly 100 if all other conditions are valid);
`claimed_quantity` must never exceed 100 or become negative; no member may own duplicate claims; counter must equal
successful claim count.

### Engagement and analytics

- Duplicate event ID is not counted twice.
- Delivered count is derived from inbox records; no separate `DELIVERED` event row is required.
- Impression/click/page-view totals and distinct-member counts are calculated separately.
- Successful claims are sourced from `coupon_claims`.
- Claim attempt audit is server-generated for each authenticated claim command for an existing campaign: `SUCCEEDED`,
  `FAILED`, or `REPLAYED`. Success audit commits with the claim; failure audit survives claim rollback in a separate
  transaction; replay audit is written independently. Audit write contains no secrets or raw idempotency key.
  `campaign_events` rejects a `CLAIM_ATTEMPT` row without an allowed `metadata.outcome`.
- Landing visitor rate includes only distinct audience-snapshot members with authenticated page-view events; do not
  calculate click-to-landing conversion. Anonymous page views create no member event.
- Zero denominators produce unavailable/null rates.

## 3. Required frontend tests

- Admin and Member routes render the correct screens and deny wrong-role navigation at the UX layer.
- Admin delivery status displays progress and sanitized failure details; the retry action is enabled only for `FAILED`
  jobs and is hidden/disabled for other statuses.
- Login, refresh, logout, and CSRF behavior match the API contract.
- Audience builder prevents unsupported rule combinations.
- Campaign forms display server validation errors.
- Landing page displays claimed/sold-out/available states accurately.
- Anonymous visitors can read public landing content and coupon summaries but cannot see member claim state or submit a
  claim; after sign-in they return to the intended campaign page. Anonymous page views create no `campaign_events` row;
  authenticated page views are member-attributed.
- Published landing pages remain readable while paused/ended; claim actions are disabled. Authenticated non-audience
  members see an explicit not-eligible state.
- The fixed landing-page template renders the configured headline/body and campaign/coupon data; no page-builder
  behavior exists.
- Duplicate click or network retry does not create duplicate claim UI state.
- Empty, loading, error, and success states are covered.

## 4. End-to-end scenarios

Implement all 21 acceptance scenarios in `PRD.md` as automated or documented repeatable E2E flows. At minimum, automate
campaign creation/publication, member delivery, coupon claim, duplicate claim, concurrency, and analytics verification.

## 5. Test data and environment

- Use fictional deterministic seed data.
- Use PostgreSQL in integration tests (prefer Testcontainers) rather than relying exclusively on an in-memory database
  for transaction/concurrency behavior.
- Tests must clean up their data and be repeatable.
- Use a fixed/test clock for date-window scenarios.
- Do not use production credentials or real member records.

## 6. Definition of Done for each phase

- Acceptance criteria are met.
- Relevant unit/integration/API/UI tests pass.
- No known regression in previously completed phases.
- API/DB/documentation changes are synchronized.
- Build and test commands are documented and reproducible.

### Phase 8 landing page and engagement coverage (planned)

- Anonymous reads can access published campaign/coupon content only; DRAFT campaigns are not public.
- PAUSED/ENDED published pages remain readable while new claims are unavailable.
- Authenticated member state is principal-scoped; audience-snapshot ineligibility is explicit and cannot be inferred
  from stock.
- Claim UI reuses Phase 6 claim/idempotency semantics; duplicate submission and network retry do not consume inventory
  twice.
- Anonymous claim redirects through login and returns only to a validated same-origin campaign route; no automatic claim
  after login.
- Impression/click requires a matching delivered `member_messages` row; landing-page view requires MEMBER
  authentication; anonymous page views are not attributed.
- Duplicate `eventId` does not inflate event counts; unsupported event types and client-submitted claim/delivery events
  are rejected.
- Event ingestion failure does not block public page rendering or claim interaction.
- Phase 8 automated coverage must include API authorization/CSRF, PostgreSQL event deduplication/context constraints,
  frontend route/state tests, and browser-level login-return/claim scenarios.

### Phase 8 landing page, coupon claim, and engagement coverage

- Anonymous public reads return only published campaigns and public coupon fields; anonymous and ADMIN payloads omit
  `memberState` and never expose member IDs, audience rows, claim records, or delivery internals.
- Published PAUSED and effectively ENDED campaigns remain readable; new coupon claims are unavailable. DRAFT/unpublished
  campaigns return the documented not-found response.
- MEMBER eligibility is derived only from `campaign_audience_members`; a non-target member sees `NOT_ELIGIBLE` even when
  coupon inventory remains.
- Coupon status covers `AVAILABLE`, `UNAVAILABLE`, `ALREADY_CLAIMED`, and `NOT_ELIGIBLE`; inventory is never reserved by
  a read.
- Claim UI reuses the Phase 6 claim endpoint and keeps the same idempotency key for a logical command retry; login
  return accepts only a validated internal `/campaigns/{uuid}` route and never auto-claims.
- Authenticated landing-page views are recorded; anonymous views are not. Message impressions fire only after at least
  50% visibility in the member inbox; clicks are deliberate navigation actions.
- Event writes derive member identity from the principal, require MEMBER + CSRF, validate event type/context and
  delivered inbox-row ownership, and rely on `(member_id,event_id)` database uniqueness. Duplicate IDs do not add rows.
- Client `CLAIM_ATTEMPT`/`DELIVERED`, wrong-member/wrong-campaign message IDs, unsupported event types, and message IDs
  on `LANDING_PAGE_VIEW` are rejected.
- Phase 8 PostgreSQL integration coverage is in `CampaignExperienceIntegrationTest`; the repeatable API verification is
  `scripts/api-tests/suites/phase8-member-experience-verification.zsh`.

### Phase 9 campaign analytics and coupon reporting coverage

- Campaign target count is derived from `campaign_audience_members`; delivered count is derived from `member_messages`.
- Impression/click/page-view event totals are counted separately from distinct-member reach/clicker/visitor metrics.
- Unique landing visitors are restricted to the immutable campaign audience snapshot; anonymous page views are not
  attributed and no click-to-landing conversion is calculated.
- Successful claim totals and unique claimers are derived only from `coupon_claims`; claim attempts and failed attempts
  are derived from server-generated `CLAIM_ATTEMPT` audit events and their allowed outcomes.
- Coupon-level configured quantity, successful claims, remaining quantity, unique claimers, and target claim rate are
  verified. Counter/claim-row disagreement is surfaced as an integrity failure.
- Repeated events from one member increase event totals but not distinct-member counts. Duplicate event IDs remain
  governed by the existing database uniqueness constraint.
- All rates include numerator and denominator; zero denominators produce `null`/unavailable, never `0%`.
- Independent aggregate queries/CTEs prevent fan-out multiplication across events, claims, delivery, and coupons.
- ADMIN can read analytics; MEMBER and anonymous callers are denied. Responses expose no member IDs, raw event metadata,
  raw claim records, idempotency keys, or other personal data.
- Test zero-activity campaigns, zero-member targets, no-claim coupons, multiple coupons claimed by one member, all claim
  attempt outcomes, and coupon inventory/claim-counter mismatch.
- No date-range, attribution, export, or schema migration behavior is introduced unless separately approved.
