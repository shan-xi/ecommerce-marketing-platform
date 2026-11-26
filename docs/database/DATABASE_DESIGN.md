# Database Design

**Status:** Reviewed — pending owner approval  
**Database:** PostgreSQL  
**Schema versioning:** Flyway migrations are recommended for implementation.

## 1. Design principles

- PostgreSQL is the source of truth for campaign, audience snapshot, message inbox, coupon inventory, claims, and engagement events.
- Use UUID primary keys for externally referenced entities and stable identifiers.
- Use `timestamptz` for instants; persist in UTC and render in `Asia/Taipei` by default.
- Use foreign keys, check constraints, unique constraints, and indexes to protect invariants.
- Store mutable business state relationally. Use JSONB only for bounded audience-rule definitions and event metadata, not for core relationships.
- Never use analytics event rows as proof of coupon ownership; `coupon_claims` is authoritative.

## 2. Proposed tables

The table names, column names, SQL types, nullability, checks, unique constraints, foreign keys, and stated indexes below are the normative MVP persistence contract for implementation. Flyway migration syntax may differ, but it must preserve these semantics. Any semantic schema change must be reflected in this document and the ERD before implementation proceeds.

### 2.1 Identity and members

#### `users`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `email` | VARCHAR(320) | NOT NULL, UNIQUE; login identifier |
| `password_hash` | VARCHAR(255) | NOT NULL |
| `role` | VARCHAR(20) | NOT NULL; `ADMIN` or `MEMBER`; CHECK constraint |
| `status` | VARCHAR(20) | NOT NULL; e.g. `ACTIVE`, `DISABLED` |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

#### `members`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL, UNIQUE FK → users.id |
| `display_name` | VARCHAR(120) | NOT NULL |
| `birth_date` | DATE | Optional; age is derived using the `Asia/Taipei` calendar date. Preview uses its run date; publication snapshot uses the actual publication date, which is authoritative. |
| `gender` | VARCHAR(30) | Nullable; CHECK in `FEMALE`, `MALE`, `OTHER`, `UNDISCLOSED` |
| `registered_at` | TIMESTAMPTZ | NOT NULL |
| `membership_tier` | VARCHAR(30) | NOT NULL; CHECK in `STANDARD`, `SILVER`, `GOLD`, `PLATINUM` |
| `order_count` | INTEGER | NOT NULL DEFAULT 0, CHECK >= 0 |
| `total_spending` | NUMERIC(14,2) | NOT NULL DEFAULT 0, CHECK >= 0 |
| `last_order_at` | TIMESTAMPTZ | Nullable |
| `last_login_at` | TIMESTAMPTZ | Nullable |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

Seeded member records must be fictional and must not contain real personal data.

### 2.2 Refresh-token sessions

#### `refresh_token_sessions`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK; identifies one refresh-token generation/record |
| `token_family_id` | UUID | NOT NULL; stable ID for one browser login session and all its rotated token records |
| `user_id` | UUID | NOT NULL FK → users.id |
| `token_hash` | VARCHAR(255) | NOT NULL, UNIQUE; SHA-256 hash of the complete serialized refresh JWT; never store raw token |
| `expires_at` | TIMESTAMPTZ | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `consumed_at` | TIMESTAMPTZ | Nullable; set atomically when this token is successfully rotated |
| `revoked_at` | TIMESTAMPTZ | Nullable; explicit revocation or family revocation |
| `replaced_by_token_id` | UUID | Nullable FK → refresh_token_sessions.id; next token record in this family |
| `user_agent` | VARCHAR(500) | Optional, avoid excessive fingerprinting |

Each row represents exactly one refresh-token generation, not the whole login session. `token_family_id` remains the same across rotation. Login creates a new family and first token row. Refresh atomically changes the presented row from unconsumed/unrevoked to consumed, inserts a new row with the same family/user, and links `replaced_by_token_id`; all changes occur in one transaction. A concurrent second use cannot consume the same row. If a consumed or revoked token is presented again, reject it and revoke every still-active row in that family. Logout revokes all active rows in the presented token family. Expired/revoked rows may be cleaned only after the configured audit-retention window.

Indexes: `(user_id, token_family_id)`, `(token_family_id, revoked_at)`, `(expires_at)`. Enforce that a replacement token belongs to the same user and token family with a composite self-FK:

```sql
ALTER TABLE refresh_token_sessions
  ADD CONSTRAINT uq_refresh_token_generation_family UNIQUE (id, user_id, token_family_id);

ALTER TABLE refresh_token_sessions
  ADD CONSTRAINT fk_refresh_token_replacement_same_family
  FOREIGN KEY (replaced_by_token_id, user_id, token_family_id)
  REFERENCES refresh_token_sessions (id, user_id, token_family_id);

ALTER TABLE refresh_token_sessions
  ADD CONSTRAINT uq_refresh_token_single_predecessor UNIQUE (replaced_by_token_id);
```

Because `replaced_by_token_id` is nullable, the first/current token in a family has no replacement yet. The composite FK prevents linking a token to another user's or another family's token.

### 2.3 Audience

#### `audience_segments`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `name` | VARCHAR(120) | NOT NULL |
| `description` | VARCHAR(500) | Nullable |
| `rules` | JSONB | NOT NULL; validated allowlisted condition document |
| `created_by_user_id` | UUID | NOT NULL FK → users.id |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |
| `archived_at` | TIMESTAMPTZ | Nullable; soft archive if needed |

#### `campaign_audience_members`

| Column | Type | Rules / purpose |
|---|---|---|
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `member_id` | UUID | NOT NULL FK → members.id |
| `snapshot_at` | TIMESTAMPTZ | NOT NULL |
| `source_segment_id` | UUID | Nullable FK → audience_segments.id; audit/reference only |
| `PRIMARY KEY` | `(campaign_id, member_id)` | One member appears once in a campaign snapshot |

Indexes: `(member_id, campaign_id)` for member eligibility checks. The snapshot rows are immutable after publication; do not cascade-delete them because a segment changes.

### 2.4 Campaigns and coupons

#### `campaigns`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `name` | VARCHAR(160) | NOT NULL |
| `description` | TEXT | Nullable |
| `landing_page_headline` | VARCHAR(200) | NOT NULL; fixed landing-page headline field |
| `landing_page_body` | TEXT | NOT NULL; fixed landing-page body field |
| `status` | VARCHAR(20) | NOT NULL; persisted values `DRAFT`, `ACTIVE`, `PAUSED` only |
| `starts_at`, `ends_at` | TIMESTAMPTZ | NOT NULL; CHECK ends_at > starts_at |
| `audience_segment_id` | UUID | Nullable FK → audience_segments.id |
| `audience_rules` | JSONB | Nullable; campaign-specific rules; if both segment and rules exist, the effective audience is saved-segment match AND campaign-rule match |
| `published_at` | TIMESTAMPTZ | Nullable; set atomically when the campaign is published |
| `created_by_user_id` | UUID | NOT NULL FK → users.id |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

Campaign lifecycle constraint: persisted status must be one of `DRAFT`, `ACTIVE`, or `PAUSED` (database CHECK constraint). `ENDED` is derived for reads and is never stored.

```sql
CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED'))
```

Constraint: at least one audience definition must be supplied:

```sql
CHECK (audience_segment_id IS NOT NULL OR audience_rules IS NOT NULL)
```

When both are supplied, audience resolution applies AND semantics. A NULL-valued member attribute does not satisfy any supported comparison or set operator.

`ENDED` is a derived API/read-model status when `published_at IS NOT NULL AND ends_at <= now()`, whether the persisted state was `ACTIVE` or `PAUSED`; it is not persisted and requires no background status-transition job. A draft remains `DRAFT` after its configured end time. The API may accept `ENDED` as a list filter by applying the same derived-status rule. A campaign is claimable only when persisted status is `ACTIVE` and `starts_at <= now() < ends_at`. Effective API status is otherwise the persisted status.

Recommended indexes: `(status, starts_at, ends_at)`, `(created_at DESC)`.

#### `campaign_coupons`

For the MVP, a coupon is configured within a campaign; a separate global reusable coupon master table is unnecessary unless a future requirement needs it.

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `name` | VARCHAR(160) | NOT NULL |
| `description` | TEXT | Nullable |
| `discount_type` | VARCHAR(30) | NOT NULL; e.g. `FIXED_AMOUNT`, `PERCENTAGE` |
| `discount_value` | NUMERIC(12,2) | NOT NULL; `FIXED_AMOUNT` is TWD amount > 0; `PERCENTAGE` is percent > 0 and <= 100 (application validation or type-aware check) |
| `minimum_order_amount` | NUMERIC(14,2) | Nullable, CHECK >= 0; TWD; descriptive only in MVP because redemption is out of scope |
| `total_quantity` | INTEGER | NOT NULL, CHECK >= 1; zero-quantity coupons are invalid configuration |
| `claimed_quantity` | INTEGER | NOT NULL DEFAULT 0, CHECK >= 0 and <= total_quantity |
| `starts_at`, `ends_at` | TIMESTAMPTZ | NOT NULL; CHECK ends_at > starts_at |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

Recommended type-aware database check:

```sql
CHECK (
  (discount_type = 'FIXED_AMOUNT' AND discount_value > 0)
  OR
  (discount_type = 'PERCENTAGE' AND discount_value > 0 AND discount_value <= 100)
)
```

Also constrain `discount_type` to `FIXED_AMOUNT` or `PERCENTAGE`.

Indexes: `(campaign_id)`. Use `claimed_quantity` as the concurrency-safe counter; periodically verify it equals successful claim rows in tests/diagnostics. Coupon claimability is determined by campaign status/window, coupon validity window, audience eligibility, and remaining inventory; the MVP does not maintain a separate coupon status.

#### `coupon_claims`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `campaign_coupon_id` | UUID | NOT NULL FK → campaign_coupons.id |
| `member_id` | UUID | NOT NULL FK → members.id |
| `idempotency_key` | UUID | NOT NULL; client/request retry key |
| `claimed_at` | TIMESTAMPTZ | NOT NULL |

Constraints:
- `UNIQUE (campaign_id, campaign_coupon_id, member_id)` — one successful claim per member per campaign coupon.
- `UNIQUE (member_id, idempotency_key)` — one successful claim result per member/request key. Reusing the same key for a different campaign/coupon is detected by loading the existing claim and returns `IDEMPOTENCY_KEY_CONFLICT`.
- Enforce that the referenced coupon belongs to the same campaign with a composite foreign key (SQL below).

Indexes: `(member_id, claimed_at DESC)`, `(campaign_id, campaign_coupon_id)`.

Campaign/coupon ownership integrity:

```sql
ALTER TABLE campaign_coupons
  ADD CONSTRAINT uq_campaign_coupon_id UNIQUE (campaign_id, id);

ALTER TABLE coupon_claims
  ADD CONSTRAINT fk_claim_campaign_coupon
  FOREIGN KEY (campaign_id, campaign_coupon_id)
  REFERENCES campaign_coupons (campaign_id, id);
```

The composite foreign key supplements the existing individual foreign keys; it prevents a claim from pairing one campaign ID with a coupon owned by another campaign.

When a unique constraint rejects a concurrent duplicate claim, roll back the inventory increment, then load the existing successful claim by `(member_id, idempotency_key)` or `(campaign_id, campaign_coupon_id, member_id)` and map it to the documented idempotency/duplicate response. Do not treat an application-side pre-check as sufficient.

### 2.5 Messaging and delivery

#### `campaign_messages`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_id` | UUID | NOT NULL, UNIQUE FK → campaigns.id; exactly one message per campaign |
| `title` | VARCHAR(160) | NOT NULL |
| `body` | TEXT | NOT NULL |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

Add `UNIQUE (campaign_id, id)` in addition to the existing unique `campaign_id`; this composite key is referenced by inbox and event integrity constraints below.

#### `campaign_delivery_jobs`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_id` | UUID | NOT NULL UNIQUE FK → campaigns.id |
| `status` | VARCHAR(20) | NOT NULL; CHECK in `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `cursor_member_id` | UUID | Nullable; last successfully processed member ID in ascending UUID order |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0, CHECK between 0 and 3; automatic retries already consumed at current cursor (0–3) |
| `last_error` | TEXT | Nullable, sanitized; latest failed batch error |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

Add database checks `status IN ('PENDING','PROCESSING','COMPLETED','FAILED')` and `attempt_count BETWEEN 0 AND 3`. Allowed job transitions are `PENDING -> PROCESSING -> COMPLETED` or `FAILED`; an interrupted `PROCESSING` job returns to `PENDING` on worker startup; an administrator may transition `FAILED -> PENDING` using the retry endpoint only. `COMPLETED` is terminal. This table is a simple database-backed delivery job/outbox for the MVP. It is not a general-purpose message broker. The MVP runs one worker instance, so no lease or `SKIP LOCKED` coordination fields are required. The job is committed with publication. Pause and campaign end do not cancel it. The worker uses batches of 100 snapshot members. `cursor_member_id` is the last member in the last successfully committed batch. Each batch inserts inbox rows with `ON CONFLICT (campaign_id, member_id) DO NOTHING` and advances the cursor in the same transaction. On batch failure, that transaction rolls back and the cursor is unchanged. `attempt_count` increases to count automatic retries already consumed (not the initial attempt). After the initial failure, retry after 1s; after the next failure, retry after 2s; after the next failure, retry after 4s. If the fourth total attempt fails, mark the job `FAILED`. Members are processed in deterministic ascending `member_id` order; the next batch selects `member_id > cursor_member_id` (or all rows when the cursor is NULL). A successful batch resets `attempt_count` to zero. `attempt_count` is capped at 3 and counts the three automatic retries after the initial attempt. A fourth total failure at the same cursor marks the job `FAILED` and stops automatic retries. An administrator can manually retry only a `FAILED` job; retry sets `PENDING`, resets `attempt_count` to zero, clears `last_error`, and preserves the cursor. On process restart, an interrupted `PROCESSING` job is set to `PENDING` and resumes from its last committed cursor. An empty audience snapshot creates a `COMPLETED` job in the publication transaction. No retry scheduler, lease, multi-worker coordination, or DLQ is used.

#### `member_messages`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_message_id` | UUID | NOT NULL FK → campaign_messages.id |
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `member_id` | UUID | NOT NULL FK → members.id |
| `delivered_at` | TIMESTAMPTZ | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Constraints: `UNIQUE (campaign_id, member_id)`; `UNIQUE (campaign_id, campaign_message_id, member_id)` as the candidate key for event-recipient integrity; composite FK `(campaign_id, campaign_message_id)` → `campaign_messages(campaign_id, id)` ensures the message belongs to the same campaign. This prevents duplicate inbox delivery and cross-campaign message references. Index `(member_id, delivered_at DESC)` and `(campaign_id, campaign_message_id)`.


## 2.6 Engagement events

#### `campaign_events`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `event_id` | UUID | NOT NULL; client-generated for impression/click/page-view, server-generated for claim audit |
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `member_id` | UUID | NOT NULL FK → members.id |
| `campaign_message_id` | UUID | Nullable FK → campaign_messages.id |
| `campaign_coupon_id` | UUID | Nullable FK → campaign_coupons.id |
| `event_type` | VARCHAR(40) | NOT NULL; `IMPRESSION`, `CLICK`, `LANDING_PAGE_VIEW`, `CLAIM_ATTEMPT` |
| `occurred_at` | TIMESTAMPTZ | NOT NULL; client event time for UI events, server time for claim audit |
| `received_at` | TIMESTAMPTZ | NOT NULL; server receipt time |
| `metadata` | JSONB | Optional, size-limited and allowlisted |

Constraints: `UNIQUE (member_id, event_id)`; `CHECK event_type IN ('IMPRESSION', 'CLICK', 'LANDING_PAGE_VIEW', 'CLAIM_ATTEMPT')`; `IMPRESSION` and `CLICK` require `campaign_message_id` and no coupon context; `CLAIM_ATTEMPT` requires no message context and may have a nullable coupon context for invalid/mismatched coupon IDs; `LANDING_PAGE_VIEW` requires neither context ID. For `CLAIM_ATTEMPT`, `metadata` must be non-NULL and `metadata.outcome` must be one of `SUCCEEDED`, `FAILED`, or `REPLAYED`. Composite FK `(campaign_id, campaign_message_id)` → `campaign_messages(campaign_id, id)` when `campaign_message_id` is not NULL; composite FK `(campaign_id, campaign_coupon_id)` → `campaign_coupons(campaign_id, id)` when `campaign_coupon_id` is not NULL. These prevent event context from referencing a message or coupon owned by another campaign.

For each authenticated claim command that references an existing campaign and passes basic request-shape validation, the server generates `event_id` and writes `metadata.outcome` as `SUCCEEDED`, `FAILED`, or `REPLAYED`; failed business attempts also include a safe `failureCode` from the API error-code list (for example `CAMPAIGN_INACTIVE`, `MEMBER_NOT_ELIGIBLE`, `COUPON_OUTSIDE_VALIDITY`, `COUPON_SOLD_OUT`, `CLAIM_ALREADY_EXISTS`, or `IDEMPOTENCY_KEY_CONFLICT`). If the requested coupon belongs to the campaign, populate `campaign_coupon_id`; otherwise leave it NULL and place the requested coupon UUID in allowlisted `metadata.couponId`. This event is never client-submitted. A replay audit is written in a short independent transaction because no new claim transaction is required for replay. A successful new claim writes the audit event in the same transaction as `coupon_claims`. A failed business attempt is written after the claim transaction rolls back, in a separate short transaction, so it survives rollback. A replay is audited as `REPLAYED`. Store no raw token, idempotency key, or sensitive profile data in metadata.

Database check constraints for event context and audit outcome:

```sql
CHECK (
  (event_type IN ('IMPRESSION', 'CLICK')
    AND campaign_message_id IS NOT NULL
    AND campaign_coupon_id IS NULL)
  OR (event_type = 'LANDING_PAGE_VIEW'
    AND campaign_message_id IS NULL
    AND campaign_coupon_id IS NULL)
  OR (event_type = 'CLAIM_ATTEMPT'
    AND campaign_message_id IS NULL)
);

CHECK (
  event_type <> 'CLAIM_ATTEMPT'
  OR (metadata IS NOT NULL
    AND metadata ? 'outcome'
    AND metadata->>'outcome' IN ('SUCCEEDED', 'FAILED', 'REPLAYED'))
);
```

Indexes: `(campaign_id, event_type, received_at)`, `(campaign_id, member_id, event_type)`, `(member_id, received_at DESC)`, and composite context indexes as required by the foreign keys.

`DELIVERED` is best derived from `member_messages` to avoid a dual-write between inbox creation and a separate event. If the analytics model requires a unified event stream, create the delivery event from the same transaction or derive it in a view/read model. Successful `COUPON_CLAIMED` is derived from `coupon_claims`, not client event submission.

**Cross-campaign relationship constraints (normative):**

```sql
-- campaign_messages must expose a composite candidate key
ALTER TABLE campaign_messages
  ADD CONSTRAINT uq_campaign_message_campaign_id UNIQUE (campaign_id, id);

ALTER TABLE member_messages
  ADD CONSTRAINT fk_member_message_campaign_message
  FOREIGN KEY (campaign_id, campaign_message_id)
  REFERENCES campaign_messages (campaign_id, id);

ALTER TABLE member_messages
  ADD CONSTRAINT uq_member_message_campaign_message_member
  UNIQUE (campaign_id, campaign_message_id, member_id);

ALTER TABLE campaign_events
  ADD CONSTRAINT fk_campaign_event_recipient_message
  FOREIGN KEY (campaign_id, campaign_message_id, member_id)
  REFERENCES member_messages (campaign_id, campaign_message_id, member_id);

ALTER TABLE campaign_events
  ADD CONSTRAINT fk_campaign_event_campaign_message
  FOREIGN KEY (campaign_id, campaign_message_id)
  REFERENCES campaign_messages (campaign_id, id);

ALTER TABLE campaign_events
  ADD CONSTRAINT fk_campaign_event_campaign_coupon
  FOREIGN KEY (campaign_id, campaign_coupon_id)
  REFERENCES campaign_coupons (campaign_id, id);

ALTER TABLE campaign_delivery_jobs
  ADD CONSTRAINT fk_delivery_cursor_campaign_snapshot_member
  FOREIGN KEY (campaign_id, cursor_member_id)
  REFERENCES campaign_audience_members (campaign_id, member_id);
```

PostgreSQL's default `MATCH SIMPLE` behavior permits nullable message/coupon context and a NULL delivery cursor to be omitted; when context/cursor IDs are supplied, the campaign and referenced row must match. The recipient-message FK applies only when `campaign_message_id` is non-NULL, so it validates impressions/clicks without affecting landing-page or claim-audit events. Retain direct FKs to the parent tables as appropriate for clear ownership and delete behavior.

## 3. Audience field / type / operator validation matrix

The following table is the backend allowlist and must match the PRD and OpenAPI descriptions.

| Field | Type | Allowed operators | Accepted values |
|---|---|---|---|
| `age` | Integer | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | 0–120 |
| `gender` | Enum | `=`, `!=`, `IN`, `NOT IN` | `FEMALE`, `MALE`, `OTHER`, `UNDISCLOSED` |
| `registeredAt` | DateTime | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 offset timestamp |
| `membershipTier` | Enum | `=`, `!=`, `IN`, `NOT IN` | `STANDARD`, `SILVER`, `GOLD`, `PLATINUM` |
| `orderCount` | Integer | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Integer >= 0 |
| `totalSpending` | Decimal | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Decimal >= 0, at most 2 fractional digits, TWD |
| `lastOrderAt` | DateTime nullable | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 offset timestamp |
| `lastLoginAt` | DateTime nullable | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 offset timestamp |

Every scalar operator requires one non-null scalar of the exact field type. `IN` and `NOT IN` require a non-empty array whose items are non-null and of the exact field type. Enum comparisons are case-sensitive. Ordered comparisons are invalid for enum fields. NULL member attributes never match any operator, including `!=` and `NOT IN`. `age` is NULL when `birth_date` is NULL. The application validator must reject incompatible field/operator/value combinations before building SQL; it must not interpolate client-provided SQL identifiers or expressions.

## 4. Audience rules JSON contract

Only allowlisted field/operator combinations are accepted. Example:

```json
{
  "version": 1,
  "match": "ALL",
  "conditions": [
    { "field": "membershipTier", "operator": "IN", "value": ["GOLD", "PLATINUM"] },
    { "field": "orderCount", "operator": ">=", "value": 3 },
    { "field": "lastOrderAt", "operator": "<", "value": "2026-01-01T00:00:00Z" }
  ]
}
```

`match: ALL` means AND. Comparison operators require one non-null scalar value; `IN` and `NOT IN` require a non-empty array of non-null scalar values. Values must match the selected field type. NULL array elements are invalid. Nested groups and arbitrary SQL are prohibited in the MVP. If a campaign selects a saved segment and also supplies campaign-specific rules, both definitions are combined with AND; a member must satisfy the saved segment and all campaign-specific conditions. If only one is supplied, it defines the effective audience; at least one must be supplied. A comparison or set operation against a member attribute whose value is SQL `NULL` does not match (SQL `UNKNOWN` is treated as false). Explicit `IS NULL`/`IS NOT NULL` operators are not supported in the MVP.

## 5. Coupon claim transaction (normative)

The claim use case uses PostgreSQL's default `READ COMMITTED` isolation and follows this order:

1. Authenticate the member and perform a read-only lookup by `(member_id, idempotency_key)`. If a successful claim already exists for the same campaign/coupon, return that original result even if the campaign is now paused or ended. If the key belongs to a different campaign/coupon, return `IDEMPOTENCY_KEY_CONFLICT`.
2. Within the same transaction, acquire a shared row lock on the campaign row (`SELECT ... FOR SHARE`). This is the first business-row lock.
3. Re-check `(member_id, idempotency_key)` after acquiring the lock, because another concurrent request may have completed while this request was waiting. Apply the same replay/conflict behavior as step 1.
4. Validate authenticated member and campaign snapshot membership.
5. Validate persisted campaign status and campaign time window while holding the lock.
6. Validate the coupon time window.
7. Conditionally reserve inventory:

```sql
UPDATE campaign_coupons
SET claimed_quantity = claimed_quantity + 1,
    updated_at = now()
WHERE id = :campaignCouponId
  AND campaign_id = :campaignId
  AND starts_at <= now()
  AND ends_at > now()
  AND claimed_quantity < total_quantity;
```

8. If zero rows are updated, return `COUPON_SOLD_OUT` when inventory is exhausted; validate campaign and coupon time windows separately and return their corresponding business errors.
9. Insert `coupon_claims` and a server-generated `CLAIM_ATTEMPT` audit event with `metadata.outcome = SUCCEEDED` in the same transaction.
10. Commit. If a unique constraint or any later step fails, roll back the inventory increment and success audit event. After rollback, record a `CLAIM_ATTEMPT` audit event with `metadata.outcome = FAILED` and a safe business `failureCode` in a separate short transaction. On a concurrent unique-key conflict, roll back and load the winning claim to return the documented replay/conflict response; record the request as `REPLAYED` if it resolves to the same successful claim.

The unique constraint is the final guard against duplicate claims. Do not rely only on a prior `SELECT` check.

**Pause/claim serialization (normative):** `PauseCampaign` updates the same campaign row and therefore obtains a row lock that conflicts with the claim's `FOR SHARE` lock. Multiple claims may hold compatible shared locks concurrently. If a claim obtains its lock first, Pause waits for that claim to commit or roll back; that claim is ordered before Pause. Once Pause commits, claims that subsequently obtain the campaign lock observe `PAUSED` and are rejected. All claim flows must lock the campaign row before the coupon inventory row to maintain a consistent lock order.

## 6. Audience snapshot publication

- Publication is allowed only during the campaign window (`starts_at <= now() < ends_at`); future-dated or already-ended campaigns cannot be published. Scheduled publication is not supported. A campaign must have at least one configured coupon with `total_quantity >= 1` before publication, matching PRD acceptance criteria.
- For the MVP, resolve the effective audience and insert all matching rows into `campaign_audience_members` in the same publication transaction, using a set-based database operation (`INSERT ... SELECT`) where practical.
- Create the `campaign_delivery_jobs` row in that same transaction. The campaign must not become publishable/active and the worker must not start until the complete snapshot and job are committed.
- Do not add a `campaign_snapshot_jobs` table, snapshot lease, or publication state machine for the MVP. If measured audience size makes the single snapshot transaction impractical, revisit this as a separately approved future change.
- A zero-member snapshot is valid. Publish succeeds with a warning; the delivery job is created directly as `COMPLETED`, with no inbox rows.
- The snapshot is immutable for a published campaign.
- The persisted member rows are the authoritative eligibility snapshot. Full historical versioning of the evaluated rule document is deferred; it is not required to determine eligibility after publication.
- Save snapshot creation timestamp and member rows. The current schema records `snapshot_at` on each row; all rows for one campaign publication use the same publication timestamp.
- Delivery worker reads only the snapshot, never re-evaluates the segment. It must not check campaign status to decide whether to process a committed delivery job; Pause does not cancel a pending or processing job.
- Changes to a reusable segment later must not mutate an existing campaign snapshot.
- A published campaign cannot be republished to silently replace its audience snapshot. If a new audience is required, create a new draft/revision; a dedicated republish workflow is out of MVP scope.

## 6.1 Landing-page read behavior

A published campaign landing page remains publicly readable when persisted status is `PAUSED` or its effective status is `ENDED`. Public content and non-personal coupon summaries remain visible. In those states all coupons are shown as unavailable for new claims; no claim request can succeed. For an authenticated member, the response may include that member's claim state and an explicit `eligible` / `notEligible` indicator based on the immutable audience snapshot. Anonymous responses must not expose audience membership or member-specific state.

## 7. Deletion and retention

- Prefer soft archive for audience segments.
- Do not hard-delete campaigns with claims or engagement history in the MVP; use status transitions/archival.
- User deletion/anonymization policy is not fully specified; do not cascade-delete financial/claim audit history without an approved retention rule.
- Set a retention policy for raw engagement events before production use; this demo may retain them for the assignment lifecycle.

## 8. Migration and index policy

- Use Flyway versioned migrations.
- Never edit an already-applied migration; add a new migration.
- Add indexes based on query patterns and `EXPLAIN (ANALYZE, BUFFERS)` evidence where possible.
- Avoid indexing every column. Review write overhead for event-heavy tables.
- Add migration tests for unique constraints and check constraints.

## 9. Deferred implementation and production considerations

The following decisions are already part of the MVP baseline:

- Administrators and members share the `users` identity table. The `role` column distinguishes `ADMIN` and `MEMBER`; member-only attributes live in the one-to-one `members` profile.
- A coupon is owned by exactly one campaign through `campaign_coupons.campaign_id`. The MVP does not introduce a separate global reusable coupon master or promotion-rule engine.
- MVP coupon discount metadata supports `FIXED_AMOUNT` and `PERCENTAGE`; `minimum_order_amount` is optional and descriptive because checkout/redemption is out of scope.
- Landing-page content uses fixed `landing_page_headline` and `landing_page_body` columns; there is no Page Builder or arbitrary page-content schema.
- Saved-segment rules and campaign-specific rules are combined with AND when both are supplied; NULL-valued member attributes do not match supported comparisons or set operators.

The following items are intentionally deferred and do not block the MVP repository-foundation phase:

- Exact retention/anonymization requirements must be set before production use; MVP/demo data is retained for the assignment lifecycle.
- The MVP assumes a single set-based snapshot transaction is acceptable for the demo/initial deployment. Revisit only if measured audience size makes it impractical; no numeric capacity guarantee is made.
