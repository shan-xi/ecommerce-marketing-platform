# Phase 10 Plan — Hardening and Delivery Readiness

**Status:** COMPLETE — FORMALLY OWNER-ACCEPTED (2026-11-28), WITH ENVIRONMENT-DEPENDENT VERIFICATION FOLLOW-UP OPEN  
**Prepared:** 2026-11-28  
**Baseline:** Owner-approved v1.5.0; Phases 0–10 formally owner-accepted  
**Scope authority:** Approved PRD, architecture, API/OpenAPI, database design, security design, testing strategy,
accepted ADRs, and the accepted Phase 1–9 implementation.

> Owner authorization: The owner explicitly instructed implementation based on the `phase10-ready-to-develop` package on
> 2026-11-28. This instruction approves this plan and authorizes Phase 10 implementation within the scope below.

## 1. Objective

Harden the accepted MVP and establish a reproducible, verifiable delivery baseline without introducing new product
scope or changing approved business behavior.

Phase 10 focuses on:

1. proving Coupon inventory and per-member claim correctness under concurrent requests;
2. running cross-phase regression and end-to-end campaign journey verification;
3. verifying security and authorization boundaries across the delivered workflows;
4. validating clean local setup, operational constraints, and troubleshooting documentation;
5. recording known limitations and a final release-readiness result.

## 2. Scope boundaries

### In scope

- Dedicated high-contention Coupon inventory race test, previously deferred from Phase 6.
- Concurrent same-member/same-coupon claim attempts and concurrent different-member claims against limited inventory.
- Verification of inventory never exceeding configured quantity and successful claims remaining unique/idempotent.
- Cross-phase regression for authentication, audience, campaign draft, coupon setup/claim, publication/delivery,
  landing-page engagement, and analytics.
- API-level end-to-end verification using the existing API contracts and test infrastructure.
- Security regression for ADMIN/MEMBER/anonymous access, CSRF behavior, cookie/session behavior, and campaign/member
  data boundaries where applicable to existing endpoints.
- Frontend test/typecheck/build and documented manual browser walkthroughs for the full member/admin journey.
- Clean-environment setup and operational documentation review, including the existing one-backend-instance constraint.
- Final known-limitations and release-readiness checklist.

### Out of scope

- New product features, campaign types, coupon types, attribution models, exports, dashboards beyond the accepted
  report,
  or changes to approved business rules.
- Redis, Kafka, microservices, distributed locks, a new queue, or other new infrastructure.
- Multi-instance backend/worker deployment or changes to Phase 7 delivery ownership/recovery.
- Database schema redesign, migrations, or performance redesign unless testing exposes a concrete defect and the owner
  approves the required change separately.
- Production hosting, cloud provisioning, secrets management, data retention/anonymization policy implementation, or
  production SLO/capacity guarantees.
- Load/capacity claims unsupported by a reproducible test environment and measured results.

## 3. Work packages

### 3.1 Coupon inventory concurrency and idempotency

**Purpose:** Close the explicit Phase 6 hardening item and demonstrate the correctness of the existing PostgreSQL
inventory reservation transaction.

Proposed scenarios:

1. Configure a disposable campaign coupon with a small known quantity (for example, 10).
2. Submit substantially more concurrent claims from distinct eligible members than the available quantity.
3. Assert successful claim rows never exceed the configured quantity; remaining quantity never becomes negative;
   and the persisted claimed counter equals the number of successful claim rows.
4. Assert requests that lose the inventory race receive the existing documented unavailable/sold-out result and do not
   create successful claim rows.
5. Submit concurrent duplicate requests for the same member and idempotency key; assert one logical successful claim
   and consistent replay behavior.
6. Submit same-member requests with different idempotency keys; assert the per-member coupon uniqueness rule still
   prevents duplicate successful claims.
7. Verify failed attempts do not reserve inventory and follow the already-approved failed-claim idempotency behavior.
8. Run the test repeatedly against isolated disposable data; do not depend on timing sleeps as the correctness
   mechanism.

Implementation guidance:

- Prefer a deterministic backend integration test using the existing PostgreSQL test infrastructure and bounded
  executor/latch coordination.
- Use independent transactions/connections for competing requests.
- Assert database state after all requests complete, not only HTTP/service responses.
- Keep test concurrency and resource use bounded so it remains stable in CI/local development.
- Do not add Redis or replace the existing PostgreSQL atomic reservation strategy.

### 3.2 Cross-phase regression and end-to-end journey

Verify the existing MVP journey end to end:

1. ADMIN authenticates and creates/configures a campaign and audience.
2. ADMIN configures one or more coupons and the campaign message.
3. ADMIN publishes the campaign and delivery completes under the existing worker model.
4. MEMBER signs in, sees the delivered inbox message, and opens the campaign landing page.
5. Eligible MEMBER claims a coupon; duplicate/replayed requests follow the approved behavior.
6. Impression, click, and authenticated landing-page events are ingested according to the existing event contract.
7. ADMIN opens campaign analytics and verifies audience, delivery, engagement, page-view, claim, attempt, and coupon
   inventory metrics.

Regression must include:

- existing phase API suites (Phases 4–9) where applicable;
- backend Maven `clean verify`;
- frontend automated tests, typecheck, and production build;
- browser checks for loading, empty, validation, forbidden, unauthorized, not-found, and retryable error states;
- no regression to the accepted API paths, response contracts, role boundaries, or metric definitions.

Use disposable fixtures for mutating suites. Do not reuse campaigns that must remain editable or active when a suite
publishes or pauses them.

### 3.3 Security regression

Verify the already-approved security design; do not redefine it.

- ADMIN-only campaign management, publication, delivery administration, and analytics endpoints reject MEMBER and
  anonymous callers as specified.
- MEMBER-only profile, inbox, and claim operations reject unauthorized roles.
- Public landing-page content remains readable anonymously, while member-specific state remains protected.
- CSRF protection is enforced for state-changing cookie-authenticated requests.
- Access/refresh cookie attributes and refresh-token rotation/reuse behavior remain consistent with the approved
  security baseline.
- Cross-campaign and cross-member identifiers cannot be used to access or mutate another owner's records.
- Responses do not expose raw member identifiers, secrets, refresh tokens, or internal event metadata beyond the
  approved API contract.

### 3.4 Reproducible setup and operational readiness

- Verify `.env.example`, Docker Compose configuration, Maven wrapper, frontend lockfile, and documented prerequisites.
- Follow the README from a clean checkout or clean local environment; record any manual steps that are genuinely
  required.
- Verify database startup, Flyway migration, backend readiness/liveness, frontend startup, and the documented API test
  commands.
- Reconfirm and prominently retain the Phase 7 rule: exactly one backend application instance/replica is supported
  because startup recovery can reset `PROCESSING` delivery jobs.
- Document test fixture creation/cleanup expectations, test commands, expected outcomes, and troubleshooting.
- Do not claim production readiness, multi-instance support, or a capacity target from local tests alone.

### 3.5 Final review and release-readiness record

- Review changed files for module-boundary violations, transaction boundaries, unsafe concurrency, missing tests, and
  stale documentation.
- Confirm PRD scope and accepted ADRs remain unchanged.
- Record actual command outputs/results supplied by the owner; never invent test counts or claim tests ran in an
  environment where they were not executed.
- Produce a concise final Phase 10 implementation report and acceptance guide.
- Normal completion requires all applicable exit criteria to pass and explicit owner acceptance. If the owner explicitly
  accepts the delivered scope while environment-dependent checks remain unverified, record the phase as
  `OWNER-ACCEPTED WITH VERIFICATION FOLLOW-UP OPEN`; do not mark the outstanding checks as passed or imply they were
  waived as technical evidence.

## 4. Proposed deliverables

| Deliverable                         | Description                                                                                     |
|-------------------------------------|-------------------------------------------------------------------------------------------------|
| Coupon concurrency integration test | Reproducible race coverage for limited inventory, duplicate requests, and per-member uniqueness |
| Cross-phase regression result       | Backend/frontend and existing phase API suite results                                           |
| End-to-end verification             | Repeatable ADMIN-to-MEMBER campaign journey checks                                              |
| Security regression result          | Role, CSRF, cookie/session, and ownership-boundary checks                                       |
| Operations/documentation updates    | Verified clean setup, test commands, limitations, and troubleshooting                           |
| Phase 10 implementation report      | Actual delivered changes, test evidence, limitations, and owner acceptance                      |
| Phase 10 acceptance guide           | Repeatable verification steps and formal acceptance record                                      |

## 5. Proposed exit criteria

Phase 10's normal acceptance recommendation requires all applicable criteria to be satisfied. On 2026-11-28, the
owner explicitly accepted the delivered modifications after final Code Review with the environment-dependent checks
below retained as follow-up. The acceptance status therefore records owner approval of the delivered scope, not a claim
that these unexecuted checks passed. Leave each check unchecked until supported by actual evidence.

- [ ] Coupon concurrency test repeatedly passes with no inventory oversell.
- [ ] Successful claim rows, coupon `claimed_quantity`, and remaining quantity are consistent.
- [ ] Same-key replay and same-member uniqueness behavior pass under concurrent execution.
- [ ] Backend `./mvnw -f backend/pom.xml clean verify` passes.
- [ ] Frontend `npm test`, `npm run typecheck`, and `npm run build` pass.
- [ ] Existing phase API verification suites pass against appropriate disposable fixtures.
- [ ] End-to-end ADMIN → MEMBER → coupon claim → analytics journey passes.
- [ ] Security regression checks pass for roles, CSRF, cookies/session, and ownership boundaries.
- [ ] Clean local setup instructions are followed successfully and documentation is corrected where needed.
- [x] Single-backend-instance deployment limitation and all known exclusions are documented.
- [x] No unapproved infrastructure or product-scope expansion was introduced (static review).
- [x] Owner formally accepts the delivered Phase 10 modifications with verification limitations recorded (2026-11-28).

If a test exposes a defect requiring a schema, API, or business-rule change, stop and present the evidence and options
for owner approval before expanding scope.

## 6. Dependencies and execution order

1. Owner approval and implementation authorization were granted on 2026-11-28.
2. Inspect existing Coupon claim transaction and test fixtures before writing concurrency tests.
3. Implement Coupon concurrency coverage.
4. Prepare cross-phase regression and end-to-end verification steps.
5. Prepare security regression and operational/documentation verification.
6. Fix only defects within the approved scope; seek separate approval for scope changes.
7. Publish implementation report and acceptance guide for owner verification.
8. Owner runs environment-dependent checks and records the actual evidence when available.
9. Owner formally accepts the delivered Phase 10 modifications on 2026-11-28, with outstanding verification
   follow-ups explicitly retained.

## 7. Decisions intentionally left unchanged

- Project baseline remains v1.5.0.
- Modular monolith remains the application architecture.
- PostgreSQL remains the source of truth.
- Existing campaign, audience, coupon, claim, publication, delivery, engagement, and analytics contracts remain
  authoritative.
- Phase 7 delivery remains single-backend-instance only.
- No new schema migration is planned; any migration must be justified by a reproduced defect and separately approved.
- Existing retention/anonymization and production deployment decisions remain deferred as recorded in the design pack.

## 8. Approval record

- **Plan prepared:** 2026-11-28.
- **Owner plan approval:** Approved by explicit implementation instruction on 2026-11-28.
- **Owner implementation authorization:** Granted on 2026-11-28.
- **Implementation status:** Delivered scope formally accepted by the owner on 2026-11-28.
- **Acceptance qualification:** The owner accepted the Phase 10 modifications after the final code review. This records
  acceptance of the delivered scope; it does not convert environment-blocked or unrun tests into passing results.
  The outstanding verification items remain explicitly tracked in `PHASE_10_IMPLEMENTATION_REPORT.md` and
  `PHASE_10_ACCEPTANCE.md`.
