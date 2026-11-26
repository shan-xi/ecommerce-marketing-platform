# Phase 2 Implementation Report — Database Foundation

**Status:** IN PROGRESS — owner authorized Phase 2 on 2026-11-26.  
**Baseline:** Owner-approved v1.5.0 Development Baseline.  
**Scope:** PostgreSQL connection, Flyway schema migrations, fictional deterministic seed data, and PostgreSQL integration tests only.

## 1. Scope implemented

- Added Spring Boot JDBC and Flyway integration.
- Added the PostgreSQL JDBC driver and PostgreSQL-specific Flyway database module.
- Configured datasource credentials and connection pool settings through environment variables with local-development defaults matching `.env.example`.
- Enabled Flyway version validation and disabled Flyway clean.
- Added a versioned migration creating all 12 tables in `DATABASE_DESIGN.md`.
- Added documented primary keys, foreign keys, composite candidate keys, unique constraints, check constraints, and query-pattern indexes.
- Added deterministic fictional Admin / Member seed rows. Password material is stored only as BCrypt hashes; no raw password is stored in SQL.
- Added a PostgreSQL Testcontainers base class and integration tests for migration, seed records, and critical database constraints.

## 2. Migration inventory

| Version | File | Purpose |
|---|---|---|
| V1 | `V1__create_initial_schema.sql` | Creates all 12 approved tables, constraints, and indexes |
| V2 | `V2__seed_fictional_admin_and_members.sql` | Inserts one fictional Admin and three fictional Members with one-to-one member profiles |

The V1 migration is immutable after application. Future schema changes must use a new versioned migration.

## 3. Tables created

1. `users`
2. `members`
3. `refresh_token_sessions`
4. `audience_segments`
5. `campaigns`
6. `campaign_audience_members`
7. `campaign_coupons`
8. `coupon_claims`
9. `campaign_messages`
10. `campaign_delivery_jobs`
11. `member_messages`
12. `campaign_events`

## 4. Integration test coverage added

- All 12 tables exist and Flyway records both migrations.
- Seed data contains one Admin, three Members, and three Member profiles.
- Invalid user role is rejected.
- Negative member order count is rejected.
- Campaign without an audience definition is rejected.
- Invalid persisted campaign status is rejected.
- Coupon quantity zero is rejected.
- Cross-campaign coupon claim reference is rejected.
- Duplicate successful claim for a campaign/coupon/member is rejected.
- Refresh-token replacement across token families is rejected.
- Delivery cursor outside its campaign snapshot is rejected.
- Impression event without the matching member inbox message is rejected.
- Claim-attempt event without an allowed outcome is rejected.

The existing health endpoint test now uses the same PostgreSQL Testcontainers environment because Flyway and datasource initialization are part of application startup.

## 5. Verification status

The following commands are required, but have **not yet completed in this implementation environment**:

| Command | Result |
|---|---|
| `./mvnw -f backend/pom.xml verify` | BLOCKED — Maven Wrapper attempted to download Maven 3.10.2, but this environment could not resolve `dlcdn.apache.org` |
| PostgreSQL Testcontainers integration tests | BLOCKED — Docker CLI/daemon is not available in this environment |
| `docker compose config` | BLOCKED — Docker Compose is not installed in this environment |
| Frontend `npm run typecheck` | BLOCKED — frontend dependencies are not installed; TypeScript and React type packages are unavailable |
| Frontend `npm run test` | PASS — 4 tests passed, 0 failed |
| Frontend `npm run build` | BLOCKED — frontend dependencies are not installed; TypeScript and Vite are unavailable |

JDK 21 and Node.js 22 are present. The four dependency-free frontend tests passed. The inability to run Maven/Docker and the missing frontend packages are environment limitations, not passing verification results. Phase 2 is **not complete** until Maven verification and PostgreSQL integration tests run successfully in a Docker-enabled environment and the owner accepts the result.

## 6. Explicitly out of scope

No authentication, JWT, refresh-token rotation, security configuration, business repositories/services, CRUD APIs, audience evaluation, coupon claim workflow, campaign publication, message delivery worker, Redis, Kafka, or additional persistence technology has been implemented.

## 7. Next verification steps

From the repository root, with JDK 21, network access for Maven dependencies, and Docker available:

```bash
./mvnw -f backend/pom.xml verify

docker compose config
docker compose up -d postgres
docker compose ps
```

Then start the backend with the local environment loaded and verify:

```bash
set -a; . ./.env; set +a
./mvnw -f backend/pom.xml spring-boot:run

curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/readyz
curl --fail http://localhost:8080/livez
```

After all verification gates pass, update this report and `IMPLEMENTATION_PLAN.md` with the observed results. Phase 3 remains unauthorized until Phase 2 is accepted by the owner.
