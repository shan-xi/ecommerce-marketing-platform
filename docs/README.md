# Documentation Index

| Document                                                                      | Purpose                                                                                                       |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| [PRD](PRD.md)                                                                 | Product goals, scope, business rules, user journeys, acceptance criteria                                      |
| [Architecture](architecture/ARCHITECTURE.md)                                  | System topology, component interactions, technology choices                                                   |
| [Module Design](architecture/MODULE_DESIGN.md)                                | Backend module ownership, use cases, dependency rules                                                         |
| [Security Design](architecture/SECURITY_DESIGN.md)                            | JWT cookies, refresh rotation, CSRF, RBAC, data isolation                                                     |
| [Database Design](database/DATABASE_DESIGN.md)                                | Approved tables, constraints, indexes, transactions                                                           |
| [ERD](database/ERD.md)                                                        | Entity relationships and cardinalities                                                                        |
| [API Specification](api/API_SPECIFICATION.md)                                 | Endpoint behavior, request/response conventions, auth contract                                                |
| [OpenAPI](api/openapi.yaml)                                                   | Machine-readable approved HTTP contract                                                                       |
| [Error Codes](api/ERROR_CODES.md)                                             | Stable error codes and client behavior                                                                        |
| [Development Guidelines](development/DEVELOPMENT_GUIDELINES.md)               | Coding and repository conventions                                                                             |
| [Testing Strategy](development/TESTING_STRATEGY.md)                           | Unit, integration, API, E2E, security, concurrency tests                                                      |
| [Implementation Plan](development/IMPLEMENTATION_PLAN.md)                     | Phased delivery and exit criteria                                                                             |
| [Phase 1 Implementation Report](development/PHASE_1_IMPLEMENTATION_REPORT.md) | Phase 1 implementation, verification results, and owner acceptance record                                     |
| [Phase 2 Implementation Report](development/PHASE_2_IMPLEMENTATION_REPORT.md) | Phase 2 database foundation implementation, owner verification, and acceptance record                         |
| [Phase 3 Plan](development/PHASE_3_PLAN.md)                                   | Authorized Identity and Security implementation scope and acceptance criteria                                 |
| [Phase 3 Implementation Report](development/PHASE_3_IMPLEMENTATION_REPORT.md) | Delivered Phase 3 implementation, final verification results, and owner acceptance record                     |
| [Phase 4 Plan](development/PHASE_4_PLAN.md)                                   | Member and audience scope, dependencies, acceptance criteria; planning only, not implementation authorization |
| [AI Development Guidelines](development/AI_DEVELOPMENT_GUIDELINES.md)         | How future AI work must use and preserve the docs                                                             |
| [Design Decisions](OPEN_DESIGN_QUESTIONS.md)                                  | Confirmed decisions and Ready-for-Development checklist                                                       |
| [ADR directory](decisions/ADR/)                                               | Decision records and rationale                                                                                |

## Current document status

- `PRD.md`: Ready for Development — v1.5.0 owner-approved baseline.
- API specification, OpenAPI, database design, ERD, architecture, module design, security design, and testing strategy:
  Approved as part of the v1.5.0 baseline.
- ADR-001 through ADR-005: Accepted as part of the v1.5.0 baseline.
- Development guidelines and AI development guidelines: Approved and governing the project; Phase 3 is accepted and
  Phase 4 implementation remains gated on explicit owner approval of the Phase 4 plan.
- `IMPLEMENTATION_PLAN.md`: Approved; Phase 0 complete; Phases 1 and 2 owner-accepted (2026-11-26); Phase 3
  owner-accepted (2026-11-27); Phase 4 planning draft prepared, owner approval pending.
- `PHASE_1_IMPLEMENTATION_REPORT.md`: Phase 1 formally accepted (2026-11-26).
- `PHASE_2_IMPLEMENTATION_REPORT.md`: Phase 2 formally accepted (2026-11-26).
- `PHASE_3_PLAN.md`: Owner-authorized implementation baseline.
- `PHASE_3_IMPLEMENTATION_REPORT.md`: Phase 3 is complete and formally accepted on 2026-11-27; owner verification
  results
  are recorded.
- `PHASE_4_PLAN.md`: Phase 4 Member and Audience planning draft; implementation is not authorized until owner approval.
- `OPEN_DESIGN_QUESTIONS.md`: records confirmed owner decisions, design approval, and phase acceptance gates.

Phases 1 and 2 were accepted on 2026-11-26. Phase 3 — Identity and Security was verified and formally accepted on
2026-11-27. Phase 4 — Member and Audience planning may proceed under the approved v1.5.0 baseline; implementation
remains
subject to explicit owner approval of `PHASE_4_PLAN.md`.
