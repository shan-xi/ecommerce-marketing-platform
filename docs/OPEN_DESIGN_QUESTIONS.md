# Design Decisions and Ready-for-Development Checklist

**Status:** Decision review complete — six owner decisions confirmed; final Design Pack approval is pending.
**Scope:** These decisions confirm the existing MVP direction and do not authorize scope expansion.

## 1. Confirmed decisions

| # | Decision | Confirmed MVP behavior | Key implications |
|---:|---|---|---|
| 1 | Saved Segment + Campaign Rules | Combine with **AND** when both are supplied. If only one is supplied, use that definition. At least one audience definition is required. | Preview and publication snapshot use the same rule-composition semantics; preview is an estimate and the publication-date snapshot is authoritative when age targeting is used. |
| 2 | Landing Page access | Public campaign content and non-personal coupon summaries are readable anonymously. Member-specific claim state is visible only when authenticated; Claim requires `MEMBER`. | Anonymous Claim attempts redirect to login and return to the intended campaign page. |
| 3 | Pause during Delivery | The delivery job committed during publication continues to completion, even if it remains `PENDING` when Pause occurs. Existing inbox messages are not retracted. Pause blocks claims after its transaction commits; claims already holding the campaign shared lock may finish before Pause commits. | No delivery cancellation, rewind, or resume workflow is added. |
| 4 | Campaign publication timing | Publish only within the campaign window (`starts_at <= now < ends_at`). Future-dated scheduled publication/delivery is not supported. `ENDED` is derived when `published_at` is set and `ends_at` has passed, whether the persisted state was `ACTIVE` or `PAUSED`; it is not persisted and requires no scheduler. | API returns `409 CAMPAIGN_NOT_PUBLISHABLE` when the campaign has not started or has already ended. |
| 5 | Landing Page structure | One reusable fixed template with required `headline` and `body`; campaign details, period, and coupon summaries are rendered by the template. No Page Builder or custom layouts. | Persist fixed columns; do not introduce arbitrary page JSON/schema. |
| 6 | NULL audience attributes | A NULL-valued member attribute does not match any supported comparison or set condition. Explicit NULL-check operators are not supported. | SQL `UNKNOWN` is treated as non-match in preview and snapshot evaluation. |

## 2. Other recommended defaults adopted

| Topic | MVP default | Status / boundary |
|---|---|---|
| Re-publishing / audience snapshot | No silent re-snapshot of a published campaign. Use a new draft/revision if a new audience is needed. | Adopted; dedicated republish workflow is out of scope. |
| Business timezone | `Asia/Taipei`; persist instants in UTC. | Adopted. |
| Access/refresh token lifetime | Access token 15 minutes; refresh token 7 days; both configurable by environment. | Adopted as MVP defaults; production may adjust through deployment configuration. |
| Cookie policy | `HttpOnly` for access/refresh; `SameSite=Lax`; `Secure` in HTTPS (local HTTP exception only); host-only; access path `/api/v1`; refresh path `/api/v1/auth`; allowlisted credentialed CORS origin. | Adopted as MVP defaults; only change for a documented cross-site deployment. |
| Audience snapshot size | One set-based snapshot operation in the publication transaction; no snapshot-job table/state machine. | Adopted for MVP/demo; revisit only if measured size makes the transaction impractical. No unsupported capacity guarantee is made. |
| Failed claim idempotency | Only successful claims are replayable by idempotency key. Failed attempts do not reserve inventory and are not response-cached. | Adopted. |
| Age targeting | Store fictional `birth_date`; preview derives age as of the preview date and publication derives age as of the publication date in `Asia/Taipei`. | Adopted; publication snapshot is authoritative. |
| Event / refresh-session retention | Retain through MVP/demo; define explicit retention and deletion policy before production use. | Adopted for demo; production policy deferred. |
| Currency / percentage | TWD; `PERCENTAGE` stores percentage points (`10` means 10% off; maximum 100). | Adopted. |
| Coupon ownership | Coupon belongs directly to one campaign; no global reusable coupon master or promotion-rule engine. | Previously confirmed; unchanged. |
| Identity model | Shared `users` table with `ADMIN` / `MEMBER` role; member-only attributes in one-to-one `members` profile. | Previously confirmed; unchanged. |
| Delivery simplification | One worker, bounded batches, persisted cursor; no lease, `SKIP LOCKED`, or snapshot-job table. | Previously confirmed; unchanged. |

## 3. Review outcome and next gate

The six owner decisions above remain confirmed. The v1.5.0 baseline also adopts the following implementation defaults without expanding product scope: (1) the PRD field/type/operator matrix is authoritative; (2) delivery uses one worker, batch size 100, 1s/2s/4s retry backoff, one initial attempt plus three automatic retries before FAILED, and admin retry from the last committed cursor; (3) refresh-token rows represent individual generations grouped by token_family_id, and reuse revokes the family; (4) composite foreign keys protect campaign/message/coupon ownership; (5) analytics use explicit target-member denominators and do not claim click-to-landing attribution; (6) server-generated claim audit records distinguish SUCCEEDED/FAILED/REPLAYED; (7) zero-member audiences may publish and immediately complete delivery; paused/ended published landing pages remain public but claims are disabled; (8) implementation phases are sequenced so publication is completed in Phase 7. Remaining production-only items are retention/anonymization policy, deployment hosting/secrets, and any measured scale redesign. Token TTL defaults are fixed for MVP configuration (15 minutes / 7 days) and remain environment-configurable. These do not block MVP implementation.

The Design Pack is **not automatically marked Ready for Development** by recording these decisions. The PRD and technical documents remain subject to the owner's final baseline approval. Phase 0 in `development/IMPLEMENTATION_PLAN.md` remains the gate for that approval.

### Ready-for-development checklist

- [x] Six owner decisions are recorded and reflected in relevant documents.
- [x] Recommended MVP defaults are adopted or explicitly deferred as above.
- [x] Audience rule combination, field/type/operator matrix, enum values, and NULL semantics are consistent across PRD, API, database, and tests.
- [x] Delivery retry, FAILED recovery, and zero-audience behavior are defined.
- [x] Refresh-token generation/family rotation and reuse response are defined.
- [x] Composite foreign keys protect cross-campaign message/coupon relationships.
- [x] Analytics metric sources, denominators, and attribution limitations are explicit.
- [x] Claim-attempt success/failure/replay audit behavior is defined.
- [x] Paused/ended landing-page behavior and empty-audience publication are defined.
- [x] Implementation phases have explicit dependencies and publication orchestration ownership.
- [x] Public landing-page access, authenticated Claim, pause/delivery behavior, and publication timing are specified.
- [x] MVP exclusions are documented; no Page Builder, scheduled delivery, Kafka, Redis, microservices, snapshot job, or worker lease is added.
- [ ] Owner gives final approval to the complete Design Pack.
- [ ] PRD status is explicitly changed to `Ready for Development`.
- [ ] Phase 1 implementation is explicitly authorized.

Do not begin implementation until the remaining owner-approval items are completed.
