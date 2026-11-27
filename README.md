# E-commerce Campaign & Coupon Engagement Platform

**Project baseline:** v1.5.0 (owner-approved)  
**Implementation status:** Phases 0–6 are complete and owner-accepted. Phase 7 implementation has been delivered; full verification is pending. Phase 7 is not yet owner-accepted.

This repository implements the approved MVP described in [`docs/PRD.md`](docs/PRD.md). The project is a modular monolith
with one Spring Boot backend, one React + TypeScript SPA, and PostgreSQL as the source of truth.

## Current implementation status

**Phase 1 — Repository Foundation: COMPLETE and owner-accepted (2026-11-26).** The backend and frontend scaffolds, local
PostgreSQL service, Actuator health endpoints, and frontend-to-backend health shell were verified by the owner. See [
`docs/development/PHASE_1_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_1_IMPLEMENTATION_REPORT.md).

**Phase 2 — Database Foundation: COMPLETE and owner-accepted (2026-11-26).** The owner verified PostgreSQL 18.7, Flyway
V1/V2, all 12 business tables, seed data, constraints/indexes, 15 backend tests, and frontend regression checks (4
tests, typecheck, and build). See [
`docs/development/PHASE_2_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_2_IMPLEMENTATION_REPORT.md).

**Phase 3 — Identity and Security: COMPLETE and owner-accepted (2026-11-27).** Login, JWT cookie authentication,
refresh rotation, logout, CSRF, CORS, role boundaries, frontend session-state handling, and security lifecycle
regression
tests passed owner verification. See [
`docs/development/PHASE_3_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_3_IMPLEMENTATION_REPORT.md).

**Phase 4 — Member and Audience: COMPLETE and owner-accepted (2026-11-27).** Implements authenticated member
profile lookup, the canonical audience rule validator/evaluator, reusable segment create/list/detail/update APIs,
reusable-segment preview, AND composition support, and the Admin audience UI. Owner-run backend, frontend, runtime, and
27-assertion API verification passed. Phase 5 now integrates the campaign-scoped audience preview using the saved
campaign configuration. See
[`docs/development/PHASE_4_PLAN.md`](docs/development/PHASE_4_PLAN.md) and
[`docs/development/PHASE_4_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_4_IMPLEMENTATION_REPORT.md).

## Phase 6 — Coupon Configuration and Claim Backend

The Admin Campaign workspace at `/admin/campaigns` now includes coupon list/create/edit for a selected DRAFT campaign.
The backend provides campaign-scoped coupon configuration, authenticated member claim and claim-list APIs, PostgreSQL
inventory reservation, successful-request idempotency, audience-snapshot eligibility, and server-generated claim-attempt
audit records. No schema migration was required. Member claim UI, public landing page, engagement events, and analytics remain in later phases.

Run the Phase 6 Coupon configuration and Claim API verification against an existing disposable DRAFT campaign (and optional ACTIVE claim fixture):

```zsh
export PHASE6_DRAFT_CAMPAIGN_ID="<draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase6-coupon-claim-verification.zsh
```

The owner accepted Phase 6 on 2026-11-27 after reporting successful backend startup, Maven clean verify, frontend tests/typecheck/build/dev, Phase 6 API verification, and Admin Coupon create/edit UI. A dedicated high-contention Coupon inventory race test is not included in this package and is tracked for Phase 10 hardening. See [`docs/development/PHASE_6_PLAN.md`](docs/development/PHASE_6_PLAN.md) and [`docs/development/PHASE_6_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_6_IMPLEMENTATION_REPORT.md).

## Phase 7 — Message, Publication Orchestration, and Delivery

Phase 7 adds the single campaign message, atomic audience snapshot publication, Pause/Claim serialization, asynchronous inbox delivery, retries/recovery, delivery status, and member inbox APIs. The Admin Campaign workspace includes bounded Message, Publish, Pause, Delivery Status, and failed-job Retry controls. No database migration was required because the approved V1 schema already contains the message, audience snapshot, delivery job, and inbox tables. The owner authorized implementation on 2026-11-27 and subsequently reported Spring Boot startup, Maven clean verify, `npm run dev`, and Phase 7 API verification (23 PASS, 0 FAIL). The final code-review corrections require a fresh regression run before formal acceptance. See [`docs/development/PHASE_7_PLAN.md`](docs/development/PHASE_7_PLAN.md) and [`docs/development/PHASE_7_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_7_IMPLEMENTATION_REPORT.md).

Run the Phase 7 lifecycle API suite against a fresh disposable DRAFT campaign with a valid audience and at least one Coupon. The suite publishes and pauses the campaign, so do not reuse a campaign that must remain editable or active:

```zsh
export PHASE7_DRAFT_CAMPAIGN_ID="<disposable-draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh
```

## Technology versions

See [`docs/development/PHASE_1_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_1_IMPLEMENTATION_REPORT.md) for the
exact versions and selection rationale.

## Prerequisites

- JDK 21.
- Node.js 22.12 or newer (Node.js 22 LTS recommended).
- npm 10 or newer.
- Docker Engine with the Docker Compose plugin.
- Internet access for the first Maven and npm dependency downloads.

The Maven bootstrap script downloads a pinned Maven distribution and verifies its SHA-512 checksum before use.

## Local setup

1. Copy the local environment example:

   ```bash
   cp .env.example .env
   ```

2. Optionally edit `.env` and set a private `DEMO_SEED_PASSWORD='your-local-only-password'` for the four fictional
   seeded accounts. Leave it blank to retain the existing V2 seed hashes. Never commit `.env`.

3. Start PostgreSQL:

   ```bash
   docker compose up -d postgres
   docker compose ps
   ```

4. Load the local environment values and start the backend in terminal 1:

   ```bash
   set -a; . ./.env; set +a
   ./scripts/dev/run-backend.zsh
   ```

   Verify the health endpoint:

   ```bash
   curl --fail http://localhost:8080/actuator/health
   curl --fail http://localhost:8080/readyz
   curl --fail http://localhost:8080/livez
   ```

5. Install frontend dependencies and start the SPA in terminal 2:

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

6. Open <http://localhost:5173>. The login screen appears after the backend health check. After signing in as ADMIN, the
   Audience Segments and Campaigns workspaces are available; select a DRAFT Campaign to configure Coupons and the Phase 7 in-app message, then publish or pause and inspect delivery status; member profile access is provided by the authenticated
   `GET /api/v1/member/profile` API; Phase 4 does not add a Member Portal screen.

The backend connects to PostgreSQL and applies Flyway migrations on startup. **Phase 7 deployment constraint:** run exactly one backend application instance/replica. Startup recovery resets all `PROCESSING` delivery jobs to `PENDING`; running multiple instances can reset jobs owned by another live worker. Multi-instance deployment is unsupported until a separately approved worker-ownership design is implemented. If startup recovery fails, the application aborts startup; check PostgreSQL availability/permissions and restart the single instance after resolving the cause. See [`docs/development/PHASE_7_OPERATIONS.md`](docs/development/PHASE_7_OPERATIONS.md) for the recovery runbook. Use the `.env.example` values for local
development only; override datasource settings through environment variables in other environments.

## Verification commands

From the repository root:

```bash
# Backend tests and package
./mvnw -f backend/pom.xml verify

# Frontend checks
cd frontend
npm run test
npm run typecheck
npm run build
cd ..

# Phase 4 and Phase 5 HTTP/API verification (backend must be running)
zsh scripts/api-tests/suites/phase4-audience-verification.zsh
zsh scripts/api-tests/suites/phase5-campaign-verification.zsh
# Phase 6 (set PHASE6_DRAFT_CAMPAIGN_ID to a disposable DRAFT campaign first)
zsh scripts/api-tests/suites/phase6-coupon-claim-verification.zsh

# PostgreSQL service configuration and health
docker compose config
docker compose up -d postgres
docker compose ps
```

## Project documentation

- Product scope and acceptance: [`docs/PRD.md`](docs/PRD.md)
- Architecture: [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md)
- Module boundaries: [`docs/architecture/MODULE_DESIGN.md`](docs/architecture/MODULE_DESIGN.md)
- API contract: [`docs/api/API_SPECIFICATION.md`](docs/api/API_SPECIFICATION.md) and [
  `docs/api/openapi.yaml`](docs/api/openapi.yaml)
- Database design: [`docs/database/DATABASE_DESIGN.md`](docs/database/DATABASE_DESIGN.md) and [
  `docs/database/ERD.md`](docs/database/ERD.md)
- Phase plan: [`docs/development/IMPLEMENTATION_PLAN.md`](docs/development/IMPLEMENTATION_PLAN.md)
- Phase 6 implementation report: [
  `docs/development/PHASE_6_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_6_IMPLEMENTATION_REPORT.md)
- Phase 6 plan: [`docs/development/PHASE_6_PLAN.md`](docs/development/PHASE_6_PLAN.md)
- Phase 7 implementation and verification report: [`docs/development/PHASE_7_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_7_IMPLEMENTATION_REPORT.md)
- Phase 7 worker deployment and recovery runbook: [`docs/development/PHASE_7_OPERATIONS.md`](docs/development/PHASE_7_OPERATIONS.md)
- Phase 5 implementation report: [
  `docs/development/PHASE_5_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_5_IMPLEMENTATION_REPORT.md)
- Phase 4 implementation report: [
  `docs/development/PHASE_4_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_4_IMPLEMENTATION_REPORT.md)
- Phase 3 implementation report: [
  `docs/development/PHASE_3_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_3_IMPLEMENTATION_REPORT.md)
- Phase 4 plan (owner-approved and implementation authorized): [
  `docs/development/PHASE_4_PLAN.md`](docs/development/PHASE_4_PLAN.md)
- Phase 5 plan (owner-approved; implementation report and acceptance pending): [
  `docs/development/PHASE_5_PLAN.md`](docs/development/PHASE_5_PLAN.md)
- Phase 3 plan and implementation report: [`docs/development/PHASE_3_PLAN.md`](docs/development/PHASE_3_PLAN.md)
- AI development protocol: [
  `docs/development/AI_DEVELOPMENT_GUIDELINES.md`](docs/development/AI_DEVELOPMENT_GUIDELINES.md)
- Phase 1 report: [
  `docs/development/PHASE_1_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_1_IMPLEMENTATION_REPORT.md)
- Phase 2 report: [
  `docs/development/PHASE_2_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_2_IMPLEMENTATION_REPORT.md)

## Development rules

- Implement one explicitly authorized phase at a time.
- Do not begin a later phase until the current phase passes its exit criteria and the owner accepts it.
- A plan or review document does not authorize implementation.
- Do not introduce Redis, Kafka, microservices, external providers, or other unapproved infrastructure.
- Local Compose credentials are for development only; never reuse them in shared or production environments.

## Phase 5 — Campaign Draft Management

The Admin Campaign workspace is available at `/admin/campaigns` after signing in as an ADMIN. It supports draft
create/list/detail/update and preview of the campaign's persisted audience. The existing Audience Segments workspace
remains at `/admin`. Publish/Pause and coupon/message/delivery workflows are intentionally not included in Phase 5.

Run the Phase 5 local API verification from the repository root (requires the backend, PostgreSQL, local demo seed,
and the same password configured for the demo accounts):

```zsh
zsh scripts/api-tests/suites/phase5-campaign-verification.zsh
```

The suite creates a uniquely named audience segment and campaign verification draft because this phase has no delete
endpoint. API response files are stored with restrictive permissions under `/tmp/marketing-platform-api-test/`.
See `docs/development/PHASE_5_IMPLEMENTATION_REPORT.md` for implementation scope and owner verification checklist.
