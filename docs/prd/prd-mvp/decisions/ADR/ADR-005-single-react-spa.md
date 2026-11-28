# ADR-005: One React + TypeScript SPA for Admin and Member Experiences

- **Status:** Accepted — approved as part of the v1.5.0 Development Baseline
- **Date:** 2026-11-25

## Context

The product has three user-facing experiences: Admin Portal, Member Portal, and campaign landing page. The landing page
belongs to the member journey and does not require an independent deployment.

## Decision

Use one React + TypeScript application under `../../../../frontend`, with route-level separation for `/admin/**`,
`/member/**`, and
`/campaigns/:campaignId`.

## Alternatives considered

- **Three separate frontend applications:** rejected for MVP because it duplicates build/deployment/auth integration
  without a demonstrated need.
- **Separate Admin and Member apps:** possible future option if ownership/deployment needs diverge.

## Consequences

- Shared API client and auth flow can be reused.
- Route and component boundaries must remain clear.
- Backend authorization remains mandatory; frontend route guards are not security controls.
