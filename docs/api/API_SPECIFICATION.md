# API Specification

**Status:** Approved — part of the owner-approved v1.5.0 Development Baseline  
**Base path:** `/api/v1`  
**Transport:** HTTPS + JSON  
**Authentication:** JWT access/refresh tokens in HttpOnly cookies; CSRF token required for state-changing browser
requests.

## 1. Conventions

- JSON uses `camelCase`; database columns use `snake_case`.
- IDs are UUID strings.
- Timestamps are ISO-8601 UTC instants (for example `2026-11-25T10:30:00Z`).
- List endpoints are paginated with `page` (0-based) and `size` (default 20, maximum 100). Default stable sorting:
  campaigns and segments by `createdAt DESC`; member inbox by `deliveredAt DESC`; member claims by `claimedAt DESC`;
  campaign coupons by `createdAt ASC`.
- All write requests require a valid CSRF header when cookie authentication is used.
- Do not accept `memberId` from a member client for self-service operations. Resolve it from the authenticated
  principal.
- Admin endpoints require `ADMIN`; member endpoints require `MEMBER`.
- Persisted campaign states are `DRAFT`, `ACTIVE`, and `PAUSED`. `ENDED` is a derived read/API status when `publishedAt`
  is set and `endsAt <= now`, whether the persisted state was `ACTIVE` or `PAUSED`; it is not persisted and no
  background transition job is required. A draft remains `DRAFT` after its end time.
- Campaign landing-page content and non-personal coupon summaries may be read anonymously. Member-specific claim state
  is returned only to an authenticated member; the claim endpoint always requires `MEMBER`.

## 2. Cookie and CSRF contract

Suggested cookie names:

- `access_token`: HttpOnly, Secure in HTTPS, SameSite policy configured for deployment, short expiry.
- `refresh_token`: HttpOnly, Secure in HTTPS, longer expiry, rotated on refresh; its Path must include both
  `/auth/refresh` and `/auth/logout`.
- `XSRF-TOKEN`: readable by frontend JavaScript, Path `/`, Secure in HTTPS, SameSite=Lax; contains CSRF token only, not
  an authentication token.\n\nThe implementation uses host-only cookies and SameSite=Lax. `access_token` uses Path
  `/api/v1`; `refresh_token` uses Path `/api/v1/auth`. The local Spring `local` profile may disable Secure only for
  plain HTTP localhost. The CSRF header is `X-CSRF-TOKEN`.

For state-changing requests, frontend sends `X-CSRF-TOKEN` with the value obtained from `XSRF-TOKEN`. The backend
validates it according to the configured Spring Security CSRF repository. Cookie names and exact paths are
configuration, but frontend and backend must use one documented contract.

## 3. Standard response envelope

Successful single-resource response:

```json
{
  "data": {
    "id": "a6b66f0b-3b7a-4a0c-a1a7-7ad85d7b4d44"
  }
}
```

Successful list response:

```json
{
  "data": [],
  "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
}
```

Error response (RFC 9457 Problem Details style with stable application code):

```json
{
  "type": "about:blank",
  "title": "Coupon is sold out",
  "status": 409,
  "code": "COUPON_SOLD_OUT",
  "detail": "No claimable inventory remains.",
  "traceId": "c3e3b1e6f02b4c51"
}
```

Never expose stack traces, SQL, secrets, or internal class names in API errors.

## 4. Authentication APIs

| Method | Path            | Auth                           | Purpose                                                                                    |
|--------|-----------------|--------------------------------|--------------------------------------------------------------------------------------------|
| POST   | `/auth/login`   | Anonymous + CSRF               | Authenticate; set access and refresh cookies                                               |
| POST   | `/auth/refresh` | Refresh cookie + CSRF          | Rotate refresh token and issue new access cookie                                           |
| POST   | `/auth/logout`  | Optional refresh cookie + CSRF | If refresh cookie identifies a family, revoke it; always clear auth cookies and return 204 |
| GET    | `/auth/me`      | Authenticated                  | Current user ID and role                                                                   |
| GET    | `/auth/csrf`    | Anonymous                      | Initialize/return CSRF cookie/token as configured                                          |

### Login request

```json
{ "email": "admin@example.test", "password": "example-only" }
```

Response: `200 OK`, user summary in JSON, access and refresh tokens set as HttpOnly cookies. Passwords in examples are
placeholders only.

### Current user response

```json
{
  "data": {
    "userId": "uuid",
    "role": "ADMIN"
  }
}
```

## 5. Admin campaign APIs

| Method | Path                                    | Purpose                                       |
|--------|-----------------------------------------|-----------------------------------------------|
| POST   | `/admin/campaigns`                      | Create draft campaign                         |
| GET    | `/admin/campaigns`                      | Search/list campaigns                         |
| GET    | `/admin/campaigns/{campaignId}`         | Campaign detail                               |
| PUT    | `/admin/campaigns/{campaignId}`         | Update draft campaign                         |
| POST   | `/admin/campaigns/{campaignId}/publish` | Publish, snapshot audience, initiate delivery |
| POST   | `/admin/campaigns/{campaignId}/pause`   | Pause active campaign                         |

### Create/update campaign request

```json
{
  "name": "November Member Celebration",
  "description": "A member-only coupon campaign.",
  "startsAt": "2026-12-01T00:00:00Z",
  "endsAt": "2026-12-31T15:59:59Z",
  "landingPage": {
    "headline": "A little thank-you for our members",
    "body": "Choose an available coupon below."
  },
  "audience": {
    "segmentId": "optional-uuid",
    "rules": {
      "version": 1,
      "match": "ALL",
      "conditions": [
        { "field": "membershipTier", "operator": "IN", "value": ["GOLD", "PLATINUM"] }
      ]
    }
  }
}
```

Campaign detail responses include the persisted editable fields (`description`, fixed `landingPage.headline`/`body`,
`audience.segmentId`/`rules`), effective `status`, and available lifecycle/creation/update timestamps. List responses
return a compact summary with identifiers, name, description, effective status, schedule, and timestamps, plus the
standard `page` metadata. List ordering is `createdAt DESC`; Phase 5 supports `page`, `size`, and `status` only (no
unapproved free-text query parameter).

The request must include an `audience` object with at least one of `segmentId` or `rules`. If both `segmentId` and
`rules` are provided, the effective audience is their intersection (AND): a member must match the saved segment and
every campaign-specific condition. If only one is provided, that definition is used; at least one is required. Each rule
document uses `match: ALL`, and any comparison/set condition on a NULL member attribute evaluates to non-match. Explicit
NULL-check operators are not supported.

### Pause behavior

Pausing an active campaign blocks claims after the pause transaction commits. Claim and pause serialize on the campaign
row: a claim first acquires `SELECT ... FOR SHARE` on the campaign row and holds it through commit; Pause updates that
row and waits for earlier claims to finish. Claims that begin after Pause commits observe `PAUSED` and are rejected.
Publication commits the delivery job in the same transaction as the audience snapshot; Pause does not cancel that
committed job, even if it is still `PENDING` and the worker has not picked it up. Existing inbox messages are not
retracted.

### Publish response

`202 Accepted` is recommended because message delivery continues asynchronously after publication. Publication is
rejected with `409 CAMPAIGN_NOT_PUBLISHABLE` unless the campaign is currently within its start-inclusive/end-exclusive
window (`startsAt <= now < endsAt`); scheduled publication is not supported. Response includes campaign status,
`publishedAt`, audience count, and delivery job status. If audience count is zero, include
`warningCode: NO_AUDIENCE_MEMBERS` for the admin UI. A successful publish means the complete audience snapshot and
delivery job are committed in the publication transaction; it does not promise every inbox row already exists. A
zero-member snapshot is valid: publication succeeds with a warning and the job is immediately `COMPLETED`. The MVP uses
one delivery worker and a persisted batch cursor, without worker leases.

### Member profile API

`GET /member/profile` requires an authenticated `MEMBER` principal. The backend resolves the member by the principal
user ID; a member ID supplied as a query parameter or other client input is ignored and cannot select another profile.
The response contains the current member profile summary and the derived age using the `Asia/Taipei` calendar date.
`ADMIN` is not authorized to call this member-only endpoint.

Response data fields: `memberId`, `displayName`, `birthDate`, `age`, `gender`, `registeredAt`, `membershipTier`,
`orderCount`, `totalSpending`, `lastOrderAt`, and `lastLoginAt`. Nullable member attributes are returned as JSON `null`.

## 6. Audience segment APIs

| Method | Path                                             | Purpose                             |
|--------|--------------------------------------------------|-------------------------------------|
| POST   | `/admin/audience-segments`                       | Create reusable segment             |
| GET    | `/admin/audience-segments`                       | List segments                       |
| GET    | `/admin/audience-segments/{segmentId}`           | Get segment                         |
| PUT    | `/admin/audience-segments/{segmentId}`           | Update segment                      |
| POST   | `/admin/audience-segments/preview`               | Preview rule match count/sample     |
| POST   | `/admin/campaigns/{campaignId}/audience-preview` | Preview campaign effective audience |

Rule JSON supports only the top-level `version`, `match`, and `conditions` properties; each condition contains only
`field`, `operator`, and `value`. Unknown properties are rejected. Rule JSON supports allowlisted fields and operators
only. Comparison operators require one non-null scalar value; `IN`
and `NOT IN` require a non-empty array of non-null scalar values. Values must match the selected field type. Preview
should return count and optionally a small sample of non-sensitive member display identifiers; do not expose full member
attributes unnecessarily.

| Field                        | Type              | Allowed operators                               | Value constraints                         |
|------------------------------|-------------------|-------------------------------------------------|-------------------------------------------|
| `age`                        | Integer           | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | 0–120                                     |
| `gender`                     | Enum              | `=`, `!=`, `IN`, `NOT IN`                       | `FEMALE`, `MALE`, `OTHER`, `UNDISCLOSED`  |
| `registeredAt`               | DateTime          | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 timestamp                        |
| `membershipTier`             | Enum              | `=`, `!=`, `IN`, `NOT IN`                       | `STANDARD`, `SILVER`, `GOLD`, `PLATINUM`  |
| `orderCount`                 | Integer           | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Integer >= 0                              |
| `totalSpending`              | Decimal           | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | TWD decimal >= 0, max 2 fractional digits |
| `lastOrderAt`, `lastLoginAt` | Nullable DateTime | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 timestamp                        |

Enum comparisons are case-sensitive; ordered comparisons are invalid for enum fields. `IN`/`NOT IN` require non-empty
arrays with no NULL elements. NULL member attributes never match any operator, including `!=`/`NOT IN`; `age` is NULL
when `birthDate` is NULL. Boolean values and explicit NULL checks are unsupported. The backend validator enforces
field/operator/value compatibility before constructing a query. Age conditions are evaluated using the current
`Asia/Taipei` date at preview time; publication evaluates them using the actual publication date. A preview is therefore
an estimate, while the committed publication snapshot is authoritative.

## 7. Coupon configuration APIs

| Method | Path                                               | Purpose                               |
|--------|----------------------------------------------------|---------------------------------------|
| POST   | `/admin/campaigns/{campaignId}/coupons`            | Add campaign coupon                   |
| GET    | `/admin/campaigns/{campaignId}/coupons`            | List campaign coupons                 |
| PUT    | `/admin/campaigns/{campaignId}/coupons/{couponId}` | Update coupon while campaign is draft |

Coupon request example:

```json
{
  "name": "NT$100 off",
  "description": "Discount offer details.",
  "discountType": "FIXED_AMOUNT",
  "discountValue": 100,
  "minimumOrderAmount": 1000,
  "totalQuantity": 2000,
  "startsAt": "2026-12-01T00:00:00Z",
  "endsAt": "2026-12-31T15:59:59Z"
}
```

`totalQuantity` must be at least 1. Redemption and order validation are out of scope; `minimumOrderAmount` is offer
metadata only in the MVP.

## 8. Campaign message APIs

| Method | Path                                            | Purpose                                                                     |
|--------|-------------------------------------------------|-----------------------------------------------------------------------------|
| PUT    | `/admin/campaigns/{campaignId}/message`         | Create/update the single message while DRAFT (ADMIN + CSRF)                 |
| GET    | `/admin/campaigns/{campaignId}/message`         | Get message configuration (ADMIN)                                           |
| GET    | `/admin/campaigns/{campaignId}/delivery-status` | Delivery progress and failures (ADMIN)                                      |
| POST   | `/admin/campaigns/{campaignId}/delivery/retry`  | Manually retry a `FAILED` job from its last committed cursor (ADMIN + CSRF) |

Delivery status response includes `status`, `totalCount` (audience snapshot count), `processedCount` (created inbox
rows), `cursorMemberId`, `attemptCount` (automatic retries consumed, 0–3), `lastError` (sanitized), and `retryable`
(true only when status is `FAILED`). Delivery uses batches of 100. Each batch insert and cursor update are atomic. The
initial attempt plus three automatic retries are allowed at a cursor, with 1s/2s/4s backoff; `attemptCount` records
automatic retries consumed (0–3) and resets after a successful batch. A fourth total failure moves the job to `FAILED`.
Manual retry is allowed only for `FAILED`; it resets the retry count, clears the last error, preserves the last
committed cursor, and returns the job to `PENDING`. An interrupted `PROCESSING` job returns to `PENDING` on worker
restart. An empty audience snapshot creates a `COMPLETED` job during publication.

A database unique constraint on `campaign_messages.campaign_id` enforces one message per campaign. Message `title` is
required and limited to 160 characters; `body` is required and limited to 10,000 characters. Both fields are editable
only
while the campaign is `DRAFT`. A campaign must have at least one configured coupon with `totalQuantity >= 1` before
publication, matching the PRD acceptance criteria. The message response contains `id`, `campaignId`, `title`, `body`,
`createdAt`, and `updatedAt`.

## 9. Member APIs

| Method | Path                           | Purpose                                  |
|--------|--------------------------------|------------------------------------------|
| GET    | `/member/profile`              | Current member profile summary           |
| GET    | `/member/messages`             | Current member inbox                     |
| GET    | `/member/messages/{messageId}` | Message detail for a delivered inbox row |
| GET    | `/member/coupons`              | Current member's successful claims       |

Member inbox list accepts `page` (zero-based, default 0) and `size` (1–100, default 20), ordered by
`deliveredAt DESC` with a stable ID tie-breaker. Both list and detail responses are scoped to the authenticated
`MEMBER` principal; `messageId` identifies the member's inbox row, not a campaign-wide message. Each row returns
`id`, `campaignId`, `campaignName`, `title`, `body`, and `deliveredAt`. An inbox detail outside the current member's
records returns the generic `RESOURCE_NOT_FOUND` response.

Member identity is derived from the access-token principal. No endpoint accepts a member ID to choose whose private data
is returned.

## 10. Campaign landing page and claim APIs

| Method | Path                                                | Auth     | Purpose                                                                                                                               |
|--------|-----------------------------------------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------|
| GET    | `/campaigns/{campaignId}`                           | Optional | Public published-campaign fixed-template content, including when paused/ended; member eligibility/claim state only when authenticated |
| GET    | `/campaigns/{campaignId}/coupons`                   | Optional | Public coupon summary and authenticated member claim/eligibility state; paused/ended coupons are unavailable for new claims           |
| POST   | `/campaigns/{campaignId}/coupons/{couponId}/claims` | MEMBER   | Claim a coupon; unauthenticated visitors must sign in and return to the campaign page                                                 |

Public campaign response returns only `id`, `name`, `description`, derived publication `status`, `startsAt`, `endsAt`,
`landingPage.headline`, `landingPage.body`, and `claimable`. An authenticated MEMBER additionally receives
`memberState.eligible`; anonymous and ADMIN responses omit `memberState`. Coupon reads return public coupon fields,
remaining inventory, a per-coupon `memberStatus` (`AVAILABLE`, `UNAVAILABLE`, `ALREADY_CLAIMED`, or `NOT_ELIGIBLE`), an
optional safe `unavailableReason`, and page metadata. Anonymous and ADMIN responses omit the top-level `memberState`.
Public reads never expose member IDs, audience snapshot rows, claim records, or delivery internals.

Claim request:

```json
{ "idempotencyKey": "f3e2e6a0-2c20-4a1f-a7f3-4d6ef1f96a22" }
```

Successful response: `201 Created` with `claimId`, campaign/coupon IDs, status `CLAIMED`, and `claimedAt`. A replay with
the same member and idempotency key for the same campaign/coupon returns the original successful claim response even if
the campaign has since been paused or ended; idempotency replay is checked before new-claim eligibility. Reusing that
key for a different campaign/coupon returns `409 IDEMPOTENCY_KEY_CONFLICT`. While the campaign is otherwise claimable, a
duplicate claim with a different key returns `409 CLAIM_ALREADY_EXISTS`; if the campaign is paused or ended, a new claim
attempt is rejected as `CAMPAIGN_INACTIVE`. Only successful claim results are persisted for idempotent replay; failed
attempts do not reserve inventory and are not replay-cached.

## 11. Engagement APIs

| Method | Path                             | Auth   | Purpose                                     |
|--------|----------------------------------|--------|---------------------------------------------|
| POST   | `/campaigns/{campaignId}/events` | MEMBER | Record impression, click, landing-page view |

Request example:

```json
{
  "eventId": "4a27a4d9-7a4d-40d9-a9b0-50f9c9a5a3d1",
  "eventType": "IMPRESSION",
  "messageId": "5a1b2c3d-4e5f-4678-9012-abcdef123456",
  "occurredAt": "2026-11-25T10:30:00Z"
}
```

For `IMPRESSION` and `CLICK`, `messageId` is the member inbox row ID returned by `GET /member/messages` (not the
campaign-wide message ID). The server verifies ownership and maps it to the persisted campaign message ID before
inserting the event. `LANDING_PAGE_VIEW` must omit `messageId`.

The backend derives `memberId` from authentication. This endpoint requires an authenticated `MEMBER`; allowed client
event types are `IMPRESSION`, `CLICK`, and `LANDING_PAGE_VIEW`. `IMPRESSION` and `CLICK` require `messageId` and are
accepted only if the authenticated member has the matching `member_messages` inbox row; `LANDING_PAGE_VIEW` omits it and
may be recorded for any authenticated visitor. Analytics counts only audience-snapshot members for the unique target
landing visitor metric. The client cannot submit `CLAIM_ATTEMPT` events; there is no separate `CLAIM_FAILED` event type
because failure outcome is stored in `CLAIM_ATTEMPT.metadata.outcome`. Anonymous landing-page reads do not submit events
and do not create rows in `campaign_events`, because `member_id` is required. Every authenticated claim command for an
existing campaign that passes basic request-shape validation creates a server-generated `CLAIM_ATTEMPT` audit record
with outcome `SUCCEEDED`, `FAILED`, or `REPLAYED`; failures include a safe business failure code. Successful claim audit
is committed with `coupon_claims`; failed-attempt audit is written in a separate short transaction after the claim
transaction rolls back. The client cannot submit claim outcome events. `DELIVERED` is derived from `member_messages`,
and successful claim is derived from `coupon_claims`; neither needs a duplicate client-submitted event.

## 12. Admin analytics APIs

| Method | Path                                      | Purpose                                              |
|--------|-------------------------------------------|------------------------------------------------------|
| GET    | `/admin/campaigns/{campaignId}/analytics` | Campaign metrics and embedded coupon-level reporting |

Metrics must distinguish total event counts from distinct-member counts. Definitions: Delivered Count = member_messages
rows; Impression/Click/Landing Page View counts = matching campaign_events rows; Unique Reach/Clickers = distinct member
IDs with the event; Unique Landing Visitors = distinct members in the campaign audience snapshot with an authenticated
landing-page view; Successful Claims/Unique Claimers = coupon_claims rows/distinct members in coupon_claims.
Claim-attempt counts are shown separately and never counted as successful claims. Rates are Delivery Rate = Delivered /
Target, Reach Rate = Unique Reach / Target, Click Rate = Unique Clickers / Delivered, Target Landing Visitor Rate =
Unique target-snapshot visitors / Target, and Target Claim Rate = Unique Claimers / Target. Do not calculate
Clicker-to-Landing-Visitor conversion because landing pages allow direct public entry and anonymous views are not
tracked. If a denominator is zero, the rate is `null`/unavailable, not `0%`.

The response includes `campaignId`, `campaignName`, all campaign totals and distinct-member counts,
`claimAttemptsByOutcome` (`succeeded`, `failed`, `replayed`), and a deterministic `coupons` array. Each coupon includes
`couponId`, `name`, `totalQuantity`, `successfulClaims`, `remainingQuantity`, `uniqueClaimers`, and `targetClaimRate`.
Every rate is an object with `numerator`, `denominator`, and nullable `value`. Coupon `claimed_quantity` must equal the
corresponding `coupon_claims` row count; otherwise the API fails closed with `ANALYTICS_INTEGRITY_ERROR` (500). The
endpoint is read-only, ADMIN-only, has no query parameters, and does not require CSRF.

## 13. HTTP status and error mapping

| HTTP | Meaning                                                   | Example codes                                                                                                                                                                                          |
|-----:|-----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  400 | Malformed/invalid request                                 | `VALIDATION_ERROR`, `INVALID_AUDIENCE_RULE`                                                                                                                                                            |
|  401 | Missing/invalid authentication                            | `UNAUTHENTICATED`, `TOKEN_EXPIRED`                                                                                                                                                                     |
|  403 | Authenticated but forbidden / CSRF failure                | `FORBIDDEN`, `CSRF_INVALID`                                                                                                                                                                            |
|  404 | Resource not found or not visible                         | `RESOURCE_NOT_FOUND`                                                                                                                                                                                   |
|  409 | State conflict or business invariant                      | `CAMPAIGN_NOT_PUBLISHABLE`, `CAMPAIGN_INACTIVE`, `MEMBER_NOT_ELIGIBLE`, `COUPON_OUTSIDE_VALIDITY`, `COUPON_SOLD_OUT`, `CLAIM_ALREADY_EXISTS`, `IDEMPOTENCY_KEY_CONFLICT`, `DELIVERY_JOB_NOT_RETRYABLE` |
|  422 | Semantically invalid business input (optional convention) | `INVALID_CAMPAIGN_PERIOD`                                                                                                                                                                              |
|  429 | Rate limit exceeded, if enabled                           | `RATE_LIMITED`                                                                                                                                                                                         |
|  500 | Unexpected server failure / analytics integrity mismatch  | `INTERNAL_ERROR`, `ANALYTICS_INTEGRITY_ERROR`                                                                                                                                                          |

Use one consistent mapping across controllers. Avoid returning 200 for failed business operations.

## 14. API contract governance

- `openapi.yaml` is the machine-readable contract baseline; keep it synchronized with this document.
- Generate or validate frontend API types from OpenAPI rather than hand-maintaining duplicate types where practical.
- Any breaking contract change requires updating OpenAPI, this document, frontend client/types, backend tests, and
  relevant ADRs.
