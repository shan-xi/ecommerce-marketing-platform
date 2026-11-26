# E-commerce Marketing Platform — Design Pack

**Document package:** v1.5.0 Development Baseline

**Package purpose:** Product and technical design documentation only.  
**Current phase:** Phase 1 — Repository foundation (authorized / ready; not started).  
**Implementation status:** Not started.

This repository contains the owner-approved v1.5.0 Development Baseline. Phase 0 is complete and Phase 1 is authorized / ready to start, but the owner explicitly requested that implementation remain on hold until a separate start notification. The `backend/` and `frontend/` directories are intentionally empty placeholders. No application source code, migrations, build configuration, or running service has been created.

## Start here

1. [`docs/PRD.md`](docs/PRD.md) — product scope and acceptance criteria.
2. [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) — system architecture and technology choices.
3. [`docs/architecture/MODULE_DESIGN.md`](docs/architecture/MODULE_DESIGN.md) — module boundaries and Clean Architecture rules.
4. [`docs/database/DATABASE_DESIGN.md`](docs/database/DATABASE_DESIGN.md) and [`docs/database/ERD.md`](docs/database/ERD.md) — approved relational model baseline.
5. [`docs/api/API_SPECIFICATION.md`](docs/api/API_SPECIFICATION.md) and [`docs/api/openapi.yaml`](docs/api/openapi.yaml) — API contract.
6. [`docs/architecture/SECURITY_DESIGN.md`](docs/architecture/SECURITY_DESIGN.md) — authentication and authorization.
7. [`docs/development/IMPLEMENTATION_PLAN.md`](docs/development/IMPLEMENTATION_PLAN.md) — implementation phases and exit criteria.
8. [`docs/development/AI_DEVELOPMENT_GUIDELINES.md`](docs/development/AI_DEVELOPMENT_GUIDELINES.md) — rules for future AI development.
9. [`docs/OPEN_DESIGN_QUESTIONS.md`](docs/OPEN_DESIGN_QUESTIONS.md) — confirmed decisions and completed approval checklist.
10. [`docs/BASELINE_APPROVAL.md`](docs/BASELINE_APPROVAL.md) — owner approval record and Phase 1 execution hold.

## Proposed repository layout

```text
ecommerce-marketing-platform/
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

- The PRD defines **what** the product must do and what is in/out of scope. PRD v1.5.0 is the owner-approved Development Baseline and is `Ready for Development`.
- Architecture, database, API, and security documents define the approved technical direction and contracts for the v1.5.0 MVP baseline.
- The implementation plan in `docs/development/IMPLEMENTATION_PLAN.md` defines **in what order** work should be delivered and is the authoritative phase sequence.
- If documents conflict, do not guess. Raise the conflict, agree on the intended behavior, update the documents, and only then implement.

## Current design baseline

- Backend: Java 21 + Spring Boot, modular monolith, Clean Architecture principles.
- Frontend: one React + TypeScript SPA with separate Admin and Member route groups.
- Database: PostgreSQL.
- Authentication: Spring Security, short-lived JWT access token and rotating refresh token, both transported in secure HttpOnly cookies; CSRF protection required.
- MVP channel: in-app only; exactly one message per campaign; immediate delivery initiated on publication.
- Audience: reusable rule-based segments plus campaign-specific rules; complete immutable member snapshot committed during publication before delivery starts.
- Coupon claim: transactional inventory reservation and claim insert, database-enforced uniqueness; successful request retries replay the original claim. Public landing-page content is readable anonymously, but claiming requires member authentication.
- Audience: saved-segment and campaign-specific rules are combined with AND; the field/type/operator matrix and enum values are defined in PRD Section 4.2; NULL member attributes never match supported comparisons.
- Delivery: one worker, batches of 100, three automatic retries after the initial attempt per cursor (1s/2s/4s backoff), FAILED state and admin manual retry from the last committed cursor. Zero-member audience produces a COMPLETED job.
- Refresh tokens: one database row per token generation, grouped by token_family_id; reuse revokes the family. MVP defaults: access 15 minutes, refresh 7 days; environment-configurable.
- Audit/analytics: server-generated claim-attempt audit; successful claims are sourced only from coupon_claims; landing visitor rates are target-member based and no click-to-landing attribution is reported.
- Publication/delivery: campaigns may be published only within their active window (`starts_at <= now < ends_at`); `ENDED` is derived after the end time for a published campaign. Pause blocks new claims after its transaction commits but does not cancel the delivery job committed during publication, even while it is pending.
- Landing page: one fixed template with `headline` and `body`; no page builder in the MVP.
- No Redis, Kafka, microservices, external channel provider, scheduled delivery, coupon redemption, or order/payment integration in the MVP.

## Status and caveats

The PRD is **Ready for Development — v1.5.0 owner-approved baseline**. The API, database, architecture, security, ERD, testing contracts, and ADRs are approved as part of the same baseline. Phase 0 is complete; Phase 1 is authorized and ready, but **not started**. The owner requested that no development begin until a separate notification. The `backend/` and `frontend/` directories remain empty.
