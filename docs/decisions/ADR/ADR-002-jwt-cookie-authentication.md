# ADR-002: JWT Access and Refresh Tokens in HttpOnly Cookies

- **Status:** Proposed — pending owner approval
- **Date:** 2026-11-25

## Context

The product has browser-based Admin and Member experiences. The owner selected Spring Security with JWT access and refresh tokens.

## Decision

Use short-lived JWT access tokens and rotating refresh tokens. Transport them through HttpOnly cookies, not browser localStorage/sessionStorage. Persist refresh-token hashes/session state for rotation and revocation. Enable CSRF protection for cookie-authenticated state-changing requests.

## Alternatives considered

- **Server-side session cookie:** valid and simpler for browser-only systems, but the selected project direction is JWT access/refresh.
- **JWT in localStorage:** rejected due to increased exposure to token theft through XSS.
- **JWT in HttpOnly cookies without CSRF:** rejected because browsers attach cookies automatically.

## Consequences

- Frontend must implement CSRF header behavior and refresh handling.
- Backend must implement token rotation, revocation, expiry, and cookie policy.
- Cookie SameSite/domain/path settings must match deployment topology.
