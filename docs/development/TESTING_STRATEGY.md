# Testing Strategy

**Status:** Proposed — implementation has not started.

## 1. Test pyramid

| Level | Purpose | Examples |
|---|---|---|
| Unit | Fast domain/application correctness | Campaign lifecycle, audience rule validation, coupon eligibility, conversion calculations |
| Repository/integration | Real PostgreSQL behavior and constraints | Unique claims, atomic inventory update, Flyway migrations, query filters |
| API integration | HTTP contract, auth, CSRF, serialization | Role enforcement, cookie refresh, error envelope, request validation |
| Frontend component | User interaction and state rendering | Campaign form, audience builder, inbox, coupon claim states |
| End-to-end | Complete user journey | Admin publishes → member sees message → opens page → claims coupon → admin sees metrics |
| Concurrency | Race conditions and invariants | Oversubscribed coupon inventory, duplicate claims, duplicate delivery/event submissions |

## 2. Required backend tests

### Authentication and authorization
- Admin can authenticate and access admin endpoints.
- Member cannot access admin endpoints.
- Member A cannot retrieve Member B's private data.
- Refresh token rotation, expiration, revocation, and logout work.
- CSRF protection rejects missing/invalid tokens on state-changing cookie requests.

### Audience
- Each supported field/operator combination is validated.
- Unsupported fields/operators and malformed values are rejected.
- AND semantics are correct.
- Saved segment and campaign-specific rule combination follows the approved rule.
- Snapshot contains the matching members at publication and remains unchanged after member attributes or segment rules change.

### Publication and delivery
- Campaign cannot publish without required setup.
- Publish is idempotent or rejects duplicate publish safely.
- Audience snapshot and delivery job are committed consistently.
- Delivery worker retries without duplicate inbox records.
- Delivery can resume after worker interruption.

### Coupon claim correctness
- Claim succeeds for eligible member and available coupon.
- Duplicate claim by same member is rejected without consuming extra inventory.
- Ineligible member, paused/ended campaign, expired coupon, and sold-out coupon are rejected.
- Replaying the same idempotency key returns the original successful result.
- Failure after inventory reservation rolls back the inventory update.

**Concurrency acceptance test:** seed a coupon with quantity 100 and send at least 1,000 concurrent claim attempts from distinct eligible members. Exactly 100 or fewer claims may succeed (exactly 100 if all other conditions are valid); `claimed_quantity` must never exceed 100 or become negative; no member may own duplicate claims; counter must equal successful claim count.

### Engagement and analytics
- Duplicate event ID is not counted twice.
- Delivered count is derived from inbox records.
- Impression/click/page-view totals and distinct-member counts are calculated separately.
- Successful claims are sourced from `coupon_claims`.
- Zero denominators produce unavailable/null rates.

## 3. Required frontend tests

- Admin and Member routes render the correct screens and deny wrong-role navigation at the UX layer.
- Login, refresh, logout, and CSRF behavior match the API contract.
- Audience builder prevents unsupported rule combinations.
- Campaign forms display server validation errors.
- Landing page displays claimed/sold-out/available states accurately.
- Duplicate click or network retry does not create duplicate claim UI state.
- Empty, loading, error, and success states are covered.

## 4. End-to-end scenarios

Implement the 12 acceptance scenarios in `PRD.md` as automated or documented repeatable E2E flows. At minimum, automate campaign creation/publication, member delivery, coupon claim, duplicate claim, concurrency, and analytics verification.

## 5. Test data and environment

- Use fictional deterministic seed data.
- Use PostgreSQL in integration tests (prefer Testcontainers) rather than relying exclusively on an in-memory database for transaction/concurrency behavior.
- Tests must clean up their data and be repeatable.
- Use a fixed/test clock for date-window scenarios.
- Do not use production credentials or real member records.

## 6. Definition of Done for each phase

- Acceptance criteria are met.
- Relevant unit/integration/API/UI tests pass.
- No known regression in previously completed phases.
- API/DB/documentation changes are synchronized.
- Build and test commands are documented and reproducible.
