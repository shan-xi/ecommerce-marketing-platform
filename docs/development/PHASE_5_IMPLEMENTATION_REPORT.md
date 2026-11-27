# Phase 5 Implementation Report — Campaign Draft Management

**Plan:** `PHASE_5_PLAN.md` (owner-approved 2026-11-27)  
**Implementation:** completed for owner verification  
**Owner acceptance:** pending  
**Baseline:** owner-approved v1.5.0; Phases 1–4 accepted

## Delivered

- Campaign module with application service, persistence port, JDBC adapter, and Admin controller.
- Admin campaign create/list/detail/update endpoints under `/api/v1/admin/campaigns`.
- Campaign-scoped persisted audience preview at `POST /api/v1/admin/campaigns/{campaignId}/audience-preview`, calling
  the Audience application interface.
- DRAFT-only update guarded by a database row lock and conditional status check.
- Request validation for required campaign fields, fixed landing-page fields, ISO-8601 instants, `endsAt > startsAt`,
  and audience definitions.
- Effective `ENDED` read status for published ACTIVE/PAUSED campaigns; DRAFT remains DRAFT after its end time.
- React Admin Campaign Management UI and typed API client; existing Audience Segments route is preserved.
- API/OpenAPI campaign response schema completion, error-code documentation, implementation plan synchronization,
  frontend source tests, backend service tests, PostgreSQL repository integration test, and Phase 5 zsh API verification
  suite.

## Scope decisions

- The approved plan was implemented as written. No `q` free-text search parameter was added; list supports documented
  pagination and status filter.
- No migration was added because the existing `campaigns` table contains the required fields and constraints.
- Publish/Pause, coupon/message configuration, snapshot persistence, delivery, public landing page, and analytics remain
  out of scope.

## Verification performed in this environment

- `frontend npm run test`: **12 passed, 0 failed** (Node test suite).
- Owner's local Maven run executed 43 tests: 42 passed and 1 errored in `CampaignRepositoryIntegrationTest`. The error
  was the test fixture attempting to set an ACTIVE campaign's `ends_at` before `starts_at`, violating the existing
  `ck_campaigns_time_window` database constraint.
- This verification ZIP corrects that fixture by setting a valid earlier `starts_at` alongside the past `ends_at`; the
  repository implementation is unchanged for this test correction. The corrected test still verifies the persisted
  campaign becomes effectively ENDED and that updates are rejected once status is non-DRAFT.
- The corrected test has not yet been rerun by the owner, so full Maven verification remains pending. Frontend
  typecheck/build and the Phase 5 API suite also remain to be confirmed by the owner.

## Owner verification checklist

1. Run backend verification: `./mvnw -f backend/pom.xml verify`.
2. In `frontend/`, run `npm ci`, `npm run test`, `npm run typecheck`, and `npm run build`.
3. Start the project using the existing local Compose instructions in `README.md`.
4. Run `zsh scripts/api-tests/suites/phase5-campaign-verification.zsh` from the repository root.
5. Sign in as ADMIN, open `/admin/campaigns`, create a draft with a saved segment, save it, reopen it, update it, and
   preview the saved audience. Confirm the Phase 4 Audience Segments route still works.
6. Confirm there are no Publish/Pause controls or later-phase workflows.

Phase 6 must not begin until the owner reviews the verification results and formally accepts Phase 5.
