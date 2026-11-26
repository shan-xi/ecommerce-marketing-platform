# E-commerce Marketing Platform — Design Pack

**Package purpose:** Product and technical design documentation only.  
**Current phase:** Documentation / design review.  
**Implementation status:** Not started.

This repository contains the reviewed PRD and the proposed engineering documents that will guide a later AI-assisted implementation. The `backend/` and `frontend/` directories are intentionally empty placeholders. No application source code, migrations, build configuration, or running service has been created.

## Start here

1. [`docs/PRD.md`](docs/PRD.md) — product scope and acceptance criteria.
2. [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) — system architecture and technology choices.
3. [`docs/architecture/MODULE_DESIGN.md`](docs/architecture/MODULE_DESIGN.md) — module boundaries and Clean Architecture rules.
4. [`docs/database/DATABASE_DESIGN.md`](docs/database/DATABASE_DESIGN.md) and [`ERD.md`](docs/database/ERD.md) — proposed relational model.
5. [`docs/api/API_SPECIFICATION.md`](docs/api/API_SPECIFICATION.md) and [`openapi.yaml`](docs/api/openapi.yaml) — API contract.
6. [`docs/architecture/SECURITY_DESIGN.md`](docs/architecture/SECURITY_DESIGN.md) — authentication and authorization.
7. [`docs/development/IMPLEMENTATION_PLAN.md`](docs/development/IMPLEMENTATION_PLAN.md) — implementation phases and exit criteria.
8. [`docs/development/AI_DEVELOPMENT_GUIDELINES.md`](docs/development/AI_DEVELOPMENT_GUIDELINES.md) — rules for future AI development.
9. [`docs/OPEN_DESIGN_QUESTIONS.md`](docs/OPEN_DESIGN_QUESTIONS.md) — remaining decisions to confirm before development.

## Proposed repository layout

```text
ecommerce-campaign-platform/
├── backend/                       # Future Spring Boot application (empty for now)
├── frontend/                      # Future React + TypeScript application (empty for now)
├── docs/
│   ├── PRD.md
│   ├── architecture/
│   ├── api/
│   ├── database/
│   ├── development/
│   └── decisions/ADR/
├── .gitignore
└── README.md
```

## Document authority

- The PRD defines **what** the product must do and what is in/out of scope.
- Architecture, database, API, and security documents define the agreed technical direction and contracts.
- The implementation plan defines **in what order** work should be delivered.
- If documents conflict, do not guess. Raise the conflict, agree on the intended behavior, update the documents, and only then implement.

## Current design baseline

- Backend: Java 21 + Spring Boot, modular monolith, Clean Architecture principles.
- Frontend: one React + TypeScript SPA with separate Admin and Member route groups.
- Database: PostgreSQL.
- Authentication: Spring Security, short-lived JWT access token and rotating refresh token, both transported in secure HttpOnly cookies; CSRF protection required.
- MVP channel: in-app only; exactly one message per campaign; immediate delivery initiated on publication.
- Audience: reusable rule-based segments plus campaign-specific rules; complete immutable member snapshot committed during publication before delivery starts.
- Coupon claim: transactional inventory reservation and claim insert, database-enforced uniqueness; successful request retries replay the original claim.
- No Redis, Kafka, microservices, external channel provider, scheduled delivery, coupon redemption, or order/payment integration in the MVP.

## Status and caveats

All technical documents in this pack are proposed design contracts for review. Where exact behavior was not fully specified in the PRD, this pack records a recommended default and labels it as an assumption or decision to confirm. The product remains **Draft** until the owner explicitly approves the design and changes the PRD status to **Ready for Development**.
