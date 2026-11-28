# Phase 10 Acceptance Guide — Hardening and Delivery Readiness

**Status:** OWNER-ACCEPTED (2026-11-28) — VERIFICATION FOLLOW-UP OPEN  
**Baseline:** v1.5.0; Phases 0–10 owner-accepted  
**Purpose:** Repeatable owner-run verification of the delivered Phase 10 changes.

## 1. Prerequisites

- JDK 21.
- Docker Engine and Docker Compose plugin running.
- Node.js 22.12+ and npm 10+.
- zsh, curl, and Python 3.
- Network access for Maven/npm dependencies on the first run.
- A disposable local database and the fictional demo accounts from V2 seed data.

From the repository root:

```bash
cp .env.example .env
docker compose up -d postgres
docker compose ps
```

In terminal 1:

```bash
set -a; . ./.env; set +a
./scripts/dev/run-backend.zsh
```

Wait for all health checks:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/readyz
curl --fail http://localhost:8080/livez
```

## 2. Coupon concurrency integration test

Run the dedicated PostgreSQL-backed test:

```bash
./mvnw -f backend/pom.xml clean -Dtest=CouponConcurrencyIntegrationTest test
```

Expected behavior:

- Three repetitions of each of the three concurrency scenarios complete successfully.
- 24 distinct members compete for 8 units: exactly 8 successful claim records.
- `campaign_coupons.claimed_quantity` equals the count of `coupon_claims` rows.
- Remaining inventory never becomes negative and successful claims never exceed total quantity.
- Same member + same idempotency key: every request resolves to one claim ID; only one claim row/inventory reservation
  exists.
- Same member + different idempotency keys: exactly one request succeeds; all other requests receive
  `CLAIM_ALREADY_EXISTS`.
- The test removes its disposable campaign, coupon, member, and user fixtures.

Run the complete backend suite:

```bash
zsh scripts/dev/verify-backend.zsh
```

This includes existing identity, CSRF, role, refresh-rotation, coupon, audience, campaign, publication/delivery,
engagement, and analytics tests.

## 2.1 Maven test-compilation troubleshooting

If Maven reports that test packages such as `com.example.marketingplatform.audience.application` or
`com.example.marketingplatform.identity.domain` do not exist, do not edit the test imports or delete tests.
Those packages are provided by `../../../backend/src/main/java`. First remove stale Maven output and rebuild the main
classes before compiling tests:

```bash
./mvnw -f backend/pom.xml clean test-compile
```

For the complete backend verification, use:

```bash
zsh scripts/dev/verify-backend.zsh
```

The helper always invokes `clean verify`, so stale `backend/target/classes` from an earlier checkout cannot be
mistaken for the current source tree. If the clean build still fails, retain the first `[ERROR]` lines from the
`compile` phase (not only `testCompile`) and share the complete log; that would indicate a separate source or
dependency compilation failure.

## 3. Frontend regression

```bash
cd frontend
npm ci
npm test
npm run typecheck
npm run build
cd ..
```

All commands must exit with status 0.

## 4. Existing API regression suites

Start the backend and frontend as described in the root README. Set the shared API test credentials; the scripts prompt
for the password if `API_TEST_PASSWORD` is unset.

```zsh
export API_BASE_URL="http://localhost:8080/api/v1"
export API_TEST_ADMIN_EMAIL="admin@example.test"
export API_TEST_MEMBER_EMAIL="member.one@example.test"
# Optional: export API_TEST_PASSWORD="your-local-demo-password"
```

Run Phase 4 and Phase 5. These suites create uniquely named verification records:

```zsh
zsh scripts/api-tests/suites/phase4-audience-verification.zsh
zsh scripts/api-tests/suites/phase5-campaign-verification.zsh
```

Phase 6 requires an existing disposable DRAFT campaign. It creates/updates coupon configuration on that campaign:

```zsh
export PHASE6_DRAFT_CAMPAIGN_ID="<disposable-draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase6-coupon-claim-verification.zsh
```

For Phase 6 claim-path coverage, additionally prepare an ACTIVE campaign whose immutable audience snapshot includes
`member.one@example.test`, with one claimable coupon:

```zsh
export PHASE6_ACTIVE_CAMPAIGN_ID="<active-campaign-uuid>"
export PHASE6_ACTIVE_COUPON_ID="<coupon-uuid>"
# Optional second coupon for cross-target idempotency verification:
export PHASE6_ACTIVE_COUPON_ALT_ID="<second-coupon-uuid>"
```

Phase 7 requires a separate disposable DRAFT campaign whose `startsAt <= now < endsAt`. The suite configures the
message, publishes, checks delivery/inbox, pauses, and verifies retry guards. It is destructive to that campaign's
lifecycle:

```zsh
export PHASE7_DRAFT_CAMPAIGN_ID="<disposable-draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh
```

Phase 8 requires a published campaign that has a delivered inbox message for `member.one@example.test`:

```zsh
export PHASE8_PUBLISHED_CAMPAIGN_ID="<published-campaign-uuid>"
zsh scripts/api-tests/suites/phase8-member-experience-verification.zsh
```

Phase 9 is read-only and requires an existing campaign:

```zsh
export PHASE9_CAMPAIGN_ID="<campaign-uuid>"
zsh scripts/api-tests/suites/phase9-analytics-verification.zsh
```

Run the Phase 3 auth suite as an additional security regression if desired:

```zsh
zsh scripts/api-tests/suites/phase3-auth-verification.zsh
```

Use different disposable campaigns for Phase 6 and Phase 7. Do not point a destructive suite at a campaign that must
remain editable or active. The scripts write response artifacts under `/tmp/marketing-platform-api-test/`.

## 5. Manual browser E2E walkthrough

Run the frontend:

```bash
cd frontend
npm run dev
```

Open `http://localhost:5173`.

1. Sign in as the seeded ADMIN.
2. Create or select a disposable audience segment and DRAFT campaign.
3. Configure the campaign's coupon and in-app message.
4. Publish the campaign; wait until delivery status is `COMPLETED`.
5. Sign out and sign in as `member.one@example.test`, who must be in the campaign audience snapshot.
6. Open Member Inbox and confirm the delivered campaign message appears.
7. Open the message's campaign landing page.
8. Verify eligible state and claim a coupon.
9. Replay the same claim only through the API test path with the same idempotency key; verify it does not create a
   second claim.
10. Generate/record the existing impression, click, and landing-page view events through the UI.
11. Sign out and sign in as ADMIN.
12. Open campaign analytics; verify target/delivery, engagement, landing-page, claim-attempt, and coupon inventory
    metrics agree with the underlying journey.

Also inspect loading, empty, invalid-input, unauthorized, forbidden, not-found, and retryable-error states where the
relevant UI flow provides them.

## 6. Security regression checklist

Backend Maven tests and API suites must verify:

- Anonymous requests cannot access protected ADMIN/MEMBER endpoints.
- MEMBER cannot access ADMIN campaign management, publication, delivery administration, or analytics.
- ADMIN cannot use MEMBER-only claim/inbox/profile operations.
- State-changing cookie-authenticated requests without a valid CSRF header are rejected.
- Untrusted CORS origins are rejected; configured frontend origin is allowed.
- Invalid JWT signatures are rejected; refresh rotation/reuse behavior remains correct.
- Public landing-page content does not disclose member-specific state.
- One member cannot claim or read another member's records; campaign-scoped IDs cannot cross campaign boundaries.
- Responses do not expose raw refresh tokens, password hashes, member IDs, idempotency keys, or unapproved event
  metadata.

## 7. Operational readiness

```bash
docker compose config
docker compose up -d postgres
docker compose ps
```

Confirm `.env` is local-only and never committed. Follow the README from a fresh checkout, including Flyway startup and
health checks.

**Deployment constraint:** run exactly one backend application instance/replica. Phase 7 startup recovery can reset
`PROCESSING` delivery jobs; multiple live instances are unsupported. Do not claim multi-instance support or production
capacity from these local tests.

## 8. Acceptance record

**Formal owner decision:** The owner accepted the delivered Phase 10 modifications on 2026-11-28 after the final Code
Review. This is an acceptance of the delivered scope with the verification limitations below explicitly retained; it
must not be interpreted as a claim that every environment-dependent test was executed or passed.

### Delivered-scope acceptance

- [x] No unapproved scope or infrastructure was introduced (confirmed by static review of the delivered package).
- [x] Owner formally accepts the delivered Phase 10 modifications (2026-11-28).

### Verification follow-up — still unverified

- [ ] Dedicated Coupon concurrency suite passes (all repeated scenarios).
- [ ] Backend `clean verify` passes.
- [ ] Frontend tests, typecheck, and build pass.
- [ ] Phase 4–9 API regression suites pass with disposable fixtures.
- [ ] ADMIN → MEMBER → claim → analytics browser journey passes.
- [ ] Security boundaries pass.
- [ ] Clean setup and operational checks pass.

**Owner acceptance date:** 2026-11-28.  
**Acceptance qualification:** Environment-dependent verification remains open. Do not infer or pre-fill test
counts/logs;
record actual results from the owner's environment when these checks are run.
