# Documentation Index

| Document | Purpose |
|---|---|
| [PRD](PRD.md) | Product goals, scope, business rules, user journeys, acceptance criteria |
| [Architecture](architecture/ARCHITECTURE.md) | System topology, component interactions, technology choices |
| [Module Design](architecture/MODULE_DESIGN.md) | Backend module ownership, use cases, dependency rules |
| [Security Design](architecture/SECURITY_DESIGN.md) | JWT cookies, refresh rotation, CSRF, RBAC, data isolation |
| [Database Design](database/DATABASE_DESIGN.md) | Proposed tables, constraints, indexes, transactions |
| [ERD](database/ERD.md) | Entity relationships and cardinalities |
| [API Specification](api/API_SPECIFICATION.md) | Endpoint behavior, request/response conventions, auth contract |
| [OpenAPI](api/openapi.yaml) | Machine-readable draft HTTP contract |
| [Error Codes](api/ERROR_CODES.md) | Stable error codes and client behavior |
| [Development Guidelines](development/DEVELOPMENT_GUIDELINES.md) | Coding and repository conventions |
| [Testing Strategy](development/TESTING_STRATEGY.md) | Unit, integration, API, E2E, security, concurrency tests |
| [Implementation Plan](development/IMPLEMENTATION_PLAN.md) | Phased delivery and exit criteria |
| [AI Development Guidelines](development/AI_DEVELOPMENT_GUIDELINES.md) | How future AI work must use and preserve the docs |
| [Design Decisions](OPEN_DESIGN_QUESTIONS.md) | Confirmed decisions and Ready-for-Development checklist |
| [ADR directory](decisions/ADR/) | Decision records and rationale |

## Current document status

- `PRD.md`: Draft — Development Baseline v1.5.0, pending owner approval.
- API specification, OpenAPI, database design, ERD, architecture, module design, security design, testing strategy, and ADR-004: Reviewed — pending owner approval; synchronized to the v1.5.0 baseline.
- Development guidelines, AI development guidelines, implementation plan, and ADR-001/002/003/005: Proposed — pending owner approval / planning only.
- `OPEN_DESIGN_QUESTIONS.md`: six owner decisions confirmed; v1.5.0 MVP defaults adopted; final Design Pack approval remains pending.

These statuses describe document review only. No document authorizes implementation until the owner approves the complete baseline, marks the PRD `Ready for Development`, and explicitly authorizes Phase 1.
