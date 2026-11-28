# Backend Module Design

**Status:** Approved — part of the owner-approved v1.5.0 Development Baseline

## 1. Module map

| Module       | Owns                                                                      | Public application capabilities                                                                 | Must not own                                 |
|--------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|----------------------------------------------|
| `identity`   | Admin/member credentials, roles, refresh sessions                         | Login, refresh, logout, current principal                                                       | Campaign or coupon business rules            |
| `member`     | Member profile and seeded targeting attributes                            | Member profile lookup, member administration/seed access                                        | Authentication token lifecycle               |
| `audience`   | Rule definitions, reusable segments, evaluation, snapshots                | Preview audience, save segment, resolve/snapshot audience                                       | Campaign lifecycle decisions                 |
| `campaign`   | Campaign details, status, publication lifecycle                           | Create/update/list/publish/pause campaign, campaign read model                                  | Coupon inventory mutation                    |
| `messaging`  | One campaign message, member inbox, delivery jobs and retry state         | Configure campaign message, list inbox, inspect delivery, initiate delivery, retry a failed job | Campaign audience rule evaluation            |
| `coupon`     | Campaign coupon configuration, inventory, claims                          | Add/update coupons, claim coupon, list member claims                                            | Analytics event counts as ownership truth    |
| `engagement` | Client event validation/deduplication and server-side claim audit records | Record impression/click/page-view events and claim-attempt audit                                | Coupon claim transaction and claim ownership |
| `analytics`  | Read models and metric aggregation                                        | Campaign and coupon reports                                                                     | Write-side ownership of campaign/claim data  |

## 2. Dependency rules

1. A module exposes application-level interfaces/use cases, not its persistence entities.
2. Other modules call those interfaces; they do not query another module's repository directly.
3. Domain objects do not depend on Spring or persistence annotations.
4. Infrastructure adapters implement ports defined by application/domain layers.
5. Cross-module events or explicit application calls are preferred over reaching into another module's tables.
6. Shared code is limited to technical primitives (IDs, clock abstraction, pagination, error envelope); do not create a
   large shared business module.

## 3. Main use cases

### Identity

- `AuthenticateUser`
- `RefreshSession`
- `LogoutSession`
- `GetCurrentPrincipal`

### Audience

- `CreateAudienceSegment`
- `UpdateAudienceSegment`
- `PreviewAudience`
- `ResolveCampaignAudience`
- `PersistAudienceSnapshot`

### Campaign

- `CreateCampaign`
- `UpdateDraftCampaign`
- `GetCampaign`
- `ListCampaigns`
- `PublishCampaign`
- `PauseCampaign`

### Messaging

- `ConfigureCampaignMessage`
- `InitiateCampaignDelivery`
- `DeliverNextBatch`
- `ListMemberMessages`
- `RecordMessageImpression`
- `RecordMessageClick`

### Coupon

- `ConfigureCampaignCoupon`
- `GetCampaignCoupons`
- `ClaimCoupon`
- `ListMemberClaims`

### Engagement and analytics

- `RecordLandingPageView`
- `RecordEngagementEvent`
- `RecordClaimAttemptAudit` (invoked by coupon claim orchestration; server-side only)
- `GetCampaignAnalytics` (returns campaign summary and its coupon-level summaries in one bounded read model)

## 4. Publication orchestration

`PublishCampaign` is an application use case coordinating campaign, audience, and messaging capabilities. It should not
become a controller-level script.

Recommended sequence:

1. Load and validate draft campaign.
2. Ask audience module to resolve matching member IDs: when both a saved segment and campaign-specific rules are
   supplied, combine them with AND; otherwise use the supplied definition. NULL attribute values never satisfy a
   comparison/set condition.
3. Resolve and persist the complete immutable campaign audience snapshot in the publication transaction (one set-based
   operation for the MVP).
4. Validate exactly one campaign message and at least one coupon with `total_quantity >= 1` exist, as required by the
   PRD MVP acceptance scenario.
5. Transition campaign to `ACTIVE` and insert one delivery job/outbox record in the same transaction as the snapshot.
6. One worker handles delivery asynchronously in batches of 100; inbox inserts and cursor advancement are atomic per
   batch. It allows an initial attempt plus three automatic retries after 1s/2s/4s backoff; a fourth total failure at
   the current cursor marks the job `FAILED`. Admin may retry only a `FAILED` job; it resumes from the last committed
   cursor. A committed job continues even if the campaign is paused while the job is still `PENDING`; the worker does
   not gate processing on campaign status. A zero-member snapshot creates a `COMPLETED` job. Multi-worker lease
   coordination is deferred.

For the MVP, do not introduce a snapshot-job table or publication state machine. The initial deployment assumes the
complete snapshot fits one publication transaction. If measured scale disproves that assumption, stop and propose a
separate reviewed design; never allow delivery from a partial snapshot.

## 5. Coupon claim transaction

`ClaimCoupon` owns the transaction. It first replays an existing successful result by `(member_id, idempotency_key)` (or
returns an idempotency conflict) before evaluating current eligibility. For a new claim, it locks the campaign row with
`SELECT ... FOR SHARE`, re-checks the key to cover concurrent requests, then coordinates eligibility checks and a
repository operation that atomically reserves one unit of inventory. The inventory update and claim insert must commit
or roll back together. The database unique constraint is the final duplicate-claim guard. Claim transactions acquire a
shared lock on the campaign row before checking status and hold it through commit; `PauseCampaign` updates the same row,
serializing pause against in-flight claims. Lock order is campaign row before coupon inventory row.

## 6. Cross-module contracts

- Identity exposes an authenticated principal containing stable user ID and role; member-specific use cases resolve the
  associated member ID server-side.
- Campaign exposes campaign status, active-window, and campaign ownership information to coupon/landing-page use cases.
- Audience exposes a membership check against the persisted campaign snapshot.
- Messaging exposes delivery initiation and inbox read operations.
- Coupon exposes claim and claim-list operations; analytics reads claim records through an application query port.
- Engagement exposes validated event records and a `ClaimAttemptAuditPort`; analytics reads event records through query
  ports. Coupon owns the claim transaction and invokes the audit port in the same transaction for a successful claim;
  after a failed claim rolls back, it invokes the audit port in a separate short transaction.

## 7. Suggested package layout

```text
com.example.campaign/
  identity/
    domain/ application/ infrastructure/ presentation/
  member/
  audience/
  campaign/
  messaging/
  coupon/
  engagement/
  analytics/
  shared/
```

Within each module, use only the layers needed. Avoid empty abstractions and excessive interfaces for trivial code;
ports are most valuable at infrastructure boundaries and meaningful module boundaries.
