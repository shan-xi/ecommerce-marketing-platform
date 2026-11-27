# Documentation Index

| Document                                                                      | Purpose                                                                                     |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [PRD](PRD.md)                                                                 | Product goals, scope, business rules, user journeys, acceptance criteria                    |
| [Architecture](architecture/ARCHITECTURE.md)                                  | System topology, component interactions, technology choices                                 |
| [Module Design](architecture/MODULE_DESIGN.md)                                | Backend module ownership, use cases, dependency rules                                       |
| [Security Design](architecture/SECURITY_DESIGN.md)                            | JWT cookies, refresh rotation, CSRF, RBAC, data isolation                                   |
| [Database Design](database/DATABASE_DESIGN.md)                                | Approved tables, constraints, indexes, transactions                                         |
| [ERD](database/ERD.md)                                                        | Entity relationships and cardinalities                                                      |
| [API Specification](api/API_SPECIFICATION.md)                                 | Endpoint behavior, request/response conventions, auth contract                              |
| [OpenAPI](api/openapi.yaml)                                                   | Machine-readable approved HTTP contract                                                     |
| [Error Codes](api/ERROR_CODES.md)                                             | Stable error codes and client behavior                                                      |
| [Development Guidelines](development/DEVELOPMENT_GUIDELINES.md)               | Coding and repository conventions                                                           |
| [Testing Strategy](development/TESTING_STRATEGY.md)                           | Unit, integration, API, E2E, security, concurrency tests                                    |
| [Implementation Plan](development/IMPLEMENTATION_PLAN.md)                     | Phased delivery and exit criteria                                                           |
| [Phase 1 Implementation Report](development/PHASE_1_IMPLEMENTATION_REPORT.md) | Phase 1 implementation, verification results, and owner acceptance record                   |
| [Phase 2 Implementation Report](development/PHASE_2_IMPLEMENTATION_REPORT.md) | Phase 2 database foundation implementation, owner verification, and acceptance record       |
| [Phase 3 Plan](development/PHASE_3_PLAN.md)                                   | Authorized Identity and Security implementation scope and acceptance criteria               |
| [Phase 3 Implementation Report](development/PHASE_3_IMPLEMENTATION_REPORT.md) | Delivered Phase 3 implementation, final verification results, and owner acceptance record   |
| [Phase 4 Plan](development/PHASE_4_PLAN.md)                                   | Accepted Member and Audience scope, implementation record, and owner acceptance |
| [Phase 4 Implementation Report](development/PHASE_4_IMPLEMENTATION_REPORT.md) | Phase 4 source implementation, verification results, and owner acceptance record          |
| [Phase 5 Plan](development/PHASE_5_PLAN.md)                                   | Draft Campaign Management plan for owner review; implementation not authorized             |
| [AI Development Guidelines](development/AI_DEVELOPMENT_GUIDELINES.md)         | How future AI work must use and preserve the docs                                           |
| [Design Decisions](OPEN_DESIGN_QUESTIONS.md)                                  | Confirmed decisions and Ready-for-Development checklist                                     |
| [ADR directory](decisions/ADR/)                                               | Decision records and rationale                                                              |

## Current document status

- `PRD.md`: Ready for Development — v1.5.0 owner-approved baseline.
- API specification, OpenAPI, database design, ERD, architecture, module design, security design, and testing strategy:
  Approved as part of the v1.5.0 baseline.
- ADR-001 through ADR-005: Accepted as part of the v1.5.0 baseline.
- Development guidelines and AI development guidelines: Approved and governing the project; Phases 1–4 are formally
  accepted.
- `IMPLEMENTATION_PLAN.md`: Approved; Phases 0–4 complete and owner-accepted; Phase 5 plan drafted and awaiting owner
  approval; Phase 5 implementation is not authorized.
- `PHASE_1_IMPLEMENTATION_REPORT.md`: Phase 1 formally accepted (2026-11-26).
- `PHASE_2_IMPLEMENTATION_REPORT.md`: Phase 2 formally accepted (2026-11-26).
- `PHASE_3_PLAN.md`: Owner-authorized implementation baseline.
- `PHASE_3_IMPLEMENTATION_REPORT.md`: Phase 3 is complete and formally accepted on 2026-11-27; owner verification
  results
  are recorded.
- `PHASE_4_PLAN.md`: Phase 4 Member and Audience plan approved and formally accepted on 2026-11-27.
- `PHASE_4_IMPLEMENTATION_REPORT.md`: records delivered scope, owner-reported verification, final review, and owner
  acceptance on 2026-11-27.
- `PHASE_5_PLAN.md`: draft Campaign Draft Management plan for owner review; no implementation authorization.
- `OPEN_DESIGN_QUESTIONS.md`: records confirmed owner decisions, design approval, and phase acceptance gates.

Phases 1 and 2 were accepted on 2026-11-26. Phases 3 and 4 were formally accepted on 2026-11-27. The Phase 4
implementation report records owner-run backend, frontend, runtime, browser-route, and API verification, final source
review, and documentation synchronization. Phase 5 planning is captured in `development/PHASE_5_PLAN.md` and awaits
owner approval. Campaign-scoped Audience Preview is assigned to Phase 5 integration; its existing API path and
request/response contract are preserved.
