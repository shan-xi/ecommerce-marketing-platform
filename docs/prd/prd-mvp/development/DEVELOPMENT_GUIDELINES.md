# Development Guidelines

**Status:** Approved — applies to implementation under the v1.5.0 baseline.

## 1. General rules

- Follow `PRD.md`, architecture, database, API, and security documents as the project contract.
- Prefer the simplest design that satisfies the approved requirements.
- Do not introduce microservices, Redis, Kafka, external providers, or additional infrastructure without an approved
  ADR.
- Keep backend and frontend source under their respective top-level directories.
- Use UTC instants in persistence and APIs; display in the configured business timezone.
- Do not put real credentials or personal data in source control.

## 2. Backend conventions

- Java 21 and Spring Boot.
- Use constructor injection; avoid field injection.
- Validate all incoming DTOs at the boundary and enforce business invariants in application/domain logic.
- Keep REST DTOs separate from persistence entities and domain models.
- Use explicit transaction boundaries in application use cases.
- Use Flyway for schema evolution; never edit an already-applied migration.
- Use database constraints as the final defense for uniqueness and inventory invariants.
- Use `Clock` abstraction for time-dependent business rules and deterministic tests.
- Do not expose JPA entities directly from controllers.
- Use structured logs with correlation/trace IDs; never log secrets or tokens.

## 3. Frontend conventions

- React + TypeScript, strict TypeScript mode.
- One SPA with explicit `/admin`, `/member`, and `/campaigns/:campaignId` route groups.
- Keep API access in a typed API client layer; UI components should not construct endpoint URLs ad hoc.
- Treat backend authorization as authoritative; frontend route guards are UX only.
- Keep access/refresh tokens out of browser storage; rely on HttpOnly cookies and CSRF header contract.
- Handle 401 refresh once per request flow; avoid infinite refresh loops.
- Model loading, empty, success, and error states explicitly.
- Use generated OpenAPI types where practical; do not hand-maintain duplicated request/response definitions without
  reason.

## 4. Database conventions

- PostgreSQL is the source of truth.
- Use UUID IDs, foreign keys, check constraints, unique constraints, and indexes based on query patterns.
- Use transactions for multi-row business operations.
- Avoid N+1 queries and unbounded result sets.
- Use keyset/batched processing for large audience delivery rather than one huge request transaction.
- Never treat an analytics event as authoritative for coupon ownership.

## 5. API conventions

- Follow `../../api/API_SPECIFICATION.md` and `openapi.yaml`.
- Use consistent error envelope and stable error codes.
- Use correct HTTP status codes.
- Member endpoints derive member identity from the authenticated principal.
- Write endpoints authenticated by cookies must validate CSRF tokens.
- Claim endpoint must support idempotency and database-enforced uniqueness.

## 6. Code quality

- Keep methods and classes focused; avoid speculative abstractions.
- Prefer readable code over clever code.
- Document non-obvious concurrency and transaction behavior.
- Add tests with every feature and bug fix.
- Run formatting, static checks, unit tests, and integration tests before considering a phase complete.
- Do not suppress test failures or weaken assertions just to obtain a green build.

## 7. Change management

When implementation reveals a necessary design change:

1. Stop before implementing a breaking or scope-changing change.
2. Explain the discovered issue and evidence.
3. Propose the smallest viable change and alternatives.
4. Identify affected PRD/API/schema/architecture/tests.
5. Obtain owner approval.
6. Update the relevant document and ADR.
7. Implement and test against the updated contract.
