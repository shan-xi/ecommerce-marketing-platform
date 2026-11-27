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
| [Phase 4 Plan](development/PHASE_4_PLAN.md)                                   | Owner-approved Member and Audience scope, dependencies, sequencing, and acceptance criteria |
| [Phase 4 Implementation Report](development/PHASE_4_IMPLEMENTATION_REPORT.md) | Phase 4 source implementation, verification status, and remaining acceptance gates          |
| [AI Development Guidelines](development/AI_DEVELOPMENT_GUIDELINES.md)         | How future AI work must use and preserve the docs                                           |
| [Design Decisions](OPEN_DESIGN_QUESTIONS.md)                                  | Confirmed decisions and Ready-for-Development checklist                                     |
| [ADR directory](decisions/ADR/)                                               | Decision records and rationale                                                              |

## Current document status

- `PRD.md`: Ready for Development — v1.5.0 owner-approved baseline.
- API specification, OpenAPI, database design, ERD, architecture, module design, security design, and testing strategy:
  Approved as part of the v1.5.0 baseline.
- ADR-001 through ADR-005: Accepted as part of the v1.5.0 baseline.
- Development guidelines and AI development guidelines: Approved and governing the project; Phases 1–3 are accepted and
  Phase 4 technical verification is complete, pending formal owner acceptance.
- `IMPLEMENTATION_PLAN.md`: Approved; Phase 0 complete; Phases 1–3 owner-accepted; Phase 4 implemented and technically
  verified from owner-run checks; formal owner acceptance pending.
- `PHASE_1_IMPLEMENTATION_REPORT.md`: Phase 1 formally accepted (2026-11-26).
- `PHASE_2_IMPLEMENTATION_REPORT.md`: Phase 2 formally accepted (2026-11-26).
- `PHASE_3_PLAN.md`: Owner-authorized implementation baseline.
- `PHASE_3_IMPLEMENTATION_REPORT.md`: Phase 3 is complete and formally accepted on 2026-11-27; owner verification
  results
  are recorded.
- `PHASE_4_PLAN.md`: Phase 4 Member and Audience plan approved on 2026-11-27; technical acceptance criteria verified.
  Formal owner acceptance remains pending. Campaign-scoped preview remains Phase 5.
- `OPEN_DESIGN_QUESTIONS.md`: records confirmed owner decisions, design approval, and phase acceptance gates.

Phases 1 and 2 were accepted on 2026-11-26. Phase 3 — Identity and Security was verified and formally accepted on
2026-11-27. The Phase 4 plan was approved and implementation explicitly authorized on 2026-11-27. Owner-run backend,
frontend, runtime,
browser-route, and API verification have passed; final source review and documentation synchronization are recorded in
the Phase 4 implementation report. Formal owner acceptance remains pending. Campaign-scoped Audience Preview is assigned
to Phase 5 integration; the existing API contract remains unchanged.
