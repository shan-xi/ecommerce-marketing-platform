# Implementation Plan

**Status:** Approved — v1.5.0 baseline accepted; Phase 0 complete; Phase 1 in progress (first implementation created; verification and owner acceptance pending).
**Scope:** Deliver the approved MVP in incremental, verifiable phases.

## 1. Execution principles

- Implement one phase at a time; do not generate the whole application in one pass.
- Each phase must include tests and a demonstrable result.
- A phase is complete only when its exit criteria pass and the owner accepts the result.
- Do not begin a later phase while a blocking defect or unresolved design question remains in an earlier phase.
- Keep documentation synchronized with implementation; if code and docs differ, stop and resolve the discrepancy.

## 2. Phase plan

### Current execution gate (2026-11-26)

- **Phase 0 — Design baseline:** COMPLETE.
- **Phase 1 — Repository foundation:** IN PROGRESS. The owner explicitly authorized Phase 1 on 2026-11-26. Backend/frontend scaffolds, local PostgreSQL configuration, and health-check wiring have been created. Full build and runtime acceptance remains pending because the implementation environment has no Maven or Docker installation and cannot reach Maven Central/npm registry.
- **Phases 2–10:** PLANNED; they remain gated by completion and owner acceptance of the preceding phase.
- Phase 1 must not be marked complete, and Phase 2 must not begin, until all Phase 1 verification steps pass and the owner accepts the result.

| Phase | Scope | Main deliverables | Exit criteria |
|---|---|---|---|
| 0. Design baseline | Review and approve the v1.5.0 Design Pack | Approved PRD, architecture, API/OpenAPI, database/ERD, security, accepted ADRs, and phase plan | **Complete (2026-11-26):** owner approved the complete baseline, PRD is `Ready for Development`, no blocking open questions remain, and Phase 1 is authorized |
| 1. Repository foundation | Backend/frontend scaffolding and local environment | Spring Boot skeleton, React+TS skeleton, build scripts, local PostgreSQL, README | Both apps build; health endpoint works; frontend can call backend; no business features |
| 2. Database foundation | Initial schema and seed strategy | Flyway migrations for all v1.5 tables/constraints, fictional admin/member seed data, repository integration | Migrations apply cleanly; composite FKs and checks verified; deterministic seed data |
| 3. Identity and security | Login, JWT cookies, refresh rotation, logout, roles, CSRF | Auth endpoints, token-family persistence, CSRF config, frontend auth flow | Rotation/reuse/concurrency/logout tests pass; member/admin isolation verified |
| 4. Member and audience | Member profile, audience rule validation/evaluation, reusable segments, preview | Audience use cases and APIs, admin audience UI, canonical field/operator validator | Full matrix tests pass; AND/NULL behavior correct; preview count/sample correct. Snapshot persistence is integrated later with Publish in Phase 7. |
| 5. Campaign draft management | Draft create/edit/list/detail and lifecycle validation | Campaign CRUD APIs and admin screens; fixed landing-page content fields | Draft validation and date-window rules pass; no publish endpoint/worker yet |
| 6. Coupon configuration and claim backend | Coupon CRUD/configuration, inventory reservation, claim transaction, claim list | Coupon APIs, atomic claim service, idempotency, server-side claim audit | Duplicate and oversubscription tests pass; inventory equals successful claims; claim API contract stable |
| 7. Message, publication orchestration, and delivery | Single message configuration, publish, immutable snapshot, delivery job/worker/retry | Message APIs, publish orchestration, snapshot + job transaction, batch worker, delivery status/manual retry | Publish atomically commits snapshot and job; empty snapshot completes immediately; delivery retries/recovery and no-duplicate inbox tests pass |
| 8. Landing page and engagement | Public landing page, member claim UI, impression/click/page-view events | Landing route, eligibility/claim states, sign-in return flow, event ingestion | Paused/ended public page behavior, anonymous behavior, eligibility display, event dedup all pass |
| 9. Analytics | Campaign funnel and coupon reports | Analytics APIs and Admin screens | Metrics match source records; target-based rates and zero-denominator handling verified |
| 10. Hardening and delivery | E2E, concurrency, security, docs, reproducible demo | Full test suite, deployment/local run instructions, final README | All PRD acceptance scenarios pass; clean setup reproducible; limitations documented |

## 3. Phase details and dependencies

### Phase 1 — Repository foundation

**Phase status:** IN PROGRESS — first implementation created; build/runtime verification and owner acceptance remain outstanding.

- [x] Create backend and frontend project scaffolds.
- [x] Choose exact compatible framework/library versions and record the selection rationale.
- [x] Configure local PostgreSQL through Docker Compose.
- [x] Add health/readiness endpoints and a minimal frontend shell that calls backend health through the Vite proxy.
- [ ] Verify backend build and tests on JDK 21.
- [ ] Install frontend dependencies, generate and commit `package-lock.json`, and verify tests/typecheck/build.
- [ ] Start PostgreSQL and verify its Compose health check.
- [ ] Start backend and frontend and verify browser-to-backend health connectivity.
- [ ] Obtain owner acceptance.
- Do not implement business features in this phase.

### Phase 2 — Database foundation

- Implement tables and constraints from `DATABASE_DESIGN.md` using Flyway.
- Include refresh-token family/generation columns, composite campaign/message/coupon foreign keys, and delivery retry fields.
- Add fictional deterministic seed records for admin users and members using the documented enum values.
- Verify constraints with PostgreSQL integration tests.
- Avoid adding Redis/Kafka or a second persistence technology.

### Phase 3 — Identity and security

- Implement password hashing, access/refresh JWT cookies, token-family rotation/reuse detection, server-side revocation, logout, CSRF, and role-based authorization.
- Implement frontend login/session handling without storing tokens in browser storage.
- Test concurrent refresh, token reuse family revocation, and member data isolation.

### Phase 4 — Audience

- Implement the canonical field/type/operator matrix and allowlisted rule model/evaluator.
- Implement saved segment CRUD and preview.
- Implement campaign audience rule composition according to AND semantics.
- Do not persist the final campaign snapshot in this phase; publication snapshot + delivery job are one transaction in Phase 7.

### Phase 5 — Campaign draft management

- Implement draft create/update/list/detail and campaign date validation.
- Implement fixed landing-page headline/body fields.
- Publish and Pause endpoints are integrated in Phase 7 after coupon/message prerequisites are available.

### Phase 6 — Coupon configuration and claim backend

- Implement coupon configuration and member claim-list APIs.
- Implement atomic conditional inventory update and claim insert in one transaction.
- Until Publish is integrated in Phase 7, test the claim service with deterministic fixtures for an already-ACTIVE campaign and its audience snapshot.
- Add database uniqueness, idempotency replay, and server-generated claim-attempt audit behavior.
- Run concurrency tests against PostgreSQL.

### Phase 7 — Message, publication, and delivery

- Implement one message definition per campaign.
- Implement `PublishCampaign` and `PauseCampaign`: validate draft, active time window, message, positive-quantity coupon, resolve and persist complete audience snapshot, transition to ACTIVE, and create delivery job in one transaction. Pause uses the same campaign-row lock protocol as ClaimCoupon.
- If audience count is zero, create the delivery job as `COMPLETED`; otherwise `PENDING`.
- Implement one worker with batch size 100, cursor, three automatic retries after the initial attempt (1s/2s/4s backoff), FAILED state, status API, and manual retry from last committed cursor.
- Pause must not cancel a committed job.

### Phase 8 — Landing page and events

- Implement `/campaigns/:campaignId` as public fixed-template content.
- Keep published content readable when paused/ended, but disable claims. Show authenticated audience eligibility and claim state.
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

No scheduled message delivery, additional channels, coupon redemption, checkout, orders, payments, logistics, microservices, Redis, Kafka, or third-party production integrations are included in the MVP plan.


## 6. Phase 1 authorization and execution gate

The original pre-development hold was superseded by the owner's explicit instruction to begin Phase 1 on 2026-11-26. Phase 1 implementation is now in progress. Continue only within the Phase 1 scope. Do not begin Phase 2, add migrations, create domain tables, or implement business features until Phase 1 verification passes and the owner explicitly accepts the phase.

The current verification limitation and exact remaining acceptance steps are recorded in `PHASE_1_IMPLEMENTATION_REPORT.md`.
