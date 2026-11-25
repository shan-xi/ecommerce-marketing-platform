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
| 7 | What is the exact publication consistency strategy for very large audiences? | Snapshot must be complete before delivery starts; use a resumable publication/snapshot state machine if one transaction is too large. | Avoids partial delivery or partially eligible campaigns. |
| 8 | What happens to in-flight delivery when an admin pauses a campaign? | Stop scheduling new deliveries after pause is observed; already-created inbox messages remain. Coupon claims are rejected while paused. | Clarifies race behavior and user expectations. |
| 9 | Should failed claim responses be persisted for idempotent replay? | Successful claims are always replayable by idempotency key; failed attempts may be recorded as events, but do not reserve inventory. | Defines retry behavior and support diagnostics. |
| 11 | Is `age` stored or derived? | Store `birth_date` in fictional seed data and derive age at evaluation time; define the age reference date. | Avoids stale age values. |
| 12 | What is the retention policy for engagement events and refresh-token history? | Retain through MVP/demo; production retention requires an explicit policy. | Data lifecycle and privacy requirements. |

## Decisions confirmed during owner review

The following decisions have been confirmed and are no longer open questions. This does not mean the entire Design Pack has been approved or is ready for development.

- **Coupon ownership:** A coupon belongs directly to one campaign through `campaign_coupons.campaign_id`. The MVP does not include a separate global reusable coupon master or a promotion-rule engine.
- **Coupon discount metadata:** Support `FIXED_AMOUNT` and `PERCENTAGE`; `minimum_order_amount` remains optional and descriptive because checkout/redemption is out of scope.
- **Identity model:** Administrators and members share `users`; `users.role` distinguishes `ADMIN` and `MEMBER`. Member-only attributes are stored in a one-to-one `members` profile.

## Ready-for-development checklist

- [ ] PRD status explicitly changed to `Ready for Development` by the owner.
- [ ] All P0 requirements and acceptance criteria are agreed.
- [ ] Questions above are confirmed or explicitly deferred with no impact on the first implementation phase.
- [ ] API paths, cookie/CSRF contract, and error semantics are agreed.
- [ ] Database ownership, unique constraints, claim transaction, and snapshot strategy are agreed.
- [ ] MVP exclusions are understood and protected from accidental scope expansion.
- [ ] Implementation phase 1 is authorized.

Do not change the PRD status automatically. Only the owner can declare the documentation baseline ready for development.
