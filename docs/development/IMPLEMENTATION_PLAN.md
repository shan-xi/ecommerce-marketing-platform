# Implementation Plan

**Status:** Approved — v1.5.0 baseline accepted; Phases 0–4 complete and formally owner-accepted (Phase 4 accepted
2026-11-27); Phase 5 plan drafted for owner review and not yet authorized for implementation. **Scope:** Deliver the
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

### Current execution gate (2026-11-27)

- **Phase 0 — Design baseline:** COMPLETE.
- **Phase 1 — Repository foundation:** COMPLETE — formally accepted by the owner on 2026-11-26.
- **Phase 2 — Database foundation:** COMPLETE — formally accepted by the owner on 2026-11-26.
- **Phase 3 — Identity and security:** COMPLETE — formally accepted by the owner on 2026-11-27; see
  `PHASE_3_IMPLEMENTATION_REPORT.md`.
- **Phase 4 — Member and audience:** COMPLETE and formally owner-accepted on 2026-11-27. See
  `PHASE_4_PLAN.md` and `PHASE_4_IMPLEMENTATION_REPORT.md`.
- **Phase 5 — Campaign draft management:** PLAN DRAFTED for owner review in `PHASE_5_PLAN.md`; implementation has
  not started and is not authorized.
- **Phases 6–10:** PLANNED; each remains gated by completion and owner acceptance of the preceding phase and explicit
  owner authorization.
- Phase 4 acceptance permits Phase 5 planning only; Phase 5 implementation requires its own explicit approval and
  authorization.

| Phase                                               | Scope                                                                                                      | Main deliverables                                                                                                                                          | Exit criteria                                                                                                                                                                                                                         |
|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0. Design baseline                                  | Review and approve the v1.5.0 Design Pack                                                                  | Approved PRD, architecture, API/OpenAPI, database/ERD, security, accepted ADRs, and phase plan                                                             | **Complete (2026-11-26):** owner approved the complete baseline, PRD is `Ready for Development`, no blocking open questions remained, and Phase 1 was authorized                                                                      |
| 1. Repository foundation                            | Backend/frontend scaffolding and local environment                                                         | Spring Boot skeleton, React+TS skeleton, build scripts, local PostgreSQL, README                                                                           | **Complete and owner-accepted (2026-11-26):** backend build/test, frontend checks/build, PostgreSQL health, backend health, and frontend display verified by owner                                                                    |
| 2. Database foundation                              | Initial schema and seed strategy                                                                           | Flyway migrations for all v1.5 tables/constraints, fictional admin/member seed data, repository integration                                                | **Complete and owner-accepted (2026-11-26):** Flyway V1/V2 applied; all 12 tables, seed data, constraints/indexes verified; 15 backend tests and 4 frontend tests passed; typecheck/build passed                                      |
| 3. Identity and security                            | Login, JWT cookies, refresh rotation, logout, roles, CSRF                                                  | Auth endpoints, token-family persistence, CSRF config, frontend auth flow                                                                                  | **Complete and owner-accepted (2026-11-27):** backend 30 tests pass; API suite 26/26; frontend 8/8, typecheck/build pass; browser auth/session/role checks pass.                                                                      |
| 4. Member and audience                              | Member profile, audience rule validation/evaluation, reusable segments, reusable-segment preview           | Audience use cases and APIs, admin audience UI, canonical field/operator validator; evaluator supports Segment Rules + Campaign Rules composition          | Full matrix tests pass; AND/NULL behavior correct; reusable-segment preview count/sample correct; composition semantics tested. Campaign-scoped preview integration is deferred to Phase 5. Snapshot persistence remains Phase 7.     |
| 5. Campaign draft management                        | Draft create/edit/list/detail, campaign read model, lifecycle validation, campaign-scoped audience preview | Campaign CRUD APIs and admin screens; fixed landing-page content fields; existing campaign audience-preview API integrated with Phase 4 Audience evaluator | Draft validation/date-window rules pass; campaign-scoped preview loads persisted campaign rules through Campaign read model and calls Audience application interface; existing API contract unchanged; no publish endpoint/worker yet |
| 6. Coupon configuration and claim backend           | Coupon CRUD/configuration, inventory reservation, claim transaction, claim list                            | Coupon APIs, atomic claim service, idempotency, server-side claim audit                                                                                    | Duplicate and oversubscription tests pass; inventory equals successful claims; claim API contract stable                                                                                                                              |
| 7. Message, publication orchestration, and delivery | Single message configuration, publish, immutable snapshot, delivery job/worker/retry                       | Message APIs, publish orchestration, snapshot + job transaction, batch worker, delivery status/manual retry                                                | Publish atomically commits snapshot and job; empty snapshot completes immediately; delivery retries/recovery and no-duplicate inbox tests pass                                                                                        |
| 8. Landing page and engagement                      | Public landing page, member claim UI, impression/click/page-view events                                    | Landing route, eligibility/claim states, sign-in return flow, event ingestion                                                                              | Paused/ended public page behavior, anonymous behavior, eligibility display, event dedup all pass                                                                                                                                      |
| 9. Analytics                                        | Campaign funnel and coupon reports                                                                         | Analytics APIs and Admin screens                                                                                                                           | Metrics match source records; target-based rates and zero-denominator handling verified                                                                                                                                               |
| 10. Hardening and delivery                          | E2E, concurrency, security, docs, reproducible demo                                                        | Full test suite, deployment/local run instructions, final README                                                                                           | All PRD acceptance scenarios pass; clean setup reproducible; limitations documented                                                                                                                                                   |

## 3. Phase details and dependencies

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
Phase 3 on 2026-11-27. The Phase 4 plan was approved and implementation explicitly authorized on 2026-11-27; implementation passed
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

**Plan status:** DRAFT FOR OWNER REVIEW; see `PHASE_5_PLAN.md`.  
**Implementation status:** NOT STARTED; implementation is not authorized.

- Implement draft create/update/list/detail and campaign date validation.
- Implement fixed landing-page headline/body fields.
- Integrate the already-specified `POST /api/v1/admin/campaigns/{campaignId}/audience-preview` endpoint after
  Campaign CRUD/read access is available. Campaign loads its own campaign data and invokes the Audience module's
  application-level evaluator interface; do not access Audience repositories directly. Keep the API path and
  request/response contract unchanged.
- Publish and Pause endpoints are integrated in Phase 7 after coupon/message prerequisites are available.

### Phase 6 — Coupon configuration and claim backend

- Implement coupon configuration and member claim-list APIs.
- Implement atomic conditional inventory update and claim insert in one transaction.
- Until Publish is integrated in Phase 7, test the claim service with deterministic fixtures for an already-ACTIVE
  campaign and its audience snapshot.
- Add database uniqueness, idempotency replay, and server-generated claim-attempt audit behavior.
- Run concurrency tests against PostgreSQL.

### Phase 7 — Message, publication, and delivery

- Implement one message definition per campaign.
- Implement `PublishCampaign` and `PauseCampaign`: validate draft, active time window, message, positive-quantity
  coupon, resolve and persist complete audience snapshot, transition to ACTIVE, and create delivery job in one
  transaction. Pause uses the same campaign-row lock protocol as ClaimCoupon.
- If audience count is zero, create the delivery job as `COMPLETED`; otherwise `PENDING`.
- Implement one worker with batch size 100, cursor, three automatic retries after the initial attempt (1s/2s/4s
  backoff), FAILED state, status API, and manual retry from last committed cursor.
- Pause must not cancel a committed job.

### Phase 8 — Landing page and events

- Implement `/campaigns/:campaignId` as public fixed-template content.
- Keep published content readable when paused/ended, but disable claims. Show authenticated audience eligibility and
  claim state.
- Implement login return-to-page behavior.
- Record authenticated impression/click/landing-page events; record claim-attempt audit only on the server.

### Phase 9 — Analytics

- Derive delivery from inbox rows and successful claims from claim rows.
- Derive claim attempts from server-generated audit events.
- Aggregate totals and distinct target-member counts separately; do not report click-to-landing conversion.
- Add indexes based on measured query plans.

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
`PHASE_1_IMPLEMENTATION_REPORT.md` and `PHASE_2_IMPLEMENTATION_REPORT.md`. Phase 3 is formally accepted on 2026-11-27;
its results are recorded in `PHASE_3_IMPLEMENTATION_REPORT.md`. The Phase 4 plan was approved and implementation explicitly authorized by the owner on 2026-11-27; Phase 4 was
formally accepted on 2026-11-27. The Phase 5 plan is drafted in `PHASE_5_PLAN.md` and awaits owner approval.
The campaign-scoped audience-preview endpoint is assigned to Phase 5 integration; its existing API path and
request/response contract remain unchanged unless an explicitly approved clarification is made.
