# Phase 8 Acceptance Guide

**Status:** ACCEPTED — owner approval recorded on 2026-11-28. The owner reported Maven `clean verify`, Spring Boot
startup, frontend dev-server startup, the Phase 8 API verification suite (`all`), and successful Member UI coupon claim.
See Section 6 for the exact acceptance record and verification boundary. The steps below are retained as a repeatable
acceptance reference, not as an outstanding approval request.

Use this guide to verify the Phase 8 package locally. Run the existing project from a clean checkout and retain the
command output for owner review.

## 1. Prerequisites

- Java 21, Node.js 22.12+, npm, Docker Compose, and zsh.
- Local PostgreSQL from `compose.yaml` and the normal local environment variables from `.env.example`.
- The demo seed password configured as `DEMO_SEED_PASSWORD`.
- A published campaign with a completed Phase 7 delivery to `member.one@example.test`, plus at least one coupon. For the
  eligibility walkthrough, choose a campaign whose immutable snapshot includes Member One but excludes Member Two. Keep
  the campaign ID for the API suite.
- Run exactly one backend instance, as required by the Phase 7 operations runbook.

## 2. Automated verification

From the repository root:

```bash
./mvnw -f backend/pom.xml clean verify
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```

Start the local database/backend/frontend using the existing project instructions. After the backend is ready, run the
existing Phase 1–7 API suites, then Phase 8:

```bash
cd scripts/api-tests
export PHASE8_PUBLISHED_CAMPAIGN_ID='<published-campaign-uuid>'
./suites/phase8-member-experience-verification.zsh
```

The Phase 8 suite is non-destructive: it reads a published campaign, reads the configured member inbox, and writes
idempotent engagement events. It does not claim a coupon, publish, pause, or otherwise mutate campaign lifecycle state.
The selected campaign must already have a delivered inbox row for the configured member.

The backend integration test `CampaignExperienceIntegrationTest` covers anonymous payload minimization, snapshot-based
eligibility, duplicate event IDs, delivered-message ownership, ADMIN rejection, and paused/ended public behavior. It
runs as part of Maven `verify` and requires Docker/Testcontainers.

## 3. Browser acceptance walkthrough

1. Open `/campaigns/<published-campaign-uuid>` in a private/incognito browser window. Confirm campaign name,
   description, fixed headline/body, campaign period, and coupon summaries appear without signing in.
2. Inspect the public GET responses in browser developer tools. Anonymous campaign and coupon responses must omit
   `memberState`; no member ID, audience list, claim row, or delivery-job detail may appear.
3. Select Claim while anonymous. Confirm navigation to `/login?returnTo=...`; sign in as a MEMBER. Confirm return to the
   same campaign route and verify that no claim was submitted automatically.
4. As an eligible member, confirm coupon status is available and click Claim once. Confirm a clear success message and
   then `已領取` after the page refreshes its state.
5. Reload or retry the same logical request after a lost response using the same idempotency key. Confirm the original
   claim is returned and inventory is not consumed twice. A separate new key after a successful claim must not create a
   second claim.
6. Sign in as `member.two@example.test` (not in the test campaign snapshot unless the campaign explicitly includes
   them). Confirm explicit not-eligible state even when stock remains.
7. Open `/member/messages`. Scroll a delivered message card at least halfway into view and confirm one IMPRESSION event.
   Click `查看活動`; confirm a CLICK event is submitted and the campaign landing page opens.
8. Sign in as ADMIN and open the public campaign URL. Confirm public content remains visible but no MEMBER
   eligibility/claim state is exposed and ADMIN cannot submit engagement events.
9. For a published PAUSED campaign or campaign whose `endsAt` has passed, confirm the public page remains readable, all
   new claims are unavailable, and no claim request succeeds.
10. Open a DRAFT campaign URL. Confirm the public endpoint returns the documented not-found behavior.

## 4. Database checks (optional, useful for event deduplication)

Run against the local development database, replacing the UUID values:

```sql
SELECT event_type, count(*)
FROM campaign_events
WHERE campaign_id = '<campaign-uuid>'
GROUP BY event_type
ORDER BY event_type;

SELECT member_id, event_id, count(*)
FROM campaign_events
WHERE campaign_id = '<campaign-uuid>'
GROUP BY member_id, event_id
HAVING count(*) > 1;
```

The second query must return no rows. To inspect one inbox event's association:

```sql
SELECT ce.event_type, ce.campaign_message_id, mm.id AS member_inbox_row_id,
       ce.member_id, ce.event_id, ce.occurred_at, ce.received_at
FROM campaign_events ce
LEFT JOIN member_messages mm
  ON mm.campaign_id = ce.campaign_id
 AND mm.campaign_message_id = ce.campaign_message_id
 AND mm.member_id = ce.member_id
WHERE ce.campaign_id = '<campaign-uuid>'
  AND ce.event_type IN ('IMPRESSION', 'CLICK');
```

## 5. Acceptance criteria (historical acceptance gate)

The following criteria defined the Phase 8 acceptance gate:

- Maven clean verify and PostgreSQL integration tests pass.
- Frontend npm test, typecheck, and production build pass.
- Phase 1–7 regression suites pass.
- Phase 8 API suite passes with no failures.
- Browser walkthrough confirms anonymous/member/admin boundaries, safe login return, claim idempotency, coupon state,
  and message/page event behavior.
- The owner reviews the implementation report and explicitly accepts Phase 8.


## 6. Formal owner acceptance record (2026-11-28)

The owner explicitly approved Phase 8 and reported the following results:

| Verification | Owner-reported result |
|---|---|
| `./mvnw -f backend/pom.xml clean verify` | PASS |
| `./mvnw -f backend/pom.xml spring-boot:run` | PASS |
| `npm run dev` | PASS |
| `zsh scripts/api-tests/suites/phase8-member-experience-verification.zsh all` | PASS — all checks |
| Member UI flow | PASS — coupon successfully claimed |

**Decision:** Phase 8 is formally accepted and closed on 2026-11-28. Phase 9 planning is unblocked.

These are owner-reported results; they were not re-executed by the assistant. The owner did not separately enumerate
`npm test`, `npm run typecheck`, `npm run build`, or Phase 1–7 regression suite results in the acceptance message, so
this record does not assert separate PASS results for those commands.
