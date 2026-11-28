# Implementation Plan

**Status:** Approved — v1.5.0 baseline accepted; Phases 0–9 complete and formally owner-accepted; Phase 10
implementation delivered and awaiting owner verification/acceptance. **Scope:**
Deliver the
approved MVP in incremental, verifiable phases.

## 1. Execution principles

- Implement one phase at a time; do not generate the whole application in one pass.
- Each phase must include tests and a demonstrable result.
- A phase is complete only when its exit criteria pass and the owner accepts the result.
- Do not begin a later phase while a blocking defect or unresolved design question remains in an earlier phase.
- Keep documentation synchronized with implementation; if code and docs differ, stop and resolve the discrepancy.
- A planning document is not implementation authorization. The owner must explicitly approve a phase plan and authorize
  development before code changes begin.

## 2. Phase plan

### Current execution gate (2026-11-28)

- **Phase 0 — Design baseline:** COMPLETE.
- **Phase 1 — Repository foundation:** COMPLETE — formally accepted by the owner on 2026-11-26.
- **Phase 2 — Database foundation:** COMPLETE — formally accepted by the owner on 2026-11-26.
- **Phase 3 — Identity and security:** COMPLETE — formally accepted by the owner on 2026-11-27; see
  `PHASE_3_IMPLEMENTATION_REPORT.md`.
- **Phase 4 — Member and audience:** COMPLETE and formally owner-accepted on 2026-11-27. See
  `PHASE_4_PLAN.md` and `PHASE_4_IMPLEMENTATION_REPORT.md`.
- **Phase 5 — Campaign draft management:** COMPLETE and formally owner-accepted on 2026-11-27; see
  `PHASE_5_PLAN.md` and `PHASE_5_IMPLEMENTATION_REPORT.md`.
- **Phase 6 — Coupon configuration and claim backend:** COMPLETE and formally owner-accepted on 2026-11-27 after
  owner-reported backend, frontend, API, and Admin Coupon UI verification. See `PHASE_6_PLAN.md` and
  `PHASE_6_IMPLEMENTATION_REPORT.md`. The dedicated high-contention Coupon inventory race test was delivered in Phase
  10; owner-run execution is pending.
- **Phase 7 — Message, publication orchestration, and delivery:** COMPLETE and formally owner-accepted on 2026-11-27.
  Owner reported Spring Boot startup, Maven clean verify, `npm run dev`, script automation tests, and API verification
  (23 PASS, 0 FAIL). The final review corrections (removing `SKIP LOCKED`, documenting the single-instance constraint,
  and fail-fast startup recovery) are included in the accepted package. See `PHASE_7_PLAN.md`,
  `PHASE_7_IMPLEMENTATION_REPORT.md`, and `PHASE_7_OPERATIONS.md`.
- **Phase 8 — Landing page and engagement:** COMPLETE — formally owner-accepted on 2026-11-28. The owner reported
  Maven `clean verify`, Spring Boot startup, `npm run dev`, Phase 8 API verification `all`, and successful Member UI
  coupon claim. See `PHASE_8_IMPLEMENTATION_REPORT.md` and `PHASE_8_ACCEPTANCE.md`.
- **Phase 9 — Analytics:** COMPLETE — formally owner-accepted on 2026-11-28 after the owner confirmed successful
  Phase 9 testing and approved the changes. See `PHASE_9_IMPLEMENTATION_REPORT.md` and `PHASE_9_ACCEPTANCE.md`.
- **Phase 10 — Hardening and delivery:** owner-authorized on 2026-11-28. Implementation artifacts are delivered;
  owner-run verification and formal acceptance remain pending. See `PHASE_10_PLAN.md`,
  `PHASE_10_IMPLEMENTATION_REPORT.md`, and `PHASE_10_ACCEPTANCE.md`.
- Phase 4 acceptance enabled Phase 5 planning; the owner explicitly approved the Phase 5 plan and authorized
  implementation on 2026-11-27. Phase 5 acceptance enabled Phase 6 planning; the owner explicitly authorized Phase 6
  implementation and approved its bounded Admin coupon UI clarification on 2026-11-27. The owner accepted Phase 6 on
  2026-11-27, explicitly authorized Phase 7 implementation, accepted Phase 7, and explicitly instructed Phase 8
  implementation on 2026-11-27.

| Phase                                               | Scope                                                                                                                               | Main deliverables                                                                                                                                          | Exit criteria                                                                                                                                                                                                                                                                                           |
|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0. Design baseline                                  | Review and approve the v1.5.0 Design Pack                                                                                           | Approved PRD, architecture, API/OpenAPI, database/ERD, security, accepted ADRs, and phase plan                                                             | **Complete (2026-11-26):** owner approved the complete baseline, PRD is `Ready for Development`, no blocking open questions remained, and Phase 1 was authorized                                                                                                                                        |
| 1. Repository foundation                            | Backend/frontend scaffolding and local environment                                                                                  | Spring Boot skeleton, React+TS skeleton, build scripts, local PostgreSQL, README                                                                           | **Complete and owner-accepted (2026-11-26):** backend build/test, frontend checks/build, PostgreSQL health, backend health, and frontend display verified by owner                                                                                                                                      |
| 2. Database foundation                              | Initial schema and seed strategy                                                                                                    | Flyway migrations for all v1.5 tables/constraints, fictional admin/member seed data, repository integration                                                | **Complete and owner-accepted (2026-11-26):** Flyway V1/V2 applied; all 12 tables, seed data, constraints/indexes verified; 15 backend tests and 4 frontend tests passed; typecheck/build passed                                                                                                        |
| 3. Identity and security                            | Login, JWT cookies, refresh rotation, logout, roles, CSRF                                                                           | Auth endpoints, token-family persistence, CSRF config, frontend auth flow                                                                                  | **Complete and owner-accepted (2026-11-27):** backend 30 tests pass; API suite 26/26; frontend 8/8, typecheck/build pass; browser auth/session/role checks pass.                                                                                                                                        |
| 4. Member and audience                              | Member profile, audience rule validation/evaluation, reusable segments, reusable-segment preview                                    | Audience use cases and APIs, admin audience UI, canonical field/operator validator; evaluator supports Segment Rules + Campaign Rules composition          | Full matrix tests pass; AND/NULL behavior correct; reusable-segment preview count/sample correct; composition semantics tested. Campaign-scoped preview integration is deferred to Phase 5. Snapshot persistence remains Phase 7.                                                                       |
| 5. Campaign draft management                        | Draft create/edit/list/detail, campaign read model, lifecycle validation, campaign-scoped audience preview                          | Campaign CRUD APIs and admin screens; fixed landing-page content fields; existing campaign audience-preview API integrated with Phase 4 Audience evaluator | **Complete and owner-accepted (2026-11-27):** Maven verify/startup, frontend install/tests/typecheck/build/dev, API suite 29/29, Audience composition, Campaign UI flow, and boundary tests all passed; no publish endpoint/worker                                                                      |
| 6. Coupon configuration and claim backend           | Coupon CRUD/configuration, inventory reservation, claim transaction, claim list                                                     | Coupon APIs, atomic claim service, idempotency, server-side claim audit, Admin Campaign workspace coupon UI                                                | **Complete and owner-accepted (2026-11-27):** owner reported backend startup/Maven verify, frontend tests/typecheck/build/dev, Phase 6 API suite, and Admin Coupon create/edit UI all passing. Dedicated high-contention Coupon race coverage is delivered in Phase 10; owner-run execution is pending. |
| 7. Message, publication orchestration, and delivery | Single message configuration, publish, immutable snapshot, delivery job/worker/retry, member inbox APIs, bounded Admin lifecycle UI | Message APIs, publish/pause orchestration, snapshot + job transaction, batch worker, delivery status/manual retry, inbox APIs, Admin controls              | **Complete and owner-accepted (2026-11-27):** owner reported Spring Boot startup, Maven clean verify, `npm run dev`, script automation tests, and Phase 7 API suite 23/23 PASS. Final code-review corrections are included. Deployment is limited to one backend instance; see `PHASE_7_OPERATIONS.md`. |
| 8. Landing page and engagement                      | Public landing page, member claim UI, impression/click/page-view events                                                             | Landing route, eligibility/claim states, sign-in return flow, event ingestion                                                                              | **COMPLETE — owner-accepted (2026-11-28):** owner reported Maven clean verify, backend startup, frontend dev startup, Phase 8 API suite `all`, and successful Member coupon claim. See Phase 8 report/acceptance.                                                                                       |
| 9. Analytics                                        | Campaign-level metrics and coupon-level reporting per PRD Sections 6.8 and 7                                                        | Analytics query/API, Admin campaign analytics page, coupon report, API verification suite                                                                  | **COMPLETE — owner-accepted (2026-11-28):** owner confirmed Phase 9 testing completed successfully and approved the changes.                                                                                                                                                                            |
| 10. Hardening and delivery                          | Coupon inventory contention, cross-phase regression/E2E, security boundaries, operational readiness, reproducible demo              | Dedicated concurrency tests, end-to-end verification, regression/security checks, runbook and documentation updates                                        | Implementation delivered; owner verification pending (2026-11-28). See Phase 10 implementation report and acceptance guide.                                                                                                                                                                             |

## 3. Phase details and dependencies

## Phase 8 — Member landing page and engagement

**Phase status:** COMPLETE — formally owner-accepted on 2026-11-28. Phase 8 implementation was explicitly authorized
on 2026-11-27; the owner-reported verification and approval are recorded in `PHASE_8_IMPLEMENTATION_REPORT.md` and
`PHASE_8_ACCEPTANCE.md`.

The owner-authorized scope is defined in `PHASE_8_PLAN.md` and is subordinate to the approved PRD, API/OpenAPI, database
design, architecture, and accepted Phase 1–7 implementation. The implementation report and owner acceptance guide are
`PHASE_8_IMPLEMENTATION_REPORT.md` and `PHASE_8_ACCEPTANCE.md`. Existing Phase 6 claim APIs and V1 database tables are
reused; no new schema migration or claim/delivery behavior change was introduced.

Implementation and owner acceptance are complete. The acceptance guide remains as a repeatable verification reference.
Phase 9 implementation is delivered according to the owner-approved `PHASE_9_PLAN.md`; owner verification and acceptance
remain pending.

### Phase 1 — Repository foundation

**Phase status:** COMPLETE — owner formally accepted Phase 1 on 2026-11-26.

- [x] Create backend and frontend project scaffolds.
- [x] Choose exact compatible framework/library versions and record the selection rationale.
- [x] Configure local PostgreSQL through Docker Compose.
- [x] Add health/readiness endpoints and a minimal frontend shell that calls backend health through the Vite proxy.
- [x] Verify backend build and tests on JDK 21 (`./mvnw -f backend/pom.xml verify`; owner-reported BUILD SUCCESS, 1 test
  passed).
- [x] Verify frontend tests, TypeScript typecheck, and production build (`npm run test`: 4 tests passed;
  `npm run typecheck`: passed; `npm run build`: passed).
- [x] Start PostgreSQL and verify its Compose health check (owner reported PostgreSQL container healthy).
- [x] Start backend and frontend and verify health connectivity (owner reported backend health UP and frontend page
  displayed).
- [x] Obtain owner acceptance (2026-11-26).
- No business features were implemented in this phase; database persistence and Flyway were Phase 2 scope.

### Phase 2 — Database foundation

**Phase status:** COMPLETE — owner formally accepted Phase 2 on 2026-11-26.

- [x] Connect Spring Boot to PostgreSQL using externalizable datasource settings.
- [x] Add Flyway and PostgreSQL JDBC dependencies.
- [x] Add versioned migrations for all 12 tables, documented constraints, and indexes.
- [x] Add fictional deterministic Admin / Member seed records.
- [x] Add PostgreSQL Testcontainers integration tests for schema and key integrity constraints.
- [x] Execute Maven verification and PostgreSQL integration tests in the owner's Docker-enabled environment.
- [x] Verify Flyway V1/V2, all 12 business tables, and seeded data.
- [x] Export and review constraints/indexes against the approved database design.
- [x] Verify frontend regression (`npm ci`, tests, typecheck, build).
- [x] Record owner acceptance (2026-11-26) in `PHASE_2_IMPLEMENTATION_REPORT.md`.
- No Redis/Kafka or second persistence technology was introduced.

Phase 2 did not implement authentication, domain repositories/services, CRUD APIs, audience evaluation, coupon claim
workflows, or message delivery workers; those remain later-phase scope.

### Phase 3 — Identity and security

**Phase status:** COMPLETE — owner formally accepted Phase 3 on 2026-11-27; see `PHASE_3_IMPLEMENTATION_REPORT.md`.

The delivered Phase 3 scope follows the approved Security Design and API Specification:

- Implement password verification and login using the existing `users` records.
- Implement short-lived JWT access and refresh cookies; keep token values out of browser storage.
- Persist only SHA-256 hashes of refresh JWTs in the existing `refresh_token_sessions` table.
- Implement token-family rotation, one-time token consumption, concurrent refresh protection, and family revocation on
  reuse.
- Implement logout that revokes the presented refresh-token family and clears cookies even if the access token has
  expired.
- Implement `/auth/csrf`, `/auth/login`, `/auth/refresh`, `/auth/logout`, and `/auth/me` according to the approved API
  contract.
- Enforce `ADMIN` / `MEMBER` / anonymous access boundaries and CSRF on state-changing cookie-authenticated requests.
- Add frontend login/session state and a single-flight refresh path without storing authentication tokens in
  local/session storage.
- Verify cookie flags, CORS origin allowlist, CSRF behavior, token lifecycle, role boundaries, and security logging.

Phase 3 implementation is complete within the approved scope. The owner completed verification and formally accepted
Phase 3 on 2026-11-27. The Phase 4 plan was approved and implementation explicitly authorized on 2026-11-27;
implementation passed
owner-run technical verification and was formally accepted by the owner on 2026-11-27.

### Phase 4 — Member and audience

**Plan status:** APPROVED by the owner on 2026-11-27.  
**Implementation status:** COMPLETE and formally owner-accepted on 2026-11-27; see
`PHASE_4_IMPLEMENTATION_REPORT.md`.

- Implement the authenticated member profile lookup using the identity principal; never accept a client-selected member
  ID.
- Implement the canonical field/type/operator matrix and allowlisted rule model/evaluator.
- Implement saved segment CRUD and reusable-segment preview.
- Implement and test audience composition in the reusable evaluator: Segment Rules AND Campaign Rules; either one alone
  is valid, and at least one definition is required.
- Do not implement the campaign-scoped preview HTTP endpoint in Phase 4; that endpoint is integrated in Phase 5 after
  the Campaign read model exists. Preserve its existing API contract.
- Do not persist the final campaign snapshot in this phase; publication snapshot + delivery job are one transaction in
  Phase 7.

### Phase 5 — Campaign draft management

**Plan status:** OWNER-APPROVED on 2026-11-27; see `PHASE_5_PLAN.md`.  
**Implementation status:** COMPLETE and formally owner-accepted on 2026-11-27; see
`PHASE_5_IMPLEMENTATION_REPORT.md`. The owner reported backend startup/Maven verification, frontend
install/tests/typecheck/build/dev,
Phase 5 API suite (29 PASS, 0 FAIL), Campaign Audience composition, Campaign management UI flow, and boundary tests all
passing.

- Implement draft create/update/list/detail and campaign date validation.
- Implement fixed landing-page headline/body fields.
- Integrate the already-specified `POST /api/v1/admin/campaigns/{campaignId}/audience-preview` endpoint after
  Campaign CRUD/read access is available. Campaign loads its own campaign data and invokes the Audience module's
  application-level evaluator interface; do not access Audience repositories directly. Keep the API path and
  request/response contract unchanged.
- Publish and Pause endpoints are integrated in Phase 7 after coupon/message prerequisites are available.

### Phase 6 — Coupon configuration and claim backend

**Plan status:** OWNER-APPROVED FOR IMPLEMENTATION; see `PHASE_6_PLAN.md`.  
**Implementation status:** COMPLETE AND OWNER-ACCEPTED on 2026-11-27; see `PHASE_6_IMPLEMENTATION_REPORT.md`.

- Implement coupon configuration and member claim-list APIs.
- Implement atomic conditional inventory update and claim insert in one transaction.
- Until Publish is integrated in Phase 7, test the claim service with deterministic fixtures for an already-ACTIVE
  campaign and its audience snapshot.
- Add database uniqueness, idempotency replay, and server-generated claim-attempt audit behavior.
- Owner-reported verification passed: backend startup, Maven clean verify, frontend tests/typecheck/build/dev, Phase 6
  API suite, and Admin Coupon UI create/edit.
- Dedicated high-contention last-inventory Coupon race coverage is delivered in Phase 10; owner-run execution is
  pending.

### Phase 7 — Message, publication, and delivery

**Plan status:** OWNER-AUTHORIZED; see `PHASE_7_PLAN.md`.  
**Implementation status:** COMPLETE and formally owner-accepted (2026-11-27). Owner-reported verification: Spring Boot
startup, Maven clean verify, `npm run dev`, script automation test, and API suite 23/23 PASS. See
`PHASE_7_IMPLEMENTATION_REPORT.md`.

- Implement one message definition per campaign.
- Implement `PublishCampaign` and `PauseCampaign`: validate draft, active time window, message, positive-quantity
  coupon, resolve and persist complete audience snapshot, transition to ACTIVE, and create delivery job in one
  transaction. Pause uses the same campaign-row lock protocol as ClaimCoupon.
- If audience count is zero, create the delivery job as `COMPLETED`; otherwise `PENDING`.
- Implement one worker with batch size 100, cursor, three automatic retries after the initial attempt (1s/2s/4s
  backoff), FAILED state, status API, and manual retry from last committed cursor.
- Pause must not cancel a committed job.

### Phase 8 — Landing page and events

- Implemented `/campaigns/:campaignId` as public fixed-template content.
- Published content remains readable when paused/ended, with new claims disabled; MEMBER eligibility and claim state are
  server-derived.
- Login return-to-page behavior validates an internal campaign path and does not auto-claim.
- Authenticated impression/click/landing-page events are ingested idempotently; claim-attempt audit remains
  server-generated.
- Phase 8 was formally accepted by the owner on 2026-11-28 after the owner-reported Maven, startup, API-suite, and
  Member coupon-claim verification recorded in `PHASE_8_IMPLEMENTATION_REPORT.md`.

### Phase 9 — Analytics

- Implemented the owner-approved scope in `PHASE_9_PLAN.md`: ADMIN analytics endpoint, campaign-lifetime metrics, coupon
  summaries, Admin UI, API verification script, and synchronized API/OpenAPI docs.
- No schema migration was introduced.
- Owner confirmed successful Phase 9 testing and formally accepted the changes on 2026-11-28; see
  `PHASE_9_IMPLEMENTATION_REPORT.md` and `PHASE_9_ACCEPTANCE.md`.

### Phase 10 — Hardening

- Execute all PRD scenarios and security/concurrency suites.
- Review logs, health checks, error responses, and operational documentation.
- Verify fresh-clone setup from README.

## 4. Cross-phase acceptance checklist

- [ ] No unapproved scope expansion.
- [ ] No hidden dependencies or infrastructure added.
- [ ] Tests pass, including PostgreSQL integration tests for correctness-critical behavior.
- [ ] API contract and OpenAPI file updated when APIs change.
- [ ] Database design/ERD and Flyway migration history are consistent.
- [ ] Security and CSRF behavior verified in browser flow.
- [ ] README commands work from a clean checkout.
- [ ] Owner accepts phase before next phase begins.

## 5. Not part of the plan

No scheduled message delivery, additional channels, coupon redemption, checkout, orders, payments, logistics,
microservices, Redis, Kafka, or third-party production integrations are included in the MVP plan.

## 6. Phase acceptance and authorization gates

Phase 1 and Phase 2 are formally accepted by the owner on 2026-11-26; their results are recorded in
`PHASE_1_IMPLEMENTATION_REPORT.md` and `PHASE_2_IMPLEMENTATION_REPORT.md`. Phases 3 and 4 are formally accepted on
2026-11-27. Phase 5 was formally accepted on 2026-11-27 after the owner reported successful backend startup and Maven
verification, frontend checks, Phase 5 API suite (29 PASS, 0 FAIL), Campaign Audience composition, Campaign management
UI flow, and boundary tests; see `PHASE_5_IMPLEMENTATION_REPORT.md`.

The owner explicitly approved `PHASE_6_PLAN.md`, authorized Phase 6 implementation, and approved the bounded Admin
coupon-configuration UI clarification on 2026-11-27. On 2026-11-27, the owner explicitly accepted the delivered Phase 6
changes after reporting successful backend startup, Maven clean verify, frontend tests/typecheck/build/dev, Phase 6 API
verification, and Admin Coupon create/edit UI. A dedicated high-contention Coupon race test was subsequently delivered
in Phase 10. Phase 7 was formally accepted on 2026-11-27; see
`PHASE_7_IMPLEMENTATION_REPORT.md`. Phase 8 was formally accepted on 2026-11-28; see
`PHASE_8_IMPLEMENTATION_REPORT.md` and `PHASE_8_ACCEPTANCE.md`.

## Phase 9 — Campaign analytics and coupon reporting

**Phase status:** COMPLETE — FORMALLY OWNER-ACCEPTED (2026-11-28).

`PHASE_9_PLAN.md` is derived from the approved PRD metric definitions (Sections 6.8 and 7.1–7.3), existing V1 data
sources, and the accepted Phase 6–8 behavior. The delivered implementation adds an ADMIN-only campaign analytics read
endpoint
and the Admin route `/admin/campaigns/:campaignId/analytics`. It reports campaign lifetime metrics and coupon-level
claim performance. Event counts and distinct-member counts remain separate; delivery comes from `member_messages`,
successful claims from `coupon_claims`, claim attempts from server audit events, and zero-denominator rates are `null`.
No attribution, date-range reporting, exports, new event types, or schema migration is planned by default.

The owner approved `PHASE_9_PLAN.md` and explicitly authorized implementation on 2026-11-28. The owner confirmed
successful Phase 9 testing and approved the changes on 2026-11-28. No schema migration was introduced.

The owner explicitly authorized Phase 10 implementation on 2026-11-28 by requesting implementation based on the
`phase10-ready-to-develop` package. The concurrency integration tests and verification/operations documentation are
delivered. Maven/Testcontainers and frontend dependency verification could not be executed in the implementation
environment because Docker is unavailable and outbound dependency downloads are blocked; see
`PHASE_10_IMPLEMENTATION_REPORT.md`. Phase 10 remains pending owner-run verification and formal acceptance.
