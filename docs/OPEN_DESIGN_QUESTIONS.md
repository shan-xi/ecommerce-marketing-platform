# Open Design Questions Before Ready for Development

**Status:** Review checklist — these are not permission to expand MVP scope.  
The technical documents contain recommended defaults for most items below. The owner should confirm or change them before implementation begins.

| # | Question | Recommended default in this pack | Why it matters |
|---:|---|---|---|
| 1 | When a campaign selects a saved segment and also supplies campaign-specific rules, how are they combined? | Combine with AND; both are evaluated at publication. | Determines audience preview and snapshot membership. |
| 2 | Can an active campaign be re-published with a new audience snapshot? | No silent re-snapshot. Create a new draft/revision or explicitly define a republish workflow later. | Prevents eligibility and delivery population from changing unexpectedly. |
| 3 | Is the campaign landing page publicly readable? | Campaign content may be public; member-specific claim state and claiming require authentication. | Determines anonymous API behavior and login redirect. |
| 4 | What is the business timezone? | `Asia/Taipei`; persist instants in UTC. | Campaign start/end boundaries and reporting must be consistent. |
| 5 | What are access-token and refresh-token lifetimes? | Short-lived access token and longer refresh token; exact durations configured before implementation. | Security and session UX. |
| 6 | What cookie SameSite/domain/path settings fit the actual deployment? | Same-site deployment with `Lax` where compatible; narrow refresh-cookie path where practical. | Browser behavior depends on domain and deployment topology. |
| 7 | What maximum audience size must the initial release support? | MVP uses one set-based snapshot insert in the publication transaction; defer a snapshot job/state machine unless measured size makes this impractical. | Keeps the MVP simple while preserving the rule that delivery never starts from a partial snapshot. |
| 8 | What happens to in-flight delivery when an admin pauses a campaign? | Recommended MVP default: the existing delivery job continues to completion; already-created inbox messages remain; new coupon claims are rejected while paused. | Avoids introducing partial-delivery resume behavior. |
| 9 | Should failed claim responses be persisted for idempotent replay? | Only successful claims are replayable by idempotency key; failed attempts do not reserve inventory and are not response-cached. | Keeps idempotency storage simple; failed attempts can be recorded for analytics. |
| 10 | Can an administrator publish a campaign before its `starts_at` time? | No scheduled message delivery is supported; recommended default is to allow publication only when `starts_at <= now`, so immediate delivery does not precede the campaign period. | Avoids sending an immediately delivered message for a campaign that cannot yet be claimed. |
| 11 | Is `age` stored or derived? | Store `birth_date` in fictional seed data and derive age at evaluation time; evaluate age as of the campaign publication date in `Asia/Taipei` (confirm before implementation). | Avoids stale age values and inconsistent segment results. |
| 12 | What is the retention policy for engagement events and refresh-token history? | Retain through MVP/demo; production retention requires an explicit policy. | Data lifecycle and privacy requirements. |
| 13 | Confirm currency and percentage-discount semantics. | MVP monetary values use TWD; `PERCENTAGE` stores percentage points (`10` means 10% off; maximum 100). | Prevents different interpretations of coupon values across admin UI, API, and persistence. |

## Decisions confirmed during owner review

The following decisions have been confirmed during owner review and are no longer open questions. This does not mean the entire Design Pack has been approved or is ready for development.

- **Coupon ownership:** A coupon belongs directly to one campaign through `campaign_coupons.campaign_id`. The MVP does not include a separate global reusable coupon master or a promotion-rule engine.
- **Coupon discount metadata:** Support `FIXED_AMOUNT` and `PERCENTAGE`; `minimum_order_amount` remains optional and descriptive because checkout/redemption is out of scope.
- **Identity model:** Administrators and members share `users`; `users.role` distinguishes `ADMIN` and `MEMBER`. Member-only attributes are stored in a one-to-one `members` profile.
- **MVP delivery simplification:** one delivery worker with bounded batches and a persisted cursor; no lease, `SKIP LOCKED`, or snapshot-job table. The full snapshot is committed before delivery begins.

## Ready-for-development checklist

- [ ] PRD status explicitly changed to `Ready for Development` by the owner.
- [ ] All P0 requirements and acceptance criteria are agreed.
- [ ] Questions above are confirmed or explicitly deferred with no impact on the first implementation phase.
- [ ] API paths, cookie/CSRF contract, and error semantics are agreed.
- [ ] Database ownership, unique constraints, claim transaction, and snapshot strategy are agreed.
- [ ] MVP exclusions are understood and protected from accidental scope expansion.
- [ ] Implementation phase 1 is authorized.

Do not change the PRD status automatically. Only the owner can declare the documentation baseline ready for development.
