# Backend Module Design

**Status:** Proposed — requires owner review

## 1. Module map

| Module | Owns | Public application capabilities | Must not own |
|---|---|---|---|
| `identity` | Admin/member credentials, roles, refresh sessions | Login, refresh, logout, current principal | Campaign or coupon business rules |
| `member` | Member profile and seeded targeting attributes | Member profile lookup, member administration/seed access | Authentication token lifecycle |
| `audience` | Rule definitions, reusable segments, evaluation, snapshots | Preview audience, save segment, resolve/snapshot audience | Campaign lifecycle decisions |
| `campaign` | Campaign details, status, publication lifecycle | Create/update/list/publish/pause campaign, campaign read model | Coupon inventory mutation |
| `messaging` | One campaign message, member inbox, delivery attempts | Configure campaign message, list inbox, initiate/retry delivery | Campaign audience rule evaluation |
| `coupon` | Campaign coupon configuration, inventory, claims | Add/update coupons, claim coupon, list member claims | Analytics event counts as ownership truth |
| `engagement` | Client event validation and deduplication | Record impression/click/page-view events | Coupon claim transaction |
| `analytics` | Read models and metric aggregation | Campaign and coupon reports | Write-side ownership of campaign/claim data |

## 2. Dependency rules

1. A module exposes application-level interfaces/use cases, not its persistence entities.
2. Other modules call those interfaces; they do not query another module's repository directly.
3. Domain objects do not depend on Spring or persistence annotations.
4. Infrastructure adapters implement ports defined by application/domain layers.
5. Cross-module events or explicit application calls are preferred over reaching into another module's tables.
6. Shared code is limited to technical primitives (IDs, clock abstraction, pagination, error envelope); do not create a large shared business module.

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
- `GetCampaignAnalytics`
- `GetCouponAnalytics`

## 4. Publication orchestration

`PublishCampaign` is an application use case coordinating campaign, audience, and messaging capabilities. It should not become a controller-level script.

Recommended sequence:

1. Load and validate draft campaign.
2. Ask audience module to resolve the matching member IDs using the campaign's selected saved segment and/or campaign-specific conditions.
3. Resolve and persist the complete immutable campaign audience snapshot in the publication transaction (one set-based operation for the MVP).
4. Validate exactly one campaign message and at least one valid coupon exist, as required by the PRD MVP acceptance scenario.
5. Transition campaign to `ACTIVE` and insert one delivery job/outbox record in the same transaction as the snapshot.
6. One worker handles delivery asynchronously in bounded batches; inbox inserts and cursor advancement are atomic per batch. Multi-worker lease coordination is deferred.

For the MVP, do not introduce a snapshot-job table or publication state machine. The initial deployment assumes the complete snapshot fits one publication transaction. If measured scale disproves that assumption, stop and propose a separate reviewed design; never allow delivery from a partial snapshot.

## 5. Coupon claim transaction

`ClaimCoupon` owns the transaction. It coordinates eligibility checks and a repository operation that atomically reserves one unit of inventory. The inventory update and claim insert must commit or roll back together. The database unique constraint is the final duplicate-claim guard.

## 6. Cross-module contracts

- Identity exposes an authenticated principal containing stable user ID and role; member-specific use cases resolve the associated member ID server-side.
- Campaign exposes campaign status, active-window, and campaign ownership information to coupon/landing-page use cases.
- Audience exposes a membership check against the persisted campaign snapshot.
- Messaging exposes delivery initiation and inbox read operations.
- Coupon exposes claim and claim-list operations; analytics reads claim records through an application query port.
- Engagement exposes validated event records; analytics reads those records through query ports.

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

Within each module, use only the layers needed. Avoid empty abstractions and excessive interfaces for trivial code; ports are most valuable at infrastructure boundaries and meaningful module boundaries.
