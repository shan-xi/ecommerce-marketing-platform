# Phase 2 Implementation Report — Database Foundation

**Status:** COMPLETE — formally accepted by the owner on 2026-11-26.  
**Baseline:** Owner-approved v1.5.0 Development Baseline.  
**Scope:** PostgreSQL connection, Flyway schema migrations, fictional deterministic seed data, and PostgreSQL
integration tests only.

## 1. Scope implemented

- Added Spring Boot JDBC and Flyway integration.
- Added the PostgreSQL JDBC driver and PostgreSQL-specific Flyway database module.
- Configured datasource credentials and connection pool settings through environment variables with local-development
  defaults matching `.env.example`.
- Enabled Flyway version validation and disabled Flyway clean.
- Added a versioned migration creating all 12 tables in `DATABASE_DESIGN.md`.
- Added documented primary keys, foreign keys, composite candidate keys, unique constraints, check constraints, and
  query-pattern indexes.
- Added deterministic fictional Admin / Member seed rows. Password material is stored only as BCrypt hashes; no raw
  password is stored in SQL.
- Added a PostgreSQL Testcontainers base class and integration tests for migration, seed records, and critical database
  constraints.

## 2. Migration inventory

| Version | File                                       | Purpose                                                                                 | Owner verification   |
|---------|--------------------------------------------|-----------------------------------------------------------------------------------------|----------------------|
| V1      | `V1__create_initial_schema.sql`            | Creates all 12 approved tables, constraints, and indexes                                | Applied successfully |
| V2      | `V2__seed_fictional_admin_and_members.sql` | Inserts one fictional Admin and three fictional Members with one-to-one member profiles | Applied successfully |

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

The health endpoint test uses the same PostgreSQL Testcontainers environment because Flyway and datasource
initialization are part of application startup.

## 5. Owner-reported verification results

The following results were supplied by the owner after running the verification steps in the owner's Docker-enabled
macOS environment on 2026-11-26. These are owner-reported results; this report does not claim that the commands were
executed in the AI implementation environment.

### 5.1 Development environment

| Check            | Owner-reported result                     |
|------------------|-------------------------------------------|
| Java             | Temurin OpenJDK 21.0.13 LTS               |
| Maven Wrapper    | Maven 3.10.2, Java 21.0.13                |
| Node.js / npm    | Node.js 25.2.1 / npm 11.6.2               |
| Docker / Compose | Docker 28.0.4 / Compose v2.34.0-desktop.1 |
| Docker daemon    | Available; Linux arm64                    |
| Result           | **PASS**                                  |

### 5.2 PostgreSQL service

| Check                                               | Owner-reported result |
|-----------------------------------------------------|-----------------------|
| `docker compose config`                             | Passed                |
| PostgreSQL image                                    | `postgres:18.7`       |
| Compose container                                   | Running and healthy   |
| `pg_isready -U marketing_app -d marketing_platform` | Accepting connections |
| Result                                              | **PASS**              |

A PostgreSQL log entry reported `FATAL: role "postgres" does not exist` for a connection attempt using the `postgres`
role. The configured application role was available, PostgreSQL reported healthy, and the backend connected successfully
using `marketing_app`. The log entry did not block the observed verification; it is not treated as proof that every
possible connection path is valid.

### 5.3 Backend startup and Flyway

| Check                        | Owner-reported result        |
|------------------------------|------------------------------|
| Spring Boot startup          | Successful on port 8080      |
| Hikari datasource connection | Successful                   |
| Flyway validation            | 2 migrations validated       |
| Flyway schema version        | Version 2; schema up to date |
| `/actuator/health`           | `UP`                         |
| `/readyz`                    | `UP`                         |
| `/livez`                     | `UP`                         |
| Result                       | **PASS**                     |

### 5.4 Database schema, migrations, and seed data

| Check                                        | Owner-reported result                        |
|----------------------------------------------|----------------------------------------------|
| Business tables                              | All 12 expected tables present               |
| Flyway history                               | V1 and V2 both successful                    |
| Seeded users                                 | 1 `ADMIN`, 3 `MEMBER`                        |
| Member profiles                              | 3                                            |
| Constraints and indexes export               | 207 rows returned                            |
| Key primary/foreign/unique/check constraints | Present in the exported database definitions |
| Required query-pattern indexes               | Present in the exported database definitions |
| Result                                       | **PASS**                                     |

The exported constraint/index definitions were reviewed against the approved database design. The important composite
constraints were present, including campaign/coupon claim ownership, refresh-token same-family replacement, delivery
cursor membership in the campaign snapshot, and campaign/message/member event relationships.

### 5.5 Backend verification

Command:

```bash
./mvnw -f backend/pom.xml verify
```

Owner-reported result: **BUILD SUCCESS**.

| Test suite                      |  Tests | Failures | Errors | Skipped |
|---------------------------------|-------:|---------:|-------:|--------:|
| `DatabaseSchemaIntegrationTest` |     14 |        0 |      0 |       0 |
| `HealthEndpointTest`            |      1 |        0 |      0 |       0 |
| **Total**                       | **15** |    **0** |  **0** |   **0** |

Testcontainers successfully started PostgreSQL 18.7 for the integration tests. The PostgreSQL integration tests were
executed rather than skipped.

### 5.6 Frontend regression

| Command             | Owner-reported result                    |
|---------------------|------------------------------------------|
| `npm ci`            | Passed; 0 vulnerabilities reported       |
| `npm run typecheck` | Passed                                   |
| `npm run test`      | 4 passed, 0 failed, 0 skipped            |
| `npm run build`     | Passed; Vite production build successful |
| Result              | **PASS**                                 |

### 5.7 Non-blocking warnings observed

- Docker emitted a warning about invalid `docker-feedback` plugin metadata and daemon configuration warnings.
- Maven emitted HTTP 401 metadata/feed warnings while resolving optional repository metadata; the requested build still
  completed successfully.
- Mockito / Byte Buddy emitted dynamic-agent warnings during tests; the test suite still completed with zero failures
  and zero errors.

These warnings are recorded for transparency. Based on the owner-reported successful commands and results, none blocked
the Phase 2 acceptance criteria.

## 6. Phase 2 acceptance decision

**Phase 2 exit criteria: PASSED.**

On 2026-11-26, the owner explicitly confirmed:

> 依據 ecommerce-marketing-platform-v0.1.0-phase2-review.zip  
> 我確認 Phase 2 通過測試，正式接受 Phase 2

The owner-reported environment, PostgreSQL, backend/Flyway, schema/constraint/index, backend integration test, and
frontend regression checks all passed. Phase 2 is therefore **formally accepted and closed as of 2026-11-26**.

Phase 1 remains complete and owner-accepted. No Phase 1 or Phase 2 acceptance work remains outstanding.

## 7. Explicitly out of scope

No authentication, JWT, refresh-token rotation, security configuration, business repositories/services, CRUD APIs,
audience evaluation, coupon claim workflow, campaign publication, message delivery worker, Redis, Kafka, or additional
persistence technology was implemented in Phase 2.

## 8. Next phase gate

Phase 3 — Identity and Security has been documented in `PHASE_3_PLAN.md` for owner review only. Planning does not
authorize implementation. Phase 3 may begin only after the owner explicitly approves its scope and authorizes
development.
