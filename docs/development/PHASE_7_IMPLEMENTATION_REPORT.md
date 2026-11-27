# Phase 7 Implementation Report — Message, Publication Orchestration, and Delivery

**Plan:** `PHASE_7_PLAN.md`  
**Owner authorization:** Explicitly granted 2026-11-27.  
**Implementation status:** Delivered in source; full verification pending.  
**Owner acceptance:** Not yet recorded.

## 1. Delivered scope

### Campaign message configuration

- Added `PUT` and `GET /api/v1/admin/campaigns/{campaignId}/message`.
- Enforces one message per campaign through the existing V1 unique constraint.
- Message title is required (maximum 160 characters); body is required (maximum 10,000 characters).
- Message writes lock the campaign row and are allowed only while the campaign is `DRAFT`.
- Existing ADMIN authorization and cookie CSRF protections apply; no new security mechanism or endpoint was introduced.

### Atomic publication and pause

- Added the existing Publish and Pause API paths from the approved OpenAPI contract.
- Publish locks the campaign row, validates DRAFT state and the start-inclusive/end-exclusive campaign window, requires a configured message and at least one coupon, and resolves the effective audience through the Audience application boundary.
- Audience evaluation uses the actual publication date in `Asia/Taipei`, including saved-segment AND campaign-rule composition and existing NULL semantics.
- The complete immutable audience snapshot, ACTIVE transition/`published_at`, and exactly one delivery job are committed in one transaction.
- Empty audience publication succeeds with `NO_AUDIENCE_MEMBERS` and a `COMPLETED` delivery job.
- Pause updates the campaign row with an exclusive lock, serializing against Phase 6 ClaimCoupon's shared lock. It does not cancel or gate an already committed delivery job.

### Asynchronous delivery

- Added one in-process scheduled worker. It reads only the persisted audience snapshot, processes batches of 100, and does not re-evaluate audience rules or inspect campaign status.
- Inbox inserts and cursor advancement are committed in one transaction; `(campaign_id, member_id)` uniqueness remains the final duplicate-delivery guard.
- Delivery status reports snapshot count, processed inbox count, cursor, retry count, sanitized error, and FAILED-only retryability.
- Automatic retry policy is initial attempt plus three retries with 1s/2s/4s backoff. A fourth total failure moves the job to `FAILED`; successful batches reset the retry count.
- Startup recovery resets interrupted `PROCESSING` jobs to `PENDING`; manual retry is FAILED-only and preserves the last committed cursor.

### Member inbox APIs

- Added paginated `GET /api/v1/member/messages` and `GET /api/v1/member/messages/{messageId}`.
- Member identity is resolved from the authenticated principal. Detail lookup uses the member inbox row ID and returns not-found for another member's row.

### Admin UI

- Extended the existing Campaign workspace with DRAFT-only message configuration, Publish, ACTIVE-only Pause, delivery status/progress, and a Retry action shown only for FAILED jobs.
- Zero-audience publication warning and delivery-in-progress state are surfaced in the workspace.

### Database

- No migration was added. The approved V1 schema already contains `campaign_messages`, `campaign_audience_members`, `campaign_delivery_jobs`, and `member_messages` with the required uniqueness and foreign-key constraints.

## 2. Changed and added areas

- Backend Audience application boundary now exposes effective member-ID resolution without exposing its repository.
- New backend `message` application, infrastructure worker/transaction adapter, and presentation controller.
- Existing application bootstrap enables the single in-process scheduled worker.
- Frontend Campaign API client and `CampaignLifecycle` workspace component.
- API specification, OpenAPI, error-code documentation, testing strategy, README, implementation plan, and Phase 7 plan status synchronized.
- Added PostgreSQL integration coverage for non-empty publication/delivery/member isolation/pause and zero-audience publication.
- Added `scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh` for owner-environment API verification. It permanently publishes and pauses the disposable campaign supplied to it.

## 3. Runtime SQL correction (2026-11-27)

- Owner-reported PostgreSQL logs showed `BadSqlGrammarException` in `DeliveryWorkerTransactions.claimNextJob()` with `operator does not exist: timestamp with time zone <= interval`.
- Root cause: the previous predicate put the bound `:now` parameter on the right side of timestamp arithmetic (`updated_at <= :now - interval`). PostgreSQL inferred the prepared-statement parameter in an incompatible operator context, leaving a timestamp-to-interval comparison.
- Corrected the predicate to add the retry interval to the timestamp column and compare the resulting timestamp with the bound timestamp: `updated_at + CASE attempt_count WHEN 1 THEN INTERVAL '1 second' WHEN 2 THEN INTERVAL '2 seconds' ELSE INTERVAL '4 seconds' END <= :now`. This preserves the documented 1s/2s/4s backoff while ensuring both sides of `<=` are timestamps.
- Removed the previously added `FOR UPDATE SKIP LOCKED` because it is explicitly prohibited by the owner-approved Phase 7 plan. Candidate selection uses `FOR UPDATE` inside the short claim transaction, followed by the existing conditional `PENDING` to `PROCESSING` update. The MVP supports exactly one backend instance; multi-instance worker coordination is out of scope.
- The existing PostgreSQL publication/delivery integration test exercises the initial claim path, but automated execution could not be completed in this build environment (see verification table). Retry-delay/failure-injection behavior still requires owner-environment verification.


### Backend startup classpath correction

- Owner-reported startup failed while Spring inspected `AudienceRuleValidator`: the compiled class referenced `com.example.marketingplatform.audience.domain.InvalidAudienceRuleException`, but that class was absent from the runtime classpath (`NoClassDefFoundError` / `ClassNotFoundException`).
- Inspection of the delivered source archive confirms that both `AudienceRuleValidator.java` and `InvalidAudienceRuleException.java` are present in the correct `backend/src/main/java` package paths. The failure therefore indicates that the local compiled output/runtime classpath was incomplete or stale; it is not evidence that the exception source file is missing from the ZIP.
- Added `scripts/dev/run-backend.zsh`, which runs `./mvnw -f backend/pom.xml clean compile` before launching `spring-boot:run`. Updated the README startup instructions to use this clean-build launcher. This guarantees the domain exception is compiled into `backend/target/classes` before Spring starts.
- Owner recovery without the helper script: from the project root run `./mvnw -f backend/pom.xml clean compile`, then `./mvnw -f backend/pom.xml spring-boot:run`.
- Maven execution could not be performed in this packaging environment because the pinned Maven distribution is not cached and outbound DNS/download is unavailable. The owner must verify startup with the new launcher.

### API verification fixture precondition correction (2026-11-27)

- Owner execution reached publication but received HTTP 409 `CAMPAIGN_NOT_PUBLISHABLE` with the service's documented message that the campaign must be DRAFT and within its active time window.
- The backend rule is intentional: publication is allowed only when `startsAt <= now < endsAt`; this is specified in `PHASE_7_PLAN.md` and the API specification. The reported response does not indicate a publication transaction defect; it indicates that the supplied disposable campaign's time window did not include the request time (or one of the schedule values was missing).
- Updated the Phase 7 API verification suite to check the campaign's `startsAt`/`endsAt` immediately after confirming it is DRAFT, before reading/writing message configuration. An inactive or missing window now prints the exact schedule and current UTC instant, then exits without mutating the campaign.
- The suite deliberately does not rewrite the campaign schedule automatically: that would silently alter an existing user fixture. Set `startsAt` to a time at or before now and `endsAt` to a future time on a fresh disposable DRAFT campaign before rerunning.

### API verification script correction

- Owner execution of `phase7-message-publication-delivery-verification.zsh` reached successful publication (`HTTP 202`) and then stopped with `read-only variable: status` at the delivery polling block.
- In zsh, `status` is a read-only special parameter. Renamed the script-local delivery result variable to `delivery_status` consistently in initialization, polling, completion checks, and assertion. This does not change API behavior or test expectations.
- The current build environment does not include the `zsh` executable, so a native `zsh -n` syntax check and a live rerun could not be performed here. Please rerun the API suite in the owner environment.

## 4. Verification performed in this build environment

| Check | Result | Notes |
|---|---|---|
| Frontend static tests | PASS — 14/14 | `cd frontend && npm test` |
| Maven compile | NOT RUN | Maven wrapper attempted to download pinned Maven 3.10.2; network DNS/download unavailable (`curl: Could not resolve host`). |
| Maven clean verify / PostgreSQL Testcontainers | NOT RUN | Maven distribution/dependencies unavailable; Docker CLI is not present in this build environment. |
| Frontend typecheck/build | NOT RUN | `npm ci --offline` could not find the pinned Vite package in local npm cache. A global TypeScript fallback lacks React/Node type packages and is not a valid project typecheck. |
| Phase 7 API suite | NOT RUN | Requires a running local PostgreSQL-backed application and disposable DRAFT campaign; script is included for owner execution. |
| Browser/manual UI verification | NOT RUN | Requires running backend and installed frontend dependencies. |

No claim is made that Maven, Testcontainers, frontend typecheck/build, or the API verification suite passed. The delivered ZIP is therefore an implementation handoff for owner-environment verification, not a formally accepted Phase 7 release.

## 5. Owner verification instructions

From the extracted project root, use the existing `.env.example` setup and local PostgreSQL instructions in `README.md`.

1. Install dependencies and start PostgreSQL/backend/frontend using the README.
2. Run `./mvnw -f backend/pom.xml clean verify` with Docker available for Testcontainers.
3. Run `cd frontend && npm ci && npm test && npm run typecheck && npm run build`.
4. Create a disposable DRAFT campaign whose time window includes the current time, configure at least one coupon, and configure a valid audience.
5. Run the Phase 7 API suite. It will publish and then pause the supplied campaign; do not use a campaign that must remain editable or active.

```zsh
export PHASE7_DRAFT_CAMPAIGN_ID="<disposable-draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh
```

6. In `/admin/campaigns`, select a DRAFT campaign, save the in-app message, publish, inspect delivery status, and pause an ACTIVE campaign. Verify that an already committed delivery job completes after Pause.
7. Verify the seeded member inbox via `GET /api/v1/member/messages` and verify a different member cannot retrieve another member's inbox-row ID.

## 6. Remaining exit criteria

- [ ] Maven clean verify and PostgreSQL integration tests pass in the owner environment.
- [ ] Frontend test, typecheck, and production build pass with locked dependencies installed.
- [ ] Phase 7 API verification suite passes against a disposable campaign.
- [ ] Verify worker retry timing/failure injection and restart recovery in the owner environment.
- [ ] Verify Pause/Claim lock ordering under a controlled concurrency test.
- [ ] Owner confirms UI behavior and formally accepts Phase 7.

Phase 8 must not begin until Phase 7 is fully verified and formally accepted.

### Owner-reported verification before final review corrections (2026-11-27)

The owner reported successful execution of Spring Boot startup, `./mvnw clean verify`, `npm run dev`, and the Phase 7 message/publication/delivery API suite (23 PASS, 0 FAIL). These results apply to the pre-correction package. The final code-review changes below still require a regression run in the owner environment.

### Final code-review corrections (2026-11-27)

- Removed `SKIP LOCKED` from `DeliveryWorkerTransactions.claimNextJob()` to conform to the explicit Phase 7 plan. The row lock and conditional status update remain within the claim transaction.
- Documented the operational constraint that exactly one backend instance/replica may run. Startup recovery updates all `PROCESSING` delivery jobs, so multiple live instances are unsupported and can interfere with one another.
- Hardened startup recovery failure handling in `MessageDeliveryWorker`: recovery success is logged; a recovery exception is logged with PostgreSQL/permissions/restart guidance and rethrown as an `IllegalStateException` so application startup fails rather than silently serving with potentially stranded jobs.
- Added `docs/development/PHASE_7_OPERATIONS.md` with deployment constraints, startup recovery behavior, failure response, and manual recovery guidance. Updated the Phase 7 plan, architecture, and root README to keep the constraint visible.
- No API, database schema, migration, or frontend behavior was changed.

