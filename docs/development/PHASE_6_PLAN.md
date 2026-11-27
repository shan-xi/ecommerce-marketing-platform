# Phase 6 Plan — Coupon Configuration and Claim Backend

**Document status:** DRAFT FOR OWNER REVIEW — not yet approved.  
**Implementation status:** NOT STARTED — implementation is not authorized by this plan.  
**Baseline:** Owner-approved v1.5.0 Development Baseline.  
**Preceding phases:** Phases 1–5 formally accepted; Phase 5 accepted by the owner on 2026-11-27.  
**Purpose:** Define the bounded Phase 6 work package from the approved PRD, API/OpenAPI contract, database design,
architecture, and implementation sequence.

## 1. Objective

Implement campaign-scoped coupon configuration and the transactional coupon-claim backend:

- Administrators can create, list, and update coupons belonging to a DRAFT campaign.
- An authenticated eligible member can claim an available coupon.
- Inventory cannot be oversold under concurrent requests.
- A member can successfully claim a campaign coupon at most once.
- Successful claim requests are idempotently replayable.
- The member can retrieve their own successful coupon claims.
- Every authenticated claim command for an existing campaign that passes basic request-shape validation receives a
  server-generated claim-attempt audit record according to the existing API contract.

Phase 6 does not implement publication, audience snapshot creation, message delivery, public landing pages, claim UI,
or analytics. Until Phase 7 publication exists, claim behavior must be tested with deterministic fixtures for an
already-ACTIVE campaign and its audience snapshot.

## 2. Authoritative source documents

This plan is subordinate to the owner-approved v1.5.0 baseline and the accepted Phase 1–5 implementation:

1. `docs/PRD.md` — product scope, coupon configuration, member coupon claim journey, inventory and duplicate-claim rules.
2. `docs/api/API_SPECIFICATION.md` — Sections 7 and 9–10: coupon configuration, member claims, landing-page claim
   contract, idempotency, and server-generated claim-attempt audit behavior.
3. `docs/api/openapi.yaml` — existing coupon configuration, member coupon list, and claim endpoint contracts.
4. `docs/api/ERROR_CODES.md` — coupon validity, sold-out, duplicate claim, idempotency conflict, authorization, and
   validation responses.
5. `docs/database/DATABASE_DESIGN.md` and `docs/database/ERD.md` — `campaign_coupons`, `coupon_claims`,
   `campaign_events`, ownership constraints, and indexes.
6. `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` — existing schema and database constraints.
7. `docs/architecture/ARCHITECTURE.md` and `docs/architecture/MODULE_DESIGN.md` — modular-monolith boundaries,
   transaction ownership, and module dependencies.
8. `docs/development/IMPLEMENTATION_PLAN.md` and `docs/development/TESTING_STRATEGY.md` — phase sequencing and
   verification expectations.
9. `docs/development/PHASE_5_IMPLEMENTATION_REPORT.md` — accepted Campaign module and persistence/read-model baseline.

If a conflict is found, stop and reconcile the source documents before implementation. Do not silently change the PRD,
API contract, schema, or MVP scope.

## 3. Scope

### 3.1 In scope

**Admin coupon configuration APIs**
- Add a coupon to a campaign.
- List coupons for a campaign.
- Update a coupon only while its parent campaign is `DRAFT`.
- If the owner approves the Section 3.3 clarification, add a minimal Admin coupon configuration UI for list/create/edit
  within the existing Campaign workspace.
- Enforce campaign ownership and ADMIN authorization; all state-changing requests retain existing CSRF protection.

**Member claim APIs**
- Claim a coupon for the authenticated member.
- List the authenticated member's successful coupon claims.
- Derive member identity from the authenticated principal; never accept a client-selected member ID.

**Claim correctness and audit**
- Atomic inventory reservation and successful claim insertion in one transaction.
- One successful claim per member per campaign coupon.
- Idempotent replay of a successful request using `(member_id, idempotency_key)`.
- Server-generated `CLAIM_ATTEMPT` audit records for `SUCCEEDED`, `FAILED`, and `REPLAYED` outcomes.
- PostgreSQL integration and concurrency tests.

### 3.2 Explicitly out of scope

- Publish/Pause endpoints, publication orchestration, and creation of the authoritative audience snapshot (Phase 7).
- Campaign message configuration, delivery jobs, workers, retries, and inbox delivery (Phase 7).
- Public campaign landing-page UI, member claim UI, and engagement event ingestion endpoints (Phase 8).
- Analytics and coupon reporting (Phase 9).
- Coupon redemption, checkout, order creation, payment, refund, and logistics.
- A global reusable coupon master, promotion-rule engine, coupon code generation, or external coupon-provider integration.
- Redis, Kafka, microservices, scheduled jobs, or new infrastructure.
- A new database migration unless code inspection proves the existing approved schema is insufficient.

### 3.3 Frontend scope clarification requiring owner decision

The PRD says administrators configure multiple coupons, while the current Phase 6 row in
`IMPLEMENTATION_PLAN.md` explicitly lists backend APIs/services as its main deliverables and does not name an Admin UI.
The PRD also places the member claim experience in the Member Portal, whose UI is sequenced to Phase 8.

**Recommendation for owner review:** include a minimal Admin coupon configuration UI in Phase 6, alongside the
coupon APIs, because coupon setup is an explicit Admin Portal capability in the PRD and belongs naturally beside the
existing Campaign workspace. Keep the UI bounded to list/create/edit coupons for a DRAFT campaign; do not add coupon
redemption or advanced promotion tooling. The Member claim and claimed-coupon UI remains in Phase 8 with the landing-page
experience. This is a proposed clarification to the current implementation-plan row and requires explicit owner approval
before implementation; until approved, the current baseline remains backend-only.

## 4. Existing API contract

Use the existing API paths and payloads; do not invent alternate endpoints.

| Method | Path | Auth | Phase 6 behavior |
|---|---|---|---|
| POST | `/api/v1/admin/campaigns/{campaignId}/coupons` | ADMIN + CSRF | Create a coupon for a DRAFT campaign |
| GET | `/api/v1/admin/campaigns/{campaignId}/coupons` | ADMIN | List campaign coupons, ordered by `createdAt ASC` |
| PUT | `/api/v1/admin/campaigns/{campaignId}/coupons/{couponId}` | ADMIN + CSRF | Update a coupon only while its campaign is DRAFT |
| GET | `/api/v1/member/coupons` | MEMBER | List the current member's successful claims |
| POST | `/api/v1/campaigns/{campaignId}/coupons/{couponId}/claims` | MEMBER + CSRF | Claim a coupon for the current member |

The public campaign and coupon summary GET endpoints are part of the Phase 8 landing-page experience and are not added
as new Phase 6 endpoints unless the existing OpenAPI explicitly assigns them earlier.

### 4.1 Coupon write model and validation

Use the existing API/OpenAPI request shape:

- `name`: required, maximum 160 characters.
- `description`: nullable.
- `discountType`: `FIXED_AMOUNT` or `PERCENTAGE`.
- `discountValue`: positive decimal; `FIXED_AMOUNT` is a TWD amount greater than zero; `PERCENTAGE` is percentage
  points greater than zero and at most 100 (`10` means 10%).
- `minimumOrderAmount`: nullable, TWD, and non-negative; descriptive metadata only in MVP.
- `totalQuantity`: integer at least 1.
- `startsAt` and `endsAt`: required instants with `endsAt > startsAt`.
- `claimedQuantity`: server-managed; never accepted as a client-controlled write field.

The database already has type-aware discount checks, quantity checks, validity-window checks, and the composite
campaign/coupon ownership key. Do not add a global coupon table or duplicate these invariants in a new schema.

Do not silently impose an additional rule that coupon validity must be contained within the campaign window unless the
owner-approved contract is clarified to require it. Claimability must require both campaign and coupon windows to be
currently valid.

### 4.2 Coupon configuration lifecycle

- Coupons belong to exactly one campaign.
- A coupon can be created or updated only while its parent campaign is `DRAFT`.
- The API cannot set or modify `claimedQuantity`.
- Coupon configuration is not a separate ACTIVE/PAUSED state machine; claimability is derived from campaign state,
  campaign time window, coupon time window, audience eligibility, and remaining inventory.
- A campaign must have at least one coupon with `totalQuantity >= 1` before publication. Phase 7 enforces this during
  publication; Phase 6 does not add Publish.
- Unknown campaign/coupon IDs and cross-campaign coupon IDs must return the documented not-found/validation behavior;
  never reveal or mutate a coupon belonging to another campaign.

## 5. Claim business rules and transaction design

### 5.1 Eligibility

A new claim is allowed only when all of the following are true:

1. The authenticated principal has role `MEMBER` and resolves to the member profile.
2. The campaign exists, is persisted as `ACTIVE`, has `published_at` set, and satisfies
   `starts_at <= now < ends_at`.
3. The coupon belongs to the requested campaign and satisfies `starts_at <= now < ends_at`.
4. The member exists in `campaign_audience_members` for that campaign's publication snapshot.
5. The coupon has remaining inventory.
6. The member has not already successfully claimed that campaign coupon.

A paused or effectively ended campaign cannot accept a new claim. The persisted publication snapshot, not a live
re-evaluation of segment rules, is the eligibility authority. This keeps claim behavior consistent with Phase 7's
immutable audience snapshot.

### 5.2 Idempotency and duplicate behavior

- The idempotency key is a required UUID in the request body.
- Look up an existing successful claim for `(member_id, idempotency_key)` before checking current claim eligibility.
- If it refers to the same campaign and coupon, return the original successful claim response, even if the campaign has
  since been paused or ended; record audit outcome `REPLAYED`.
- If it refers to a different campaign or coupon, return `409 IDEMPOTENCY_KEY_CONFLICT`.
- If the member already claimed the same campaign coupon using a different key and the campaign is otherwise claimable,
  return `409 CLAIM_ALREADY_EXISTS`.
- Failed attempts do not reserve inventory, create a `coupon_claims` row, or reserve/cache the idempotency key.
- A failed request may be retried with the same key after its underlying eligibility problem is resolved.

### 5.3 Concurrency-safe transaction

Implement the claim operation as a transaction with PostgreSQL as the correctness authority:

1. Resolve the member from the authenticated principal and validate the request shape.
2. Acquire a shared row lock on the campaign row (`SELECT ... FOR SHARE`) and hold it through commit. This is the
   agreed serialization point with Phase 7 Pause, which updates the same campaign row.
3. Check for an existing successful claim by `(member_id, idempotency_key)` before new-claim eligibility. Resolve
   same-target replay versus cross-target idempotency conflict.
4. Validate campaign/coupon ownership, campaign status/window, coupon window, and membership in the immutable
   `campaign_audience_members` snapshot.
5. Reserve one unit using a conditional atomic update equivalent to:
   `UPDATE campaign_coupons SET claimed_quantity = claimed_quantity + 1 ... WHERE id = :couponId AND campaign_id = :campaignId AND claimed_quantity < total_quantity AND starts_at <= :now AND ends_at > :now`.
   Require exactly one updated row; otherwise map the result to the documented business error after distinguishing
   invalid coupon/window from sold-out inventory.
6. Insert the `coupon_claims` row in the same transaction.
7. Insert a server-generated `campaign_events` row with `event_type = 'CLAIM_ATTEMPT'` and metadata outcome
   `SUCCEEDED` in the same transaction.
8. Commit and return `201 Created`.

The database unique constraints remain authoritative for concurrent duplicate claims and idempotency keys. If a unique
constraint rejects a concurrent insert, roll back the entire claim transaction (including the inventory increment),
then load the winning successful claim in a fresh transaction and map it to `CLAIM_ALREADY_EXISTS`, an idempotent replay,
or `IDEMPOTENCY_KEY_CONFLICT` as appropriate. Do not rely on an application-side pre-check alone.

The campaign shared lock is intentionally compatible with the Phase 7 Pause protocol: Pause's campaign-row update
waits for claims that already hold the shared lock; claims beginning after Pause commits observe the paused state.

### 5.4 Claim-attempt audit

Follow the existing API specification and database constraint:

- Every authenticated claim command for an existing campaign that passes basic request-shape validation generates a
  server-side `CLAIM_ATTEMPT` record.
- `SUCCEEDED`: audit row commits atomically with `coupon_claims`.
- `REPLAYED`: audit row records the replay outcome; the original successful claim is returned.
- `FAILED`: the claim transaction rolls back first; write a failure audit row in a separate short transaction.
- Failure metadata includes a safe business failure code and must not include credentials, tokens, or unnecessary
  personal data.
- The client cannot submit `CLAIM_ATTEMPT`, choose the outcome, or forge audit metadata.
- A failure to persist the required audit record must not be silently represented as a successful audited command;
  define and test the error handling so it does not accidentally commit inventory or a claim without its success audit.

## 6. Architecture and persistence boundaries

- Keep coupon configuration and claim use cases within the existing modular-monolith conventions.
- Campaign owns campaign lifecycle and the campaign-row lock protocol. Coupon owns coupon configuration, inventory, and
  claim persistence/business invariants, subject to the existing module design; cross-module calls must use application
  ports/interfaces rather than direct repository/table access.
- Resolve the authenticated member through the existing Identity/Member application interface; never trust a member ID
  from the request.
- Use injected `Clock` for all time-dependent checks and deterministic tests.
- Keep inventory increment, claim insert, and successful audit insert in one transaction.
- The existing V1 migration already creates `campaign_coupons`, `coupon_claims`, and the required uniqueness,
  type/quantity/time-window, and composite ownership constraints. The expected path is **no new migration**. If
  implementation discovers a missing required constraint/index, stop and document the DATABASE_DESIGN/ERD impact before
  adding a migration.
- Do not introduce external locks, Redis, Kafka, or a separate inventory service.

## 7. Error mapping

Use the existing stable error codes; do not add new codes without an approved contract change.

| Condition | Expected response |
|---|---|
| Invalid coupon payload / invalid discount type or value / invalid time window | Existing documented validation response |
| Campaign or coupon does not exist / coupon does not belong to campaign | Existing documented not-found behavior |
| Campaign is not ACTIVE or outside its claimable window | `409 CAMPAIGN_INACTIVE` |
| Coupon is outside its validity window | `409 COUPON_OUTSIDE_VALIDITY` |
| No remaining inventory | `409 COUPON_SOLD_OUT` |
| Member already claimed coupon with another key | `409 CLAIM_ALREADY_EXISTS` |
| Same idempotency key targets a different campaign/coupon | `409 IDEMPOTENCY_KEY_CONFLICT` |
| Anonymous or ADMIN attempts member claim | Existing authentication/authorization response |
| MEMBER attempts Admin coupon APIs | `403` |
| Missing/invalid CSRF on state-changing cookie-authenticated requests | Existing CSRF response |

Preserve the existing error envelope and safe error-detail conventions.

## 8. Required tests

### 8.1 Backend unit tests

- Coupon create/list/update validation and DRAFT-only update behavior.
- Type-aware `FIXED_AMOUNT` and `PERCENTAGE` validation.
- Positive quantity, non-negative minimum order amount, and valid time window.
- `claimedQuantity` cannot be set by the client.
- Claim eligibility for ACTIVE/in-window campaign, in-window coupon, and snapshot member.
- Reject paused/ended/not-yet-started campaigns, out-of-window coupons, and non-snapshot members.
- Idempotent replay, cross-target idempotency conflict, and duplicate claim with a different key.
- Failure audit outcomes and safe failure codes.

### 8.2 PostgreSQL integration tests

Use PostgreSQL integration tests; in-memory substitutes are not sufficient for correctness-critical locking/constraint
behavior.

- Coupon CRUD persistence, ordering, FK behavior, and cross-campaign ownership.
- Atomic inventory update and claim insertion.
- Concurrent requests for the final inventory unit: exactly one succeeds and `claimed_quantity` equals successful
  claim rows.
- Concurrent duplicate claims for the same member/coupon: exactly one successful claim row; losing transaction does not
  consume inventory.
- Concurrent reuse of the same idempotency key: exactly one successful claim result; same-target retries replay and
  cross-target reuse conflicts.
- Rollback on claim insert/audit insert failure leaves inventory and claim rows unchanged.
- Claimant must be in the campaign snapshot; client cannot choose another member.
- Campaign shared-lock behavior is compatible with an UPDATE of the campaign row, providing the lock contract Phase 7
  Pause will use.
- Claim-attempt audit rows distinguish `SUCCEEDED`, `FAILED`, and `REPLAYED`; failed attempts do not create successful
  claim rows or reserve inventory.
- Inventory consistency assertion: `claimed_quantity` equals the count of successful `coupon_claims` for each fixture.

Use deterministic fixtures for an already-ACTIVE campaign with `published_at` set and explicit
`campaign_audience_members` rows. These fixtures are test scaffolding only; Phase 6 must not implement publication or
snapshot creation.

### 8.3 API verification

Add `scripts/api-tests/suites/phase6-coupon-claim-verification.zsh`, reusing existing helpers and remaining macOS zsh
compatible. Cover:

- Anonymous, ADMIN, and MEMBER role boundaries.
- Admin coupon create/list/update and DRAFT-only update guard.
- Invalid coupon fields and cross-campaign IDs.
- Member successful claim and member claim list.
- Same-key replay, cross-target idempotency conflict, duplicate claim with a different key.
- Sold-out and coupon/campaign validity errors.
- Audience snapshot eligibility.
- CSRF on Admin writes and Member claim.
- Server-generated claim-attempt audit outcomes, verified through the approved test/inspection path without exposing
  audit write controls to the client.

### 8.4 Regression

- Preserve all Phase 1–5 tests and API verification suites.
- Run `./mvnw -f backend/pom.xml verify`.
- Run frontend `npm ci`, `npm run test`, `npm run typecheck`, and `npm run build` if frontend files/contracts change.
- Run the Phase 6 API suite and all prior API suites.
- Verify the app starts locally and existing Admin Campaign and Audience routes remain functional.

## 9. Exit criteria

- [ ] Coupon create/list/update APIs match the existing API/OpenAPI contract.
- [ ] If approved in Section 3.3, the Admin coupon configuration UI supports list/create/edit for DRAFT campaigns.
- [ ] Only DRAFT campaigns accept coupon configuration changes.
- [ ] Coupon validation and server-managed inventory fields are enforced.
- [ ] Member claim and member coupon-list APIs use the authenticated principal.
- [ ] Inventory cannot be oversold under PostgreSQL concurrency.
- [ ] One successful claim per member/campaign coupon is enforced by database constraints.
- [ ] Successful idempotency replay and cross-target conflict semantics match the API specification.
- [ ] Failed attempts do not reserve inventory or persist successful claims.
- [ ] Server-generated `CLAIM_ATTEMPT` audit behavior is correct for SUCCEEDED/FAILED/REPLAYED.
- [ ] Campaign row locking is compatible with the Phase 7 Pause protocol.
- [ ] All PostgreSQL integration, API, and regression tests pass.
- [ ] API/OpenAPI, error codes, database docs (only if schema changes), README, implementation plan, and report are synchronized.
- [ ] Owner reviews and formally accepts Phase 6 before Phase 7 begins.

## 10. Dependencies and implementation sequence

1. Re-read the authoritative documents and inspect the accepted Campaign, Identity, Member, and Audience application
   interfaces.
2. Resolve the frontend scope clarification in Section 3.3 before implementation; update `IMPLEMENTATION_PLAN.md`
   if the Admin coupon UI is approved for Phase 6.
3. Inspect the existing V1 schema and confirm no migration is required.
4. Implement coupon domain model, validation, repository ports, and JDBC adapters.
5. Implement Admin coupon create/list/update use cases and controller.
6. Implement the claim use case with campaign shared lock, idempotency ordering, eligibility, conditional inventory
   reservation, claim insert, and server-generated audit.
7. Implement member claim-list use case and controller.
8. Add unit, PostgreSQL integration, concurrency, and API verification tests.
9. Run all Phase 1–5 regression checks; update API/OpenAPI/error docs only if a genuine contract clarification is
   approved.
10. Submit `PHASE_6_IMPLEMENTATION_REPORT.md` and updated ZIP for owner verification; do not begin Phase 7 until owner
    acceptance.

## 11. Approval gate

This document is a proposal for owner review only. Phase 5 was formally accepted on 2026-11-27, which permits Phase 6
planning. **Phase 6 implementation is not authorized until the owner explicitly approves this plan and authorizes
implementation.** Any change to product scope, API semantics, schema, or phase sequencing requires explicit review.
