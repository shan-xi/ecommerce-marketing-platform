# ADR-001: Modular Monolith with Clean Architecture Principles

- **Status:** Accepted — approved as part of the v1.5.0 Development Baseline
- **Date:** 2026-11-25

## Context

The MVP contains related business capabilities (identity, members, audience, campaigns, messaging, coupons, engagement, analytics). The project needs clear boundaries but does not require independent scaling/deployment of each capability.

## Decision

Use one Spring Boot deployable organized as a modular monolith. Apply Clean Architecture dependency direction within meaningful modules.

## Alternatives considered

- **Microservices:** rejected for MVP due to deployment, distributed transaction, and observability overhead.
- **Unstructured layered monolith:** rejected because it risks cross-module coupling and business logic leakage into controllers/repositories.

## Consequences

- Simpler local development and deployment.
- Clear domain ownership and test boundaries.
- Future extraction remains possible if justified, but is not a current requirement.
- Module boundaries must be enforced by code review/tests/conventions.
