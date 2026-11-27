# Phase 3 Implementation Report — Identity and Security

**Baseline:** `ecommerce-marketing-platform-v0.1.0-phase3-readytodevelop.zip`  
**Implementation authorization:** Owner instruction received 2026-11-26.  
**Implementation status:** Implemented — owner verification pending.  
**Phase acceptance:** NOT ACCEPTED YET.  
**Database migration:** None added; existing V1/V2 migrations remain unchanged.

## 1. Delivered scope

### Backend

- Added Spring Security and Spring Security JOSE dependencies.
- Added the `identity` module for active-user lookup, BCrypt password verification, JWT issuance/validation,
  refresh-session persistence, token rotation, family revocation, and logout.
- Added stateless access-cookie authentication. The current role is reloaded from the active `users` row on each
  authenticated request.
- Added the five documented endpoints:
    - `GET /api/v1/auth/csrf`
    - `POST /api/v1/auth/login`
    - `POST /api/v1/auth/refresh`
    - `POST /api/v1/auth/logout`
    - `GET /api/v1/auth/me`
- Added role boundaries for `/api/v1/admin/**` and `/api/v1/member/**`; public GET campaign paths and MEMBER-only
  claim/event POST paths are configured for the future API contract.
- Added CSRF cookie/header validation, explicit credentialed CORS allowlist, stateless session policy, and generic
  authentication errors.
- Access and refresh cookies are HttpOnly, host-only, SameSite=Lax, with paths `/api/v1` and `/api/v1/auth`.
  `XSRF-TOKEN` is readable and uses `/` so the SPA can read it.
- Refresh rows store SHA-256 hashes only. A generation is consumed and replaced in one transaction. Reuse revokes active
  refresh rows in the token family.
- Logout uses the refresh cookie and does not require a valid access cookie.

### Local demo credentials

The original V2 migration's BCrypt hash has no documented plaintext password. It was not guessed or changed.

An opt-in initializer is available only under the `local` Spring profile. If `DEMO_SEED_PASSWORD` is explicitly set in
the developer's untracked `.env`, startup re-hashes that supplied password and applies it to the four fictional
`@example.test` seed accounts. If it is blank, the V2 hashes remain untouched. No plaintext password is committed and no
migration is modified.

### Frontend

- Added typed auth API operations for login, refresh, logout, and current-user lookup.
- Added `AuthProvider` with loading/authenticated/anonymous/error state vocabulary.
- Added a minimal login/logout UI.
- Uses `credentials: 'include'`, the `X-CSRF-TOKEN` header, a shared single-flight refresh promise, and at most one
  retry for an eligible request.
- Does not use localStorage, sessionStorage, IndexedDB, or application-managed persistent storage for tokens.
- Added Vite proxying for `/api` and retained the health endpoint proxy.

## 2. Important implementation behavior

- Access token default TTL: 15 minutes.
- Refresh token default TTL: 7 days.
- JWT algorithm: HS256 using a Base64-encoded key supplied through `JWT_SECRET_BASE64`; the application rejects keys
  shorter than 256 bits.
- JWT issuer, audience, token TTLs, frontend origin, and cookie Secure flag are environment-configurable.
- The `local` profile's committed `application-local.yml` is the only configuration that disables Secure cookies for
  plain HTTP localhost; the base configuration keeps Secure enabled.
- Do not use the sample key in `.env.example` outside local development.

## 3. Files added or changed

Key backend files:

- `backend/pom.xml`
- `backend/src/main/java/com/example/marketingplatform/config/SecurityConfiguration.java`
- `backend/src/main/java/com/example/marketingplatform/identity/**`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/test/resources/application.yml`
- `backend/src/test/java/com/example/marketingplatform/identity/**`

Key frontend files:

- `frontend/src/shared/lib/authApi.ts`
- `frontend/src/features/auth/AuthProvider.tsx`
- `frontend/src/features/auth/LoginForm.tsx`
- `frontend/src/app/App.tsx`
- `frontend/src/main.tsx`
- `frontend/src/styles.css`
- `frontend/vite.config.ts`
- `frontend/scripts/foundation.test.mjs`

Documentation/configuration:

- `.env.example`
- `README.md`
- `docs/development/PHASE_3_PLAN.md`
- `docs/development/PHASE_3_IMPLEMENTATION_REPORT.md`

No V1/V2 SQL migration was edited.

## 3.1 Owner verification follow-up — Spring Boot 4 MockMvc test wiring

The first owner-side `mvnw verify` run reached `testCompile` but failed because the Phase 3 test sources used the Spring
Boot 3 MockMvc package and the project did not declare the Spring Boot 4 MVC test starter. The follow-up correction is:

- Add `spring-boot-starter-webmvc-test` with `test` scope.
- Update `AutoConfigureMockMvc` imports to `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- No production code, API contract, database schema, or migration is changed by this correction.

This correction is based on the Spring Boot 4.2.0 MVC testing package and starter layout. The owner then reran
`mvnw verify`: main and test sources compiled, and the test suite executed. That run exposed three additional
test/runtime defects, addressed in the next follow-up below.

## 3.2 Owner verification follow-up — JWT issuer handling and shared Testcontainers lifecycle

The next owner-side run reported 23 tests: 18 passed, 1 assertion failure, and 4 errors. The failures were traced to
these implementation/test defects:

- JWT issuer comparison called Spring Security's `Jwt.getIssuer()`, which converts the `iss` claim to a URL. The
  configured issuer is a valid JWT StringOrURI identifier but is not necessarily an absolute URL. Validation now
  compares the raw `iss` string claim using `getClaimAsString("iss")`; a unit assertion protects this behavior.
- The JUnit `@Container` extension stopped the static PostgreSQL container after each test class, while Spring's test
  context cache retained a `DataSource` configured with the previous container's mapped port. The integration-test base
  now starts one singleton PostgreSQL container for the test JVM and leaves it running for the suite, so cached contexts
  continue to use a live database. The per-class `@Container` / `@Testcontainers` lifecycle annotations were removed.
- The role-boundary test's 401 was a consequence of the JWT issuer conversion exception being swallowed by the
  access-cookie filter, so the request was treated as anonymous. Fixing issuer parsing addresses the authentication
  failure; the authorization expectation remains 403 for an authenticated user with the wrong role.

The owner-side run also showed 14 database schema tests passing and the health endpoint test passing. The remaining 5
failing tests are not represented as passing; they require a rerun against this follow-up ZIP.

## 3.3 Final Code Review fixes

The final review follow-up is limited to Phase 3 Identity/Security behavior; it does not add business features,
infrastructure, or database migrations.

### Frontend session-state handling

- `authApi.ts` now throws a typed `AuthApiError` carrying the HTTP status instead of reducing every HTTP failure to a
  generic `Error`.
- `AuthProvider.reload()` treats only HTTP 401 as an anonymous session. HTTP 403/5xx and network failures remain visible
  as an error state instead of being mistaken for a signed-out user.
- `AuthProvider.signOut()` clears local identity only after the logout endpoint succeeds. If the request fails, the
  authenticated view remains visible and an error explains that server-side session revocation could not be confirmed.
- `LoginForm` handles the logout Promise and displays the failure instead of creating an unhandled rejection.

### Security lifecycle regression coverage added

- Invalid access-token signature is rejected by the authenticated endpoint.
- JWT decoder rejects an expired access token.
- Expired refresh-session rows and unknown refresh tokens are rejected; expiry/reuse revokes the associated active token
  family where applicable.
- Refresh and logout reject requests missing the CSRF header, even when cookies are present.
- CORS preflight accepts the configured frontend origin and rejects an unlisted origin.
- Login response is asserted not to contain access/refresh JWT values.
- Logout is exercised without an access cookie, confirming it does not depend on access-token authentication.
- Existing PostgreSQL concurrent refresh coverage remains in place.

### API test-script hardening

The shared zsh helper now applies `umask 077` before creating test artifacts and enforces mode `700` on the test-output
directory. Cookie jars and request bodies retain their explicit restrictive permissions.

No production database migration or business-module scope was added.

## 4. Verification performed in the implementation environment

| Check                                       | Result  | Notes                                                                                                                                                                   |
|---------------------------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ZIP source inspection                       | PASS    | Exact owner-provided Phase 3 baseline inspected                                                                                                                         |
| Frontend Node tests                         | PASS    | Final-review package: 8/8 tests passed in this packaging environment using Node 22.16.0.                                                                                |
| Frontend TypeScript typecheck               | BLOCKED | `node_modules` is absent in this packaging environment; React and Node type declarations are unavailable. Owner must run after `npm ci`.                                |
| Frontend production build                   | NOT RUN | Dependencies are absent; owner must run after `npm ci`.                                                                                                                 |
| Backend Maven test/verify                   | BLOCKED | Maven wrapper bootstrap attempted but DNS/network access to `dlcdn.apache.org` is unavailable.                                                                          |
| PostgreSQL/Testcontainers integration tests | NOT RUN | Docker is not installed in this environment.                                                                                                                            |
| Runtime browser/API verification            | NOT RUN | Backend could not be compiled/launched in this environment. The prior owner API result (26 PASS / 0 FAIL) predates these final-review source changes and must be rerun. |

The blocked checks are not represented as passing. Owner-side verification is required using the commands in Section 5.

### 4.1 Owner-side Maven verification evidence (2026-11-26)

| Check                                     | Result                            | Evidence / follow-up                                                                                                                      |
|-------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Backend main/test compilation             | PASS                              | Owner log compiled 16 production and 7 test source files after the MockMvc dependency correction.                                         |
| Database schema integration tests         | PASS                              | 14 tests passed.                                                                                                                          |
| Health endpoint test                      | PASS                              | 1 test passed.                                                                                                                            |
| Role-boundary integration test            | FAIL — corrected, rerun required  | Authenticated-role request returned 401 instead of expected 403 due to issuer claim conversion; see Section 3.2.                          |
| Refresh rotation integration test         | ERROR — corrected, rerun required | Cached Spring context reused a DataSource after the per-class Testcontainers extension stopped its PostgreSQL container; see Section 3.2. |
| Login / refresh / logout integration test | ERROR — corrected, rerun required | Same PostgreSQL container lifecycle failure; see Section 3.2.                                                                             |
| JWT token service unit tests              | ERROR — corrected, rerun required | `Jwt.getIssuer()` attempted String-to-URL conversion; see Section 3.2.                                                                    |
| Overall Maven verify                      | FAIL — rerun required             | Owner reported 23 tests: 18 passed, 1 failure, 4 errors. This follow-up ZIP has not yet been run by the owner.                            |

### 4.2 Final-review test additions

The tests described in Section 3.3 are included in this ZIP but have not been executed against the owner's
Docker/PostgreSQL environment in this packaging session. The previous owner result of 26 PASS / 0 FAIL applies to the
earlier API suite; the zsh suite was hardened but its test count and assertions were not changed. Re-run backend
`verify`, frontend checks, and the API suite against this exact ZIP before marking Phase 3 accepted.

## 5. Owner verification checklist

### 5.1 Prepare local environment

1. Unzip the project.
2. Copy `.env.example` to `.env`.
3. Set a private local demo password in `.env`:

   ```dotenv
   DEMO_SEED_PASSWORD=choose-a-local-only-password
   ```

   Use a strong local-only value. Do not commit `.env`.

4. Keep the sample `JWT_SECRET_BASE64` only for local development.
5. Install frontend dependencies:

   ```bash
   cd frontend
   npm ci
   cd ..
   ```

### 5.2 Start PostgreSQL and backend

From the project root:

```bash
set -a
. ./.env
set +a

docker compose up -d postgres
docker compose ps

./mvnw -f backend/pom.xml verify

./mvnw -f backend/pom.xml spring-boot:run
```

The `.env.example` sets `SPRING_PROFILES_ACTIVE=local`, which loads `application-local.yml` and disables Secure cookies
only for local HTTP testing.

In another terminal, verify:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/readyz
curl --fail http://localhost:8080/livez
```

### 5.3 Start frontend

```bash
cd frontend
npm ci
npm run test
npm run typecheck
npm run build
npm run dev
```

Open `http://localhost:5173`.

### 5.4 Login and cookie verification

Use the local seed accounts:

- `admin@example.test`
- `member.one@example.test`
- `member.two@example.test`
- `member.three@example.test`

The password is the value you supplied as `DEMO_SEED_PASSWORD`.

Verify in browser Developer Tools → Application/Storage → Cookies:

- `access_token`: HttpOnly, Path `/api/v1`, SameSite `Lax`, Secure off only on local HTTP.
- `refresh_token`: HttpOnly, Path `/api/v1/auth`, SameSite `Lax`.
- `XSRF-TOKEN`: readable by JavaScript, Path `/`.

Verify that the access and refresh JWT values do not appear in localStorage, sessionStorage, IndexedDB, or login
response JSON.

### 5.5 API behavior

Run these requests using browser DevTools or an HTTP client that preserves cookies:

1. `GET /api/v1/auth/csrf` — receives a CSRF token/cookie.
2. `POST /api/v1/auth/login` with valid CSRF header and demo credentials — returns 200 and sets the two HttpOnly
   cookies.
3. `GET /api/v1/auth/me` — returns the current user's ID and role.
4. `POST /api/v1/auth/refresh` with refresh cookie and CSRF header — returns 200 and replaces both auth cookies.
5. Re-submit the old refresh token — returns 401 and revokes the token family.
6. `POST /api/v1/auth/logout` with a current refresh cookie and CSRF header — returns 204 and clears all cookies.
7. Repeat logout — remains safe and returns 204.
8. POST login/refresh/logout without CSRF header — returns 403.
9. Anonymous `GET /api/v1/auth/me`, `/api/v1/admin/probe`, and `/api/v1/member/probe` — returns 401.
10. After logging in as MEMBER, `GET /api/v1/admin/probe` — returns 403. After logging in as ADMIN,
    `GET /api/v1/member/probe` — returns 403.

For step 5, use a separate cookie jar or preserve a copy of the original refresh cookie before rotating. Reuse is
intentionally treated as a security event: active refresh tokens in that family are revoked.

### 5.6 Concurrent refresh verification

The backend integration suite includes a PostgreSQL concurrency test. Confirm it passes with Docker available:

```bash
./mvnw -f backend/pom.xml -Dtest=RefreshRotationIntegrationTest test
```

Expected: one concurrent request rotates the presented generation; the other is rejected as reuse and causes active
refresh generations in that family to be revoked.

## 6. Acceptance status

Phase 3 implementation and the final Code Review fixes are delivered for owner verification, but Phase 3 is **not yet
marked COMPLETE or ACCEPTED**. Please return:

- backend `./mvnw -f backend/pom.xml verify` result,
- frontend `npm run test`, `npm run typecheck`, and `npm run build` results,
- Docker Compose/PostgreSQL status,
- login/refresh/logout/cookie/CSRF/role-boundary observations,
- any failing logs with secrets and cookie/token values redacted.

After reviewing those results, defects will be fixed before requesting formal Phase 3 acceptance.
