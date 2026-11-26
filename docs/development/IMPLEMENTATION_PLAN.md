# Implementation Plan

**Status:** Proposed — planning only; no development has started.  
**Scope:** Deliver the approved MVP in incremental, verifiable phases.

## 1. Execution principles

- Implement one phase at a time; do not generate the whole application in one pass.
- Each phase must include tests and a demonstrable result.
- A phase is complete only when its exit criteria pass and the owner accepts the result.
- Do not begin a later phase while a blocking defect or unresolved design question remains in an earlier phase.
- Keep documentation synchronized with implementation; if code and docs differ, stop and resolve the discrepancy.

## 2. Phase plan

| Phase | Scope | Main deliverables | Exit criteria |
|---|---|---|---|
| 0. Design baseline | Review PRD and technical docs | Approved PRD, approved architecture/API/DB/security, ADR decisions | Owner explicitly changes status to Ready for Development; no blocking open questions |
| 1. Repository foundation | Backend/frontend scaffolding and local environment | Spring Boot skeleton, React+TS skeleton, build scripts, local PostgreSQL configuration, README | Both apps build; health endpoint works; frontend can call backend; no business features yet |
| 2. Database foundation | Initial schema and seed strategy | Flyway migrations, fictional admin/member seed data, repository integration setup | Migrations apply cleanly to empty DB; constraints verified; seed data deterministic |
| 3. Identity and security | Login, JWT cookies, refresh rotation, logout, roles, CSRF | Auth endpoints, Spring Security config, frontend auth flow | Security tests pass; member/admin isolation verified; tokens not stored in browser storage |
| 4. Member and audience | Member profile, rule validation/evaluation, reusable segments, preview, snapshots | Audience use cases and APIs, admin audience UI | Rule tests pass; snapshot is complete and immutable; supported fields/operators enforced |
| 5. Campaign management | Draft create/edit/list/detail, lifecycle, landing content | Campaign module/API, admin campaign screens | Campaign validation and lifecycle tests pass |
| 6. Coupon configuration and claim | Coupon CRUD/configuration, inventory reservation, claim list | Claim transaction, idempotency, member coupon UI | Duplicate and oversubscription tests pass; inventory equals successful claims |
| 7. Message and delivery | One message per campaign, publication delivery job, worker, inbox | Message APIs, DB-backed batch delivery, inbox UI | Publish initiates delivery; retries create no duplicate inbox rows; delivery progress observable |
| 8. Landing page and engagement | Campaign landing page, impression/click/page-view event ingestion | Member landing route, event API, deduplication | Event dedup and landing-page flows pass; eligibility and claim state correct |
| 9. Analytics | Campaign funnel and coupon reports | Admin analytics APIs and screens | Counts and distinct-member metrics match source records; zero denominators handled |
| 10. Hardening and delivery | E2E, concurrency, security, docs, containerized demo | Full test suite, deployment/local run instructions, final README | All PRD acceptance criteria pass; clean setup reproducible; known limitations documented |

## 3. Phase details and dependencies

### Phase 1 — Repository foundation

- Create backend and frontend project scaffolds.
- Choose exact compatible framework/library versions and record them.
- Configure local PostgreSQL through Docker Compose or an equivalent repeatable environment.
- Add health/readiness endpoint and minimal frontend shell.
- Do not implement business features in this phase.

### Phase 2 — Database foundation

- Implement tables and constraints from `DATABASE_DESIGN.md` using Flyway.
- Add fictional deterministic seed records for admin users and members.
- Verify constraints with integration tests.
- Avoid adding Redis/Kafka or a second persistence technology.

### Phase 3 — Identity and security

- Implement password hashing, access/refresh JWT cookies, refresh rotation, server-side refresh revocation, logout, CSRF, and role-based authorization.
- Implement frontend login/session handling without storing tokens in browser storage.
- Test member data isolation.

### Phase 4 — Audience

- Implement allowlisted rule model and evaluator.
- Implement saved segment CRUD and preview.
- Implement campaign audience rule composition according to the approved semantics.
- Persist the complete publication snapshot as immutable rows in the publication transaction (no separate snapshot job in MVP).

### Phase 5 — Campaign management

- Implement draft lifecycle and campaign details.
- Enforce exactly one message per campaign at publish time.
- Keep campaign pause and end behavior consistent with claim eligibility.

### Phase 6 — Coupon claim

- Implement coupon configuration and member claim list.
- Implement atomic conditional inventory update and claim insert in one transaction.
- Add database uniqueness and idempotency handling.
- Run concurrency tests against PostgreSQL.

### Phase 7 — Message delivery

- Implement one message definition per campaign.
- Publication transaction creates a delivery job.
- Worker delivers in bounded batches from the immutable audience snapshot.
- Use one worker instance, a persisted cursor, and a unique campaign/member inbox constraint; batch inserts and cursor advancement commit together. Multi-worker leases are deferred.

### Phase 8 — Landing page and events

- Implement `/campaigns/:campaignId` within the Member Portal.
- Record impression only when the message is visibly rendered, click on click, and landing-page view on page open.
- Event IDs deduplicate client retries.

### Phase 9 — Analytics

- Derive delivery from inbox rows and claims from claim rows.
- Aggregate event totals and distinct members separately.
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
