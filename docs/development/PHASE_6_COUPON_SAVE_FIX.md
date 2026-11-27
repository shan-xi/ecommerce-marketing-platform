# Phase 6 Coupon Save Failure — Fix Notes

**Date:** 2026-11-27  
**Baseline:** `ecommerce-marketing-platform-v0.1.0-phase6-implemented-fix2.zip`

## Observed symptom

The Admin Coupon form displayed `The request could not be completed.` when saving a Coupon. This text is the safe
generic detail returned by `ApiExceptionHandler` for an unhandled server exception; it does not identify the underlying
exception to the browser.

## Root cause identified in the implementation

`CouponService` passed `java.time.Instant` values directly as JDBC named parameters for `starts_at`, `ends_at`, and
audit/update timestamps. The existing Campaign JDBC repository consistently converts `Instant` to `java.sql.Timestamp`
before binding. PostgreSQL JDBC does not support binding `Instant` as a native JDBC parameter in the same way, which can
raise a JDBC parameter-type error and reach the generic HTTP 500 handler.

## Changes

- Convert Coupon create/update timestamp parameters to `Timestamp.from(instant)`.
- Convert claim inventory-update and claim-audit timestamp parameters to `Timestamp.from(instant)` as well, so the same
  binding problem does not affect member claim requests.
- Log unexpected backend exceptions with a generated trace ID while keeping the public error detail generic and safe.
- Include HTTP status, stable error code, and trace ID in the Admin Coupon UI error display when the API returns a
  Problem Detail response.
- Rework `scripts/api-tests/suites/phase6-coupon-claim-verification.zsh` to validate the supplied DRAFT fixture, print
  response bodies for failed assertions, refresh CSRF tokens before writes, and verify Admin coupon create/list/update
  plus role boundaries. Optional ACTIVE campaign/coupon fixture variables enable claim, idempotent replay,
  duplicate-claim, and member-claim-list checks.

## Verification limits

The source changes and test script were inspected. Automated Maven compilation and frontend build could not be completed
in this environment: Maven wrapper distribution download was blocked by unavailable network access, and the extracted
frontend `node_modules` directory did not contain React or the required type packages. The API suite also requires a
local PostgreSQL-backed application, demo credentials, and the fixture IDs described in `scripts/api-tests/README.md`;
those services/fixtures were not available here.
