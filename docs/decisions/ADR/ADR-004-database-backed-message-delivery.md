# ADR-004: Database-Backed Immediate In-App Message Delivery

- **Status:** Proposed — pending owner approval
- **Date:** 2026-11-25

## Context

The MVP sends exactly one in-app message per campaign, immediately after publication. Large audiences should not be materialized synchronously in the publish HTTP request. Delivery must be retryable and must not create duplicate inbox rows.

## Decision

On successful publication, persist an audience snapshot and a database-backed delivery job. A worker creates inbox rows in bounded batches. Enforce unique `(campaign_id, member_id)` inbox delivery. No Kafka or external messaging provider is used in the MVP.

## Alternatives considered

- **Synchronous delivery in publish request:** rejected due to long-running request and timeout risk.
- **Kafka:** deferred because the database-backed job is sufficient for the MVP and keeps operations simpler.

## Consequences

- Publication response indicates that delivery has been initiated, not necessarily completed.
- Worker progress, retries, and failures must be observable.
- Snapshot persistence must complete before delivery begins.
