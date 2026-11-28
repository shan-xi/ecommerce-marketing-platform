# API Test Scripts

macOS **zsh** scripts for repeatable local HTTP/API verification. Shared HTTP, CSRF, authentication, cookie-jar, and
assertion helpers are under `lib/`; feature-specific entry points are under `auth/`; combined workflows are under
`suites/`.

## Requirements

- macOS with zsh
- `curl`
- `python3`
- Project backend running locally at `http://localhost:8080`
- Local demo accounts initialized using `DEMO_SEED_PASSWORD`

The scripts use the project's existing API routes. They do not change backend/frontend source code or database schema.

## Configure

Optional:

```zsh
cp scripts/api-tests/config/local.example.zsh scripts/api-tests/config/local.zsh
```

Edit `local.zsh` if your local API URL or seed account emails differ. `local.zsh` is ignored by Git. You can also
override settings with environment variables:

```zsh
export API_BASE_URL="http://localhost:8080/api/v1"
export API_TEST_ADMIN_EMAIL="admin@example.test"
export API_TEST_MEMBER_EMAIL="member.one@example.test"
```

The password is not stored in the config. Each script that needs authentication prompts for it without echoing input.
The password is read from standard input by the JSON-body helper rather than passed as a command-line argument.
Temporary request bodies and cookie jars are created with restrictive permissions under
`/tmp/marketing-platform-api-test/`. The shared helper sets `umask 077` and enforces directory mode `700`.

## Run from the repository root

Complete Phase 3 authentication suite:

```zsh
zsh scripts/api-tests/suites/phase3-auth-verification.zsh
```

Individual tests:

```zsh
zsh scripts/api-tests/auth/csrf.zsh
zsh scripts/api-tests/auth/login.zsh
zsh scripts/api-tests/auth/me.zsh
zsh scripts/api-tests/auth/refresh.zsh
zsh scripts/api-tests/auth/logout.zsh
zsh scripts/api-tests/auth/role-boundary.zsh
zsh scripts/api-tests/auth/refresh-replay.zsh
```

Each individual script creates its own cookie jar and is independently executable. The complete suite uses separate
sessions for anonymous, admin, member, and refresh-replay scenarios.

## Test coverage

The Phase 3 suite checks:

1. CSRF cookie/header token match.
2. Login without CSRF header is rejected with HTTP 403.
3. Admin login and role in response.
4. Authenticated `/auth/me`.
5. Admin is forbidden from Member API (403).
6. Refresh token rotation and continued authenticated access.
7. Logout (204), followed by `/auth/me` (401).
8. Anonymous Admin and Member API access (401).
9. Member login and authenticated `/auth/me`.
10. Member is forbidden from Admin API (403).
11. Refresh-token replay is rejected (401).

## Safety

- The default target guard allows only `localhost` and `127.0.0.1`.
- Do not set `API_TEST_ALLOW_NONLOCAL=true` for production. This override exists only for deliberate non-production
  environments.
- Refresh replay intentionally resubmits an old refresh token. The server may revoke the associated token family; the
  replay scenario therefore uses a dedicated session.
- Cookie jars contain authentication cookies. Do not paste or upload files from the temporary test directory.
- The scripts print response status and PASS/FAIL only; they do not print cookie or JWT values.
- This suite is complementary to `./mvnw -f backend/pom.xml verify`; it does not replace backend integration tests.

## Shared helper API

| Helper           | Responsibility                                                                                          |
|------------------|---------------------------------------------------------------------------------------------------------|
| `lib/common.zsh` | Environment, local-target guard, curl/Python discovery, test output, cookie jar, request and assertions |
| `lib/csrf.zsh`   | Fetch CSRF token and verify it matches `XSRF-TOKEN` cookie                                              |
| `lib/auth.zsh`   | Login, Admin/Member login, `/me`, refresh and logout                                                    |
| `auth/`          | Independently runnable authentication tests                                                             |
| `suites/`        | Combined test workflows                                                                                 |

Future feature tests should add their own feature folder and reuse `lib/common.zsh` plus the relevant shared helpers.
Keep business-specific assertions in feature test scripts rather than duplicating curl, cookie, or PASS/FAIL logic.

## Phase 4 Audience API suite

Run the complete Phase 4 Audience Segment API verification from the repository root:

```zsh
zsh scripts/api-tests/suites/phase4-audience-verification.zsh
```

This suite reuses the shared CSRF/authentication helpers and verifies Admin authorization, Segment
create/list/get/update,
audience preview response shape, and rejection of unsupported rule fields/operators. See `audience/README.md` for exact
coverage and the note that each successful run leaves one uniquely named verification Segment (the current API has no
delete/archive endpoint).

## Phase 5 Campaign API suite

Run the campaign draft and persisted audience-preview verification:

```zsh
zsh scripts/api-tests/suites/phase5-campaign-verification.zsh
```

The suite checks anonymous/MEMBER authorization, CSRF, ADMIN draft create/list/detail/update, persisted campaign
audience preview, and invalid campaign time-window rejection. It creates uniquely named verification Segment and
Campaign records because no delete endpoint exists in this phase.

## Phase 6 Coupon Configuration and Claim API verification

Run the Phase 6 Admin coupon configuration checks against an existing disposable DRAFT campaign:

```zsh
export PHASE6_DRAFT_CAMPAIGN_ID="<draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase6-coupon-claim-verification.zsh
```

The suite verifies Admin/Member/anonymous boundaries, Admin coupon list/create/update, CSRF-protected writes, and that
`claimedQuantity` is returned as server-managed data. It also verifies the Member claim, same-key replay, duplicate
claim rejection, and member coupon list when an ACTIVE campaign/coupon fixture is supplied. The active campaign must
already have `published_at` set, the coupon must be currently valid and have inventory, and the logged-in test
member must exist in its `campaign_audience_members` snapshot and must not already have claimed that coupon.

Optional claim-fixture variables:

```zsh
export PHASE6_ACTIVE_CAMPAIGN_ID="<active-campaign-uuid>"
export PHASE6_ACTIVE_COUPON_ID="<claimable-coupon-uuid>"
# Optional: second claimable coupon in the same campaign for cross-target idempotency testing.
export PHASE6_ACTIVE_COUPON_ALT_ID="<another-coupon-uuid>"
```

The DRAFT test creates one coupon and leaves it on the supplied disposable campaign. The claim test creates one
successful claim for the configured test member and leaves that claim in the database. Concurrency, final-inventory
race, audit-row inspection, and failure rollback still require the PostgreSQL integration fixtures described in
`docs/development/PHASE_6_PLAN.md`; this HTTP suite does not replace those tests.

## Phase 7 Message, Publication, and Delivery API verification

Run against an existing disposable DRAFT campaign whose time window includes the current time
(`startsAt <= now < endsAt`), with a valid audience
and at least one configured coupon. The suite checks the time window before changing the campaign message and reports
the schedule if it is not active:

```zsh
export PHASE7_DRAFT_CAMPAIGN_ID="<disposable-draft-campaign-uuid>"
zsh scripts/api-tests/suites/phase7-message-publication-delivery-verification.zsh
```

**Destructive lifecycle note:** this suite saves/overwrites the campaign message, publishes the campaign, waits for
delivery to complete, and pauses the campaign. It cannot be rerun against the same campaign because publication is
not reversible. Use a fresh disposable DRAFT campaign for each run. It checks message validation and Admin role
boundaries, publication, asynchronous delivery completion, Member inbox authorization, Pause, and rejection of retry
for a non-FAILED job. It does not inject delivery failures or replace PostgreSQL worker-recovery/concurrency tests.

### Phase 8 member experience

Run `suites/phase8-member-experience-verification.zsh` against a local published campaign with completed delivery to the
configured demo member. Set `PHASE8_PUBLISHED_CAMPAIGN_ID` to that campaign UUID. The suite is non-destructive and
checks anonymous/member reads, response privacy, role/CSRF boundaries, authenticated page-view events, inbox
impression/click events, message ownership validation, and event idempotency. It does not claim coupons or change
campaign lifecycle state.
