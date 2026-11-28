# Phase 9 Plan — Campaign Analytics and Coupon Reporting

**Document status:** OWNER-APPROVED — IMPLEMENTATION AUTHORIZED. **Planning date:** 2026-11-28. **Preceding phase:**
Phase 8 formally accepted by the owner on 2026-11-28. **Implementation status:** COMPLETE — FORMALLY OWNER-ACCEPTED
(2026-11-28). Owner approval was explicitly
provided on 2026-11-28 with the instruction: “I approve Phase 9 plan. Start implementation strictly following related
docs.”

## 1. Objective

Deliver the MVP Admin campaign analytics experience already required by the approved v1.5.0 PRD. An administrator can
open a campaign report and understand its target audience, message delivery, engagement, landing-page activity,
successful coupon claims, claim-attempt audit outcomes, and per-coupon claim performance. Counts of events/records and
counts of distinct members must be displayed separately, with explicit numerator/denominator values for rates.

Phase 9 is reporting only. It must not change publication, delivery, engagement ingestion, audience snapshots, coupon
claim behavior, or claim-audit semantics accepted in Phases 6–8.

## 2. Authoritative sources and precedence

This plan is subordinate to, and must preserve:

1. `docs/PRD.md` Sections 6.8 (FR-ANA-01–08), 7.1–7.3 (metric definitions and rates), 8 (business rules), 10.2–10.3
   (Admin route and analytics module), and Scenario 14 (Campaign Analytics).
2. `docs/database/DATABASE_DESIGN.md`, `docs/database/ERD.md`, and the already-applied V1 schema for authoritative
   record relationships and constraints.
3. `docs/api/API_SPECIFICATION.md`, `docs/api/openapi.yaml`, and `docs/api/ERROR_CODES.md` for API conventions. The
   proposed analytics read endpoint below is new and requires owner approval before implementation.
4. `docs/architecture/ARCHITECTURE.md`, `docs/architecture/MODULE_DESIGN.md`, and
   `docs/architecture/SECURITY_DESIGN.md` for module boundaries and ADMIN authorization.
5. `docs/development/TESTING_STRATEGY.md` and `docs/development/IMPLEMENTATION_PLAN.md` for verification and phase
   gates.
6. Accepted Phase 6, 7, and 8 implementation reports for claim, delivery, and event-ingestion behavior.

If a discrepancy is found between the PRD, existing code, and stored data, stop and document it. Do not silently
redefine
a metric or alter an accepted contract.

## 3. Scope

### 3.1 In scope

**Campaign-level report**

- Target Members / Target Count.
- Delivered Members / Delivered Count.
- Delivery Rate.
- Impression Count and Unique Reach.
- Reach Rate.
- Click Count and Unique Clickers.
- Click Rate.
- Landing Page Views and Unique Landing Visitors.
- Target Landing Visitor Rate.
- Successful Coupon Claims and Unique Claimers.
- Target Claim Rate.
- Claim Attempt Count and Failed Claim Attempt Count; the UI may additionally break attempts down by
  `SUCCEEDED`, `FAILED`, and `REPLAYED` because those are the existing audit outcomes.
- Show count values separately from distinct-member values. For every rate, show the numerator and denominator, not only
  a percentage.

**Coupon-level report**

For every campaign coupon, show:

- Coupon name (and identifier where useful).
- Configured quantity (`total_quantity`).
- Successful claim count (`coupon_claims` rows for that coupon).
- Remaining quantity (`total_quantity - claimed_quantity`, with the persisted counter also checked for consistency).
- Unique claimers (`COUNT(DISTINCT member_id)`).
- Coupon Target Claim Rate = unique claimers for that coupon / campaign target count, with numerator and denominator.

**Admin UI**

- Add the PRD route `/admin/campaigns/:campaignId/analytics` within the existing React SPA.
- Add a clear entry point from the existing Campaign management UI for campaigns that can be reported.
- Provide loading, empty/no-activity, unavailable/not-found, authorization, and retryable error states.
- Keep the report read-only; no mutation, export, charting library, or cross-campaign dashboard is required for this
  phase.

### 3.2 Explicitly out of scope

- Cross-campaign portfolio dashboards, date-range filtering, time-series charts, cohort analysis, attribution models,
  and
  click-to-landing conversion.
- Revenue, orders, checkout conversion, coupon redemption, refunds, or payment metrics.
- CSV/PDF export, scheduled reports, report snapshots, caching infrastructure, background aggregation jobs, or a data
  warehouse.
- New analytics event types, client tracking, anonymous attribution, fingerprinting, or changes to Phase 8 event
  ingestion.
- Changes to Phase 6 claim transaction/idempotency/audit semantics or Phase 7 delivery behavior.
- New tables/materialized views or Flyway migration by default. A migration/index is permitted only if query-plan and
  representative-volume evidence demonstrates a material need and the owner approves it; never edit an applied V1/V2
  migration.
- New roles or changes to the existing ADMIN/MEMBER security model.

## 4. Metric definitions — authoritative PRD semantics

All metrics are campaign-lifetime totals for the MVP. No date filter is introduced in this phase. Use the following
sources and rules exactly:

| Metric                      | Source / calculation                                                                                                      |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| Target Members              | `COUNT(*)` from `campaign_audience_members` for the campaign (one row per target member).                                 |
| Delivered Members           | `COUNT(*)` from `member_messages` for the campaign. Do not use delivery jobs or synthetic events.                         |
| Delivery Rate               | Delivered Members / Target Members.                                                                                       |
| Impression Count            | Count `campaign_events` rows with `event_type = IMPRESSION`.                                                              |
| Unique Reach                | Distinct `member_id` among campaign IMPRESSION events.                                                                    |
| Reach Rate                  | Unique Reach / Target Members.                                                                                            |
| Click Count                 | Count `campaign_events` rows with `event_type = CLICK`.                                                                   |
| Unique Clickers             | Distinct `member_id` among campaign CLICK events.                                                                         |
| Click Rate                  | Unique Clickers / Delivered Members.                                                                                      |
| Landing Page Views          | Count `campaign_events` rows with `event_type = LANDING_PAGE_VIEW`.                                                       |
| Unique Landing Visitors     | Distinct members with a LANDING_PAGE_VIEW who are also in this campaign's immutable `campaign_audience_members` snapshot. |
| Target Landing Visitor Rate | Unique Landing Visitors / Target Members. This is descriptive audience penetration, not click attribution.                |
| Coupon Claims               | Count successful `coupon_claims` rows for the campaign. Never infer success from `CLAIM_ATTEMPT` events.                  |
| Unique Claimers             | Distinct members with at least one successful `coupon_claims` row in the campaign.                                        |
| Target Claim Rate           | Unique Claimers / Target Members.                                                                                         |
| Claim Attempt Count         | Count server-generated `campaign_events` rows with `event_type = CLAIM_ATTEMPT`, including all allowed outcomes.          |
| Failed Claim Attempt Count  | Claim-attempt rows whose `metadata.outcome = FAILED`.                                                                     |

For coupon-level values, scope `coupon_claims` to both campaign and coupon. A member may claim different coupons in one
campaign; therefore the sum of per-coupon unique claimers can exceed campaign-level Unique Claimers. Remaining quantity
is `total_quantity - claimed_quantity`; the implementation must verify that the counter agrees with successful claim
rows and surface an integrity error rather than silently masking a mismatch.

**Rate handling:** if the denominator is zero, return `null` (unavailable), never `0%`. Return raw numerator and
denominator alongside each rate. Do not cap or otherwise cosmetically alter ratios; display formatting belongs to the
UI.

**Deduplication and timestamps:** rely on the existing `(member_id, event_id)` uniqueness constraint; do not deduplicate
again using a different rule. The PRD defines counts by persisted event records, not by client `occurred_at` windows.
`received_at` may be used for future time slicing, but no time slicing is included now.

**No attribution:** do not calculate `Unique Landing Visitors / Unique Clickers`, or describe any landing-page metric as
a
click-through conversion. Direct authenticated visits are included. Anonymous visits are absent by design.

## 5. API contract (owner-approved for implementation)

Proposed read-only endpoint:

`GET /api/v1/admin/campaigns/{campaignId}/analytics`

- ADMIN only; existing cookie authentication and security conventions apply. MEMBER and anonymous requests are denied.
- No request body and no query parameters in the MVP.
- Return one campaign summary object and a deterministic list of coupon summaries.
- Reuse the repository's standard success/error envelope and existing not-found behavior for an unknown campaign.
- Do not expose member-level identifiers, raw events, event metadata, raw claim rows, idempotency keys, or personal
  data.

Proposed response shape (illustrative field names; finalize in OpenAPI after owner approval):

```json
{
  "campaignId": "uuid",
  "campaignName": "string",
  "targetMembers": 0,
  "deliveredMembers": 0,
  "deliveryRate": { "numerator": 0, "denominator": 0, "value": null },
  "impressionCount": 0,
  "uniqueReach": 0,
  "reachRate": { "numerator": 0, "denominator": 0, "value": null },
  "clickCount": 0,
  "uniqueClickers": 0,
  "clickRate": { "numerator": 0, "denominator": 0, "value": null },
  "landingPageViews": 0,
  "uniqueLandingVisitors": 0,
  "targetLandingVisitorRate": { "numerator": 0, "denominator": 0, "value": null },
  "couponClaims": 0,
  "uniqueClaimers": 0,
  "targetClaimRate": { "numerator": 0, "denominator": 0, "value": null },
  "claimAttemptCount": 0,
  "failedClaimAttemptCount": 0,
  "claimAttemptsByOutcome": { "succeeded": 0, "failed": 0, "replayed": 0 },
  "coupons": [
    {
      "couponId": "uuid",
      "name": "string",
      "totalQuantity": 0,
      "successfulClaims": 0,
      "remainingQuantity": 0,
      "uniqueClaimers": 0,
      "targetClaimRate": { "numerator": 0, "denominator": 0, "value": null }
    }
  ]
}
```

The response shape above is the approved bounded contract and is finalized in `API_SPECIFICATION.md` and `openapi.yaml`.
The implementation uses one endpoint with coupon summaries embedded in `coupons`; it does not add a separate coupon
analytics endpoint. Any material deviation requires renewed approval.

## 6. Backend design and query safety

- Add an `analytics` module with application, infrastructure, and presentation boundaries consistent with existing
  modules; keep SQL and row mapping in infrastructure adapters.
- Expose a campaign analytics query use case; do not let the controller access repositories directly.
- Verify the campaign exists before returning the report. A valid campaign with no activity returns zero counts and
  `null` rates where denominators are zero; it is not an error.
- Use aggregate SQL scoped by `campaign_id`. Avoid joining multiple one-to-many sources into one raw join that
  multiplies counts (e.g. events × claims × inbox rows). Use independent aggregate subqueries/CTEs or separate bounded
  aggregate queries and combine results in the application layer.
- Use `COUNT(DISTINCT member_id)` for unique metrics and authoritative source tables for delivered/claims.
- Ensure target-only landing visitors join to the immutable audience snapshot.
- Keep query results bounded to one campaign plus its coupons; do not load raw event histories into Java memory.
- Inspect `EXPLAIN (ANALYZE, BUFFERS)` against representative seeded data before proposing an index. Existing V1 indexes
  include campaign/type/received event lookup, campaign/member/type lookup, coupon claim campaign/coupon lookup, and
  campaign/member delivery lookup. No migration is assumed.
- Report data is eventually consistent with ongoing delivery/events/claims at normal transaction isolation; no report
  snapshot or cross-query serializable transaction is required unless tests expose inconsistent behavior.

## 7. Frontend design

- Add `features/analytics` (or the repository-consistent equivalent) with typed API client, report types, page, and
  presentational metric sections.
- Use the existing API client/auth/error patterns and route handling. Keep all authorization enforced server-side.
- Show paired count metrics (e.g. Impression Count and Unique Reach) so users cannot confuse events with people.
- Show rates with numerator/denominator and an unavailable label for `null` values.
- Coupon table/list must show configured quantity, successful claims, remaining quantity, unique claimers, and target
  claim rate with numerator/denominator.
- Format timestamps only if already displayed by existing campaign context; no new time-window selector is included.
- Ensure responsive layout, accessible labels, and loading/empty/error states. No chart library is needed for the MVP.

## 8. Verification and acceptance criteria

### Backend / database

- Unit tests cover rate calculation, zero denominators (`null`), and outcome aggregation.
- PostgreSQL integration tests seed known audience, inbox, event, claim, and coupon rows and assert every metric against
  independently calculated expected values.
- Tests prove event totals and distinct-member totals differ correctly when one member emits repeated events.
- Tests prove delivery is derived from `member_messages`, successful claims from `coupon_claims`, and claim attempts
  from
  server-generated audit events; a failed/replayed attempt never increments successful claims.
- Tests prove Unique Landing Visitors includes only audience-snapshot members with authenticated page views; direct
  visits are included, anonymous visits cannot appear, and no click-to-landing conversion is returned.
- Tests cover campaigns with no activity, zero target members, coupons with no claims, multiple coupons claimed by one
  member, and coupon inventory/claim-counter mismatch detection.
- API tests prove ADMIN access, MEMBER/anonymous denial, unknown campaign behavior, response serialization, no PII/raw
  event exposure, and CSRF is not required for a read-only GET (consistent with project security policy).
- Existing Phase 1–8 regression suites remain green.

### Frontend

- Route and campaign analytics navigation work for ADMIN.
- MEMBER cannot access the report through API; frontend role-aware navigation is only a UX layer.
- Count metrics, distinct-member metrics, numerator/denominator, and null rates render correctly.
- Coupon rows, zero-activity state, loading, unavailable, and error/retry states render correctly.
- No unsupported attribution/conversion metric is shown.

### Owner acceptance

- Owner runs Maven `clean verify`, backend startup, frontend checks/build/dev, existing regression suites, and a Phase 9
  analytics API verification suite.
- Owner performs a browser walkthrough against a campaign with known delivery, engagement, claims, and multiple
  coupons, and checks a zero-activity campaign.
- Owner explicitly accepts Phase 9 after reviewing the implementation report and test evidence.

## 9. Deliverables

- `analytics` backend module and tests.
- ADMIN analytics GET endpoint and synchronized API/OpenAPI documentation.
- Admin campaign analytics page and navigation entry.
- Repeatable `phase9-analytics-verification.zsh` API suite with deterministic, non-destructive read assertions where
  possible (test fixture creation/cleanup must be explicit if needed).
- Updated testing strategy, implementation plan, implementation report, acceptance guide, and documentation index.
- No database migration unless justified by measured query-plan evidence and separately approved.

## 10. Dependencies, risks, and approval gate

- Phase 8 is formally owner-accepted on 2026-11-28; Phase 9 plan and implementation were explicitly authorized by the
  owner on 2026-11-28.
- Owner approved this plan and explicitly authorized implementation on 2026-11-28. Implementation was delivered
  within this scope; the owner confirmed successful testing and formally accepted Phase 9 on 2026-11-28. See
  `PHASE_9_IMPLEMENTATION_REPORT.md` and `PHASE_9_ACCEPTANCE.md`.
- Main correctness risk is aggregate fan-out from joining multiple one-to-many tables; independent aggregate
  queries/CTEs
  and fixture tests are mandatory.
- Main semantic risk is confusing events, people, successful claims, and claim attempts; UI labels and tests must
  preserve
  the PRD definitions.
- Performance risk is unbounded event scans; scope every query by campaign and inspect plans before schema changes.
- The Phase 7 single-backend-instance operational constraint remains unchanged.

**Owner decision:** APPROVED on 2026-11-28. Implementation is authorized strictly within the scope above.
