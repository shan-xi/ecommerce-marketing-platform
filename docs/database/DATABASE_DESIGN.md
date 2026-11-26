# Database Design

**Status:** Proposed — requires owner review  
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

The SQL types below are logical recommendations. Exact names and precision may be adjusted before migrations are written.

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
| `birth_date` | DATE | Optional; age is derived at evaluation time if age targeting is supported |
| `gender` | VARCHAR(30) | Optional controlled value; include only for seeded/demo targeting |
| `registered_at` | TIMESTAMPTZ | NOT NULL |
| `membership_tier` | VARCHAR(30) | NOT NULL |
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
| `id` | UUID | PK; session/family ID |
| `user_id` | UUID | NOT NULL FK → users.id |
| `token_hash` | VARCHAR(255) | NOT NULL, UNIQUE; never store raw token |
| `expires_at` | TIMESTAMPTZ | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `revoked_at` | TIMESTAMPTZ | Nullable |
| `replaced_by_session_id` | UUID | Nullable FK → refresh_token_sessions.id |
| `user_agent` | VARCHAR(500) | Optional, avoid excessive fingerprinting |

Indexes: `(user_id, revoked_at)`, `(expires_at)`. Expired/revoked rows can be cleaned by a maintenance task.

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
| `landing_page_content` | JSONB or structured columns | NOT NULL; validated content schema |
| `status` | VARCHAR(20) | NOT NULL; `DRAFT`, `ACTIVE`, `PAUSED`, `ENDED` |
| `starts_at`, `ends_at` | TIMESTAMPTZ | NOT NULL; CHECK ends_at > starts_at |
| `audience_segment_id` | UUID | Nullable FK → audience_segments.id |
| `audience_rules` | JSONB | Nullable; campaign-specific rules; saved segment plus extra rules must have defined combination semantics |
| `published_at` | TIMESTAMPTZ | Nullable |
| `created_by_user_id` | UUID | NOT NULL FK → users.id |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

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
| `total_quantity` | INTEGER | NOT NULL, CHECK >= 0 |
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

#### `campaign_delivery_jobs`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_id` | UUID | NOT NULL UNIQUE FK → campaigns.id |
| `status` | VARCHAR(20) | NOT NULL; `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `cursor_member_id` | UUID | Nullable; optional keyset-pagination progress |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0 |
| `last_error` | TEXT | Nullable, sanitized |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

This table is a simple database-backed delivery job/outbox for the MVP. It is not a general-purpose message broker. The MVP runs one delivery worker instance, so no lease or `SKIP LOCKED` coordination fields are required. `cursor_member_id` is the last snapshot member successfully processed. Each batch inserts inbox rows with `ON CONFLICT (campaign_id, member_id) DO NOTHING` and advances the cursor in the same transaction; on restart, an interrupted `PROCESSING` job is returned to `PENDING` and resumes after the saved cursor. A bounded retry policy may mark a persistently failing job `FAILED`; no separate retry scheduler is required.

#### `member_messages`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `campaign_message_id` | UUID | NOT NULL FK → campaign_messages.id |
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `member_id` | UUID | NOT NULL FK → members.id |
| `delivered_at` | TIMESTAMPTZ | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Constraint: `UNIQUE (campaign_id, member_id)`; this prevents duplicate inbox delivery for the single-message-per-campaign MVP. Index `(member_id, delivered_at DESC)`.

### 2.6 Engagement events

#### `campaign_events`

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | UUID | PK |
| `event_id` | UUID | NOT NULL; client-generated deduplication ID |
| `campaign_id` | UUID | NOT NULL FK → campaigns.id |
| `member_id` | UUID | NOT NULL FK → members.id |
| `campaign_message_id` | UUID | Nullable FK → campaign_messages.id |
| `campaign_coupon_id` | UUID | Nullable FK → campaign_coupons.id |
| `event_type` | VARCHAR(40) | NOT NULL; `IMPRESSION`, `CLICK`, `LANDING_PAGE_VIEW`, `CLAIM_ATTEMPT`, `CLAIM_FAILED` |
| `occurred_at` | TIMESTAMPTZ | NOT NULL; client event time, validated for reasonable skew |
| `received_at` | TIMESTAMPTZ | NOT NULL; server receipt time |
| `metadata` | JSONB | Optional, size-limited and allowlisted |

Constraint: `UNIQUE (member_id, event_id)`. Indexes: `(campaign_id, event_type, received_at)`, `(campaign_id, member_id, event_type)`, `(member_id, received_at DESC)`.

`DELIVERED` is best derived from `member_messages` to avoid a dual-write between inbox creation and a separate event. If the analytics model requires a unified event stream, create the delivery event from the same transaction or derive it in a view/read model. Successful `COUPON_CLAIMED` is derived from `coupon_claims`, not client event submission.

## 3. Audience rules JSON contract

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

`match: ALL` means AND. Nested groups and arbitrary SQL are prohibited in the MVP. Define null semantics for each field before implementing (recommended: a comparison against null does not match; explicit null checks are not in MVP).

## 4. Coupon claim transaction (normative)

The following operations must run in one transaction:

1. Validate authenticated member and campaign snapshot membership.
2. Validate campaign active status and campaign time window.
3. Validate the coupon time window.
4. Check whether this idempotency key already has a result; return the prior result for a safe retry.
5. Conditionally reserve inventory:

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

6. If zero rows are updated, return `COUPON_SOLD_OUT` when inventory is exhausted; validate campaign and coupon time windows separately and return their corresponding business errors.
7. Insert `coupon_claims`.
8. Commit. If unique constraint or any later step fails, roll back the inventory increment.

The unique constraint is the final guard against duplicate claims. Do not rely only on a prior `SELECT` check. Ensure campaign pause/status changes and claim validation have a clearly defined transaction/locking policy; at minimum, claim must re-check current campaign status inside the transaction.

## 5. Audience snapshot publication

- For the MVP, resolve the effective audience and insert all matching rows into `campaign_audience_members` in the same publication transaction, using a set-based database operation (`INSERT ... SELECT`) where practical.
- Create the `campaign_delivery_jobs` row in that same transaction. The campaign must not become publishable/active and the worker must not start until the complete snapshot and job are committed.
- Do not add a `campaign_snapshot_jobs` table, snapshot lease, or publication state machine for the MVP. If measured audience size makes the single snapshot transaction impractical, revisit this as a separately approved future change.
- The snapshot is immutable for a published campaign.
- The persisted member rows are the authoritative eligibility snapshot. Full historical versioning of the evaluated rule document is deferred; it is not required to determine eligibility after publication.
- Save snapshot creation timestamp and member rows. The current schema records `snapshot_at` on each row; all rows for one campaign publication use the same publication timestamp.
- Delivery worker reads only the snapshot, never re-evaluates the segment.
- Changes to a reusable segment later must not mutate an existing campaign snapshot.
- Decide before implementation how a campaign is re-published after audience changes; MVP default is that an active/published campaign cannot silently replace its snapshot. A new draft/revision or explicit republish flow needs approval if required.

## 6. Deletion and retention

- Prefer soft archive for audience segments.
- Do not hard-delete campaigns with claims or engagement history in the MVP; use status transitions/archival.
- User deletion/anonymization policy is not fully specified; do not cascade-delete financial/claim audit history without an approved retention rule.
- Set a retention policy for raw engagement events before production use; this demo may retain them for the assignment lifecycle.

## 7. Migration and index policy

- Use Flyway versioned migrations.
- Never edit an already-applied migration; add a new migration.
- Add indexes based on query patterns and `EXPLAIN (ANALYZE, BUFFERS)` evidence where possible.
- Avoid indexing every column. Review write overhead for event-heavy tables.
- Add migration tests for unique constraints and check constraints.

## 8. Open questions to resolve before schema implementation

The following identity and coupon ownership decisions are the current design baseline (they are not open questions):

- Administrators and members share the `users` identity table. The `role` column distinguishes `ADMIN` and `MEMBER`; member-only attributes live in the one-to-one `members` profile.
- A coupon is owned by exactly one campaign through `campaign_coupons.campaign_id`. The MVP does not introduce a separate global reusable coupon master or promotion-rule engine.
- MVP coupon discount metadata supports `FIXED_AMOUNT` and `PERCENTAGE`; `minimum_order_amount` is optional and descriptive because checkout/redemption is out of scope.

Remaining open questions:

- Exact campaign landing-page content schema.
- Whether audience rule combination between saved segment and campaign-specific conditions is AND-only (recommended) or replacement semantics.
- Exact retention/anonymization requirements.
- Expected maximum audience size; the MVP assumes a single set-based snapshot transaction is acceptable for the demo/initial deployment.
