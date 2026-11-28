# Entity Relationship Diagram

**Status:** Approved — part of the owner-approved v1.5.0 Development Baseline; logical ERD, not a physical migration
specification.

```mermaid
erDiagram
  USERS ||--o| MEMBERS : has_profile
  USERS ||--o{ REFRESH_TOKEN_SESSIONS : owns
  USERS ||--o{ AUDIENCE_SEGMENTS : creates
  USERS ||--o{ CAMPAIGNS : creates
  AUDIENCE_SEGMENTS o|--o{ CAMPAIGNS : selected_by
  CAMPAIGNS ||--o{ CAMPAIGN_AUDIENCE_MEMBERS : snapshots
  AUDIENCE_SEGMENTS o|--o{ CAMPAIGN_AUDIENCE_MEMBERS : snapshot_source
  MEMBERS ||--o{ CAMPAIGN_AUDIENCE_MEMBERS : included_in
  CAMPAIGNS ||--o{ CAMPAIGN_COUPONS : offers
  CAMPAIGNS ||--o| CAMPAIGN_MESSAGES : has_one_message
  CAMPAIGNS ||--o| CAMPAIGN_DELIVERY_JOBS : schedules_delivery_job
  CAMPAIGN_AUDIENCE_MEMBERS o|--o| CAMPAIGN_DELIVERY_JOBS : delivery_cursor_member
  CAMPAIGN_MESSAGES ||--o{ MEMBER_MESSAGES : delivered_as
  MEMBER_MESSAGES ||--o{ CAMPAIGN_EVENTS : recipient_message_event
  MEMBERS ||--o{ MEMBER_MESSAGES : receives
  CAMPAIGNS ||--o{ MEMBER_MESSAGES : campaign_inbox_item
  CAMPAIGNS ||--o{ CAMPAIGN_EVENTS : records
  MEMBERS ||--o{ CAMPAIGN_EVENTS : performs
  CAMPAIGN_MESSAGES o|--o{ CAMPAIGN_EVENTS : message_context
  CAMPAIGN_COUPONS o|--o{ CAMPAIGN_EVENTS : coupon_context
  CAMPAIGNS ||--o{ COUPON_CLAIMS : owns_claims
  CAMPAIGN_COUPONS ||--o{ COUPON_CLAIMS : claimed_coupon
  MEMBERS ||--o{ COUPON_CLAIMS : owns_claim

  USERS {
    uuid id PK
    varchar email UK
    varchar password_hash
    varchar role
    varchar status
  }
  MEMBERS {
    uuid id PK
    uuid user_id FK
    date birth_date
    varchar gender
    timestamptz registered_at
    varchar membership_tier
    int order_count
    numeric total_spending
    timestamptz last_order_at
    timestamptz last_login_at
  }
  REFRESH_TOKEN_SESSIONS {
    uuid id PK
    uuid token_family_id
    uuid user_id FK
    varchar token_hash UK
    timestamptz expires_at
    timestamptz consumed_at
    timestamptz revoked_at
    uuid replaced_by_token_id FK
  }
  AUDIENCE_SEGMENTS {
    uuid id PK
    varchar name
    jsonb rules
    uuid created_by_user_id FK
  }
  CAMPAIGNS {
    uuid id PK
    varchar name
    text description
    varchar landing_page_headline
    text landing_page_body
    varchar status "persisted: DRAFT, ACTIVE, PAUSED"
    timestamptz starts_at
    timestamptz ends_at
    timestamptz published_at
    uuid audience_segment_id FK
    jsonb audience_rules
  }
  CAMPAIGN_AUDIENCE_MEMBERS {
    uuid campaign_id PK,FK
    uuid member_id PK,FK
    timestamptz snapshot_at
    uuid source_segment_id FK
  }
  CAMPAIGN_COUPONS {
    uuid id PK
    uuid campaign_id FK
    varchar discount_type
    numeric discount_value
    int total_quantity
    int claimed_quantity
    timestamptz starts_at
    timestamptz ends_at
  }
  COUPON_CLAIMS {
    uuid id PK
    uuid campaign_id FK
    uuid campaign_coupon_id FK
    uuid member_id FK
    uuid idempotency_key
    timestamptz claimed_at
  }
  CAMPAIGN_MESSAGES {
    uuid id PK
    uuid campaign_id FK,UK
    varchar title
    text body
  }
  CAMPAIGN_DELIVERY_JOBS {
    uuid id PK
    uuid campaign_id FK,UK
    varchar status
    uuid cursor_member_id
    int attempt_count
    text last_error
    timestamptz created_at
    timestamptz updated_at
  }
  MEMBER_MESSAGES {
    uuid id PK
    uuid campaign_message_id FK
    uuid campaign_id FK
    uuid member_id FK
    timestamptz delivered_at
  }
  CAMPAIGN_EVENTS {
    uuid id PK
    uuid event_id
    uuid campaign_id FK
    uuid member_id FK
    uuid campaign_message_id FK
    uuid campaign_coupon_id FK
    varchar event_type
    jsonb metadata "CLAIM_ATTEMPT outcome: SUCCEEDED/FAILED/REPLAYED"
    timestamptz occurred_at
    timestamptz received_at
  }
```

## Cardinality and integrity notes

- `users` is the shared identity table for both administrators and members; `users.role` distinguishes `ADMIN` and
  `MEMBER`. Only member users have a one-to-one `members` profile containing member-specific attributes.
- `campaign_coupons` is campaign-owned through `campaign_id`. The MVP has no separate global coupon master or reusable
  promotion-rule entity; supported discount metadata is `FIXED_AMOUNT` or `PERCENTAGE`. `PERCENTAGE` stores percentage
  points (for example, `10` means 10% off); monetary amounts are TWD.

- `campaign_coupons.total_quantity` must be at least 1; zero-inventory coupons are invalid configuration.
- `campaign_messages.campaign_id` is unique: exactly one message per campaign in the MVP.
- `campaign_delivery_jobs.campaign_id` is unique: one delivery job per campaign; retries update the same job.
- A campaign must define at least one audience source (`audience_segment_id` or `audience_rules`); when both are
  present, the evaluator combines them with AND.
- `campaign_audience_members` has composite primary key `(campaign_id, member_id)`.
- `member_messages` has unique `(campaign_id, member_id)` and `(campaign_id, campaign_message_id, member_id)`, plus
  composite FK `(campaign_id, campaign_message_id)` → `campaign_messages(campaign_id, id)`.
- `coupon_claims` has unique `(campaign_id, campaign_coupon_id, member_id)`.
- `campaign_events` has unique `(member_id, event_id)` and composite FKs for optional
  `(campaign_id, campaign_message_id)`, `(campaign_id, campaign_coupon_id)`, and
  `(campaign_id, campaign_message_id, member_id)` recipient-message context. `IMPRESSION`/`CLICK` require a message
  context; `CLAIM_ATTEMPT` has no message context and may have a nullable coupon context when the requested coupon ID is
  invalid/mismatched; `LANDING_PAGE_VIEW` has neither.
- `campaign_messages` exposes `UNIQUE (campaign_id, id)` so `member_messages` and `campaign_events` cannot reference a
  message owned by another campaign.
- Each `refresh_token_sessions` row is one token generation; `token_family_id` groups rotations for one login session. A
  composite self-FK ensures `replaced_by_token_id` belongs to the same user and token family. Reuse of a consumed token
  revokes the family.
- `campaign_delivery_jobs.attempt_count` counts automatic retries already consumed at the current cursor (0–3); the
  initial attempt plus three automatic retries are allowed, and a fourth total failure moves the job to `FAILED`. Manual
  retry preserves the cursor.
- `campaign_events.CLAIM_ATTEMPT` is server-generated; `metadata.outcome` is `SUCCEEDED`, `FAILED`, or `REPLAYED`.
- Campaign coupon ownership and campaign IDs must agree; `coupon_claims(campaign_id, campaign_coupon_id)` references the
  unique `(campaign_id, id)` pair on `campaign_coupons`. `member_messages` and `campaign_events` use matching composite
  FKs for campaign/message/coupon context.
- `campaign_delivery_jobs.cursor_member_id`, when set, references a member in the same campaign audience snapshot
  through composite FK `(campaign_id, cursor_member_id)`.
- `campaign_delivery_jobs` is one row per campaign. The MVP uses one worker and batches of 100; the initial attempt plus
  three automatic retries (1s/2s/4s backoff) are allowed; a fourth total failure marks it `FAILED`. Admin retry resets
  the retry count and resumes from the cursor. A zero-member snapshot creates a `COMPLETED` job. Pause does not cancel a
  committed job, including a job still waiting in `PENDING`.
- Persisted campaign states are `DRAFT`, `ACTIVE`, and `PAUSED`. `ENDED` is derived when
  `published_at IS NOT NULL AND ends_at <= now()`, whether the persisted state was `ACTIVE` or `PAUSED`; no
  status-transition worker is required.
