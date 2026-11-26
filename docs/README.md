# Documentation Index

| Document | Purpose |
|---|---|
| [PRD](PRD.md) | Product goals, scope, business rules, user journeys, acceptance criteria |
| [Architecture](architecture/ARCHITECTURE.md) | System topology, component interactions, technology choices |
| [Module Design](architecture/MODULE_DESIGN.md) | Backend module ownership, use cases, dependency rules |
| [Security Design](architecture/SECURITY_DESIGN.md) | JWT cookies, refresh rotation, CSRF, RBAC, data isolation |
| [Database Design](database/DATABASE_DESIGN.md) | Approved tables, constraints, indexes, transactions |
| [ERD](database/ERD.md) | Entity relationships and cardinalities |
| [API Specification](api/API_SPECIFICATION.md) | Endpoint behavior, request/response conventions, auth contract |
| [OpenAPI](api/openapi.yaml) | Machine-readable approved HTTP contract |
| [Error Codes](api/ERROR_CODES.md) | Stable error codes and client behavior |
| [Development Guidelines](development/DEVELOPMENT_GUIDELINES.md) | Coding and repository conventions |
| [Testing Strategy](development/TESTING_STRATEGY.md) | Unit, integration, API, E2E, security, concurrency tests |
| [Implementation Plan](development/IMPLEMENTATION_PLAN.md) | Phased delivery and exit criteria |
| [Phase 1 Implementation Report](development/PHASE_1_IMPLEMENTATION_REPORT.md) | Phase 1 implementation, verification results, and owner acceptance record |
| [AI Development Guidelines](development/AI_DEVELOPMENT_GUIDELINES.md) | How future AI work must use and preserve the docs |
| [Design Decisions](OPEN_DESIGN_QUESTIONS.md) | Confirmed decisions and Ready-for-Development checklist |
| [Baseline Approval](BASELINE_APPROVAL.md) | Owner approval record and Phase 1 execution hold |
| [ADR directory](decisions/ADR/) | Decision records and rationale |

## Current document status

- `PRD.md`: Ready for Development — v1.5.0 owner-approved baseline.
- API specification, OpenAPI, database design, ERD, architecture, module design, security design, and testing strategy: Approved as part of the v1.5.0 baseline.
- ADR-001 through ADR-005: Accepted as part of the v1.5.0 baseline.
- Development guidelines and AI development guidelines: Approved and currently governing Phase 1 implementation.
- `IMPLEMENTATION_PLAN.md`: Approved; Phase 0 complete; Phase 1 complete and owner-accepted (2026-11-26); Phase 2 planned and awaiting explicit authorization.
- `OPEN_DESIGN_QUESTIONS.md`: all owner decisions and approval gates complete.
- `BASELINE_APPROVAL.md`: records the owner's original approval and pre-development hold. The hold was superseded by the owner's explicit Phase 1 start instruction on 2026-11-26.

Phase 1 is complete and formally accepted by the owner on 2026-11-26. The verification results are recorded in `development/PHASE_1_IMPLEMENTATION_REPORT.md`. Phase 2 remains planned only; this acceptance does not authorize Phase 2 development.
