# Phase 4 Plan — Member and Audience

**Document status:** APPROVED — owner approved this Phase 4 plan on 2026-11-27.  
**Implementation status:** TECHNICALLY VERIFIED — owner formal acceptance pending. The owner explicitly authorized Phase
4 implementation on 2026-11-27. Work is
limited to the approved scope and sequencing.  
**Baseline:** Owner-approved v1.5.0 Development Baseline.  
**Preceding phases:** Phases 1, 2, and 3 formally accepted.  
**Purpose:** Define the bounded Phase 4 work package using only the current approved project documents. This plan does
not change the PRD, API contract, database model, architecture, or MVP scope. The owner-approved sequencing decision
places the campaign-scoped preview endpoint in Phase 5; its existing API contract remains unchanged.

## 1. Objective

Deliver the member-profile lookup and audience-management foundation required by later campaign and publication phases:

- Retrieve the authenticated member's profile from the existing `members` record.
- Validate audience-rule documents against the canonical allowlist of fields, types, operators, and values.
- Evaluate supported audience conditions against seeded member attributes.
- Create, update, list, retrieve, and preview reusable audience segments.
- Implement the reusable audience evaluator so it supports the documented composition semantics: a saved segment and
  campaign-specific rules are combined with AND; either one may be used alone, and at least one audience definition is
  required.
- Provide the Admin audience-management UI specified by the implementation plan.

Phase 4 delivers the reusable-segment preview endpoint and the underlying audience composition capability. The
campaign-scoped endpoint is integrated in Phase 5 after Campaign CRUD/read access is available.

Phase 4 does **not** persist the campaign audience snapshot. Snapshot persistence and its atomic coordination with the
delivery job belong to Phase 7.

## 2. Authoritative source documents

This plan is subordinate to, and must be implemented strictly according to, these existing documents:

1. `docs/PRD.md` — especially Sections 2, 3, 4.2, 6.3, 10.3, 12, and 13.
2. `docs/api/API_SPECIFICATION.md` — Sections 6 and 9, including the audience-segment and member-profile endpoints.
3. `docs/api/openapi.yaml` — machine-readable API contract.
4. `docs/api/ERROR_CODES.md` — stable error response codes.
5. `docs/database/DATABASE_DESIGN.md` — `members`, `audience_segments`, audience validation matrix, and JSON rule
   contract.
6. `docs/database/ERD.md` — existing member and audience relationships.
7. `docs/architecture/ARCHITECTURE.md` and `docs/architecture/MODULE_DESIGN.md` — modular-monolith boundaries and
   `member` / `audience` ownership.
8. `docs/development/DEVELOPMENT_GUIDELINES.md`, `docs/development/AI_DEVELOPMENT_GUIDELINES.md`, and
   `docs/development/TESTING_STRATEGY.md`.
9. `docs/development/IMPLEMENTATION_PLAN.md` and the accepted ADRs.

If these documents conflict, implementation must stop and the conflict must be raised for owner resolution. Do not
silently invent a new rule, endpoint, field, operator, table, infrastructure component, or UI capability.

## 3. In-scope capabilities

### 3.1 Member profile lookup

- Implement `GET /api/v1/member/profile` as specified in the API document.
- Require an authenticated `MEMBER` principal.
- Resolve the member record from the authenticated principal's user ID on the server.
- Do not accept a member ID in the request to select whose profile is returned.
- Use the existing `members` table and its current fields. Do not add member registration, password management,
  account provisioning, admin member-management screens, or external member-system integration.

### 3.2 Audience rule validation and evaluation

Implement the version-1 rule JSON contract with `match: ALL` and a flat `conditions` array. Nested groups, arbitrary
SQL,
client-provided SQL identifiers/expressions, Boolean values, and explicit NULL-check operators are not supported.

The validator and evaluator must use the exact PRD / Database Design allowlist:

| Field            | Type              | Allowed operators                               | Value constraints                             |
|------------------|-------------------|-------------------------------------------------|-----------------------------------------------|
| `age`            | Integer           | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | 0–120; derived from `birth_date`              |
| `gender`         | Enum              | `=`, `!=`, `IN`, `NOT IN`                       | `FEMALE`, `MALE`, `OTHER`, `UNDISCLOSED`      |
| `registeredAt`   | DateTime          | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 offset timestamp                     |
| `membershipTier` | Enum              | `=`, `!=`, `IN`, `NOT IN`                       | `STANDARD`, `SILVER`, `GOLD`, `PLATINUM`      |
| `orderCount`     | Integer           | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | Integer >= 0                                  |
| `totalSpending`  | Decimal           | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | TWD decimal >= 0; at most 2 fractional digits |
| `lastOrderAt`    | Nullable DateTime | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 offset timestamp                     |
| `lastLoginAt`    | Nullable DateTime | `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN` | ISO-8601 offset timestamp                     |

Validation/evaluation rules:

- Scalar comparison operators require one non-null scalar of the exact field type.
- `IN` and `NOT IN` require a non-empty array of non-null values of the exact field type.
- Enum values and comparisons are case-sensitive; ordered comparisons are invalid for enum fields.
- Reject unsupported fields/operators, wrong JSON types, invalid enum values, out-of-range numbers, and malformed
  timestamps.
- NULL member attributes never match any supported comparison or set operator, including `!=` and `NOT IN`.
- `age` is NULL when `birth_date` is NULL.
- Use `Asia/Taipei` calendar date to derive age. Preview uses the date/time of that preview; publication will later use
  the
  actual publication date, as defined by the PRD.
- Construct SQL only from server-owned allowlisted field/operator mappings and bound values. Never interpolate
  client-provided identifiers or expressions.

### 3.3 Reusable audience segments

Implement the API operations already specified:

- `POST /api/v1/admin/audience-segments`
- `GET /api/v1/admin/audience-segments`
- `GET /api/v1/admin/audience-segments/{segmentId}`
- `PUT /api/v1/admin/audience-segments/{segmentId}`
- `POST /api/v1/admin/audience-segments/preview`

Use the existing `audience_segments` table and its documented columns: `name`, `description`, `rules`,
`created_by_user_id`, timestamps, and optional `archived_at`.

- Segment rules must pass the same canonical validator used by preview and campaign audience composition.
- Preserve the authenticated administrator as `created_by_user_id`.
- Do not add a DELETE endpoint or a new archive workflow; the API specification does not define one. Respect the
  existing
  optional `archived_at` data model without inventing new externally visible behavior.
- Segment listing/detail responses and preview samples must follow the API contract and avoid exposing unnecessary
  member attributes.

### 3.4 Audience preview and composition

- Segment preview returns the matching member count and may return a small sample of non-sensitive member display
  identifiers, as allowed by the API specification.
- Support campaign-specific audience rules without requiring them to be saved as a reusable segment.
- When both a saved segment and campaign-specific rules are supplied, calculate their intersection (AND).
- If only one is supplied, use that definition. At least one is required.
- Apply the same rule-composition semantics to preview and the later publication-time audience resolution.
- A preview is an estimate. The authoritative member set is resolved at publication time in Phase 7.

**Phase boundary — campaign-scoped preview is deferred to Phase 5:** The API specification defines
`POST /api/v1/admin/campaigns/{campaignId}/audience-preview`, but the campaign draft/read model is delivered in Phase 5.
By owner-approved sequencing, Phase 4 does not implement this campaign-scoped route. Phase 4 must not implement campaign
CRUD, create temporary campaign records, or invent a temporary API.

Phase 4 must nevertheless implement and test the reusable audience evaluator's composition capability: a supplied
saved segment and campaign-specific rules are combined with AND; either may be used alone; absence of both is rejected.
Tests may provide these two definitions directly to the evaluator/application use case without creating a Campaign
record.

In Phase 5, after Campaign CRUD and campaign retrieval are available, implement the already-documented
`POST /api/v1/admin/campaigns/{campaignId}/audience-preview` endpoint. The Campaign module must load the campaign
through its own read model and call the Audience module's application-level interface to evaluate the saved segment
and campaign rules. Do not access another module's repository directly. Preserve the existing API path and
request/response contract; do not add a temporary endpoint or alter OpenAPI/API specifications for this sequencing
decision.

## 4. Frontend scope

- Add the Admin audience-management UI to the existing single React + TypeScript SPA.
- Support the documented reusable-segment operations and audience preview.
- Use the existing authentication/session foundation and Admin role boundary.
- Keep API calls typed and follow the existing frontend structure and conventions.
- Do not create a second SPA, Page Builder, generic rule-builder platform, or UI features not required by the existing
  PRD
  and API contract.
- The Phase 4 plan does not define a Member Portal profile screen; the committed Phase 4 member-facing capability is the
  documented profile API. Do not infer additional UI requirements.

## 5. Explicit exclusions and phase boundaries

- No campaign CRUD, draft lifecycle, publish, or pause implementation — Phase 5 / Phase 7.
- No final `campaign_audience_members` snapshot persistence — Phase 7, in the same transaction as publication and the
  delivery job.
- No coupon configuration, claims, inventory mutation, or claim audit orchestration — Phase 6.
- No message delivery worker, inbox, landing page, engagement events, or analytics — later phases.
- No new database table or infrastructure is planned. Reuse the schema created in Phase 2. If implementation discovers a
  concrete schema defect, stop and document it for owner review before changing migrations.
- No Redis, Kafka, microservices, external member provider, or other infrastructure.
- No account registration, password reset, email verification, MFA, social login, or admin user-management UI.
- No scope expansion beyond the approved v1.5.0 baseline.

## 6. Dependencies and implementation preflight

Before coding:

1. Re-read all authoritative sources in Section 2.
2. Inspect the exact accepted Phase 1–3 code and current database schema; do not assume a module or repository already
   exists just because a package is shown in the conceptual architecture.
3. Confirm how the current security configuration exposes authenticated `MEMBER` and `ADMIN` principals to the new
   endpoints.
4. Confirm the existing member seed attributes and `audience_segments` schema against the database design.
5. Follow the owner-approved sequencing in Section 3.4: implement core audience composition and reusable-segment
   preview in Phase 4; defer the campaign-scoped preview endpoint to Phase 5, without changing its API contract.
6. Confirm error codes and OpenAPI request/response schemas before implementation.
7. Prepare backend unit/integration tests and frontend tests for the acceptance criteria below.
8. Do not add migrations unless a documented schema mismatch is proven and the owner explicitly approves the correction.

## 7. Required tests and acceptance criteria

### Member profile

- [x] Anonymous requests are rejected with the documented authentication response.
- [x] An authenticated MEMBER receives only their own profile.
- [x] A caller cannot select another member by supplying a member ID.
- [x] ADMIN is not granted MEMBER-only profile access unless the existing API/security contract explicitly says
  otherwise.

### Audience validator and evaluator

- [x] All eight fields and every allowed operator are covered by positive tests.
- [x] Unsupported field/operator combinations are rejected.
- [x] Scalar versus array shapes, exact types, enum allowlists, integer/decimal bounds, and timestamp parsing are
  tested.
- [x] Enum comparisons are case-sensitive; ordered enum comparisons are rejected.
- [x] NULL attributes do not match any supported operator, including `!=` and `NOT IN`.
- [x] Explicit NULL checks and NULL values/array elements are rejected.
- [x] Age is derived using the `Asia/Taipei` date and the correct preview date.
- [x] SQL is generated only from server-owned allowlisted mappings and bound values.

### Segments and preview

- [x] Admin-only segment create/list/detail/update and preview APIs follow the documented contract.
- [x] Invalid rules cannot be persisted.
- [x] Preview count and any sample are consistent with the matching member set.
- [x] Preview does not expose unnecessary member attributes.
- [x] Segment and campaign-specific rules combine with AND in the reusable audience evaluator; either one alone works;
  absence of both is rejected.
- [x] The campaign-scoped preview endpoint is explicitly deferred to Phase 5; Phase 4 does not add a temporary endpoint
  or change the existing API contract.

### Frontend and regression

- [x] Admin audience UI supports the approved segment and preview workflows.
- [x] Frontend tests, TypeScript typecheck, and production build pass.
- [x] Backend unit, PostgreSQL integration, and API/security tests pass.
- [x] Phase 1–3 regression tests remain green.
- [x] API Specification and OpenAPI are synchronized with the implemented contract.
- [x] Database design/ERD and migrations remain consistent.
- [x] README setup and verification instructions remain reproducible.

## 8. Exit gate and authorization

Technical acceptance criteria and regression checks are recorded as passed in `PHASE_4_IMPLEMENTATION_REPORT.md` based
on
owner-run verification. Phase 4 is not formally closed until the owner explicitly accepts the result; Phase 5 must not
begin before that acceptance.

**Plan approval record:** The owner approved this Phase 4 plan on 2026-11-27.

**Implementation authorization:** The owner explicitly instructed implementation to begin on 2026-11-27. The approved
plan has been implemented and technically verified as recorded in the implementation report. Formal owner
acceptance remains a separate gate; this does not authorize Phase 5 or any scope beyond this plan.

The Phase 4 implementation must follow the approved sequencing in Section 3.4. Campaign-scoped preview is a Phase 5
integration task and must retain the existing API contract.
