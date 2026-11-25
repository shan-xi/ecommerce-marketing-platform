# API Specification

**Status:** Proposed contract — requires owner review  
**Base path:** `/api/v1`  
**Transport:** HTTPS + JSON  
**Authentication:** JWT access/refresh tokens in HttpOnly cookies; CSRF token required for state-changing browser requests.

## 1. Conventions

- JSON uses `camelCase`; database columns use `snake_case`.
- IDs are UUID strings.
- Timestamps are ISO-8601 UTC instants (for example `2026-11-25T10:30:00Z`).
- List endpoints are paginated with `page` (0-based) and `size` (default 20, maximum 100); stable sorting must be documented per endpoint.
- All write requests require a valid CSRF header when cookie authentication is used.
- Do not accept `memberId` from a member client for self-service operations. Resolve it from the authenticated principal.
- Admin endpoints require `ADMIN`; member endpoints require `MEMBER`.
- Public campaign landing-page read may be anonymous; member-specific coupon state is returned only to an authenticated member.

## 2. Cookie and CSRF contract

Suggested cookie names:

- `access_token`: HttpOnly, Secure in HTTPS, SameSite policy configured for deployment, short expiry.
- `refresh_token`: HttpOnly, Secure in HTTPS, restricted Path where practical, longer expiry, rotated on refresh.
- `XSRF-TOKEN`: readable by frontend JavaScript, Secure, SameSite; contains CSRF token only, not an authentication token.

For state-changing requests, frontend sends `X-CSRF-TOKEN` with the value obtained from `XSRF-TOKEN`. The backend validates it according to the configured Spring Security CSRF repository. Cookie names and exact paths are configuration, but frontend and backend must use one documented contract.

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

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/login` | Anonymous + CSRF | Authenticate; set access and refresh cookies |
| POST | `/auth/refresh` | Refresh cookie + CSRF | Rotate refresh token and issue new access cookie |
| POST | `/auth/logout` | Auth/refresh cookie + CSRF | Revoke refresh session and clear cookies |
| GET | `/auth/me` | Authenticated | Current user ID and role |
| GET | `/auth/csrf` | Anonymous | Initialize/return CSRF cookie/token as configured |

### Login request

```json
{ "email": "admin@example.test", "password": "example-only" }
```

Response: `200 OK`, user summary in JSON, access and refresh tokens set as HttpOnly cookies. Passwords in examples are placeholders only.

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

| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/campaigns` | Create draft campaign |
| GET | `/admin/campaigns` | Search/list campaigns |
| GET | `/admin/campaigns/{campaignId}` | Campaign detail |
| PUT | `/admin/campaigns/{campaignId}` | Update draft campaign |
| POST | `/admin/campaigns/{campaignId}/publish` | Publish, snapshot audience, initiate delivery |
| POST | `/admin/campaigns/{campaignId}/pause` | Pause active campaign |

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

The API must define whether `segmentId` and `rules` are mutually exclusive or combined. Recommended MVP semantics: if both are provided, combine saved segment conditions and campaign-specific conditions with AND. The backend validates this and stores the effective rules used for snapshotting.

### Publish response

`202 Accepted` is recommended because message delivery continues asynchronously after publication. Response includes campaign status, `publishedAt`, audience count, and delivery job status. A successful publish means the audience snapshot and delivery job are committed; it does not promise every inbox row already exists.

## 6. Audience segment APIs

| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/audience-segments` | Create reusable segment |
| GET | `/admin/audience-segments` | List segments |
| GET | `/admin/audience-segments/{segmentId}` | Get segment |
| PUT | `/admin/audience-segments/{segmentId}` | Update segment |
| POST | `/admin/audience-segments/preview` | Preview rule match count/sample |
| POST | `/admin/campaigns/{campaignId}/audience-preview` | Preview campaign effective audience |

Rule JSON supports allowlisted fields and operators only. Preview should return count and optionally a small sample of non-sensitive member display identifiers; do not expose full member attributes unnecessarily.

## 7. Coupon configuration APIs

| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/campaigns/{campaignId}/coupons` | Add campaign coupon |
| GET | `/admin/campaigns/{campaignId}/coupons` | List campaign coupons |
| PUT | `/admin/campaigns/{campaignId}/coupons/{couponId}` | Update coupon while campaign is draft |

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

Redemption and order validation are out of scope; `minimumOrderAmount` is offer metadata only in the MVP.

## 8. Campaign message APIs

| Method | Path | Purpose |
|---|---|---|
| PUT | `/admin/campaigns/{campaignId}/message` | Create/update the single message while draft |
| GET | `/admin/campaigns/{campaignId}/message` | Get message configuration |
| GET | `/admin/campaigns/{campaignId}/delivery-status` | Delivery progress and failures |

A database unique constraint on `campaign_messages.campaign_id` enforces one message per campaign.

## 9. Member APIs

| Method | Path | Purpose |
|---|---|---|
| GET | `/member/profile` | Current member profile summary |
| GET | `/member/messages` | Current member inbox |
| GET | `/member/messages/{messageId}` | Message detail |
| GET | `/member/coupons` | Current member's successful claims |

Member identity is derived from the access-token principal. No endpoint accepts a member ID to choose whose private data is returned.

## 10. Campaign landing page and claim APIs

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/campaigns/{campaignId}` | Optional | Public campaign content; member-specific state only when authenticated |
| GET | `/campaigns/{campaignId}/coupons` | Optional | Coupon summary and authenticated member claim state |
| POST | `/campaigns/{campaignId}/coupons/{couponId}/claims` | MEMBER | Claim a coupon |

Claim request:

```json
{ "idempotencyKey": "f3e2e6a0-2c20-4a1f-a7f3-4d6ef1f96a22" }
```

Successful response: `201 Created` with `claimId`, campaign/coupon IDs, status `CLAIMED`, and `claimedAt`. A replay with the same idempotency key returns the same successful result. A duplicate claim with a different key returns `409 CLAIM_ALREADY_EXISTS`.

## 11. Engagement APIs

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/campaigns/{campaignId}/events` | MEMBER | Record impression, click, landing-page view |

Request example:

```json
{
  "eventId": "4a27a4d9-7a4d-40d9-a9b0-50f9c9a5a3d1",
  "eventType": "IMPRESSION",
  "messageId": "optional-uuid",
  "occurredAt": "2026-11-25T10:30:00Z"
}
```

The backend derives `memberId` from authentication. Allowed client event types are `IMPRESSION`, `CLICK`, and `LANDING_PAGE_VIEW`; claim attempt/outcome records may be created server-side as part of claim processing. `DELIVERED` is derived from inbox delivery records. Successful claim is derived from `coupon_claims`.

## 12. Admin analytics APIs

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/campaigns/{campaignId}/analytics` | Campaign funnel metrics |
| GET | `/admin/campaigns/{campaignId}/coupons/analytics` | Coupon-level claims and inventory |

Metrics must distinguish total event counts from distinct-member counts. If a denominator is zero, conversion rate is `null`/unavailable, not `0%`.

## 13. HTTP status and error mapping

| HTTP | Meaning | Example codes |
|---:|---|---|
| 400 | Malformed/invalid request | `VALIDATION_ERROR`, `INVALID_AUDIENCE_RULE` |
| 401 | Missing/invalid authentication | `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| 403 | Authenticated but forbidden / CSRF failure | `FORBIDDEN`, `CSRF_INVALID` |
| 404 | Resource not found or not visible | `RESOURCE_NOT_FOUND` |
| 409 | State conflict or business invariant | `CAMPAIGN_NOT_PUBLISHABLE`, `CAMPAIGN_INACTIVE`, `MEMBER_NOT_ELIGIBLE`, `COUPON_SOLD_OUT`, `CLAIM_ALREADY_EXISTS` |
| 422 | Semantically invalid business input (optional convention) | `INVALID_CAMPAIGN_PERIOD` |
| 429 | Rate limit exceeded, if enabled | `RATE_LIMITED` |
| 500 | Unexpected server failure | `INTERNAL_ERROR` |

Use one consistent mapping across controllers. Avoid returning 200 for failed business operations.

## 14. API contract governance

- `openapi.yaml` is the machine-readable contract baseline; keep it synchronized with this document.
- Generate or validate frontend API types from OpenAPI rather than hand-maintaining duplicate types where practical.
- Any breaking contract change requires updating OpenAPI, this document, frontend client/types, backend tests, and relevant ADRs.
