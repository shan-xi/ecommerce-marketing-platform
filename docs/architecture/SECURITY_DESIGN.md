# Security Design

**Status:** Proposed — requires owner review

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
- `Secure` cookie flag in HTTPS environments.
- Explicit `SameSite` policy selected for the deployment topology. Prefer `Lax` when compatible with the routing flow; use `None; Secure` only when cross-site cookie behavior is required and documented.
- Narrow cookie `Path` values where practical; refresh cookie should be sent only to the refresh/logout endpoints if implementation supports it.

The API specification assumes cookie transport. It does not require the browser to manually attach an `Authorization: Bearer` header.

## 3. Token lifecycle

1. Login validates credentials and issues an access token and refresh token.
2. Backend stores only a cryptographic hash of the refresh token (or a hash of its unique token identifier) with user ID, expiry, creation time, rotation/revocation metadata.
3. Refresh endpoint validates the refresh token, revokes/consumes the presented token, issues a new access token and refresh token, and stores the new refresh-token hash.
4. Reuse of a rotated/revoked refresh token is rejected; consider revoking the full token family/session on detected reuse.
5. Logout revokes the current refresh session and clears cookies.
6. Access token expiry is short; refresh token expiry is longer and configurable. Exact durations are configuration decisions and must be documented in deployment config, not hard-coded.

JWT claims should be minimal: subject/user ID, role(s), issued-at, expiry, token ID, and issuer/audience as appropriate. Do not place member profile, coupon, or sensitive audience attributes in tokens.

## 4. CSRF protection

Because cookies are automatically attached by browsers, CSRF protection is mandatory for state-changing requests.

- Enable Spring Security CSRF protection for cookie-authenticated browser routes.
- Use a documented token pattern, such as a CSRF cookie readable by the frontend and a matching custom request header (for example `X-CSRF-TOKEN`). The CSRF cookie itself must not be treated as an authentication credential.
- Require CSRF token for POST, PUT, PATCH, and DELETE, including login/refresh/logout as appropriate to the chosen Spring Security configuration.
- Validate `Origin`/`Referer` as defense in depth where feasible.
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
| View public campaign landing-page content | Configurable/public read | Yes | Yes, if campaign is public |

Public campaign content must not expose member-specific claim status or audience information. Coupon claiming always requires an authenticated member.

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
- Rotated/revoked/expired refresh token cannot be reused.
- Logout invalidates refresh session and clears cookies.
- Sensitive values do not appear in logs.
