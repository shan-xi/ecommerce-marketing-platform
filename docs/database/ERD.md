# Entity Relationship Diagram

**Status:** Proposed — logical ERD, not a physical migration specification.

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
  CAMPAIGN_MESSAGES ||--o{ MEMBER_MESSAGES : delivered_as
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
    varchar membership_tier
    int order_count
    numeric total_spending
  }
  REFRESH_TOKEN_SESSIONS {
    uuid id PK
    uuid user_id FK
    varchar token_hash UK
    timestamptz expires_at
    timestamptz revoked_at
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
    varchar status
    timestamptz starts_at
    timestamptz ends_at
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
    varchar event_type
    timestamptz occurred_at
  }
```

## Cardinality and integrity notes

- `users` is the shared identity table for both administrators and members; `users.role` distinguishes `ADMIN` and `MEMBER`. Only member users have a one-to-one `members` profile containing member-specific attributes.
- `campaign_coupons` is campaign-owned through `campaign_id`. The MVP has no separate global coupon master or reusable promotion-rule entity; supported discount metadata is `FIXED_AMOUNT` or `PERCENTAGE`. `PERCENTAGE` stores percentage points (for example, `10` means 10% off); monetary amounts are TWD.

- `campaign_messages.campaign_id` is unique: exactly one message per campaign in the MVP.
- `campaign_delivery_jobs.campaign_id` is unique: one delivery job per campaign; retries update the same job.
- `campaign_audience_members` has composite primary key `(campaign_id, member_id)`.
- `member_messages` has unique `(campaign_id, member_id)`.
- `coupon_claims` has unique `(campaign_id, campaign_coupon_id, member_id)`.
- `campaign_events` has unique `(member_id, event_id)`.
- Campaign coupon ownership and campaign IDs must agree; `coupon_claims(campaign_id, campaign_coupon_id)` references the unique `(campaign_id, id)` pair on `campaign_coupons`.
- `campaign_delivery_jobs` is one row per campaign. The MVP uses one worker; the cursor and attempt/error fields support batch resume without a lease/lock table.
