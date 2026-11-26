# E-commerce Campaign & Coupon Engagement Platform

**Project baseline:** v1.5.0 (owner-approved)
**Implementation:** Phase 1 — Repository Foundation (first implementation; runtime verification pending)

This repository implements the approved MVP described in [`docs/PRD.md`](docs/PRD.md). The project is a modular monolith with one Spring Boot backend, one React + TypeScript SPA, and PostgreSQL as the source of truth.

## Phase 1 scope

Included in this first implementation:

- Spring Boot backend scaffold on Java 21.
- React + TypeScript frontend scaffold with strict TypeScript settings.
- PostgreSQL local development service through Docker Compose.
- Actuator health, readiness, and liveness endpoints.
- Frontend-to-backend health check through the Vite development proxy.
- Reproducible project commands and version selection documentation.

No campaign, audience, identity, message, coupon, claim, analytics, database schema, or Flyway migration functionality is implemented in Phase 1.

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

3. Start the backend in terminal 1:

   ```bash
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

The PostgreSQL service is provisioned in Phase 1 but is not yet connected to application persistence. Database dependencies, Flyway migrations, and schema creation belong to Phase 2.

## Verification commands

From the repository root:

```bash
# Backend tests and package
./mvnw -f backend/pom.xml verify

# Frontend checks
cd frontend
npm test
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

## Development rules

- Implement one approved phase at a time.
- Do not begin a later phase until the current phase passes its exit criteria and the owner accepts it.
- Do not introduce Redis, Kafka, microservices, external providers, or other unapproved infrastructure.
- Do not add business features during Phase 1.
- Local Compose credentials are for development only; never reuse them in shared or production environments.
