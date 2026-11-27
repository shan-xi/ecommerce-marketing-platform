# Phase 5 Plan — Campaign Draft Management

**Document status:** OWNER-APPROVED — approved as written by the owner on 2026-11-27.  
**Implementation status:** COMPLETE — formally accepted by the owner on 2026-11-27.  
**Baseline:** Owner-approved v1.5.0 Development Baseline.  
**Preceding phases:** Phases 1–4 formally accepted; Phase 4 accepted by the owner on 2026-11-27.  
**Purpose:** Define the bounded Phase 5 work package from the approved PRD, API/OpenAPI contract, database design,
architecture, and implementation sequence. The owner explicitly approved this plan as written and separately authorized
Phase 5 implementation on 2026-11-27.

## 1. Objective

Deliver the first campaign-management capability: administrators can create, edit, list, and inspect draft campaigns,
and preview the effective audience using the persisted campaign configuration and the Audience application interface
delivered in Phase 4.

Phase 5 is limited to campaign draft management and campaign-scoped audience preview. It does not publish or pause
campaigns, configure coupons or messages, persist the final audience snapshot, or initiate delivery.

## 2. Authoritative source documents

This plan is subordinate to the existing owner-approved v1.5.0 baseline:

1. `docs/PRD.md` — Sections 2.1, 3, 4.1–4.2, 5.1, and 6.2–6.3.
2. `docs/api/API_SPECIFICATION.md` — Admin campaign APIs and audience preview contract.
3. `docs/api/openapi.yaml` — `/admin/campaigns`, `/admin/campaigns/{campaignId}`, and
   `/admin/campaigns/{campaignId}/audience-preview`.
4. `docs/api/ERROR_CODES.md` — stable validation, not-found, authorization, and conflict responses.
5. `docs/database/DATABASE_DESIGN.md` and `docs/database/ERD.md` — existing `campaigns` table and `audience_segment_id`
   relationship.
6. `docs/architecture/ARCHITECTURE.md` and `docs/architecture/MODULE_DESIGN.md` — modular-monolith boundaries and
   Campaign/Audience dependency rules.
7. `docs/development/IMPLEMENTATION_PLAN.md` — Phase 5 scope and Phase 6–7 boundaries.
8. `docs/development/PHASE_4_PLAN.md` and `PHASE_4_IMPLEMENTATION_REPORT.md` — accepted Audience evaluator interface and
   composition semantics.

If a conflict is found, stop and reconcile the source documents before implementation. Do not silently change the PRD,
API contract, schema, or MVP scope.

## 3. Scope

### 3.1 In scope

- Campaign draft create, list/search, detail, and update.
- Campaign read model and persistence adapter using the existing `campaigns` table.
- Fixed landing-page fields: `headline` and `body`.
- Campaign audience configuration:
    - a saved `segmentId`, or
    - campaign-specific `rules`, or
    - both; when both exist, the effective audience is their intersection (AND).
- Validation of the campaign request against the existing `CampaignWrite` contract.
- Campaign-scoped audience preview using the already-documented endpoint:
  `POST /api/v1/admin/campaigns/{campaignId}/audience-preview`.
- Admin UI for campaign list, create, edit, detail, and audience preview, integrated into the existing React +
  TypeScript SPA and ADMIN route group.
- Backend unit/integration tests, frontend tests, API verification script, and synchronized API/OpenAPI/documentation
  updates where implementation reveals a genuine contract gap.

### 3.2 Explicitly out of scope

- Publish and Pause endpoints or any campaign lifecycle transition to `ACTIVE` / `PAUSED`.
- Campaign audience snapshot persistence or publication transaction orchestration (Phase 7).
- Coupon create/update/configuration or coupon claims (Phase 6).
- Campaign message configuration, delivery jobs, workers, retry, or inbox delivery (Phase 7).
- Public campaign landing page, member claim UI, and engagement events (Phase 8).
- Analytics (Phase 9).
- Scheduled publication, automatic status transition jobs, republish, resume, or campaign revision workflow.
- New infrastructure, Redis, Kafka, microservices, external providers, or unrelated schema redesign.

## 4. Campaign contract and business rules

### 4.1 Endpoints

Implement only these Phase 5 endpoints under the existing `/api/v1` API prefix:

| Method | Path                                                    | Phase 5 behavior                                                     |
|--------|---------------------------------------------------------|----------------------------------------------------------------------|
| POST   | `/api/v1/admin/campaigns`                               | Create a campaign in `DRAFT`; return 201                             |
| GET    | `/api/v1/admin/campaigns`                               | List campaigns with documented pagination and optional status filter |
| GET    | `/api/v1/admin/campaigns/{campaignId}`                  | Return campaign detail; return 404 if absent                         |
| PUT    | `/api/v1/admin/campaigns/{campaignId}`                  | Update only a `DRAFT` campaign                                       |
| POST   | `/api/v1/admin/campaigns/{campaignId}/audience-preview` | Evaluate the persisted campaign audience and return count/sample     |

Do not implement the already documented Publish/Pause routes in Phase 5.

### 4.2 Create/update payload

Use the existing `CampaignWrite` request shape from OpenAPI:

- `name` (required, maximum 160 characters).
- `description` (nullable).
- `startsAt` and `endsAt` (required ISO-8601 date-time instants).
- `landingPage.headline` (required, maximum 200 characters).
- `landingPage.body` (required, maximum 10,000 characters).
- `audience` (required object containing at least `segmentId` or `rules`; both are allowed).

The request and response must remain aligned with `API_SPECIFICATION.md` and `openapi.yaml`. Do not introduce arbitrary
landing-page JSON or a Page Builder.

The current OpenAPI `CampaignEnvelope` only sketches `id`, `name`, `status`, `startsAt`, and `endsAt`, while the write
contract contains more fields and the Admin edit/detail UI needs the persisted configuration. Phase 5 must complete the
response schemas for campaign detail and list summaries so they explicitly describe the fields actually returned
(including description, fixed landing-page fields, audience segment/rules, lifecycle timestamps where present, and
pagination metadata). Keep response data minimal and consistent with the database/read model; do not add unrelated
coupon/message details in this phase.

### 4.3 Validation and lifecycle

- Reject a blank/invalid name, overlong fields, missing landing-page fields, malformed timestamps, invalid audience
  configuration, unknown rule fields/operators, and invalid rule values with the documented validation response.
- Require `endsAt > startsAt`. Persist instants consistently in UTC; interpret campaign schedules using the approved
  `Asia/Taipei` business-time convention.
- Saving a draft is not publication. Do not reject a draft merely because its start is in the future or its end is in
  the past; the approved lifecycle semantics say a draft remains `DRAFT`.
- New campaigns are persisted as `DRAFT`; the client cannot choose or override status.
- Only `DRAFT` campaigns may be updated. An attempt to update a non-draft campaign returns the documented conflict
  response; do not mutate published configuration.
- `ENDED` is an effective read status only when `publishedAt` exists and `endsAt <= now`, whether persisted status is
  `ACTIVE` or `PAUSED`. It is not persisted. A draft remains `DRAFT` after its end time.
- Publish-window validation (`startsAt <= now < endsAt`) belongs to Phase 7 publication integration, not Phase 5 draft
  save.
- `GET` list/detail must not leak data to unauthenticated users or MEMBER principals; ADMIN authorization is enforced
  server-side.
- All state-changing requests require the existing CSRF protection.

### 4.4 Audience preview integration

- Keep the existing path and request/response contract unchanged:
  `POST /api/v1/admin/campaigns/{campaignId}/audience-preview`.
- The endpoint loads the campaign's persisted `audience_segment_id` and `audience_rules`; it does not accept an
  alternate audience definition in the request body.
- Campaign application service calls the Audience module's application-level evaluator/use-case interface. It must not
  access Audience repositories, JDBC adapters, or tables directly.
- The Audience module remains the owner of rule validation/evaluation and AND composition.
- At least one of segment or campaign rules must be present. If both are present, combine them with AND. NULL member
  attributes remain non-matches for all supported comparisons/set operators.
- Preview returns the documented count and up to 10 non-sensitive member display identifiers only. Do not expose full
  member profiles.
- Preview is an estimate at preview time. Age is calculated using the current `Asia/Taipei` date; the publication-time
  snapshot in Phase 7 will be authoritative.
- A missing campaign returns 404. An invalid persisted audience definition or invalid reference returns the documented
  client/domain error; do not silently treat it as an empty audience.

## 5. Architecture and persistence boundaries

- Implement Campaign domain/application/presentation/infrastructure layers following the existing modular-monolith
  conventions.
- Campaign owns campaign persistence and lifecycle decisions.
- Audience owns rule validation/evaluation. Cross-module calls must use an application-level interface, never direct
  repository/table access.
- Use the existing `campaigns` table and its constraints/indexes. The current Phase 2 schema already contains the
  campaign-related tables; do not add a migration unless code inspection proves the approved schema is insufficient. Any
  schema change requires a documented justification and synchronized DATABASE_DESIGN/ERD/OpenAPI impact review.
- Use injected `Clock` for time-dependent behavior and deterministic tests.
- Use transaction boundaries for create/update and ensure a campaign update cannot race into modifying a non-draft
  campaign after its status changes.

## 6. Frontend deliverables

- Add Campaign API client/types with typed request/response contracts.
- Add Admin campaign list/search page with pagination and status filter.
- Add create/edit form for the fields in `CampaignWrite`.
- Add campaign detail/read view.
- Add audience selector supporting reusable segment, campaign-specific rules, or both.
- Add a Preview action that calls the persisted campaign-scoped preview endpoint and renders count/sample with clear
  estimate wording.
- Preserve the existing Audience Segments workspace and its route/navigation.
- Keep all Admin routes behind the existing authentication state; backend authorization remains authoritative.
- Do not add publish, pause, coupon, message, delivery, or landing-page member workflows to this phase.

## 7. Search/list and response-schema contract clarifications

The existing OpenAPI list operation defines `page`, `size`, and optional `status`, and the API specification describes
campaign list/search with `createdAt DESC` ordering. It does not currently define a free-text search parameter, while
PRD FR-CAM-03 says administrators can “view and search campaigns.”

**Recommended bounded interpretation:** implement the documented pagination and status filter first. Add an optional
case-insensitive campaign-name query parameter (recommended name: `q`) only if the owner approves this small
API-contract clarification before implementation. If approved, update API_SPECIFICATION and OpenAPI together and test
empty, partial, case-insensitive, and no-match queries. Do not silently add a search parameter during coding.

The current OpenAPI `CampaignEnvelope` is only a partial response schema, and `CampaignPageEnvelope` uses generic object
items. Before implementation, the campaign detail and list response fields must be explicitly specified in
API_SPECIFICATION/OpenAPI. The recommended detail response contains the persisted editable campaign configuration plus
identifiers, effective status, and available lifecycle timestamps; the list item contains only the fields needed for
filtering and display. This is contract completion, not permission to add new business capabilities.

## 8. Required tests

### Backend

- Create draft with valid payload; assert 201, DRAFT status, persisted fields, and audience configuration.
- Reject missing audience, empty audience, invalid segment reference, malformed rules, invalid field/operator/value,
  blank/overlong fields, malformed timestamps, and `endsAt <= startsAt`.
- List pagination and status filter; stable `createdAt DESC` order; verify effective `ENDED` read status and that an
  expired draft remains `DRAFT`.
- Detail returns correct persisted configuration; unknown ID returns 404.
- Update DRAFT successfully; reject update to non-DRAFT with conflict; reject unknown ID.
- Verify MEMBER and anonymous access are denied; ADMIN is allowed; missing/invalid CSRF is rejected for writes.
- Campaign-scoped preview loads the persisted campaign configuration and calls the Audience application interface;
  verify segment-only, rules-only, and segment-plus-rules AND behavior.
- Preview for unknown campaign returns 404; invalid audience definition returns the documented error; count/sample shape
  and sample limit are correct.
- Add PostgreSQL integration coverage for persistence, FK behavior, transaction/update status guard, and read-model
  status derivation.
- Preserve all Phase 1–4 regression tests.

### Frontend

- Campaign API client uses documented paths and typed payloads.
- List pagination/status filter and optional approved `q` behavior.
- Create/edit form validation matches backend contract.
- Audience segment/rule selection and persisted preview workflow.
- Admin route access and navigation preserve the Phase 4 Audience page.
- `npm run test`, `npm run typecheck`, and `npm run build` pass.

### Repeatable API verification

Add `scripts/api-tests/suites/phase5-campaign-verification.zsh`, reusing the Phase 3/4 API-test helpers. Cover
anonymous/MEMBER denial, ADMIN CRUD, validation failures, draft-only update, list/detail, preview, and CSRF. The script
must be macOS zsh compatible and must not require secrets to be committed.

## 9. Exit criteria

- [x] All Phase 5 scope items are implemented; no out-of-scope lifecycle or later-phase feature is present.
- [x] Campaign CRUD and campaign-scoped preview match the approved API/OpenAPI contract.
- [x] Audience integration uses only the Audience application-level interface.
- [x] Campaign validation, date handling, effective read status, and draft-only update behavior pass tests.
- [x] Admin campaign UI works alongside the Phase 4 Audience workspace.
- [x] Backend unit/PostgreSQL integration tests pass.
- [x] Frontend tests, typecheck, and production build pass.
- [x] Phase 5 zsh API suite passes.
- [x] API/OpenAPI, database docs (if schema changes), README, implementation plan, and implementation report are
  synchronized.
- [x] Owner reviews and accepts Phase 5 before Phase 6 can begin.

## 10. Dependencies and implementation sequence

1. Re-read the authoritative documents and inspect the accepted Phase 4 Audience application interface.
2. Apply the bounded interpretation in Section 7: use documented pagination/status filtering only; do not add `q`
   without separate owner approval.
3. Implement Campaign domain and application ports/use cases.
4. Implement JDBC persistence and transaction-safe DRAFT update.
5. Implement campaign controllers and campaign-scoped audience preview integration.
6. Add backend unit and PostgreSQL integration tests.
7. Add typed frontend API, campaign screens, navigation, and frontend tests.
8. Add the Phase 5 zsh API verification suite.
9. Run full regression and synchronize documentation.
10. Submit the Phase 5 implementation report and ZIP for owner verification; do not start Phase 6 until owner
    acceptance.

## 11. Approval gate

The owner approved this plan as written and authorized implementation on 2026-11-27. Implementation must remain within
this bounded scope. Any change to product scope, API semantics, schema, or phase sequencing requires explicit review.
Phase 5 was formally accepted by the owner on 2026-11-27. Phase 6 planning is documented in `PHASE_6_PLAN.md`; implementation remains gated on explicit owner approval of that plan.
