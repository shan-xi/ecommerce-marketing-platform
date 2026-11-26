# AI Development Guidelines

**Status:** Approved operating contract for AI-assisted implementation under v1.5.0.  
**Current instruction:** Phase 1 was formally accepted by the owner on 2026-11-26. The owner explicitly authorized Phase 2 — Database Foundation on 2026-11-26; Phase 2 implementation is in progress and must remain within its approved scope. Phase 3 and later phases are not authorized.

## 1. Required reading order

Before writing or modifying code, read:

1. `docs/PRD.md`
2. `docs/architecture/ARCHITECTURE.md`
3. `docs/architecture/MODULE_DESIGN.md`
4. `docs/architecture/SECURITY_DESIGN.md`
5. `docs/database/DATABASE_DESIGN.md` and `docs/database/ERD.md`
6. `docs/api/API_SPECIFICATION.md`, `docs/api/openapi.yaml`, and `docs/api/ERROR_CODES.md`
7. `docs/development/DEVELOPMENT_GUIDELINES.md`
8. `docs/development/TESTING_STRATEGY.md`
9. `docs/development/IMPLEMENTATION_PLAN.md`
10. Relevant ADRs under `docs/decisions/ADR/`

If the requested task is narrow, read the relevant documents plus the PRD and development guidelines; do not assume prior chat context is a substitute for repository documentation.

## 2. Source-of-truth hierarchy

- PRD is authoritative for product scope, business rules, and acceptance criteria.
- API specification/OpenAPI are authoritative for HTTP contract.
- Database design/ERD are authoritative for intended persistence invariants.
- Architecture/module design are authoritative for boundaries and dependency direction.
- Security design is authoritative for authentication/authorization requirements.
- Implementation plan controls phase order.
- ADRs record approved decisions and rationale.

If two documents conflict, stop and report the exact conflict. Do not choose one silently.

## 3. Work protocol

For each requested implementation task:

1. State which phase and acceptance criteria the task belongs to.
2. Summarize the planned files/modules to change.
3. Identify dependencies and risks.
4. Implement only the requested phase/scope.
5. Add/update tests before claiming completion.
6. Run relevant tests and report exact commands/results.
7. Update docs/OpenAPI/ADR if the implementation changes a contract.
8. Summarize changed files, behavior, tests, known gaps, and next safe step.

Do not claim a test passed unless it was actually executed and its result observed.

## 4. Mandatory stop-and-ask conditions

Ask the owner before proceeding when:

- A requirement is ambiguous or conflicts with another document.
- A proposed change affects MVP scope or acceptance criteria.
- A new infrastructure dependency (Redis, Kafka, broker, external provider, microservice) appears necessary.
- A schema/API change is breaking or affects existing behavior.
- A security trade-off weakens authentication, authorization, CSRF, or data isolation.
- A concurrency approach cannot preserve the coupon inventory/claim invariants.
- A phase cannot meet its exit criteria without changing an approved design.

For routine, reversible implementation details within the documented design, choose a conservative default and record it where appropriate rather than asking about every small decision.

## 5. Prohibited shortcuts

- Do not implement the entire project in one response or one unreviewed batch.
- Do not skip tests or weaken tests to make a build green.
- Do not use in-memory databases as the only verification for PostgreSQL concurrency/constraint behavior.
- Do not trust client-supplied member IDs.
- Do not store JWTs in localStorage/sessionStorage.
- Do not treat event tracking as the source of truth for coupon claims.
- Do not perform inventory check-then-update without an atomic database operation.
- Do not use application-only duplicate checks without database unique constraints.
- Do not silently alter OpenAPI, database schema, or domain rules.
- Do not introduce technologies solely because they are familiar or fashionable.

## 6. Completion report template

Every completed implementation task should report:

- **Phase / scope:**
- **Implemented behavior:**
- **Files changed:**
- **Tests added/updated:**
- **Commands executed and results:**
- **Design/document changes:**
- **Known limitations:**
- **Recommended next task:**

## 7. Documentation maintenance

When code changes a documented behavior, update the appropriate document in the same task. For a significant architectural choice, add an ADR with context, decision, alternatives, consequences, and status. Do not rewrite unrelated docs or change the approved PRD status without explicit owner approval.
