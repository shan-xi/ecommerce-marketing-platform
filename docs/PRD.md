# Product Requirements Document (PRD)
## E-commerce Campaign & Coupon Engagement Platform

**Document status:** Draft — under review  
**Version:** 1.5.0  
**Date:** 2026-11-26  
**Product type:** Web-based campaign management and member engagement platform

---

## 1. Product Overview

### 1.1 Product Summary

The E-commerce Campaign & Coupon Engagement Platform enables marketing administrators to create and manage campaigns, define target audiences, engage members through in-app messages, guide members to campaign landing pages, and measure campaign performance through coupon claims.

The MVP supports this journey:

**Campaign → Audience → In-App Message → Engagement → Landing Page → Coupon Claim → Analytics**

A campaign is the central business entity. In the MVP, each campaign has exactly one in-app message and may contain multiple coupons. The system records the member engagement funnel and protects coupon inventory and claim uniqueness under concurrent requests.

### 1.2 Product Vision

Provide a focused, measurable campaign workflow that allows marketing teams to reach selected members, encourage them to engage with an offer, and understand where members drop off before claiming a coupon.

### 1.3 Product Goals

1. Allow administrators to create, configure, publish, pause, and review campaigns.
2. Support reusable, rule-based audience segments and campaign-specific audience rules.
3. Deliver one in-app message per campaign to the campaign's resolved target audience.
4. Provide a campaign landing page where eligible members can view and claim coupons.
5. Enforce coupon quantity limits and per-member claim restrictions reliably.
6. Measure the campaign funnel from message delivery to coupon claim.
7. Provide campaign-level and coupon-level performance reporting.

### 1.4 Success Criteria

The MVP is successful when an administrator can configure and publish a campaign, eligible members receive and interact with its in-app message, members can claim available coupons, and administrators can review reliable campaign performance metrics.

---

## 2. Users and Roles

### 2.1 Marketing Administrator

An administrator uses the Admin Portal to:

- Create and edit draft campaigns.
- Define and preview target audiences.
- Create and manage reusable audience segments.
- Configure multiple coupons for a campaign.
- Configure the campaign's single in-app message.
- Publish or pause a campaign.
- Review delivery progress and manually retry a failed delivery job.
- Review campaign and coupon performance.

### 2.2 Member

A member uses the Member Portal to:

- Sign in.
- View messages in their in-app message center.
- Open a campaign landing page from a message or campaign link.
- View campaign details and available coupons.
- Claim an eligible coupon.
- View successfully claimed coupons.

### 2.3 Authentication and Authorization

- The MVP shall use Spring Security with JWT Access Tokens and Refresh Tokens.
- Access tokens shall be short-lived; refresh tokens shall support renewal of an authenticated session.
- For browser-based clients, tokens should be stored and transmitted using appropriately secured cookies (HttpOnly, Secure in HTTPS environments, and an appropriate SameSite policy), rather than browser local storage. CSRF protections must be applied when cookies are used for authentication.
- The backend shall derive the member identity from the authenticated principal. It must not trust a client-supplied member ID for member-specific operations.
- Administrator and member roles shall have separate authorization boundaries.
- Passwords must be stored using a secure password hashing algorithm.
- Each refresh-token rotation creates a new token record in the same stable token family. The presented token is atomically consumed once; reuse of a consumed/revoked token revokes the entire token family. Logout revokes the current family.
- Production identity-provider integration, social login, OAuth federation, and enterprise SSO are out of scope.

---

## 3. Scope

### 3.1 In Scope — MVP

| Area | Included capabilities |
|---|---|
| Admin Portal | Administrator login; campaign create, edit, publish, pause, list, and detail; audience segment management; delivery progress/retry; analytics |
| Campaign | Name, description, start/end time, status, fixed landing-page headline and body; exactly one in-app message |
| Audience | Rule-based audience definition; reusable saved segments; audience preview; snapshot of eligible members at publication |
| Coupon | Multiple coupons per campaign; coupon definitions, validity, limited inventory, and one-claim-per-member rule |
| Touchpoint | In-app message center only; one message per campaign; immediate delivery when the campaign is published |
| Member Portal | Member login; message inbox; campaign landing page; coupon claim; claimed-coupon list |
| Engagement Tracking | Delivered, impression, click, landing-page view, claim attempt/result events |
| Analytics | Campaign-level funnel metrics and coupon-level claim metrics |
| Reliability | JWT authentication, authorization, duplicate-claim prevention, inventory protection, event deduplication, delivery retry/manual recovery, and concurrency tests |
| Frontend | One React + TypeScript SPA with separate Admin and Member route groups |

### 3.2 Out of Scope — MVP

- Scheduled or future-dated message delivery. The MVP delivers the in-app message immediately as part of campaign publication. Scheduling may be added later.
- Multiple messages per campaign, message variants, A/B testing, and message sequencing.
- Email, mobile push, SMS, WhatsApp, and external messaging-provider integrations.
- Native iOS or Android applications.
- Automated marketing journeys, behavior-triggered campaigns, and complex workflow builders.
- Advanced audience logic such as nested Boolean expressions, arbitrary SQL, or real-time streaming segmentation.
- Coupon redemption, checkout, order creation, payment, refund, and logistics.
- Coupon usage validation at checkout.
- Revenue attribution and order conversion analytics.
- Integration with production member, order, product, or coupon systems.
- Multi-tenant enterprise administration and complex approval workflows.

The MVP ends when a member successfully claims a coupon. Coupon redemption and subsequent commerce operations are outside this product's scope.

---

## 4. Key Domain Concepts and Definitions

### 4.1 Campaign

A **Campaign** is the central marketing initiative managed by an administrator. It defines the campaign period, target audience, landing page, associated coupons, and exactly one in-app message in the MVP.

Examples include a seasonal promotion, a new-member offer, or a dormant-member re-engagement campaign.

### 4.2 Audience and Audience Segment

An **Audience** is the set of members eligible to participate in a campaign.

An **Audience Segment** is a reusable set of targeting rules saved by an administrator, such as VIP members, new members, or members who have not ordered recently. A campaign may use a saved segment, campaign-specific rules, or both. When both are provided, the effective audience is the intersection: a member must match the saved segment **AND** every campaign-specific condition. When only one is provided, that definition is used. A campaign must provide at least one audience definition.

The MVP supports simple rule-based conditions against seeded member attributes. `Age` is derived from `birth_date` using the `Asia/Taipei` calendar date. Audience preview uses the date on which the preview is run; publication snapshot uses the actual publication date. Therefore, an age-based preview is an estimate and may change if the campaign is published on a later date; the committed publication snapshot is authoritative. Age is not stored as a mutable value. Supported attributes should include:

- Age
- Gender
- Registration date
- Membership tier
- Order count
- Total spending
- Last order date
- Last login date

The MVP rule builder supports:

- Comparison operators: `=`, `!=`, `>`, `>=`, `<`, `<=`
- Set operators: `IN`, `NOT IN`
- Date and numeric comparisons where appropriate
- Comparison operators take one non-null scalar value; `IN` and `NOT IN` take a non-empty array of non-null scalar values. Values must match the selected member attribute type.
- Multiple conditions combined with `AND`

#### Audience field / type / operator matrix (normative)

The API field names below are the canonical JSON values. The Admin rule builder and backend validator must use this matrix; an operator not listed for a field is rejected with `INVALID_AUDIENCE_RULE`.

| API field | Value type | NULL allowed on member record? | Allowed operators | Value format / constraints |
|---|---|---:|---|---|
| `age` | Integer | Derived as NULL when `birth_date` is NULL | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Integer from 0 through 120; calculated as of the Asia/Taipei date used for preview/publication |
| `gender` | Enum | Yes | `=`, `!=`, `IN`, `NOT IN` | `FEMALE`, `MALE`, `OTHER`, `UNDISCLOSED` |
| `registeredAt` | DateTime | No | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 timestamp with timezone; normalized to UTC |
| `membershipTier` | Enum | No | `=`, `!=`, `IN`, `NOT IN` | `STANDARD`, `SILVER`, `GOLD`, `PLATINUM` |
| `orderCount` | Integer | No | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Integer >= 0 |
| `totalSpending` | Decimal | No | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Decimal >= 0, TWD, maximum 2 fractional digits |
| `lastOrderAt` | DateTime | Yes | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 timestamp with timezone; normalized to UTC |
| `lastLoginAt` | DateTime | Yes | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 timestamp with timezone; normalized to UTC |

For all fields, comparison operators take exactly one non-null scalar; `IN` and `NOT IN` take a non-empty array of non-null values of the field's type. `>`/`>=`/`<`/`<=` are not valid for enum fields. Boolean values are not supported. DateTime set values are timestamp strings. Enum values are case-sensitive. A member attribute that is NULL never matches any condition, including `!=` and `NOT IN`; this is an explicit product rule and must not rely on SQL's default NULL behavior.

Nested `AND`/`OR` groups, arbitrary SQL, custom scripts, and continuously updated real-time audiences are out of scope. For every supported comparison or set operator, a member attribute whose value is `NULL` does not match the condition. SQL `UNKNOWN`/three-valued results are treated as non-matches; explicit `IS NULL`/`IS NOT NULL` rule operators are not supported in the MVP.

**Audience snapshot rule:** when a campaign is published, the system resolves its audience and stores a snapshot of eligible member IDs for that campaign. The campaign's target population remains stable after publication; later changes to a member's attributes do not silently add or remove members from that published campaign. A published campaign's audience snapshot is not silently replaced. If a new target snapshot is needed, the administrator creates a new draft/revision; a dedicated republish workflow is out of scope for the MVP.

### 4.3 Touchpoint and Message

A **Touchpoint** is a channel through which campaign content is presented to a member. The MVP supports only the **In-App Message** channel.

Each campaign has exactly one in-app message in the MVP. The message contains a title, body, and destination campaign landing page. The domain model should keep the channel concept separate from campaign business rules so that email or push channels can be added in the future. No external channel provider or generic plugin framework is required now.

### 4.4 Delivery, Impression, Click, and Claim

| Term | Definition |
|---|---|
| Target Audience | Distinct members included in the campaign's audience snapshot |
| Delivered | A member-message inbox record has been successfully created and made available to the member |
| Impression | The message has been rendered in the member's visible interface and an impression event has been recorded |
| Unique Reach | Distinct members with at least one qualifying impression for the campaign |
| Click | A member clicks the campaign message |
| Unique Clicker | A distinct member who clicked the campaign message |
| Landing Page View | A member opens the campaign landing page and a view event is recorded |
| Claim Attempt | A member submits a request to claim a coupon, regardless of outcome |
| Coupon Claim | A successful claim transaction that creates a coupon claim record |
| Unique Claimer | A distinct member who successfully claims at least one coupon in the campaign |

Message delivery alone does not count as an impression or reach. Repeated impressions may increase total impression count, but a member contributes only once to unique reach.

### 4.5 Coupon

A **Coupon** is an offer that a member can claim from a campaign landing page. A campaign may contain multiple coupons, each with its own total quantity and validity period. The MVP applies one fixed rule to every coupon: each member may claim each specific coupon once.

Example:

| Coupon | Total Quantity | Claim Limit |
|---|---:|---:|
| NT$100 off orders over NT$1,000 | 2,000 | One per member |
| NT$300 off orders over NT$2,000 | 500 | One per member |
| NT$800 off orders over NT$5,000 | 100 | One per member |

The MVP records coupon ownership after a successful claim. It does not process redemption.

For the MVP, `FIXED_AMOUNT` means a fixed discount amount in TWD. `PERCENTAGE` means a percentage discount off the eligible order amount; for example, `discount_value = 10` means 10% off (valid range: greater than 0 and at most 100). `minimum_order_amount` is descriptive offer metadata only because checkout/redemption is out of scope. All monetary values in the MVP use TWD.

---

## 5. Primary User Journeys

### 5.1 Administrator Creates and Publishes a Campaign

1. Administrator signs in to the Admin Portal.
2. Administrator creates a campaign and enters its name, description, start time, end time, and landing-page content.
3. Administrator selects a saved audience segment, defines campaign-specific targeting rules, or provides both. When both are provided, a member must match both (AND).
4. The system previews the effective audience using the same AND semantics and, before publication, resolves the audience.
5. Administrator adds one or more coupons and configures each coupon's offer, total quantity, and validity period. The one-claim-per-member rule is fixed for the MVP.
6. Administrator creates the campaign's single in-app message, including title and body.
7. Administrator reviews the campaign configuration and publishes it. Publication is allowed only within the campaign window (`starts_at <= now < ends_at`); future-dated scheduled publication is not supported.
8. The system validates the configuration, creates the complete immutable audience snapshot in the publication transaction, creates one delivery job, and initiates immediate in-app message delivery to that snapshot. Inbox rows are then created asynchronously in batches. A zero-member snapshot is valid: the campaign publishes with a warning and its delivery job is immediately marked `COMPLETED` without creating inbox rows.
9. The administrator can view campaign status and performance.

### 5.2 Member Receives and Engages with a Campaign

1. Member signs in to the Member Portal.
2. Member opens the in-app message center.
3. The member sees the campaign message delivered to their account.
4. When the message is visibly rendered, the client records an impression.
5. The member clicks the message; the system records the click and navigates to the campaign landing page. The landing page may also be opened directly by an anonymous visitor.
6. The system records a landing-page view when the visitor is authenticated; anonymous page views are not attributed to a member.
7. The visitor can review public campaign content and coupon summaries even when the campaign is `PAUSED` or effectively `ENDED`. In those states coupons are shown as unavailable and cannot be newly claimed. To claim a coupon, the visitor must sign in; after login, the application returns them to the intended campaign page. The member then selects a coupon to claim.
8. The backend validates campaign status and period, audience membership, coupon validity, inventory, and prior claim history.
9. If all validations pass, the system creates a coupon claim record and confirms success.
10. The member can see the claimed coupon in their claimed-coupon list.

### 5.3 Administrator Reviews Campaign Performance

1. Administrator opens a campaign detail page.
2. Administrator reviews target count, delivered count, impressions, unique reach, clicks, landing-page views, and successful coupon claims.
3. Administrator reviews claim quantities and unique claimers by coupon.
4. The administrator uses the metrics to understand member engagement and offer performance.

---

## 6. Functional Requirements

Priority definitions:

- **P0:** Required for the MVP.
- **P1:** Valuable, but can be simplified if necessary without compromising the core journey.

### 6.1 Authentication and Access Control

| ID | Requirement | Priority |
|---|---|---|
| FR-AUTH-01 | The system shall allow administrators to sign in and refresh an authenticated session using JWT access and refresh tokens. | P0 |
| FR-AUTH-02 | The system shall allow members to sign in and refresh an authenticated session using JWT access and refresh tokens. | P0 |
| FR-AUTH-03 | The system shall enforce role-based authorization for administrator and member functions. | P0 |
| FR-AUTH-04 | The system shall derive the member identity from the authenticated principal for member-specific operations. | P0 |
| FR-AUTH-05 | The system shall prevent members from accessing another member's messages or claimed coupons. | P0 |
| FR-AUTH-06 | The system shall provide a logout/refresh-token invalidation mechanism. | P0 |

### 6.2 Campaign Management

| ID | Requirement | Priority |
|---|---|---|
| FR-CAM-01 | An administrator shall be able to create a campaign. | P0 |
| FR-CAM-02 | A campaign shall include a name, description, start time, end time, and fixed landing-page `headline` and `body` fields. | P0 |
| FR-CAM-03 | An administrator shall be able to view and search campaigns. | P0 |
| FR-CAM-04 | An administrator shall be able to edit a draft campaign. | P0 |
| FR-CAM-05 | An administrator shall be able to publish a valid campaign only when `starts_at <= now < ends_at`; future-dated scheduled publication is not supported. | P0 |
| FR-CAM-06 | An administrator shall be able to pause an active campaign; after the pause commits, new coupon claims are blocked. The delivery job committed during publication continues even if still `PENDING`, and delivered inbox messages are not retracted. | P0 |
| FR-CAM-07 | The system shall reject invalid campaign schedules, including an end time earlier than the start time. | P0 |
| FR-CAM-08 | The system shall prevent coupon claims outside the campaign's active period. | P0 |
| FR-CAM-09 | The system shall expose a reusable campaign landing page using a campaign identifier. | P0 |
| FR-CAM-10 | Publishing shall resolve and persist the campaign's audience snapshot before message delivery begins. | P0 |
| FR-CAM-11 | A campaign shall have at least one configured coupon with `total_quantity >= 1` before it can be published. | P0 |
| FR-CAM-12 | A campaign with a valid configuration may be published with a zero-member audience snapshot; the UI shall warn the administrator and the delivery job shall be completed without inbox rows. | P0 |

Campaign lifecycle semantics: persisted states are `DRAFT`, `ACTIVE`, and `PAUSED`. `ENDED` is a derived status when `published_at` is set and `ends_at <= now`, whether the persisted state was `ACTIVE` or `PAUSED`; it is not stored and requires no background status-transition job. A draft remains `DRAFT` even if its configured end time passes. A campaign is claimable only when its persisted status is `ACTIVE` and `starts_at <= now < ends_at`. The MVP has no resume or republish workflow.

### 6.3 Audience and Segment Management

| ID | Requirement | Priority |
|---|---|---|
| FR-AUD-01 | An administrator shall be able to define a rule-based audience using supported member attributes and operators. | P0 |
| FR-AUD-02 | An administrator shall be able to save an audience definition as a reusable segment. | P0 |
| FR-AUD-03 | An administrator shall be able to select a saved segment for a campaign. | P0 |
| FR-AUD-04 | An administrator shall be able to define campaign-specific audience rules without saving a reusable segment. | P0 |
| FR-AUD-05 | The system shall preview the estimated matching member count before publication. | P0 |
| FR-AUD-06 | The system shall resolve and snapshot matching member IDs when the campaign is published. | P0 |
| FR-AUD-07 | Only members in the campaign's audience snapshot shall be eligible to claim campaign coupons. | P0 |
| FR-AUD-08 | The system shall support multiple conditions combined with AND and the operators defined in Section 4.2. | P0 |

For demonstration and testing, the project shall include seeded member records with realistic values for the supported audience attributes. These records are sample application data, not a connection to a real external member system.

### 6.4 Coupon Management

| ID | Requirement | Priority |
|---|---|---|
| FR-COUP-01 | An administrator shall be able to associate multiple coupons with one campaign. | P0 |
| FR-COUP-02 | Each coupon shall have a name, description, discount information, total quantity, and validity period. | P0 |
| FR-COUP-03 | The system shall enforce one successful claim per member for each campaign coupon. The limit is fixed at one in the MVP. | P0 |
| FR-COUP-04 | The system shall display coupon availability on the campaign landing page. | P0 |
| FR-COUP-05 | The system shall prevent claims after the coupon has reached its total quantity. | P0 |
| FR-COUP-06 | The system shall prevent claims outside the coupon's validity period. | P0 |
| FR-COUP-07 | The system shall create a coupon claim record after a successful claim. | P0 |
| FR-COUP-08 | A member shall be able to view their successfully claimed coupons. | P0 |
| FR-COUP-09 | The system shall prevent the same member from claiming the same campaign coupon more than once. | P0 |

**Claim uniqueness:** a member may claim different coupons in the same campaign, but may successfully claim a specific campaign coupon only once. A suitable database constraint is `UNIQUE (campaign_id, campaign_coupon_id, member_id)`.

The claim operation must be safe under concurrent requests. Application-level checks alone are insufficient; database constraints and transactional inventory updates must protect correctness. A retry with an existing successful idempotency key returns its original result even if the campaign has since been paused or ended; idempotency replay is checked before new-claim eligibility.

### 6.5 In-App Message Management and Delivery

| ID | Requirement | Priority |
|---|---|---|
| FR-MSG-01 | Each campaign shall have exactly one in-app message in the MVP. | P0 |
| FR-MSG-02 | The message shall include a title, body, and associated campaign. | P0 |
| FR-MSG-03 | When a campaign is successfully published, the system shall commit a delivery job for its message and initiate asynchronous delivery to members in the audience snapshot. The committed job is not cancelled by a later Pause. | P0 |
| FR-MSG-04 | A member shall be able to view their in-app message list. | P0 |
| FR-MSG-05 | A member shall be able to open the message and navigate to its campaign landing page. | P0 |
| FR-MSG-06 | The system shall record message delivery, impression, and click events. | P0 |
| FR-MSG-07 | The system shall associate the message and its events with the campaign. | P0 |
| FR-MSG-08 | Message delivery shall be retryable and must not create duplicate inbox records for the same campaign/member. | P0 |
| FR-MSG-09 | An administrator shall be able to view delivery status, processed/total counts, cursor, retry count, and sanitized last error. | P0 |
| FR-MSG-10 | An administrator shall be able to manually retry a `FAILED` delivery job from its last committed cursor; retrying any other status shall be rejected. | P0 |

The MVP does not support scheduled delivery. The implementation processes inbox delivery asynchronously in batches. For the MVP, audience resolution and snapshot insertion use one database publication transaction (one set-based insert/select); the publish request does not create every inbox row. A resumable snapshot-job/state-machine design is deferred unless measured audience size makes the single snapshot transaction impractical.

**Delivery retry and recovery policy (MVP):** one worker processes one campaign job at a time in batches of 100 snapshot members. Each batch inserts inbox rows and advances `cursor_member_id` in the same transaction. On a batch failure, the transaction rolls back, the cursor remains unchanged, `attempt_count` increases by one, and the worker retries the same batch after a short exponential backoff (1 second, then 2 seconds, then 4 seconds). `attempt_count` counts automatic retries already consumed at the current cursor (0–3) and resets to zero after a successful batch. The initial attempt plus up to three automatic retries are allowed (four total attempts). After the initial failure, wait 1 second; after the second failed attempt, wait 2 seconds; after the third failed attempt, wait 4 seconds. If the fourth attempt fails, the job becomes `FAILED` and automatic retries stop. An administrator may use the delivery retry action to move a `FAILED` job back to `PENDING`; it preserves the last committed cursor and resets `attempt_count` to zero. A process restart recovers any `PROCESSING` job to `PENDING` because an uncommitted batch is rolled back; the single worker resumes after the last committed cursor. The worker must never re-evaluate audience rules or campaign status. An empty snapshot creates a `COMPLETED` job in the publication transaction.

The retry action is available only for `FAILED` jobs. `PENDING`, `PROCESSING`, and `COMPLETED` jobs cannot be manually restarted. The MVP has no retry scheduler, lease, multi-worker coordination, or dead-letter queue.

### 6.6 Member Campaign Landing Page

| ID | Requirement | Priority |
|---|---|---|
| FR-LP-01 | Each published campaign shall have a landing page rendered from one reusable fixed template; the MVP has no page builder or custom layout system. | P0 |
| FR-LP-02 | The landing page shall display fixed campaign content fields (`headline` and `body`), campaign name/description, campaign period, and associated coupons. | P0 |
| FR-LP-03 | The landing page shall display whether each coupon is available, unavailable, or already claimed by the current member. | P0 |
| FR-LP-04 | The landing page shall allow an eligible authenticated member to claim an available coupon. | P0 |
| FR-LP-05 | The system shall record landing-page views. | P0 |
| FR-LP-06 | The system shall display a clear success or failure result after a claim attempt. | P0 |
| FR-LP-07 | Public campaign landing-page content shall be readable without authentication. If a visitor attempts to claim a coupon while unauthenticated, the system shall direct them to sign in and then return them to the intended campaign page. | P0 |
| FR-LP-08 | A published campaign landing page remains publicly readable while the campaign is `PAUSED` or effectively `ENDED`; coupons are displayed as unavailable and new claims are blocked. | P0 |
| FR-LP-09 | If an authenticated visitor is not in the campaign audience snapshot, the page shall show that the member is not eligible; it shall not imply that stock availability means claim eligibility. | P0 |

### 6.7 Engagement Event Tracking

| ID | Requirement | Priority |
|---|---|---|
| FR-EVT-01 | The system shall report `DELIVERED` from successfully created `member_messages` inbox records; a duplicate `DELIVERED` row in `campaign_events` is not required. | P0 |
| FR-EVT-02 | The system shall record `IMPRESSION` when the message is visibly rendered. | P0 |
| FR-EVT-03 | The system shall record `CLICK` when a member clicks the message. | P0 |
| FR-EVT-04 | The system shall record `LANDING_PAGE_VIEW` when an authenticated member opens the campaign landing page; anonymous views are not attributed to a member. | P0 |
| FR-EVT-05 | The server shall record each authenticated claim command for an existing campaign that passes basic request-shape validation as a `CLAIM_ATTEMPT` audit event with outcome `SUCCEEDED`, `FAILED`, or `REPLAYED`; failed events include a safe business failure code. | P0 |
| FR-EVT-06 | The system shall associate events with campaign and member identifiers, and with the message or coupon where applicable. | P0 |
| FR-EVT-07 | The system shall prevent duplicate event submissions from inflating event and unique-member metrics. | P0 |
| FR-EVT-08 | The system shall distinguish total event counts from distinct-member counts. | P0 |

Event ingestion must be idempotent. Client-submitted impression/click events are accepted only when the authenticated member has the corresponding `member_messages` inbox record for that campaign/message. Client-submitted engagement events should include a client-generated event identifier or another deduplication key. Landing-page views may be recorded for any authenticated visitor; target landing visitor metrics count only members in the campaign audience snapshot. A successful coupon claim is a business transaction and the `coupon_claims` record is the source of truth; analytics events must never be used as the authority for whether a coupon was claimed. Claim-attempt audit events are generated by the server, not accepted from the client. A successful new claim writes its `CLAIM_ATTEMPT` audit event in the same transaction as the claim. A failed business attempt is recorded after the claim transaction rolls back, in a separate short transaction, so the failure audit survives the rollback. An idempotent replay is recorded as `REPLAYED`. Audit metadata may contain only the outcome, safe error code, and coupon ID; never tokens, raw idempotency keys, or sensitive member attributes.

### 6.8 Analytics and Reporting

| ID | Requirement | Priority |
|---|---|---|
| FR-ANA-01 | The administrator shall be able to view campaign-level performance. | P0 |
| FR-ANA-02 | The report shall include audience target count and delivered count. | P0 |
| FR-ANA-03 | The report shall include total impressions and unique reach. | P0 |
| FR-ANA-04 | The report shall include total clicks and unique clickers. | P0 |
| FR-ANA-05 | The report shall include landing-page views and unique landing-page visitors. | P0 |
| FR-ANA-06 | The report shall include total successful coupon claims and unique claimers. | P0 |
| FR-ANA-07 | The administrator shall be able to view claims and claimers by coupon. | P0 |
| FR-ANA-08 | The report shall clearly distinguish event counts from distinct-member counts. | P0 |

Because each campaign has exactly one message in the MVP, separate multi-message comparison and last-touch attribution are not required. Campaign-level message engagement metrics can be reported directly against that campaign's single message.

---

## 7. Analytics Definitions

### 7.1 Campaign-Level Metrics

| Metric | Definition |
|---|---|
| Target Count | Number of distinct members in the campaign's audience snapshot |
| Delivered Count | Number of successfully created member-message inbox records |
| Impression Count | Total recorded impression events |
| Unique Reach | Number of distinct members with at least one impression for the campaign |
| Click Count | Total recorded message-click events |
| Unique Clickers | Number of distinct members who clicked the campaign message |
| Landing Page Views | Total recorded campaign landing-page view events |
| Unique Landing Visitors | Number of distinct members in the campaign audience snapshot with at least one authenticated landing-page view |
| Coupon Claims | Total successful coupon claim records across campaign coupons |
| Unique Claimers | Number of distinct members with at least one successful claim in the campaign |
| Claim Attempt Count | Number of server-generated `CLAIM_ATTEMPT` audit events, including succeeded, failed, and replayed requests |
| Failed Claim Attempt Count | Number of `CLAIM_ATTEMPT` audit events with `metadata.outcome = FAILED` |

### 7.2 MVP Rates and Attribution Limits

The report shows the numerator and denominator alongside each rate:

- **Delivery Rate** = Delivered Members / Target Members
- **Reach Rate** = Unique Reach / Target Members
- **Click Rate** = Unique Clickers / Delivered Members
- **Target Landing Visitor Rate** = Unique Landing Visitors from the campaign audience snapshot / Target Members
- **Target Claim Rate** = Unique Claimers / Target Members

A unique landing visitor for this rate must be a member in the campaign's immutable audience snapshot with at least one authenticated `LANDING_PAGE_VIEW`. Anonymous page views are not counted. Direct visits are included, so the landing visitor rate is descriptive and is not a click-attribution conversion. The MVP does not report `Unique Landing Visitors / Unique Clickers` as a conversion rate. Claim counts and claimers come only from `coupon_claims`; claim attempts are reported separately as audit counts. If a denominator is zero, the rate is `null`/unavailable, not `0%`. Total event counts may be displayed separately.

### 7.3 Coupon-Level Reporting

For each coupon, report:

- Total configured quantity.
- Successful claim count.
- Remaining quantity.
- Unique claimers.
- Coupon Target Claim Rate = unique claimers for that coupon / campaign target count; show the numerator and denominator. This is audience penetration, not a checkout conversion rate.

Coupon claim count and unique claimers differ because a member may claim multiple different coupons in one campaign.

---

## 8. Business Rules

1. A campaign must have a valid start and end time. Campaign and coupon windows use start-inclusive/end-exclusive semantics: `starts_at <= now < ends_at`, interpreted as instants (stored in UTC; business display timezone is Asia/Taipei). An administrator may publish only within the campaign window (`starts_at <= now < ends_at`); scheduled publication of a future campaign is not supported.
2. A campaign must be published and within its active time window before a coupon can be claimed.
3. A paused or ended campaign must not accept new claims.
4. A member must be authenticated to access member-specific messages or claim a coupon.
5. A member must belong to the campaign's audience snapshot to claim its coupons.
6. The audience snapshot is created at publication and remains the campaign's eligibility source for that publication.
7. A campaign has exactly one in-app message in the MVP.
8. A campaign may contain multiple coupons.
9. A member may claim multiple different coupons in one campaign.
10. A member may claim a specific campaign coupon only once.
11. A coupon must have remaining inventory and be within its own validity period to be claimable.
12. Concurrent claim requests must never cause successful claims to exceed the coupon's configured quantity.
13. A failed claim must not create a successful coupon ownership record or consume inventory.
14. Message delivery does not count as an impression or unique reach.
15. Repeated impressions by the same member count as multiple impressions but one unique reach.
16. Campaign-level unique metrics must count each member only once per metric.
17. In-app message delivery begins immediately after successful campaign publication; inbox creation may finish asynchronously in batches. Scheduled delivery is not part of the MVP.
18. Successful publication commits a delivery job in the same transaction as the audience snapshot. Pausing a campaign does not cancel that committed job, even if it is still `PENDING` and the worker has not picked it up; the worker continues until the job completes. Already-created inbox messages are not retracted. Pause does not cancel or rewind delivery.
19. Claim and pause operations are serialized on the campaign row. A claim transaction obtains a shared row lock before checking campaign status and holds it through commit. The pause update obtains a conflicting row lock. A claim that acquired its lock first may complete before the pause commits; after the pause commits, new claims are rejected.
20. Coupon redemption and order-related behavior are outside the MVP.
21. A zero-member audience snapshot is valid for publication; the administrator receives a warning and the delivery job is `COMPLETED` with zero inbox rows.
22. A published campaign landing page remains publicly readable while paused or ended; coupon claim actions are unavailable in those states. A member outside the snapshot may view the public page but is shown an explicit not-eligible state.

---

## 9. Non-Functional Requirements

### 9.1 Correctness and Concurrency

- Coupon inventory must remain consistent under concurrent claim requests.
- Duplicate claim requests must not create duplicate successful claims.
- Database constraints must enforce critical uniqueness rules.
- Inventory updates and claim creation must occur within an appropriate transaction boundary.
- The claim API should support idempotent behavior for retried requests.
- Campaign publication and message delivery must be safe against retries and duplicate execution.

A suitable inventory approach is an atomic conditional database update, for example:

```sql
UPDATE campaign_coupons
SET claimed_quantity = claimed_quantity + 1
WHERE id = :campaignCouponId
  AND claimed_quantity < total_quantity;
```

The claim record should be inserted in the same database transaction. The implementation must handle uniqueness conflicts and roll back inventory changes if claim creation fails. Repeating a successful request with the same `(member_id, idempotency_key)` returns the original successful claim. Reusing that key for a different campaign/coupon request returns `IDEMPOTENCY_KEY_CONFLICT`; attempting an already-claimed coupon with a different key returns `CLAIM_ALREADY_EXISTS`.

### 9.2 Security

- Passwords must be stored using a secure password hashing algorithm.
- JWT access and refresh token handling must follow secure storage, expiration, renewal, and invalidation practices.
- Authenticated users must only access data they are authorized to view.
- Member identity must be derived from the authenticated principal.
- Administrative endpoints must enforce role-based access control.
- Input must be validated on the server.
- Sensitive authentication data must not be logged.

### 9.3 Performance and Scalability

- Campaign message delivery must support batch or asynchronous processing for larger audiences.
- Publishing a campaign should not require a single synchronous request to create every member inbox record.
- Analytics queries should avoid unnecessarily scanning large event histories for every page request.
- The architecture should allow future separation of event ingestion and reporting workloads if required.
- The MVP should remain deployable as a modular monolith; microservices are not required.

### 9.4 Reliability and Observability

- Message delivery failures must be traceable and safely retryable.
- Repeated delivery execution must not create duplicate member inbox records.
- Event ingestion should be idempotent.
- Important business operations should produce structured logs.
- The application should expose health-check endpoints.
- The system should record enough operational information to diagnose failed campaign publication, message delivery, and coupon claims.

### 9.5 Maintainability and Extensibility

- Use clear domain boundaries for Identity, Member, Campaign, Audience, Messaging, Coupon, Engagement, and Analytics.
- Keep the initial deployment architecture simple; a modular monolith is suitable for the MVP.
- Use Clean Architecture principles: domain and application rules should not depend directly on web frameworks, database implementation details, or external messaging providers.
- Keep channel-specific behavior behind an application-facing abstraction so email and push can be added later without rewriting campaign business rules.
- Do not build a generic plugin framework or implement future channels in the MVP.

---

## 10. Suggested Technical Architecture and Repository Structure

This section records the intended technical direction to guide implementation. Detailed class design, database schema, and deployment decisions may be documented in a separate Technical Design Document (TDD).

### 10.1 Architecture Style

- **Overall:** Clean Architecture principles with a Modular Monolith.
- **Backend:** Java and Spring Boot, using Spring Security for authentication and authorization.
- **Frontend:** React and TypeScript.
- **Database:** PostgreSQL.
- **Local development:** Docker Compose may be used to run the backend and database consistently.

The backend should be organized around business modules and dependency direction rather than allowing controllers, persistence entities, and domain rules to become tightly coupled.

### 10.2 Frontend Application Structure

The frontend code shall live in a dedicated top-level directory, separate from the backend. The MVP should provide two user experiences:

1. **Admin Portal:** campaign management, audience/segment management, coupon configuration, and analytics.
2. **Member Portal:** member authentication, in-app inbox, campaign landing page, coupon claiming, and claimed-coupon list.

The campaign landing page is a dedicated route and experience within the Member Portal; it does not need to be a separately deployed frontend application in the MVP.

Suggested repository layout:

```text
project-root/
├── backend/                 # Java / Spring Boot application
│   ├── src/
│   └── pom.xml
├── frontend/                # React + TypeScript application
│   ├── src/
│   │   ├── app/              # Routing and application setup
│   │   ├── features/
│   │   │   ├── admin/        # Admin Portal features
│   │   │   ├── auth/         # Shared authentication UI/state
│   │   │   ├── campaigns/    # Campaign landing page and campaign views
│   │   │   ├── messages/     # Member inbox
│   │   │   └── coupons/      # Coupon claim and claimed-coupon views
│   │   ├── shared/           # Shared components and utilities
│   │   └── api/              # Backend API client
│   ├── package.json
│   └── ...
├── docker-compose.yml
├── README.md
└── PRD.md
```

The exact frontend folder organization may evolve during implementation, but frontend source code must remain clearly separated from the Spring Boot backend. A single React application with route-level separation is sufficient for the MVP; separate frontend deployments are not required.

Suggested route groups:

- Admin: `/admin`, `/admin/campaigns`, `/admin/campaigns/:campaignId`, `/admin/segments`, `/admin/campaigns/:campaignId/analytics`
- Member: `/member/login`, `/member/messages`, `/member/coupons`
- Campaign landing page: `/campaigns/:campaignId`

### 10.3 Backend Logical Modules

Suggested logical modules:

- `identity`: authentication, JWT issuance/refresh, roles, authorization.
- `member`: member profile and seeded member data.
- `campaign`: campaign lifecycle and publication.
- `audience`: rule definitions, reusable segments, audience preview, and publication snapshot.
- `messaging`: in-app message definition, delivery, inbox, and channel abstraction.
- `coupon`: coupon configuration, inventory, and claims.
- `engagement`: impression, click, and landing-page event ingestion.
- `analytics`: campaign and coupon reporting.

### 10.4 Conceptual Layering

- **Presentation:** REST controllers, request/response DTOs, authentication filters, and validation.
- **Application:** use cases, orchestration, transaction boundaries, and ports/interfaces.
- **Domain:** business entities, value objects, and business rules.
- **Infrastructure:** PostgreSQL persistence, Spring/JPA adapters, token storage, and delivery adapters.

Domain rules should not depend directly on Spring MVC, JPA/Hibernate, PostgreSQL, or a future messaging vendor SDK.

---

## 10.1 Additional v1.5 implementation rules

- Audience field/operator compatibility is defined by the normative matrix in Section 4.2.
- Delivery allows one initial attempt plus three automatic retries per cursor/batch, with 1s/2s/4s backoff; a fourth total failure marks the job `FAILED`. Manual retry is allowed only from `FAILED` and resumes from the last committed cursor.
- Refresh-token records represent individual token generations; `token_family_id` identifies one browser login session.
- `member_messages` and `campaign_events` must enforce campaign/message/coupon ownership with composite foreign keys.
- `coupon_claims` remains the only source of truth for successful coupon ownership. Server-generated claim audit events record outcomes but never drive claim metrics.
- A zero-member audience is publishable and completes delivery immediately. Published paused/ended landing pages remain publicly readable but do not allow new claims.

## 11. Suggested Conceptual Data Model

This is a logical starting point, not a mandatory physical schema.

| Entity | Purpose | Important relationships |
|---|---|---|
| `users` | Shared identity and authentication accounts for both administrators and members; `role` distinguishes `ADMIN` and `MEMBER` | Creates/manages campaigns when role is `ADMIN`; may have one member profile when role is `MEMBER` |
| `members` | Member-only profile and audience attributes | One-to-one with `users`; one member can receive messages and claim coupons |
| `audience_segments` | Reusable segment definitions and rules | Can be selected by many campaigns; records the creating user |
| `campaigns` | Campaign definition and lifecycle | Has one audience snapshot, one message, and multiple campaign-owned coupons; records the creating user |
| `campaign_audience_members` | Resolved audience snapshot | Joins campaigns and members; unique campaign/member pair |
| `campaign_coupons` | Coupon/offer definition configured directly within a campaign; no separate global coupon master in the MVP | Belongs to one campaign; stores discount type/value, optional minimum order amount, quantity, and validity; the one-claim-per-member rule is enforced by a unique constraint |
| `coupon_claims` | Successful member coupon claims | References campaign, campaign coupon, and member; unique campaign/campaign-coupon/member combination |
| `campaign_messages` | The campaign's single in-app message definition | One-to-one with campaign in the MVP |
| `campaign_delivery_jobs` | Progress of the one asynchronous delivery job for a campaign | One-to-one with campaign; stores status, cursor, attempts, and last error |
| `member_messages` | Per-member message delivery/inbox record | References campaign message and member; unique campaign/member delivery |
| `campaign_events` | Impression, click, landing-page, and claim-attempt/failure events | References campaign, member, and relevant message/coupon identifiers; delivery and successful claims are derived from their authoritative tables |
| `refresh_token_sessions` | Refresh-token lifecycle and revocation | References the shared `users` identity/session |

### 11.1 Important Data Integrity Rules

- Unique campaign/member association in the audience snapshot.
- One campaign message per campaign in the MVP (`UNIQUE(campaign_id)`).
- Unique campaign/member message delivery to prevent duplicate inbox records.
- Unique campaign/coupon/member successful claim.
- Unique deduplication key for client-submitted engagement events.
- Inventory updates and claim creation must be atomic.
- Use foreign keys and indexes for campaign, member, message, coupon, and event lookups.

---

## 12. Suggested API Capabilities

Exact endpoint paths and payloads are defined in `docs/api/API_SPECIFICATION.md` and `docs/api/openapi.yaml`; these files are the implementation contract and must remain synchronized.

### Admin APIs

- Sign in, refresh token, and log out.
- Create, update, list, and retrieve campaigns.
- Publish and pause campaigns.
- Create, update, preview, list, and retrieve audience segments.
- Preview campaign-specific audience rules.
- Add, update, and list campaign coupons.
- Configure the campaign's single in-app message.
- Retrieve campaign analytics and coupon-level claim statistics.

### Member APIs

- Sign in, refresh token, and log out.
- Retrieve the authenticated member profile.
- List the member's in-app messages.
- Record message impression and click events.
- Retrieve campaign landing-page content.
- Retrieve campaign coupons and member-specific claim status.
- Claim a campaign coupon.
- List the member's claimed coupons.
- Record landing-page view events.

### API Behavior

- Member-specific endpoints must derive the member ID from authentication.
- Claim APIs must return clear results for success, already claimed, ineligible member, inactive campaign, expired coupon, and sold-out coupon.
- Event APIs should accept an idempotency/deduplication identifier.
- APIs should use consistent error response formats.
- Refresh-token rotation, token-family reuse detection, and family revocation are required as defined in `docs/architecture/SECURITY_DESIGN.md`.

---

## 13. Acceptance Criteria

The MVP is functionally complete when the following end-to-end scenarios pass.

### Scenario 1: Campaign Creation

**Given** an authenticated administrator  
**When** the administrator creates a campaign with valid dates, an audience definition, at least one coupon with positive quantity, and one in-app message  
**Then** the campaign is saved as a draft and can be published.

### Scenario 2: Reusable Audience Segment

**Given** seeded member data and an administrator  
**When** the administrator creates and saves a segment using supported member attributes and AND conditions  
**Then** the segment can be previewed and selected for a campaign.

### Scenario 3: Audience Rule Composition and NULL Semantics

**Given** a campaign with both a saved segment and campaign-specific rules
**When** the system previews or snapshots its audience
**Then** only members matching the saved segment AND all campaign-specific conditions are included. A NULL-valued member attribute does not match a supported comparison/set condition.

### Scenario 4: Campaign Publication Start-Time Guard

**Given** a draft campaign whose `starts_at` is in the future
**When** an administrator attempts to publish it
**Then** publication is rejected with `CAMPAIGN_NOT_PUBLISHABLE` and no audience snapshot or delivery job is created.

**When** the campaign start time has arrived
**Then** the campaign may proceed through normal publication validation.

### Scenario 5: Audience Snapshot at Publication

**Given** a valid campaign with an audience definition  
**When** the administrator publishes the campaign  
**Then** the system resolves and persists the matching member IDs, and later changes to member attributes do not silently change that campaign's audience snapshot.

### Scenario 6: Immediate Message Delivery

**Given** a valid published campaign with a resolved audience snapshot  
**When** the campaign is published  
**Then** the system initiates immediate in-app message delivery to the snapshot members and creates no more than one inbox delivery per campaign/member.

### Scenario 7: Message Impression and Click

**Given** a member with an available campaign message  
**When** the message is visibly rendered  
**Then** an impression event is recorded.  
**When** the member clicks the message  
**Then** a click event is recorded and the member can navigate to the campaign landing page.

### Scenario 8: Public Landing-Page View

**Given** a published campaign
**When** an anonymous visitor opens its landing page
**Then** the fixed campaign content and public coupon summaries are displayed without exposing member-specific claim state.

**When** an authenticated member opens the landing page
**Then** the page may also display that member's claim state and records a member-attributed landing-page view. Anonymous views are not written to `campaign_events`, whose `member_id` is required.

### Scenario 9: Authentication Required for Coupon Claim

**Given** an anonymous visitor on a public campaign landing page
**When** the visitor attempts to claim a coupon
**Then** the application requires sign-in and returns the visitor to the same campaign page after successful login.

### Scenario 10: Successful Coupon Claim

**Given** an eligible member and a claimable coupon with remaining inventory  
**When** the member claims the coupon  
**Then** the system creates one successful claim record, updates remaining inventory by one, and returns a success response.

### Scenario 11: Duplicate Claim Prevention

**Given** a member who has already successfully claimed a specific campaign coupon and the campaign is otherwise claimable  
**When** the member attempts to claim the same campaign coupon again with a different idempotency key  
**Then** the system rejects the duplicate attempt and does not consume additional inventory.

### Scenario 12: Concurrent Inventory Protection

**Given** a coupon with a limited remaining quantity  
**When** more unique members submit concurrent claim requests than the remaining quantity  
**Then** successful claims never exceed the configured quantity, inventory never becomes negative, and the inventory count agrees with successful claim records.

### Scenario 13: Audience Eligibility

**Given** a member who is not in the campaign's audience snapshot  
**When** the member attempts to claim a campaign coupon  
**Then** the system rejects the request and creates no successful claim record.

### Scenario 14: Campaign Analytics

**Given** a campaign with delivery, impression, click, landing-page, and claim activity  
**When** an administrator views campaign analytics  
**Then** the report displays total events and distinct-member metrics separately.

### Scenario 15: Pause During Message Delivery

**Given** an active campaign whose asynchronous message delivery job has already started
**When** an administrator pauses the campaign
**Then** the committed delivery job continues to completion even if its status is still `PENDING` when Pause commits; existing inbox messages remain. Claims already holding the campaign shared lock may complete before Pause commits, while claims beginning after Pause commits are rejected.

### Scenario 16: Authentication and Authorization

**Given** an authenticated member  
**When** the member attempts to access an administrator-only endpoint or another member's private data  
**Then** the system denies access.

---

### Scenario 17: Zero-member audience publication

**Given** a valid draft campaign with one message, at least one positive-quantity coupon, and a valid audience definition that currently matches zero members
**When** the administrator publishes the campaign within its campaign window
**Then** publication succeeds with audience count zero, the snapshot contains no rows, the delivery job is immediately `COMPLETED`, and no member inbox rows are created

### Scenario 18: Delivery retry and manual recovery

**Given** a delivery batch fails
**When** the initial attempt and all three automatic retries fail (four total attempts)
**Then** the job becomes `FAILED`, its cursor remains at the last committed member, and automatic retries stop; an administrator may retry it, resetting the retry count and resuming from that cursor without duplicate inbox rows

### Scenario 19: Paused or ended landing page

**Given** a published campaign is paused or has reached its end time
**When** an anonymous visitor or member opens its landing page
**Then** public content remains readable, coupon claim actions are unavailable, and an authenticated member outside the audience snapshot is explicitly shown as not eligible

### Scenario 20: Refresh token reuse detection

**Given** a refresh token has been successfully rotated
**When** the old token is presented again
**Then** the request is rejected and all active refresh tokens in that token family are revoked


---

### Scenario 21: Claim attempt audit

**Given** an authenticated member submits a claim command for an existing campaign
**When** the claim succeeds, fails a business validation, or replays an existing successful idempotent claim
**Then** the server records one `CLAIM_ATTEMPT` audit event with outcome `SUCCEEDED`, `FAILED`, or `REPLAYED`; failed audit survives rollback, and only `coupon_claims` determines successful claim counts.

## 14. MVP Release Boundary

The MVP is complete when this end-to-end journey works:

1. An administrator signs in and creates a campaign.
2. The administrator defines or selects a reusable rule-based audience segment and may add campaign-specific conditions; when both are present, both must match (AND).
3. The administrator configures multiple coupons and exactly one in-app message.
4. The system snapshots the audience at publication and immediately initiates message delivery.
5. A member signs in, views the message, and clicks it.
6. The member opens the campaign landing page and claims an eligible coupon.
7. The system safely enforces coupon inventory, audience eligibility, and duplicate-claim rules.
8. The administrator reviews campaign and coupon performance.

The product does not need to support coupon redemption or order processing to be considered complete.

---

## 15. Assumptions and Decisions

| Topic | MVP decision |
|---|---|
| Architecture | Clean Architecture principles with a Modular Monolith |
| Backend | Java and Spring Boot |
| Frontend | React and TypeScript; frontend code in a dedicated top-level `frontend/` directory |
| Frontend experiences | Admin Portal and Member Portal in one React application; campaign landing page is a Member Portal route |
| Database | PostgreSQL |
| Authentication | Spring Security with JWT Access Token and Refresh Token |
| Delivery channel | In-app message center only |
| Number of messages | Exactly one in-app message per campaign |
| Message timing | Publication commits a delivery job; the worker processes it asynchronously in batches. Pause does not cancel the committed job, including while it is still pending; scheduling is a future enhancement |
| Number of coupons | Multiple coupons per campaign |
| Claim limit | One successful claim per member per campaign coupon |
| Audience definition | At least one of saved segment or campaign-specific rules is required; if both are supplied, combine with AND |
| Audience operators | Comparison and set operators; multiple conditions combined with AND |
| Audience evaluation | Snapshot of matching members at campaign publication |
| Sample data | Seeded members with age, gender, registration date, tier, order count, spending, last order date, and last login date |
| Landing page | One reusable fixed template per campaign with `headline` and `body`; no page builder or custom layouts |
| Coupon redemption | Out of scope |
| Analytics | Delivery, impression, click, landing-page view, claim attempt/result, and successful claim metrics |
| Attribution | No multi-message attribution model required because each campaign has one message |
| External integrations | Out of scope for MVP; channel abstraction should permit future extensions |
| Cross-device attribution | Not a required MVP capability |
| Time zone | Campaign schedules use `Asia/Taipei` as the business time zone; instants are stored in UTC |

---

## 16. Recommended Implementation Approach

Implementation should prioritize a working, demonstrable end-to-end product while preserving the agreed MVP requirements.

The authoritative implementation sequence, phase dependencies, deliverables, and exit criteria are defined in `docs/development/IMPLEMENTATION_PLAN.md`. That plan is normative; this PRD does not define a competing phase order.

The implementation should demonstrate sound boundaries and data correctness without introducing microservices, external messaging providers, scheduled delivery, Redis, Kafka, or infrastructure that is not necessary for the agreed MVP.

---

## 17. Document Status and Change Control

This document is **Draft** and remains under review. It is not yet the final implementation baseline.

The PRD will be marked **Ready for Development** only after the product scope, user journeys, business rules, acceptance criteria, and synchronized technical contracts have been reviewed and approved. The v1.5.0 PRD, API/OpenAPI, Database/ERD, Architecture/Module Design, Security Design, Testing Strategy, ADRs, and Implementation Plan together form the Development Baseline; no separate TDD is required to start the MVP unless a later requirement introduces a genuinely unresolved design area.
