# Phase 7 Plan — Message, Publication Orchestration, and Delivery

**Document status:** DRAFT — prepared for owner review on 2026-11-27; not yet approved.
**Implementation status:** NOT AUTHORIZED — implementation must not begin until the owner approves this plan and explicitly authorizes Phase 7.
**Baseline:** Owner-approved v1.5.0 Development Baseline, with Phase 6 formally accepted on 2026-11-27.
**Preceding phases:** Phases 1–6 complete and owner-accepted.

## 1. Objective

Complete the campaign publication lifecycle: configure the campaign's one in-app message, atomically publish a valid DRAFT campaign with its immutable audience snapshot and delivery job, pause an active campaign without cancelling committed delivery, and reliably deliver inbox messages asynchronously.

Phase 7 is the publication and message-delivery foundation. Public landing-page presentation, member claim interaction UI, and engagement event ingestion remain Phase 8. Campaign and coupon analytics remain Phase 9.

## 2. Authoritative source documents

This plan is subordinate to the owner-approved v1.5.0 baseline and accepted Phase 1–6 implementation:

1. `docs/PRD.md` — campaign lifecycle, one-message rule, publication journey, and MVP acceptance scenarios.
2. `docs/api/API_SPECIFICATION.md` — Sections 8 and 9: message configuration, delivery status/retry, and member inbox contracts; publication and pause semantics in the campaign lifecycle sections.
3. `docs/api/openapi.yaml` — existing message, publish, pause, delivery-status, retry, and member inbox paths.
4. `docs/api/ERROR_CODES.md` — publishability, lifecycle, and delivery retry errors.
5. `docs/database/DATABASE_DESIGN.md` and `docs/database/ERD.md` — campaign audience snapshot, message, delivery-job, and member-message persistence.
6. `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` — existing schema and constraints.
7. `docs/architecture/ARCHITECTURE.md`, `docs/architecture/MODULE_DESIGN.md`, and `docs/architecture/SECURITY_DESIGN.md` — module boundaries, transaction ownership, worker model, and authorization.
8. `docs/development/IMPLEMENTATION_PLAN.md` and `docs/development/TESTING_STRATEGY.md` — phase sequence and verification expectations.
9. `docs/development/PHASE_5_IMPLEMENTATION_REPORT.md` and `docs/development/PHASE_6_IMPLEMENTATION_REPORT.md` — accepted campaign and coupon/claim foundations.

If implementation inspection finds a conflict between code and the approved contracts, stop and document the discrepancy. Do not silently alter the PRD, API contract, schema, or MVP scope.

## 3. Scope

### 3.1 In scope

**Campaign message configuration**

- Create or update exactly one in-app message per campaign while the campaign is `DRAFT`.
- Retrieve the campaign's configured message.
- Validate required `title` (maximum 160 characters) and `body` (maximum 10,000 characters).
- Enforce ADMIN authorization, campaign ownership, and CSRF protection for writes.

**Atomic publication**

- Implement the existing Publish endpoint; do not introduce an alternate path or request contract.
- Require a DRAFT campaign, a valid active time window (`startsAt <= now < endsAt`), exactly one configured message, at least one configured coupon with positive quantity, and a valid audience definition.
- Resolve the effective audience using the accepted Audience application interface and the publication-time `Asia/Taipei` age calculation.
- Insert the complete `campaign_audience_members` snapshot, transition the campaign to `ACTIVE` and set `published_at`, and create its one delivery job in one database transaction.
- A zero-member audience is valid: publication succeeds with the documented warning and the delivery job is created as `COMPLETED`; no inbox rows are created.
- Do not allow republishing to replace an existing immutable snapshot.

**Pause lifecycle**

- Implement the existing Pause endpoint for an active campaign.
- Serialize Pause with coupon claims through the same campaign-row lock protocol established in Phase 6.
- Once Pause commits, new claims are blocked. A claim already holding the shared campaign lock may complete before Pause returns.
- Pause does not cancel a committed `PENDING` or `PROCESSING` delivery job and does not retract existing inbox messages.

**Asynchronous message delivery**

- Implement one in-process worker that reads only the persisted audience snapshot; it must not re-evaluate audience rules or stop delivery because a campaign is paused.
- Process bounded batches of 100 members, creating one `member_messages` row per campaign/member.
- Atomically commit each batch's inbox inserts and delivery cursor update so a retry cannot create duplicate inbox rows.
- Use the existing job states `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`.
- At a cursor, allow one initial attempt plus three automatic retries with 1s/2s/4s backoff. `attemptCount` records automatic retries consumed (0–3) and resets after a successful batch. A fourth total failure moves the job to `FAILED` with a sanitized error.
- On worker restart, recover an interrupted `PROCESSING` job to `PENDING` and resume from the last committed cursor.
- Implement delivery status retrieval and manual retry of `FAILED` jobs only. Manual retry clears the last error, resets retry count, preserves the last committed cursor, and returns the job to `PENDING`.
- Do not add a lease, `SKIP LOCKED`, snapshot-job table, scheduled delivery, or external messaging infrastructure.

**Member inbox APIs**

- Implement the existing member inbox list and detail endpoints if not already present.
- Derive member identity from the authenticated principal; never accept a client-selected member ID.
- Return only messages delivered to the current member, with list ordering/pagination matching the API specification.

### 3.2 Explicitly out of scope

- Public campaign landing page and public campaign/coupon summary reads (Phase 8).
- Member landing-page claim interaction UI and login return-to-campaign flow (Phase 8).
- Impression, click, and landing-page event ingestion (Phase 8).
- Campaign and coupon analytics APIs/UI (Phase 9).
- Coupon redemption, checkout, order, payment, refund, and logistics.
- Scheduled/future publication, external email/SMS/push providers, multiple messages per campaign, Page Builder, and message templates beyond the fixed title/body fields.
- Redis, Kafka, microservices, a worker lease, `SKIP LOCKED`, or a separate snapshot-job state machine.
- A new database migration unless code inspection proves the approved V1 schema is insufficient. Never edit an already-applied migration.

### 3.3 Proposed UI clarification for owner review

The approved PRD requires Admins to configure one message and operate the campaign lifecycle, while the Phase 7 implementation-plan row currently names APIs/services and worker deliverables rather than screens. This plan proposes a bounded extension to the existing Admin Campaign workspace:

- Message title/body create/edit for a DRAFT campaign.
- Publish action with clear validation errors and the zero-audience warning.
- Pause action for an ACTIVE campaign.
- Delivery status display and manual Retry action only when status is `FAILED`.

This is a proposal for owner approval, not an implementation authorization. It must not grow into a general page builder or message-template system. Member inbox presentation is not added by this clarification; Phase 7 guarantees the inbox APIs, and any Member Portal UI scope must remain consistent with the Phase 8 plan unless the owner explicitly changes sequencing.

## 4. Existing API contract

Use the existing OpenAPI paths and payloads. The API base prefix is `/api/v1` in the running application.

| Method | Path | Auth | Phase 7 behavior |
|---|---|---|---|
| PUT | `/api/v1/admin/campaigns/{campaignId}/message` | ADMIN + CSRF | Create/update the single message while DRAFT |
| GET | `/api/v1/admin/campaigns/{campaignId}/message` | ADMIN | Retrieve message configuration |
| POST | `/api/v1/admin/campaigns/{campaignId}/publish` | ADMIN + CSRF | Atomically snapshot audience, activate campaign, and create delivery job |
| POST | `/api/v1/admin/campaigns/{campaignId}/pause` | ADMIN + CSRF | Pause campaign without cancelling committed delivery |
| GET | `/api/v1/admin/campaigns/{campaignId}/delivery-status` | ADMIN | Retrieve job progress and safe error state |
| POST | `/api/v1/admin/campaigns/{campaignId}/delivery/retry` | ADMIN + CSRF | Retry a FAILED job from its last committed cursor |
| GET | `/api/v1/member/messages` | MEMBER | Paginated current-member inbox |
| GET | `/api/v1/member/messages/{messageId}` | MEMBER | Read a message delivered to the current member |

No new endpoint is authorized by this plan. Preserve existing response envelopes, status codes, error codes, and OpenAPI definitions. If the implementation needs a contract change, stop and obtain approval before changing API/OpenAPI.

## 5. Transaction and lifecycle invariants

### 5.1 Publish transaction

The following effects must commit together or not at all:

1. Lock and load the campaign in a way that prevents concurrent lifecycle changes.
2. Validate DRAFT status and `starts_at <= now() < ends_at`.
3. Validate the single message and at least one configured coupon with `total_quantity >= 1`.
4. Resolve the effective audience through the Audience module's application-level interface; do not access its repositories directly.
5. Insert the complete immutable audience snapshot in a set-based operation where practical, using one publication timestamp.
6. Transition the campaign to `ACTIVE` and set `published_at`.
7. Insert exactly one delivery job: `PENDING` when the snapshot is non-empty, otherwise `COMPLETED`.

The worker must not observe or process a partially committed publication. A failure at any step rolls back the snapshot, campaign transition, and job together.

### 5.2 Pause/claim serialization

Pause must update the same campaign row that Phase 6 ClaimCoupon locks with `SELECT ... FOR SHARE`. Do not reverse lock order: campaign row first, then any coupon/job rows. A claim that acquires its shared lock first is ordered before Pause; claims acquiring the lock after Pause commits observe `PAUSED` and fail with the existing business error.

### 5.3 Delivery cursor and idempotency

- The snapshot is immutable after publication.
- Each batch insert and cursor advance are one transaction.
- The cursor identifies the last fully committed snapshot member; the next batch starts strictly after it in the deterministic member ordering chosen by the implementation.
- A crash before batch commit leaves both inbox inserts and cursor unchanged; a crash after commit resumes after the committed cursor.
- Unique constraints on `(campaign_id, member_id)` remain the final duplicate-delivery guard.
- Delivery continues for a committed job even if the campaign is paused or ended.

## 6. Testing and verification requirements

### 6.1 Message configuration and lifecycle APIs

- Anonymous and MEMBER access are rejected for Admin message, publish, pause, delivery status, and retry operations.
- ADMIN can create/read/update one message for a DRAFT campaign; writes require CSRF.
- Invalid title/body, missing message, missing coupon, invalid campaign window, already-published campaign, and invalid lifecycle transitions return documented errors.
- A campaign cannot have a second message; cross-campaign identifiers cannot expose or mutate another campaign's data.
- Pause rejects invalid states and blocks new claims after it commits.

### 6.2 Atomic publication

- A successful publish creates one immutable snapshot, sets ACTIVE/publishedAt, and creates exactly one job.
- Invalid publication leaves no partial snapshot, ACTIVE transition, or delivery job.
- Zero audience publishes successfully with `NO_AUDIENCE_MEMBERS`, a `COMPLETED` job, and zero inbox rows.
- Concurrent Publish attempts cannot create duplicate snapshots/jobs or publish twice.
- Audience snapshot uses publication-time evaluation and does not change when a saved segment is later edited.

### 6.3 Worker correctness and recovery

- Non-empty jobs process in batches of 100 and reach COMPLETED with counts/cursor consistent with snapshot and inbox rows.
- Batch failure rolls back inbox rows and cursor together.
- Retry sequence is initial attempt plus 1s/2s/4s retries; after four total failures status becomes FAILED.
- A successful batch resets `attemptCount`; a manual retry is allowed only from FAILED and resumes from the last committed cursor.
- Restart recovery changes interrupted PROCESSING to PENDING and does not duplicate previously committed inbox rows.
- Pause does not cancel a committed PENDING/PROCESSING job.
- Duplicate worker triggers/restarts do not create duplicate member inbox messages.

### 6.4 Member inbox and security

- MEMBER sees only their own delivered messages; ADMIN/anonymous access follows the API contract.
- Pagination and ordering match the specification.
- Message detail cannot be read by a member who was not delivered that campaign message.
- CSRF, authentication, role boundaries, and safe error logging are verified.

### 6.5 Regression

- Preserve all Phase 1–6 tests and API suites.
- Run `./mvnw -f backend/pom.xml clean verify` with PostgreSQL/Testcontainers available.
- Run frontend tests, typecheck, and production build if frontend code changes.
- Run the Phase 7 API verification suite and all previous API suites.
- Verify clean local startup and the existing Admin Audience/Campaign/Coupon flows.

## 7. Exit criteria

- [ ] Existing message APIs match API Specification/OpenAPI.
- [ ] Exactly one message per campaign; message writes are DRAFT-only.
- [ ] Publish atomically commits snapshot, ACTIVE transition, and one delivery job.
- [ ] Zero-member publication completes immediately with the documented warning and no inbox rows.
- [ ] Pause/claim lock ordering matches Phase 6 and new claims are blocked after Pause commits.
- [ ] Pause does not cancel committed delivery.
- [ ] Worker batches, cursor, retry, FAILED state, manual retry, and restart recovery meet the approved contract.
- [ ] Member inbox APIs enforce principal-based isolation.
- [ ] PostgreSQL integration, publication/worker concurrency, API, and regression tests pass.
- [ ] API/OpenAPI, error codes, database docs (only if schema changes), README, implementation plan, and report are synchronized.
- [ ] Owner formally accepts Phase 7.

## 8. Dependencies and implementation sequence

1. Re-read the authoritative baseline and inspect Phase 5 Campaign, Phase 4 Audience, and Phase 6 Coupon application interfaces.
2. Inspect existing message, delivery-job, snapshot, and inbox schema constraints; confirm whether a migration is genuinely required.
3. Implement message configuration use cases/repository/controller using the existing contract.
4. Implement PublishCampaign transaction and PauseCampaign lock protocol.
5. Implement delivery job orchestration, bounded worker, cursor/retry state transitions, and startup recovery.
6. Implement delivery status/manual retry and member inbox read APIs.
7. If the owner approves Section 3.3, implement only the bounded Admin Campaign workspace message/lifecycle/delivery controls.
8. Add unit, PostgreSQL integration, concurrency, restart/recovery, API, and frontend regression tests.
9. Update API/OpenAPI/error docs only for approved clarifications; synchronize operational and phase documentation.
10. Submit the Phase 7 implementation report and ZIP for owner verification. Do not begin Phase 8 until Phase 7 is formally accepted.

## 9. Approval gate

Phase 6 was formally accepted by the owner on 2026-11-27 based on the owner-reported verification of backend startup, Maven verification, frontend tests/typecheck/build/dev, Phase 6 coupon/claim API verification, and Admin coupon UI create/edit. The source ZIP does not contain a dedicated high-contention coupon inventory integration test; retain this as a documented hardening item for Phase 10 rather than claiming that a race test was executed.

**Owner review required:**

- [ ] Approve `PHASE_7_PLAN.md` as written, or request changes.
- [ ] Approve or reject the bounded Admin message/publish/pause/delivery UI clarification in Section 3.3.
- [ ] Explicitly authorize Phase 7 implementation.

A Phase 7 plan approval alone does not authorize code changes. Implementation begins only after explicit owner authorization.
