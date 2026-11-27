# Phase 7 Implementation Report — Message, Publication Orchestration, and Delivery

**Plan:** `PHASE_7_PLAN.md`  
**Owner authorization:** Explicitly granted 2026-11-27.  
**Implementation status:** COMPLETE.  
**Owner acceptance:** FORMALLY ACCEPTED on 2026-11-27.

## 1. Delivered scope

### Campaign message configuration

- Added `PUT` and `GET /api/v1/admin/campaigns/{campaignId}/message`.
- Enforces one message per campaign through the existing V1 unique constraint.
- Message title is required (maximum 160 characters); body is required (maximum 10,000 characters).
- Message writes lock the campaign row and are allowed only while the campaign is `DRAFT`.
- Existing ADMIN authorization and cookie CSRF protections apply; no new security mechanism or endpoint was introduced.

### Atomic publication and pause

- Added the existing Publish and Pause API paths from the approved OpenAPI contract.
- Publish locks the campaign row, validates DRAFT state and the start-inclusive/end-exclusive campaign window, requires
  a configured message and at least one coupon, and resolves the effective audience through the Audience application
  boundary.
- Audience evaluation uses the actual publication date in `Asia/Taipei`, including saved-segment AND campaign-rule
  composition and existing NULL semantics.
- The complete immutable audience snapshot, ACTIVE transition/`published_at`, and exactly one delivery job are committed
  in one transaction.
- Empty audience publication succeeds with `NO_AUDIENCE_MEMBERS` and a `COMPLETED` delivery job.
- Pause updates the campaign row with an exclusive lock, serializing against Phase 6 ClaimCoupon's shared lock. It does
  not cancel or gate an already committed delivery job.

### Asynchronous delivery

- Added one in-process scheduled worker. It reads only the persisted audience snapshot, processes batches of 100, and
  does not re-evaluate audience rules or inspect campaign status.
- Inbox inserts and cursor advancement are committed in one transaction; `(campaign_id, member_id)` uniqueness remains
  the final duplicate-delivery guard.
- Delivery status reports snapshot count, processed inbox count, cursor, retry count, sanitized error, and FAILED-only
  retryability.
- Automatic retry policy is initial attempt plus three retries with 1s/2s/4s backoff. A fourth total failure moves the
  job to `FAILED`; successful batches reset the retry count.
- Startup recovery resets interrupted `PROCESSING` jobs to `PENDING`; manual retry is FAILED-only and preserves the last
  committed cursor.

### Member inbox APIs

- Added paginated `GET /api/v1/member/messages` and `GET /api/v1/member/messages/{messageId}`.
- Member identity is resolved from the authenticated principal. Detail lookup uses the member inbox row ID and returns
  not-found for another member's row.

### Admin UI

- Extended the existing Campaign workspace with DRAFT-only message configuration, Publish, ACTIVE-only Pause, delivery
  status/progress, and a Retry action shown only for FAILED jobs.
- Zero-audience publication warning and delivery-in-progress state are surfaced in the workspace.

### Database

- No migration was added. The approved V1 schema already contains `campaign_messages`, `campaign_audience_members`,
  `campaign_delivery_jobs`, and `member_messages` with the required uniqueness and foreign-key constraints.

## 2. Changed and added areas

- Backend Audience application boundary now exposes effective member-ID resolution without exposing its repository.
- New backend `message` application, infrastructure worker/transaction adapter, and presentation controller.
- Existing application bootstrap enables the single in-process scheduled worker.
- Frontend Campaign API client and `CampaignLifecycle` workspace component.
- API specification, OpenAPI, error-code documentation, testing strategy, README, implementation plan, and Phase 7 plan
  status synchronized.
- Added PostgreSQL integration coverage for non-empty publication/delivery/member isolation/pause and zero-audience
  publication.
- Added `scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh` for owner-environment API
  verification. It permanently publishes and pauses the disposable campaign supplied to it.

## 3. Runtime SQL correction (2026-11-27)

- Owner-reported PostgreSQL logs showed `BadSqlGrammarException` in `DeliveryWorkerTransactions.claimNextJob()` with
  `operator does not exist: timestamp with time zone <= interval`.
- Root cause: the previous predicate put the bound `:now` parameter on the right side of timestamp arithmetic
  (`updated_at <= :now - interval`). PostgreSQL inferred the prepared-statement parameter in an incompatible operator
  context, leaving a timestamp-to-interval comparison.
- Corrected the predicate to add the retry interval to the timestamp column and compare the resulting timestamp with the
  bound timestamp:
  `updated_at + CASE attempt_count WHEN 1 THEN INTERVAL '1 second' WHEN 2 THEN INTERVAL '2 seconds' ELSE INTERVAL '4 seconds' END <= :now`.
  This preserves the documented 1s/2s/4s backoff while ensuring both sides of `<=` are timestamps.
- Removed the previously added `FOR UPDATE SKIP LOCKED` because it is explicitly prohibited by the owner-approved Phase
  7 plan. Candidate selection uses `FOR UPDATE` inside the short claim transaction, followed by the existing conditional
  `PENDING` to `PROCESSING` update. The MVP supports exactly one backend instance; multi-instance worker coordination is
  out of scope.
- The existing PostgreSQL publication/delivery integration test exercises the initial claim path, but automated
  execution could not be completed in this build environment (see verification table). Retry-delay/failure-injection
  behavior still requires owner-environment verification.

### Backend startup classpath correction

- Owner-reported startup failed while Spring inspected `AudienceRuleValidator`: the compiled class referenced
  `com.example.marketingplatform.audience.domain.InvalidAudienceRuleException`, but that class was absent from the
  runtime classpath (`NoClassDefFoundError` / `ClassNotFoundException`).
- Inspection of the delivered source archive confirms that both `AudienceRuleValidator.java` and
  `InvalidAudienceRuleException.java` are present in the correct `backend/src/main/java` package paths. The failure
  therefore indicates that the local compiled output/runtime classpath was incomplete or stale; it is not evidence that
  the exception source file is missing from the ZIP.
- Added `scripts/dev/run-backend.zsh`, which runs `./mvnw -f backend/pom.xml clean compile` before launching
  `spring-boot:run`. Updated the README startup instructions to use this clean-build launcher. This guarantees the
  domain exception is compiled into `backend/target/classes` before Spring starts.
- Owner recovery without the helper script: from the project root run `./mvnw -f backend/pom.xml clean compile`, then
  `./mvnw -f backend/pom.xml spring-boot:run`.
- Maven execution could not be performed in this packaging environment because the pinned Maven distribution is not
  cached and outbound DNS/download is unavailable. The owner must verify startup with the new launcher.

### API verification fixture precondition correction (2026-11-27)

- Owner execution reached publication but received HTTP 409 `CAMPAIGN_NOT_PUBLISHABLE` with the service's documented
  message that the campaign must be DRAFT and within its active time window.
- The backend rule is intentional: publication is allowed only when `startsAt <= now < endsAt`; this is specified in
  `PHASE_7_PLAN.md` and the API specification. The reported response does not indicate a publication transaction defect;
  it indicates that the supplied disposable campaign's time window did not include the request time (or one of the
  schedule values was missing).
- Updated the Phase 7 API verification suite to check the campaign's `startsAt`/`endsAt` immediately after confirming it
  is DRAFT, before reading/writing message configuration. An inactive or missing window now prints the exact schedule
  and current UTC instant, then exits without mutating the campaign.
- The suite deliberately does not rewrite the campaign schedule automatically: that would silently alter an existing
  user fixture. Set `startsAt` to a time at or before now and `endsAt` to a future time on a fresh disposable DRAFT
  campaign before rerunning.

### API verification script correction

- Owner execution of `phase7-message-publication-delivery-verification.zsh` reached successful publication (`HTTP 202`)
  and then stopped with `read-only variable: status` at the delivery polling block.
- In zsh, `status` is a read-only special parameter. Renamed the script-local delivery result variable to
  `delivery_status` consistently in initialization, polling, completion checks, and assertion. This does not change API
  behavior or test expectations.
- The current build environment does not include the `zsh` executable, so a native `zsh -n` syntax check and a live
  rerun could not be performed here. Please rerun the API suite in the owner environment.

## 4. Owner-reported final verification and acceptance

The owner reported the following successful checks against the final Phase 7 approved package on 2026-11-27:

| Verification | Result |
|---|---|
| Spring Boot startup | PASS |
| `./mvnw -f backend/pom.xml clean verify` | PASS |
| `npm run dev` | PASS |
| Script automation test | PASS |
| `scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh` | PASS — 23 PASS, 0 FAIL |

The owner explicitly stated Phase 7 testing was complete and approved the Phase 7 changes. Phase 7 is formally owner-accepted. These are owner-reported results; they are not represented as independently rerun in the documentation packaging environment.

## 5. Operational acceptance constraints

- Exactly one backend application instance/replica may run with the in-process delivery worker.
- Startup recovery is fail-fast. If recovery fails, resolve PostgreSQL connectivity/permission issues and restart the single instance using `PHASE_7_OPERATIONS.md`.
- Multi-instance worker ownership, leases, `SKIP LOCKED`, and external queue infrastructure remain outside the accepted Phase 7 scope.
- The accepted package includes the final code-review corrections: `SKIP LOCKED` removal, single-instance deployment documentation, and recovery failure handling/runbook.

Phase 7 is complete. Phase 8 planning is recorded in `PHASE_8_PLAN.md`; Phase 8 implementation remains gated on owner approval and explicit authorization.
