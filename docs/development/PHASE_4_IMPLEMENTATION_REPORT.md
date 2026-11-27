# Phase 4 Implementation Report — Member and Audience

**Baseline:** Owner-approved v1.5.0 Development Baseline  
**Plan:** `PHASE_4_PLAN.md` (approved 2026-11-27)  
**Implementation authorization:** Explicitly granted by the owner on 2026-11-27  
**Status:** COMPLETE — TECHNICALLY VERIFIED AND FORMALLY OWNER-ACCEPTED (2026-11-27).

## 1. Scope delivered in this source snapshot

### Member profile

- Added `GET /api/v1/member/profile` for authenticated `MEMBER` principals.
- The member row is resolved from `UserPrincipal.userId`; no request-supplied member identifier is used.
- Returns the existing member profile and targeting attributes, with age derived using the `Asia/Taipei` calendar date.
- Existing role authorization continues to deny ADMIN and anonymous access.

### Audience rules and evaluator

- Added the version-1 `match: ALL` flat rule validator for exactly the eight documented fields.
- Enforces the documented operator matrix, scalar/array shapes, exact JSON types, enum allowlists, numeric bounds,
  decimal scale, and ISO-8601 offset timestamps.
- Normalizes DateTime values to UTC `OffsetDateTime` values for JDBC binding.
- Application services depend on application-layer repository/query ports; JDBC adapters own SQL execution and
  persistence.
- SQL field expressions and operators are selected only from server-owned mappings; all values use named parameters.
- Nullable `gender`, `lastOrderAt`, `lastLoginAt`, and derived `age` use explicit non-NULL guards so `!=` and
  `NOT IN` do not match NULL member attributes.
- One preview SQL statement returns count and sample under one PostgreSQL statement snapshot.
- Age is calculated once per preview using the `Asia/Taipei` date from the injected `Clock`.
- Evaluator accepts a saved-segment rule set and/or campaign-specific rule set and combines both with AND. It rejects
  the absence of both. No Campaign entity or temporary endpoint is introduced.

### Reusable segments

Implemented the approved endpoints:

- `POST /api/v1/admin/audience-segments`
- `GET /api/v1/admin/audience-segments`
- `GET /api/v1/admin/audience-segments/{segmentId}`
- `PUT /api/v1/admin/audience-segments/{segmentId}`
- `POST /api/v1/admin/audience-segments/preview`

Rules are validated before persistence. `created_by_user_id` is taken from the authenticated Admin principal. Listing
is paginated and ordered by `created_at DESC`. No DELETE or archive workflow was added.

### Frontend

- Added typed Audience API functions and TypeScript contracts.
- Added Admin segment listing, create/edit, rule editing, and preview result UI.
- Existing single SPA and cookie/CSRF authentication client are reused. UI role selection is only a UX boundary; backend
  Spring Security remains authoritative.

## 2. Files added

- `backend/src/main/java/com/example/marketingplatform/config/TimeConfiguration.java`
- `backend/src/main/java/com/example/marketingplatform/member/application/MemberProfileRepository.java`
- `backend/src/main/java/com/example/marketingplatform/member/application/MemberProfileService.java`
- `backend/src/main/java/com/example/marketingplatform/member/infrastructure/JdbcMemberProfileRepository.java`
- `backend/src/main/java/com/example/marketingplatform/member/presentation/MemberProfileController.java`
- `backend/src/main/java/com/example/marketingplatform/audience/domain/AudienceRuleSet.java`
- `backend/src/main/java/com/example/marketingplatform/audience/domain/InvalidAudienceRuleException.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudienceRuleValidator.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudienceEvaluator.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudiencePreviewResult.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudienceMemberQuery.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudienceSegmentRepository.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudienceUseCase.java`
- `backend/src/main/java/com/example/marketingplatform/audience/infrastructure/JdbcAudienceMemberQuery.java`
- `backend/src/main/java/com/example/marketingplatform/audience/infrastructure/JdbcAudienceSegmentRepository.java`
- `backend/src/main/java/com/example/marketingplatform/audience/application/AudienceSegmentService.java`
- `backend/src/main/java/com/example/marketingplatform/audience/presentation/AudienceSegmentController.java`
- `backend/src/test/java/com/example/marketingplatform/member/MemberProfileIntegrationTest.java`
- `backend/src/test/java/com/example/marketingplatform/audience/AudienceRuleValidatorTest.java`
- `backend/src/test/java/com/example/marketingplatform/audience/AudienceEvaluationIntegrationTest.java`
- `backend/src/test/java/com/example/marketingplatform/audience/AudienceAgeTimezoneIntegrationTest.java`
- `backend/src/test/java/com/example/marketingplatform/audience/AudienceSegmentApiIntegrationTest.java`
- `backend/src/test/java/com/example/marketingplatform/audience/AudienceRuleValidatorTest.java` — expanded positive
  operator matrix across numeric fields.
- `frontend/src/shared/types/audience.ts`
- `frontend/src/shared/lib/audienceApi.ts`
- `frontend/src/features/audience/AudienceAdmin.tsx`

## 3. Existing files changed

- `backend/pom.xml` — validation and Spring Security test dependencies.
- `backend/src/main/java/com/example/marketingplatform/identity/presentation/ApiExceptionHandler.java` — documented
  `INVALID_AUDIENCE_RULE`, `RESOURCE_NOT_FOUND`, and validation error responses.
- `frontend/src/app/App.tsx` — Phase 4 status and role-specific UI integration.
- `frontend/src/styles.css` — audience-management styles.
- `frontend/scripts/foundation.test.mjs` — Phase 4 frontend contract checks; whitespace-tolerant field assertions.
- `docs/api/API_SPECIFICATION.md` — member profile and audience response details.
- `docs/api/openapi.yaml` — response schemas and explicit 400/401/403/404 responses for Phase 4 member/audience
  endpoints.
- `docs/development/PHASE_4_PLAN.md` — technical verification and owner-acceptance gate updated.
- `docs/development/AI_DEVELOPMENT_GUIDELINES.md` — records explicit Phase 4 authorization.
- `docs/development/IMPLEMENTATION_PLAN.md` — Phase 4 status updated to technically verified; formal owner acceptance
  pending.
- `README.md` — Phase 4 implementation and verification status updated.
- `docs/README.md` — documentation index and current phase status updated.
- `docs/OPEN_DESIGN_QUESTIONS.md` — Phase 4 authorization and verification status synchronized.
- `scripts/api-tests/suites/phase4-audience-verification.zsh` — repeatable Phase 4 HTTP/API verification suite.
- `scripts/api-tests/audience/README.md` — Phase 4 API test scope and execution notes.

## 4. Database and infrastructure impact

- No migration was added or modified.
- Existing `members` and `audience_segments` tables are reused.
- No new runtime infrastructure (Redis, Kafka, external provider, microservice, etc.) was added.
- Existing Authentication, JWT cookies, CSRF, CORS, and role matchers were reused.
- Campaign-scoped audience preview remains deferred to Phase 5; campaign snapshot persistence remains Phase 7.

## 5. Verification results

The following results were executed by the owner in the local macOS/Docker environment and supplied in the project
review conversation. They are recorded as owner-reported results, not as tests run by the implementation environment.

| Check                                                           | Owner-reported result                                                                   |
|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `./mvnw -f backend/pom.xml clean verify`                        | PASS — backend unit and PostgreSQL integration tests; 38 tests, 0 failures/errors/skips |
| `npm ci`                                                        | PASS — 29 packages installed; 0 vulnerabilities                                         |
| `npm run test`                                                  | PASS — 10 tests, 10 passed                                                              |
| `npm run typecheck`                                             | PASS                                                                                    |
| `npm run build`                                                 | PASS — Vite production build completed                                                  |
| `zsh scripts/api-tests/suites/phase4-audience-verification.zsh` | PASS — 27 assertions, 0 failures                                                        |
| PostgreSQL / Spring Boot startup                                | PASS — PostgreSQL healthy; backend connected and Flyway schema was up to date           |
| `/actuator/health`, `/readyz`, `/livez`                         | PASS — all returned HTTP 200 / UP                                                       |
| Browser Admin route                                             | PASS — ADMIN login and `/admin` render the Phase 4 Audience Segments interface          |

The Phase 4 API suite verified anonymous access (401), MEMBER denial (403), ADMIN login, CSRF rejection,
segment create/list/detail/update, reusable audience preview response shape, and invalid field/operator rejection.
The suite intentionally leaves one uniquely named verification Segment per successful run because the approved API
does not expose a delete/archive endpoint.

### Final source review and corrections in this review

- Confirmed that the implementation remains within the approved Phase 4 boundary. Campaign CRUD, publication,
  campaign-scoped preview HTTP integration, snapshot persistence, coupons, and delivery workflows were not added.
- Confirmed that audience SQL column/operator fragments are selected from server-owned allowlists and all rule values
  are bound parameters.
- Confirmed that nullable attributes and derived age use explicit non-NULL guards, and preview count/sample are
  produced from one SQL statement snapshot.
- Confirmed that member profile lookup is based on the authenticated principal's user ID; no client-selected member ID
  is accepted by the controller.
- Corrected brittle frontend source-contract assertions: field declarations now allow normal whitespace, and the
  conditions check now matches the actual canonicalization (`rules.conditions.map(parseCondition)`). Expanded backend
  validator positive coverage so every supported operator is exercised for all numeric fields (age, orderCount,
  totalSpending); `AudienceRuleValidatorTest.java` is therefore also modified in this review. These were test
  coverage/assertion improvements, not runtime UI defects.
- Synchronized OpenAPI responses for the Phase 4 Admin endpoints with the verified 400/401/403/404 behavior.
- No database migration, runtime dependency, or infrastructure change was required.

Independent checks run during this final review:

- `node --test frontend/scripts/*.test.mjs` — PASS, 10/10 after correcting the two brittle source-contract assertions.
- `backend/pom.xml` parsed as valid XML.
- `docs/api/openapi.yaml` parsed as valid YAML; all 28 paths were present.
- `npm ci` could not be completed in this review container because dependency installation timed out; Docker and zsh
  are also unavailable here. The owner's successful Maven, npm, Docker, and zsh results above remain the runtime
  verification evidence.

## 6. Acceptance record and remaining phase boundaries

- Technical verification is complete based on the owner-reported results above.
- The owner explicitly approved the Phase 4 modifications and formally accepted Phase 4 on 2026-11-27.
- Phase 4 is closed. Phase 5 planning is recorded separately in `PHASE_5_PLAN.md`; Phase 5 implementation still
  requires owner approval of that plan and an explicit instruction to begin.
- The Member profile capability is an authenticated API only; Phase 4 does not include a Member Portal profile screen.
- Campaign-scoped preview is a Phase 5 integration task; campaign audience snapshot persistence remains Phase 7.
