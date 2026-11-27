# Phase 1 — Repository Foundation: Completion and Acceptance Report

**Project baseline:** v1.5.0  
**Implementation date:** 2026-11-26  
**Status:** COMPLETE — the owner formally accepted Phase 1 on 2026-11-26 after completing the verification steps in
their environment. This report records the owner-reported results; it does not claim those environment-specific commands
were executed in the AI implementation environment.

## 1. Scope implemented

| Requirement               | Implementation                                                                                                         | Status                                                                                                  |
|---------------------------|------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Backend scaffold          | Java 21 + Spring Boot application and Maven build                                                                      | Implemented; owner-reported `./mvnw -f backend/pom.xml verify` succeeded (BUILD SUCCESS; 1 test passed) |
| Frontend scaffold         | React + TypeScript + Vite SPA                                                                                          | Implemented; owner-reported typecheck, tests, and production build succeeded                            |
| Exact dependency versions | Pinned in Maven parent and `frontend/package.json`                                                                     | Selected and documented                                                                                 |
| Local PostgreSQL          | `compose.yaml`, named persistent volume at `/var/lib/postgresql` (PostgreSQL 18+ layout), health check, `.env.example` | Implemented; owner reported PostgreSQL container healthy via `docker compose ps`                        |
| Health/readiness          | Spring Boot Actuator `/actuator/health`, `/readyz`, `/livez`                                                           | Implemented; owner reported backend health UP                                                           |
| Frontend calls backend    | Typed health API client + Vite `/actuator` proxy                                                                       | Implemented; owner reported frontend page displayed and health connection verified                      |
| README/setup              | Local setup, run, and verification commands                                                                            | Updated and exercised as part of owner verification                                                     |
| No business features      | No domain modules, migrations, auth, or campaign/coupon workflows                                                      | Preserved                                                                                               |

## 2. Selected versions

| Component              | Version | Rationale                                                                                                            |
|------------------------|--------:|----------------------------------------------------------------------------------------------------------------------|
| Java                   |      21 | Required project baseline; LTS JDK                                                                                   |
| Spring Boot            |   4.2.0 | Current stable Spring Boot release on 2026-11-26; official system requirements support Java 17–27, including Java 21 |
| Maven                  |  3.10.2 | Current stable Maven 3 release; pinned bootstrap script verifies the official SHA-512 sidecar                        |
| Node.js                |  22.12+ | Meets Vite 8's Node.js 22.12+ requirement; Node 22 is the chosen runtime line                                        |
| npm                    |     10+ | Comes with supported Node 22 distributions and supports lockfile-based installs                                      |
| React / React DOM      |  19.3.1 | Exact stable versions selected from npm's current stable release tags                                                |
| Vite                   |   8.3.8 | Exact stable version; supports the available Node 22.16 runtime                                                      |
| `@vitejs/plugin-react` |   6.3.0 | Matching current Vite React plugin line                                                                              |
| TypeScript             |   7.0.2 | Exact stable version; strict compiler mode enabled                                                                   |
| `@types/react`         |  19.3.3 | React 19 type definitions compatible with TypeScript 7                                                               |
| `@types/react-dom`     |  19.3.0 | React DOM 19 type definitions                                                                                        |
| `@types/node`          | 22.20.6 | Node.js 22 type definitions for Vite configuration and tooling                                                       |
| PostgreSQL             |    18.7 | Current patched PostgreSQL 18 minor release; PostgreSQL 18 remains supported                                         |

The exact package versions are pinned in `backend/pom.xml`, `frontend/package.json`, and `compose.yaml`. A frontend
lockfile must be generated by `npm install` in a network-enabled development environment and committed before treating
dependency resolution as fully reproducible. It could not be generated in this environment because the npm registry is
unreachable.

### Version references checked

- Spring Boot system requirements and stable releases: <https://docs.spring.io/spring-boot/system-requirements.html>
- Maven download and current release: <https://maven.apache.org/download.cgi>
- Vite release and Node compatibility: <https://vite.dev/releases> and <https://vite.dev/guide/>
- npm package
  versions: <https://www.npmjs.com/package/react>, <https://www.npmjs.com/package/react-dom>, <https://www.npmjs.com/package/vite>, <https://www.npmjs.com/package/@vitejs/plugin-react>, <https://www.npmjs.com/package/typescript>, <https://www.npmjs.com/package/@types/node>
- PostgreSQL supported releases: <https://www.postgresql.org/support/versioning/>

## 3. Files created or changed

### Root

- `.env.example`
- `.gitignore`
- `compose.yaml`
- `mvnw`, `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`
- `README.md`

### Backend

- `backend/pom.xml`
- `backend/src/main/java/com/example/marketingplatform/MarketingPlatformApplication.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/example/marketingplatform/HealthEndpointTest.java`

### Frontend

- `frontend/package.json`
- `frontend/index.html`
- `frontend/vite.config.ts`
- `frontend/tsconfig.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.node.json`
- `frontend/src/vite-env.d.ts`
- `frontend/src/main.tsx`
- `frontend/src/app/App.tsx`
- `frontend/src/shared/lib/healthApi.ts`
- `frontend/src/styles.css`
- `frontend/scripts/foundation.test.mjs`

### Documentation

- `docs/development/IMPLEMENTATION_PLAN.md`
- `docs/development/AI_DEVELOPMENT_GUIDELINES.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/README.md`
- `docs/development/PHASE_1_IMPLEMENTATION_REPORT.md`
- `README.md`

## 4. Verification results and owner acceptance

The following results were reported by the owner after running the project in their development environment on
2026-11-26. They are distinguished from the static checks performed during the original implementation pass.

| Check                     | Command / method                             | Result                                                                 |
|---------------------------|----------------------------------------------|------------------------------------------------------------------------|
| Backend build and tests   | `./mvnw -f backend/pom.xml verify`           | **Owner-reported: BUILD SUCCESS; 1 test passed, 0 failures, 0 errors** |
| Frontend tests            | `npm run test`                               | **Owner-reported: 4 tests passed, 0 failed**                           |
| Frontend TypeScript       | `npm run typecheck`                          | **Owner-reported: passed**                                             |
| Frontend production build | `npm run build`                              | **Owner-reported: passed; Vite build successful**                      |
| PostgreSQL service        | `docker compose ps`                          | **Owner-reported: PostgreSQL container healthy**                       |
| Backend health            | Runtime health endpoint / application status | **Owner-reported: Backend Health UP**                                  |
| Frontend runtime          | Open frontend in browser                     | **Owner-reported: frontend page displayed**                            |
| Owner acceptance          | Explicit owner confirmation in conversation  | **Accepted — Phase 1 formally accepted on 2026-11-26**                 |

The original implementation environment had no Maven or Docker executable and could not reach Maven Central/npm
registry. Those limitations describe the original implementation environment only; they do not override the successful
verification results subsequently supplied by the owner. Mockito/Byte Buddy dynamic-agent warnings were non-fatal and
did not cause the reported test run to fail.

## 5. Phase 1 acceptance decision

**Phase 1 exit criteria: PASSED.** The owner confirmed the verification results above and explicitly stated: “phase1
確定驗收完成”. Phase 1 is therefore closed and accepted as of 2026-11-26.

No Phase 1 acceptance work remains outstanding. At the time of Phase 1 acceptance, Phase 2 had not yet been authorized.
The owner subsequently explicitly authorized Phase 2 — Database Foundation on 2026-11-26; its implementation status is
tracked in `PHASE_2_IMPLEMENTATION_REPORT.md`.

## 6. Intentionally deferred to later phases

- No Flyway dependency, migration, entity, repository, or schema.
- No Spring Security, JWT, refresh token, login, or CSRF implementation.
- No domain modules or campaign/audience/coupon business logic.
- No production deployment topology, secrets manager, Redis, Kafka, or external provider.

## 7. Next phase gate

Phase 1 remains closed and accepted. Phase 2 was separately authorized on 2026-11-26; no Phase 3 work is authorized by
that decision.
