# E-commerce Campaign & Coupon Engagement Platform

**Project baseline:** v1.5.0 (owner-approved)  
**Implementation status:** Phases 0–3 complete; Phases 1–3 formally owner-accepted. Phase 4 Member and Audience is in
planning only; implementation authorization is pending.

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

**Phase 4 — Member and Audience: PLANNING ONLY.** The planning draft is documented in
[`docs/development/PHASE_4_PLAN.md`](docs/development/PHASE_4_PLAN.md). It covers member profile lookup, audience rule
validation/evaluation, reusable segments, preview, and the Admin audience UI within the existing v1.5.0 baseline. Phase
4
implementation has not been authorized.

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
   ./mvnw -f backend/pom.xml spring-boot:run
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

6. Open <http://localhost:5173>. The Phase 3 login screen should appear after the backend health check completes.

The backend connects to PostgreSQL and applies Flyway migrations on startup. Use the `.env.example` values for local
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
- Phase 3 implementation report: [
  `docs/development/PHASE_3_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_3_IMPLEMENTATION_REPORT.md)
- Phase 4 plan (planning only): [`docs/development/PHASE_4_PLAN.md`](docs/development/PHASE_4_PLAN.md)
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
