# Phase 9 Acceptance Guide — Campaign Analytics and Coupon Reporting

**Status:** OWNER VERIFICATION PENDING. This is a repeatable acceptance guide, not a record of acceptance.

## 1. Automated verification

Run from the project root:

```bash
./mvnw -f backend/pom.xml clean verify
```

Run frontend checks:

```bash
cd frontend
npm ci
npm test
npm run typecheck
npm run build
```

Run backend and frontend, then verify the API:

```bash
export PHASE9_CAMPAIGN_ID="<campaign-uuid>"
zsh scripts/api-tests/suites/phase9-analytics-verification.zsh
```

The API suite must report ADMIN `200`, MEMBER `403`, anonymous `401`, valid response fields, rate numerator/denominator
semantics, zero-denominator null behavior, coupon remaining-quantity arithmetic, and no raw member identifiers/event
metadata.

## 2. Browser walkthrough

1. Sign in as ADMIN and open Campaigns.
2. Select a published/paused campaign and choose **查看 Campaign Analytics**.
3. Verify target and delivered members are shown separately.
4. Verify impression/click/page-view event counts are distinct from unique-member counts.
5. Verify each rate shows numerator and denominator; a zero denominator is unavailable, not `0%`.
6. Verify successful claims are separate from claim attempts and outcomes.
7. Verify every coupon shows configured quantity, successful claims, remaining quantity, unique claimers, and target
   claim rate.
8. Verify no click-to-landing conversion metric is shown.
9. Open a campaign with no activity and a campaign with zero target members; confirm zero counts and unavailable rates.
10. Verify loading, retryable error, not-found, and role-denial states.

## 3. Acceptance decision

Owner acceptance requires successful backend Maven verification, frontend test/typecheck/build, Phase 9 API
verification, and browser walkthrough. After verification, update this guide and `PHASE_9_IMPLEMENTATION_REPORT.md` with
the owner's actual results and acceptance date, then mark Phase 9 complete in `IMPLEMENTATION_PLAN.md` and
`docs/README.md`.
