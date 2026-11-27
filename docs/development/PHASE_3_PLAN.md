# Phase 3 Plan — Identity and Security

**Document status:** Owner-authorized implementation baseline — accepted 2026-11-27.  
**Implementation status:** COMPLETE — owner-verified and accepted on 2026-11-27.  
**Baseline:** Owner-approved v1.5.0 Development Baseline.  
**Preceding phases:** Phase 1 and Phase 2 formally accepted on 2026-11-26.  
**Purpose:** Record the bounded Phase 3 work package, implementation scope, and acceptance outcome.

> The owner explicitly authorized implementation from `ecommerce-marketing-platform-v0.1.0-phase3-readytodevelop.zip` on
> 2026-11-26. This plan remains the scope and acceptance authority.

## 1. Objective

Implement the identity and security foundation required by the approved MVP:

- Authenticate users from the existing `users` table.
- Issue and validate short-lived JWT access tokens and rotating refresh tokens using HttpOnly cookies.
- Persist refresh-token session state in the existing `refresh_token_sessions` table.
- Enforce refresh-token rotation, single-use consumption, token-family reuse detection, revocation, and logout.
- Enforce `ADMIN` / `MEMBER` / anonymous access boundaries.
- Protect cookie-authenticated state-changing requests with CSRF.
- Provide the frontend login/session flow without storing access or refresh tokens in browser storage.

Phase 3 is limited to identity and security. It does not implement member profile, audience, campaign, coupon,
messaging, landing-page, or analytics business features.

## 2. Authoritative source documents

Phase 3 implementation must follow these approved documents:

1. `docs/PRD.md` — product scope and user roles.
2. `docs/architecture/SECURITY_DESIGN.md` — authentication, token lifecycle, cookies, CSRF, authorization, password
   handling, and security tests.
3. `docs/api/API_SPECIFICATION.md` — authentication endpoint and cookie/CSRF contract.
4. `docs/api/openapi.yaml` — machine-readable API contract.
5. `docs/api/ERROR_CODES.md` — stable error response codes.
6. `docs/database/DATABASE_DESIGN.md` and `docs/database/ERD.md` — refresh-session persistence model.
7. `docs/architecture/ARCHITECTURE.md` and `docs/architecture/MODULE_DESIGN.md` — modular monolith boundaries and
   identity ownership.
8. `docs/development/DEVELOPMENT_GUIDELINES.md` and `docs/development/TESTING_STRATEGY.md` — implementation and
   verification requirements.
9. Relevant accepted ADRs, especially `ADR-001` and `ADR-002`.

If these documents conflict or an implementation choice changes their approved security contract, stop and ask the
owner. Do not silently change the contract.

## 3. Scope

### 3.1 Backend — identity module

- Add Spring Security integration compatible with the repository's Spring Boot 4.2.0 / Java 21 baseline.
- Implement password verification against `users.password_hash`; use a secure one-way password encoder consistent with
  the existing BCrypt seed hashes unless a reviewed decision requires otherwise.
- Implement authentication of active users and generic credential-failure responses that do not disclose whether an
  email exists.
- Issue access and refresh JWTs with minimal claims: subject/user ID, role, issued-at, expiry, token ID, and
  issuer/audience as appropriate. Do not embed member profile, coupon, or audience attributes.
- Use configurable TTL defaults from the approved design: access token 15 minutes; refresh token 7 days.
- Persist only a SHA-256 hash of the complete serialized refresh JWT in `refresh_token_sessions`; never persist or log
  the raw refresh token.
- Use one `token_family_id` per login session. Each refresh token generation has its own row.
- Consume the presented refresh-token row and create/link the next generation in one database transaction.
- Ensure only one concurrent request can consume a token generation.
- On reuse of a consumed or revoked token, revoke all still-active rows in that token family.
- Reject unknown, expired, revoked, or otherwise invalid refresh tokens without disclosing whether a user/session
  exists.
- Implement logout using the refresh cookie, so logout still revokes the family and clears cookies when the access token
  has expired.
- Implement `/api/v1/auth/me` from the authenticated principal; member identity must be resolved server-side in later
  member use cases.

### 3.2 Backend — HTTP security boundary

Implement the approved authentication endpoints:

| Method | Path                   | Access                         | Purpose                                                                |
|--------|------------------------|--------------------------------|------------------------------------------------------------------------|
| GET    | `/api/v1/auth/csrf`    | Anonymous                      | Initialize/return the CSRF cookie/token                                |
| POST   | `/api/v1/auth/login`   | Anonymous + CSRF               | Verify credentials and set access/refresh cookies                      |
| POST   | `/api/v1/auth/refresh` | Refresh cookie + CSRF          | Consume/rotate refresh token and issue new cookies                     |
| POST   | `/api/v1/auth/logout`  | Optional refresh cookie + CSRF | Revoke the token family when available, clear auth cookies, return 204 |
| GET    | `/api/v1/auth/me`      | Authenticated                  | Return current user ID and role                                        |

- Configure stateless request authentication using the access cookie.
- Enforce role boundaries for `/api/v1/admin/**` and `/api/v1/member/**` route groups, even though business endpoints
  are delivered in later phases.
- Keep public health endpoints and explicitly public authentication/CSRF routes accessible as documented.
- Require CSRF validation for login, refresh, logout, and all state-changing cookie-authenticated requests.
- Configure credentialed CORS with an explicit frontend-origin allowlist; never use wildcard origins with credentials.
- Validate `Origin` / `Referer` as defense in depth where feasible.
- Return the documented error envelope and stable application codes; do not expose stack traces, SQL, secrets, or
  internal class names.
- Do not add Redis, an external identity provider, or a separate authentication service.

### 3.3 Cookie contract

Use the approved defaults from `SECURITY_DESIGN.md` and `API_SPECIFICATION.md`:

| Cookie          | JavaScript-readable? | Path                         | Policy                                                           |
|-----------------|---------------------:|------------------------------|------------------------------------------------------------------|
| `access_token`  |      No (`HttpOnly`) | `/api/v1`                    | `SameSite=Lax`; `Secure` in HTTPS; host-only                     |
| `refresh_token` |      No (`HttpOnly`) | `/api/v1/auth`               | `SameSite=Lax`; `Secure` in HTTPS; host-only                     |
| `XSRF-TOKEN`    |                  Yes | Frontend/API-compatible path | Contains only the CSRF token, never an authentication credential |

- The local development profile may disable `Secure` only for plain HTTP localhost.
- Do not set a cookie `Domain` unless a separately reviewed deployment requirement exists.
- Frontend writes send the CSRF value in `X-CSRF-TOKEN`.
- Do not return JWT values in response JSON or expose them to frontend JavaScript.

### 3.4 Frontend — authentication foundation

- Add a typed authentication API client for login, refresh, logout, current-principal lookup, and CSRF initialization.
- Add an authentication state provider/context with explicit loading, authenticated, anonymous, and error states.
- Add a minimal login interaction consistent with the existing single React SPA; no separate frontend application.
- Use `credentials: 'include'` for cookie-authenticated requests.
- Send `X-CSRF-TOKEN` for state-changing requests using the readable CSRF cookie.
- Implement a single-flight refresh operation so concurrent requests encountering an expired access token share one
  refresh request.
- Retry an eligible request at most once after successful refresh; avoid infinite refresh loops.
- On refresh failure, clear frontend authentication state and return to the anonymous/login state.
- Do not store access tokens or refresh tokens in `localStorage`, `sessionStorage`, IndexedDB, or application-managed
  persistent storage.
- Frontend route guards are UX only; backend authorization remains authoritative.

The Phase 3 frontend deliverable is the authentication/session foundation. Full Admin and Member business screens remain
later-phase work.

## 4. Token lifecycle and transaction expectations

### Login

1. Receive email/password over HTTPS (localhost exception only for local development).
2. Find an active user by normalized email according to the existing account contract.
3. Verify the password hash with a constant/generic failure response.
4. Generate a new token-family UUID and refresh-token generation.
5. Persist only the refresh JWT hash and its metadata.
6. Set access and refresh HttpOnly cookies and initialize the CSRF cookie/token as required by the approved contract.

### Refresh

1. Read the refresh cookie and validate signature, issuer/audience, expiry, and required claims.
2. Hash the complete serialized refresh JWT and locate its session row.
3. In one transaction, lock/consume the current generation only if it is unconsumed, unrevoked, and unexpired.
4. Insert the next generation with the same user and token family; set the predecessor's `replaced_by_token_id`.
5. Commit before returning the new cookie pair.
6. If the token was already consumed/revoked, revoke all active rows in that family and reject the request.
7. Handle concurrent requests so exactly one request consumes a generation; the losing/reuse path must not issue another
   valid generation.

### Logout

1. Accept an optional refresh cookie even when the access token is absent or expired.
2. If the refresh token identifies a family, revoke all active rows in that family.
3. Clear access, refresh, and CSRF cookies using matching cookie paths/attributes as applicable.
4. Return `204 No Content`; repeated logout must be safe.

## 5. Expected implementation areas

The following are planning-level file/module areas, not a command to create them now. Exact package names and class
boundaries should be confirmed against the existing repository before implementation.

### Backend

- `backend/pom.xml` — Spring Security and selected JWT support dependencies.
- `backend/src/main/resources/application.yml` — security configuration binding, cookie policy, TTLs, issuer/audience,
  CORS allowlist, and signing-key configuration through environment variables.
- `identity/domain` — user identity, role, refresh-token family/generation concepts and policies.
- `identity/application` — authenticate, refresh, logout, and get-current-principal use cases.
- `identity/infrastructure` — JDBC repositories for users and refresh sessions, password encoder, JWT issuer/verifier,
  clock and cookie adapters.
- `identity/presentation` — authentication controllers, request/response DTOs, and exception mapping.
- Security configuration — stateless filter chain, access-cookie authentication, route authorization, CSRF, CORS, and
  security headers.
- Tests — unit, PostgreSQL integration, MockMvc/API security, and refresh concurrency coverage.

### Frontend

- `frontend/src/shared/api/` or the agreed equivalent — typed API client and cookie/CSRF helpers.
- `frontend/src/features/auth/` — login, session state, and authentication use cases.
- `frontend/src/app/` — authentication provider and minimal route integration.
- Tests for auth state, CSRF header behavior, single-flight refresh, and retry limits.

Avoid creating empty abstractions or speculative modules. Follow the existing modular-monolith and clean-architecture
rules.

## 6. Prerequisites and implementation preflight

Implementation preflight decisions applied:

- Phase 1 and Phase 2 remain accepted; no regression or unresolved database defect is known from the recorded
  verification.
- Re-read the authoritative documents listed in Section 2.
- JWT implementation uses Spring Security JOSE / Nimbus with HS256 and a Base64-encoded key of at least 256 bits
  supplied by `JWT_SECRET_BASE64`. The key is external to source control; `.env.example` contains a local-only
  placeholder key.
- Configured local frontend origin is `http://localhost:5173`; `FRONTEND_ORIGIN` is the explicit credentialed CORS
  allowlist entry.
- Local seeded-account login uses an opt-in initializer restricted to the `local` Spring profile. A developer supplies
  `DEMO_SEED_PASSWORD` through an untracked `.env`; the initializer hashes it and updates only the four fictional
  `@example.test` accounts. Blank/unset leaves V2 hashes unchanged. No plaintext password is committed and V2 is not
  edited.
- Local HTTP uses the `local` profile to disable Secure; other profiles default to Secure cookies.
- Inactive-user policy: only `users.status = 'ACTIVE'` can authenticate; inactive users receive the same generic
  credential failure.
- No new infrastructure is required. PostgreSQL remains the only persistence store; no Redis/Kafka or external identity
  provider was added.

## 7. Explicitly out of scope

- Member profile API and member profile UI.
- Audience segments, audience rule validation/evaluation, preview, and snapshots.
- Campaign CRUD, publication, pause, and delivery.
- Coupon configuration, claims, inventory mutation, and claim audit orchestration.
- Inbox, landing page, engagement events, and analytics.
- Password reset, email verification, MFA, social login, account registration, and external identity providers.
- Multi-tenant identity, organization management, and admin user-management UI.
- Redis, Kafka, microservices, distributed session storage, and new infrastructure.
- Production hosting/deployment, production secret-manager integration, and formal key-rotation operations beyond
  documenting the environment configuration.

If a required feature appears to fall into this list, stop and obtain a scope decision rather than silently adding it.

## 8. Acceptance criteria — owner accepted

### Authentication and token lifecycle

- [x] Valid active user credentials authenticate and issue the documented cookies.
- [x] Invalid email/password produces a generic authentication failure without account enumeration.
- [x] Inactive users cannot authenticate.
- [x] Access token is accepted before expiry and rejected after expiry or invalid signature.
- [x] Refresh token is validated and rotated; the previous generation cannot be used again.
- [x] Only one of two concurrent refresh attempts can consume a generation and create the next generation.
- [x] Reuse of a consumed/revoked refresh token revokes the entire token family; all active family tokens subsequently
  fail.
- [x] Unknown, expired, and revoked refresh tokens are rejected without leaking account/session existence.
- [x] Refresh session rows store only SHA-256 hashes of serialized refresh JWTs; raw tokens never appear in database
  records or logs.
- [x] Logout revokes the presented family and clears cookies even when the access token has expired; repeated logout is
  safe.
- [x] Access and refresh TTL defaults are 15 minutes and 7 days and are environment-configurable.

### Cookies, CSRF, CORS, and authorization

- [x] Access and refresh cookies are HttpOnly, host-only, use the documented paths and SameSite policy, and set Secure
  in HTTPS.
- [x] Local HTTP Secure-cookie exception is restricted to the local development profile.
- [x] CSRF cookie contains no authentication credential; missing/invalid CSRF header rejects login, refresh, logout, and
  state-changing cookie-authenticated requests.
- [x] Credentialed CORS accepts only configured origins; wildcard origin is not used.
- [x] Anonymous requests cannot access protected Admin or Member route groups.
- [x] A `MEMBER` principal is rejected from Admin routes; an `ADMIN` principal is rejected from Member-only routes where
  the contract requires MEMBER.
- [x] `/auth/me` returns only the authenticated user's stable user ID and role.
- [x] Frontend never stores JWTs in browser storage; refresh is single-flight and request retry is bounded to one retry.
- [x] Sensitive credentials, token values, cookie contents, and authorization headers do not appear in logs.

### Regression and reproducibility

- [x] Backend `./mvnw -f backend/pom.xml verify` passes, including PostgreSQL integration tests.
- [x] Frontend `npm run test`, `npm run typecheck`, and `npm run build` pass.
- [x] Existing Phase 1/2 health, Flyway, schema, and seed behavior remains intact.
- [x] Security tests cover token lifecycle, CSRF, authorization, and concurrent refresh.
- [x] API Specification and OpenAPI remain synchronized; any approved contract changes are documented.
- [x] Owner verifies the Phase 3 behavior and explicitly accepts Phase 3.

## 9. Risks and controls

| Risk                                                        | Control                                                                                                    |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Concurrent refresh requests consume one token twice         | Atomic conditional consume/row lock and PostgreSQL concurrency test                                        |
| Refresh-token replay after rotation                         | Revoke the full token family and test all subsequent generations                                           |
| CSRF protection accidentally disabled on auth routes        | Explicit tests for login, refresh, logout, and generic state-changing routes                               |
| CORS wildcard combined with credentials                     | Explicit allowlist and configuration tests                                                                 |
| JWT or cookie values leak through logs or responses         | Redaction, negative log assertions, and response contract tests                                            |
| Existing seed hashes have no documented plaintext password  | Do not guess; use the opt-in local-profile credential initializer documented in the implementation report. |
| JWT signing key is weak or reused outside local development | Enforce a minimum 256-bit HS256 key and keep environment-specific keys outside source control              |

## 10. Phase gate and acceptance outcome

Implementation was performed under the owner's explicit instruction. The owner completed the verification checklist
and formally accepted Phase 3 on 2026-11-27. The final results are recorded in
`PHASE_3_IMPLEMENTATION_REPORT.md`, Section 4.2.

Phase 3 is COMPLETE and ACCEPTED. The next phase may be planned; implementation of Phase 4 requires a separate explicit
owner approval of the Phase 4 plan.
