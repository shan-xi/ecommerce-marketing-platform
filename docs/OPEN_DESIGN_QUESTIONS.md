# Design Decisions and Ready-for-Development Checklist

**Status:** Owner-approved v1.5.0 Development Baseline; Phases 1–9 formally accepted; Phase 10 plan drafted and
awaiting owner approval. **Scope:**
These decisions confirm the existing MVP direction and do not authorize scope expansion.

## 1. Confirmed decisions

| # | Decision                       | Confirmed MVP behavior                                                                                                                                                                                                                                                                                             | Key implications                                                                                                                                                                |
|--:|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Saved Segment + Campaign Rules | Combine with **AND** when both are supplied. If only one is supplied, use that definition. At least one audience definition is required.                                                                                                                                                                           | Preview and publication snapshot use the same rule-composition semantics; preview is an estimate and the publication-date snapshot is authoritative when age targeting is used. |
| 2 | Landing Page access            | Public campaign content and non-personal coupon summaries are readable anonymously. Member-specific claim state is visible only when authenticated; Claim requires `MEMBER`.                                                                                                                                       | Anonymous Claim attempts redirect to login and return to the intended campaign page.                                                                                            |
| 3 | Pause during Delivery          | The delivery job committed during publication continues to completion, even if it remains `PENDING` when Pause occurs. Existing inbox messages are not retracted. Pause blocks claims after its transaction commits; claims already holding the campaign shared lock may finish before Pause commits.              | No delivery cancellation, rewind, or resume workflow is added.                                                                                                                  |
| 4 | Campaign publication timing    | Publish only within the campaign window (`starts_at <= now < ends_at`). Future-dated scheduled publication/delivery is not supported. `ENDED` is derived when `published_at` is set and `ends_at` has passed, whether the persisted state was `ACTIVE` or `PAUSED`; it is not persisted and requires no scheduler. | API returns `409 CAMPAIGN_NOT_PUBLISHABLE` when the campaign has not started or has already ended.                                                                              |
| 5 | Landing Page structure         | One reusable fixed template with required `headline` and `body`; campaign details, period, and coupon summaries are rendered by the template. No Page Builder or custom layouts.                                                                                                                                   | Persist fixed columns; do not introduce arbitrary page JSON/schema.                                                                                                             |
| 6 | NULL audience attributes       | A NULL-valued member attribute does not match any supported comparison or set condition. Explicit NULL-check operators are not supported.                                                                                                                                                                          | SQL `UNKNOWN` is treated as non-match in preview and snapshot evaluation.                                                                                                       |

## 2. Other recommended defaults adopted

| Topic                             | MVP default                                                                                                                                                                                        | Status / boundary                                                                                                                 |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Re-publishing / audience snapshot | No silent re-snapshot of a published campaign. Use a new draft/revision if a new audience is needed.                                                                                               | Adopted; dedicated republish workflow is out of scope.                                                                            |
| Business timezone                 | `Asia/Taipei`; persist instants in UTC.                                                                                                                                                            | Adopted.                                                                                                                          |
| Access/refresh token lifetime     | Access token 15 minutes; refresh token 7 days; both configurable by environment.                                                                                                                   | Adopted as MVP defaults; production may adjust through deployment configuration.                                                  |
| Cookie policy                     | `HttpOnly` for access/refresh; `SameSite=Lax`; `Secure` in HTTPS (local HTTP exception only); host-only; access path `/api/v1`; refresh path `/api/v1/auth`; allowlisted credentialed CORS origin. | Adopted as MVP defaults; only change for a documented cross-site deployment.                                                      |
| Audience snapshot size            | One set-based snapshot operation in the publication transaction; no snapshot-job table/state machine.                                                                                              | Adopted for MVP/demo; revisit only if measured size makes the transaction impractical. No unsupported capacity guarantee is made. |
| Failed claim idempotency          | Only successful claims are replayable by idempotency key. Failed attempts do not reserve inventory and are not response-cached.                                                                    | Adopted.                                                                                                                          |
| Age targeting                     | Store fictional `birth_date`; preview derives age as of the preview date and publication derives age as of the publication date in `Asia/Taipei`.                                                  | Adopted; publication snapshot is authoritative.                                                                                   |
| Event / refresh-session retention | Retain through MVP/demo; define explicit retention and deletion policy before production use.                                                                                                      | Adopted for demo; production policy deferred.                                                                                     |
| Currency / percentage             | TWD; `PERCENTAGE` stores percentage points (`10` means 10% off; maximum 100).                                                                                                                      | Adopted.                                                                                                                          |
| Coupon ownership                  | Coupon belongs directly to one campaign; no global reusable coupon master or promotion-rule engine.                                                                                                | Previously confirmed; unchanged.                                                                                                  |
| Identity model                    | Shared `users` table with `ADMIN` / `MEMBER` role; member-only attributes in one-to-one `members` profile.                                                                                         | Previously confirmed; unchanged.                                                                                                  |
| Delivery simplification           | One worker, bounded batches, persisted cursor; no lease, `SKIP LOCKED`, or snapshot-job table.                                                                                                     | Previously confirmed; unchanged.                                                                                                  |

## 3. Additional phase-sequencing decision

| # | Decision                                    | Confirmed behavior                                                                                                                                                                                                                                                                                                        | Key implications                                                                                                                                                                                                                       |
|--:|---------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 7 | Campaign-scoped Audience Preview sequencing | Phase 4 implements the reusable Audience evaluator, including Segment Rules + Campaign Rules AND composition, and the reusable-segment preview endpoint. The campaign-scoped `POST /api/v1/admin/campaigns/{campaignId}/audience-preview` endpoint is integrated in Phase 5 after Campaign CRUD/read access is available. | Preserve the existing API path and request/response contract. Campaign loads its own persisted configuration and calls the Audience module's application-level interface; no temporary endpoint and no cross-module repository access. |

The owner approved the Phase 4 plan and sequencing on 2026-11-27, authorized implementation, and formally accepted
Phase 4 on 2026-11-27 after technical verification.

## 4. Review outcome and approval record

The six original owner decisions above remain confirmed. The v1.5.0 baseline also adopts the following implementation
defaults
without expanding product scope: (1) the PRD field/type/operator matrix is authoritative; (2) delivery uses one worker,
batch size 100, 1s/2s/4s retry backoff, one initial attempt plus three automatic retries before FAILED, and admin retry
from the last committed cursor; (3) refresh-token rows represent individual generations grouped by token_family_id, and
reuse revokes the family; (4) composite foreign keys protect campaign/message/coupon ownership; (5) analytics use
explicit target-member denominators and do not claim click-to-landing attribution; (6) server-generated claim audit
records distinguish SUCCEEDED/FAILED/REPLAYED; (7) zero-member audiences may publish and immediately complete delivery;
paused/ended published landing pages remain public but claims are disabled; (8) implementation phases are sequenced so
publication is completed in Phase 7. Remaining production-only items are retention/anonymization policy, deployment
hosting/secrets, and any measured scale redesign. Token TTL defaults are fixed for MVP configuration (15 minutes / 7
days) and remain environment-configurable. These do not block MVP implementation.

The owner has explicitly approved the complete v1.5.0 Design Pack. Phase 0 is complete. Phase 1 — Repository Foundation
and Phase 2 — Database Foundation were formally accepted on 2026-11-26. Phases 3 and 4 were formally accepted on
2026-11-27 after owner verification. The owner formally accepted Phase 5 on 2026-11-27 after reporting successful
backend, frontend, API (29 PASS, 0 FAIL), Campaign Audience composition, Campaign UI, and boundary verification.
The owner formally accepted Phase 6 on 2026-11-27 after reporting successful verification. The owner explicitly
authorized Phase 7 implementation on 2026-11-27 and formally accepted it on 2026-11-27 after reporting successful
verification; see the Phase 7 implementation report.

### Ready-for-development checklist

- [x] Six owner decisions are recorded and reflected in relevant documents.
- [x] Recommended MVP defaults are adopted or explicitly deferred as above.
- [x] Audience rule combination, field/type/operator matrix, enum values, and NULL semantics are consistent across PRD,
  API, database, and tests.
- [x] Delivery retry, FAILED recovery, and zero-audience behavior are defined.
- [x] Refresh-token generation/family rotation and reuse response are defined.
- [x] Composite foreign keys protect cross-campaign message/coupon relationships.
- [x] Analytics metric sources, denominators, and attribution limitations are explicit.
- [x] Claim-attempt success/failure/replay audit behavior is defined.
- [x] Paused/ended landing-page behavior and empty-audience publication are defined.
- [x] Implementation phases have explicit dependencies and publication orchestration ownership.
- [x] Public landing-page access, authenticated Claim, pause/delivery behavior, and publication timing are specified.
- [x] MVP exclusions are documented; no Page Builder, scheduled delivery, Kafka, Redis, microservices, snapshot job, or
  worker lease is added.
- [x] Owner gives final approval to the complete Design Pack (2026-11-26).
- [x] PRD status is explicitly changed to `Ready for Development`.
- [x] Phase 1 is formally accepted (2026-11-26).
- [x] Phase 2 — Database Foundation was formally accepted (2026-11-26).
- [x] Phase 3 — Identity and Security formally accepted by the owner (2026-11-27).
- [x] Phase 4 planning document prepared strictly from the approved v1.5.0 project documents.
- [x] Owner approved `docs/development/PHASE_4_PLAN.md` on 2026-11-27.
- [x] Campaign-scoped Audience Preview assigned to Phase 5 integration; existing API contract preserved.
- [x] Owner explicitly instructed the assistant to begin Phase 4 implementation (2026-11-27).
- [x] Owner formally accepted Phase 4 after verification (2026-11-27).
- [x] Phase 5 Campaign Draft Management plan drafted from the approved baseline.
- [x] Owner approves the Phase 5 plan as written (2026-11-27); bounded list interpretation uses documented
  pagination/status only, no `q` clarification.
- [x] Owner explicitly authorizes Phase 5 implementation (2026-11-27).
- [x] Owner verifies and formally accepts Phase 5 implementation (2026-11-27).
- [x] Phase 6 Coupon Configuration and Claim Backend plan approved and implemented from the approved PRD/API/database
  baseline.
- [x] Owner explicitly authorized Phase 6 implementation and approved the bounded Admin coupon UI clarification.
- [x] Owner formally accepted Phase 6 on 2026-11-27 after reporting successful backend, frontend, API, and Admin UI
  verification.
- [x] Phase 7 Message, Publication Orchestration, and Delivery plan drafted from the approved PRD/API/database baseline.
- [x] Owner explicitly authorized Phase 7 implementation on 2026-11-27.
- [x] Bounded Admin message/publish/pause/delivery UI clarification in `PHASE_7_PLAN.md` Section 3.3 implemented.
- [x] Owner reported Phase 7 Spring Boot startup, Maven clean verify, `npm run dev`, script automation tests, and API
  suite 23/23 PASS; Phase 7 formally accepted on 2026-11-27.
- [x] Phase 8 implementation explicitly authorized by the owner on 2026-11-27, verified, and formally accepted on
  2026-11-28.
- [x] Phase 9 Analytics plan approved and implementation explicitly authorized by the owner on 2026-11-28;
  implementation delivered, verified by the owner, and formally accepted on 2026-11-28.
- [x] Owner confirmed Phase 9 testing completed successfully and approved the Phase 9 changes on 2026-11-28.
- [ ] Phase 10 Hardening and Delivery plan reviewed and approved by the owner.
- [ ] Phase 10 implementation explicitly authorized by the owner.

All design approval gates for the v1.5.0 baseline remain complete. Phases 1–9 are formally accepted. A dedicated
high-contention Coupon inventory race test remains tracked for Phase 10 hardening. Phase 10 planning is awaiting owner
approval; implementation is not authorized.

## 5. Phase acceptance and next planning gate (2026-11-28)

- **Phase 8:** formally accepted by the owner on 2026-11-28. Owner-reported checks: Maven `clean verify`, Spring Boot
  startup, frontend `npm run dev`, Phase 8 API verification suite `all`, and successful Member UI coupon claim. Exact
  evidence boundary is documented in `development/PHASE_8_IMPLEMENTATION_REPORT.md` and
  `development/PHASE_8_ACCEPTANCE.md`.
- **Phase 9:** owner approved `development/PHASE_9_PLAN.md` and explicitly authorized implementation on 2026-11-28.
  The combined ADMIN analytics endpoint and Admin route `/admin/campaigns/:campaignId/analytics` are implemented. No
  schema change was introduced. The owner confirmed successful Phase 9 testing and formally accepted the changes on
  2026-11-28; see `PHASE_9_IMPLEMENTATION_REPORT.md` and `PHASE_9_ACCEPTANCE.md`.
- **Phase 10:** proposed hardening and delivery-readiness scope is documented in `development/PHASE_10_PLAN.md`.
  Owner approval and explicit implementation authorization are pending.
