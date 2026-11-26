# Security Design

**Status:** Approved — part of the owner-approved v1.5.0 Development Baseline

## 1. Security objectives

- Separate administrator and member privileges.
- Prevent cross-member access to inboxes, claims, and profile data.
- Protect browser authentication against token theft and CSRF.
- Keep coupon eligibility and inventory decisions server-side.
- Avoid leaking credentials, tokens, or sensitive member data in logs.

## 2. Authentication model

Use Spring Security with:

- Short-lived JWT access token.
- Refresh token with rotation and server-side revocation state.
- Access and refresh tokens transported using `HttpOnly` cookies; never store tokens in `localStorage` or `sessionStorage`.
- `HttpOnly=true` for both access and refresh cookies; JavaScript must never read either token.
- `SameSite=Lax` is the MVP default. Use `SameSite=None; Secure` only if a genuinely cross-site deployment requires it and the CORS/CSRF configuration is explicitly reviewed.
- `Secure=true` in HTTPS environments; only the local development profile may disable it for plain HTTP localhost.
- Host-only cookies by default (omit `Domain`). Access cookie path is `/api/v1`; refresh cookie path is `/api/v1/auth`, covering both `/refresh` and `/logout`.
- Logout must remain possible when the access token is expired; the refresh cookie is optional for cookie clearing and used for family revocation when present.

The API specification assumes cookie transport. It does not require the browser to manually attach an `Authorization: Bearer` header.

## 3. Token lifecycle

1. Login validates credentials and issues an access token and refresh token.
2. Backend stores only a SHA-256 hash of the complete serialized refresh JWT with user ID, token family ID, expiry, creation time, and rotation/revocation metadata; never store the raw refresh JWT.
3. Refresh endpoint validates the refresh token, revokes/consumes the presented token, issues a new access token and refresh token, and stores the new refresh-token hash.
4. Every refresh-token row represents one token generation. A stable `token_family_id` groups all generations created by one login session. Refresh atomically consumes the presented row (`consumed_at IS NULL`, `revoked_at IS NULL`, and not expired), inserts the next token row with the same family/user, and links it using `replaced_by_token_id` in one database transaction. A concurrent refresh can consume the row only once. The frontend API client must use a single-flight refresh operation so parallel expired-access requests share one refresh request rather than racing the same refresh cookie.
5. Reuse of a consumed or revoked token is rejected and revokes all still-active rows in that token family. An unknown token is rejected without disclosing whether a user/session exists.
6. Logout revokes all active tokens in the presented token family and clears access/refresh cookies. It relies on the refresh cookie, not a valid access token, so logout remains possible after access-token expiry.
7. MVP configuration defaults are access token TTL `15 minutes` and refresh token TTL `7 days`. Both are environment-configurable (`ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL`) and must be documented in deployment configuration; do not hard-code them in business logic.

JWT claims should be minimal: subject/user ID, role(s), issued-at, expiry, token ID, and issuer/audience as appropriate. Do not place member profile, coupon, or sensitive audience attributes in tokens.

## 4. CSRF protection

Because cookies are automatically attached by browsers, CSRF protection is mandatory for state-changing requests.

- Enable Spring Security CSRF protection for cookie-authenticated browser routes.
- Use a documented token pattern, such as a CSRF cookie readable by the frontend and a matching custom request header (for example `X-CSRF-TOKEN`). The CSRF cookie itself must not be treated as an authentication credential.
- Require the CSRF token for all state-changing browser endpoints, including login, refresh, logout, and all POST/PUT/PATCH/DELETE operations. The API contract requires `X-CSRF-TOKEN` for these requests.
- Validate `Origin`/`Referer` as defense in depth where feasible.
- Allow only the configured frontend origin(s) with credentials; never combine credentialed CORS with wildcard `Access-Control-Allow-Origin`.
- Configure CORS narrowly; do not use wildcard origins with credentialed requests.

## 5. Authorization matrix

| Capability | `ADMIN` | `MEMBER` | Anonymous |
|---|---:|---:|---:|
| Manage campaigns, segments, coupons, messages | Yes | No | No |
| Publish/pause campaign | Yes | No | No |
| View campaign analytics | Yes | No | No |
| View own member profile | No | Yes | No |
| View own inbox | No | Yes | No |
| Claim coupon | No | Yes | No |
| View own claims | No | Yes | No |
| View public campaign landing-page content and non-personal coupon summaries | Yes | Yes | Yes |

Public campaign content must not expose member-specific claim status or audience information. Public coupon summaries may show general availability but not a visitor's claim state. Coupon claiming always requires an authenticated member; an anonymous visitor is redirected to sign in and then returned to the intended campaign page.

## 6. Identity and tenant isolation

- Derive `userId` and role from the authenticated principal.
- Resolve `memberId` from the authenticated user on the server.
- Never trust a client-supplied member ID for member operations.
- Every member-owned query must include the authenticated member ID in its predicate.
- Admin-only endpoints must enforce role checks at both route and application-use-case boundaries for sensitive operations.

## 7. Passwords and secrets

- Hash passwords with Argon2id or BCrypt using an appropriate work factor; never encrypt or store plaintext passwords.
- Use environment variables or a secret manager for signing keys and database credentials.
- Do not commit real `.env` files, private keys, or credentials.
- Use separate keys/secrets per environment and document rotation.
- Do not log passwords, access tokens, refresh tokens, cookie values, or full authentication headers.

## 8. Input and abuse controls

- Validate request DTOs server-side, including bounds, enum values, string lengths, date ranges, and supported audience fields/operators.
- Build audience predicates from an allowlist of member attributes and operators; never concatenate user-provided SQL identifiers or expressions.
- Rate-limit login and refresh attempts at the application/reverse-proxy layer if available; do not add Redis solely for this MVP.
- Apply request size limits and pagination limits.
- Return generic authentication failure messages that do not reveal whether a username exists.

## 9. Security test requirements

- Admin endpoints reject member and anonymous principals.
- Member endpoints reject anonymous principals.
- Member A cannot retrieve Member B's inbox, claims, or profile by changing IDs.
- Forged client-supplied member IDs are ignored/rejected.
- Missing/invalid CSRF token rejects state-changing cookie-authenticated requests.
- Expired access token is rejected; valid refresh token renews session.
- Exactly one of two concurrent refresh attempts can consume a token generation.
- Reuse of a consumed/revoked token revokes the entire token family; subsequent family tokens fail.
- Logout revokes the presented token family and clears cookies even when the access token is expired.
- Sensitive values do not appear in logs.
