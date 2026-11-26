# E-commerce Campaign & Coupon Engagement Platform

**Project baseline:** v1.5.0 (owner-approved)
**Implementation:** Phase 2 — Database Foundation (in progress; Phase 1 formally accepted)

This repository implements the approved MVP described in [`docs/PRD.md`](docs/PRD.md). The project is a modular monolith with one Spring Boot backend, one React + TypeScript SPA, and PostgreSQL as the source of truth.

## Current implementation status

**Phase 1 — Repository Foundation: COMPLETE and owner-accepted (2026-11-26).** The backend and frontend scaffolds, local PostgreSQL service, Actuator health endpoints, and frontend-to-backend health shell were verified by the owner.

**Phase 2 — Database Foundation: IN PROGRESS and owner-authorized (2026-11-26).** Current scope includes:

- Spring Boot PostgreSQL datasource configuration using externalizable settings.
- Flyway versioned migrations for all 12 approved tables, constraints, and indexes.
- Fictional deterministic Admin / Member seed records.
- PostgreSQL Testcontainers integration tests for migration and data integrity.

Authentication, domain repositories/services, business workflows, CRUD APIs, audience evaluation, coupon claiming, and delivery workers remain out of scope for Phase 2.

## Technology versions

See [`docs/development/PHASE_1_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_1_IMPLEMENTATION_REPORT.md) for the exact versions and selection rationale.

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

2. Start PostgreSQL:

   ```bash
   docker compose up -d postgres
   docker compose ps
   ```

3. Load the local environment values and start the backend in terminal 1:

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

4. Install frontend dependencies and start the SPA in terminal 2:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

5. Open <http://localhost:5173>. The Phase 1 shell should report the backend connection as healthy when the backend is running.

The backend now connects to PostgreSQL and applies Flyway migrations on startup. Use the `.env.example` values for local development only; override datasource settings through environment variables in other environments.

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
- API contract: [`docs/api/API_SPECIFICATION.md`](docs/api/API_SPECIFICATION.md) and [`docs/api/openapi.yaml`](docs/api/openapi.yaml)
- Database design: [`docs/database/DATABASE_DESIGN.md`](docs/database/DATABASE_DESIGN.md) and [`docs/database/ERD.md`](docs/database/ERD.md)
- Phase plan: [`docs/development/IMPLEMENTATION_PLAN.md`](docs/development/IMPLEMENTATION_PLAN.md)
- AI development protocol: [`docs/development/AI_DEVELOPMENT_GUIDELINES.md`](docs/development/AI_DEVELOPMENT_GUIDELINES.md)
- Phase 1 report: [`docs/development/PHASE_1_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_1_IMPLEMENTATION_REPORT.md)
- Phase 2 report: [`docs/development/PHASE_2_IMPLEMENTATION_REPORT.md`](docs/development/PHASE_2_IMPLEMENTATION_REPORT.md)

## Development rules

- Implement one approved phase at a time.
- Do not begin a later phase until the current phase passes its exit criteria and the owner accepts it.
- Do not introduce Redis, Kafka, microservices, external providers, or other unapproved infrastructure.
- Keep implementation within the currently authorized phase; Phase 2 does not include business workflows.
- Local Compose credentials are for development only; never reuse them in shared or production environments.
