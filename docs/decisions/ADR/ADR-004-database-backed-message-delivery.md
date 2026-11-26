# ADR-004: Database-Backed Immediate In-App Message Delivery

- **Status:** Reviewed — pending owner approval
- **Date:** 2026-11-25

## Context

The MVP sends exactly one in-app message per campaign, immediately after publication. Creating every member inbox row should not happen synchronously in the publish HTTP request. Delivery must be retryable and must not create duplicate inbox rows. The initial MVP/demo assumes the complete audience snapshot can be inserted in one publication transaction; large-scale resumable snapshot construction is not included.

## Decision

On successful publication, persist the complete audience snapshot and one database-backed delivery job in the same transaction. A single worker creates inbox rows in bounded batches and advances a persisted cursor in the same transaction as each batch. Enforce unique `(campaign_id, member_id)` inbox delivery. On restart, an interrupted job resumes from its last committed cursor. No worker lease, `FOR UPDATE SKIP LOCKED`, snapshot-job table, Kafka, or external messaging provider is used in the MVP. The single worker processes batches of 100. It allows an initial attempt plus three automatic retries at the same cursor, with 1s/2s/4s backoff; a fourth total failure marks the job `FAILED`. An administrator may retry a failed job from its last committed cursor. An empty audience creates a `COMPLETED` job at publication.

## Alternatives considered

- **Synchronous delivery in publish request:** rejected due to long-running request and timeout risk.
- **Kafka:** deferred because the database-backed job is sufficient for the MVP and keeps operations simpler.

## Consequences

- Publication response indicates that delivery has been initiated, not necessarily completed.
- Worker progress, retries, and failures must be observable through the delivery-status endpoint.
- Publication commits the delivery job together with the audience snapshot. Pausing a campaign prevents new coupon claims but does not cancel that committed job, even if it remains `PENDING` because the worker has not picked it up yet; the worker completes it and existing inbox rows are not retracted. This is the fixed MVP behavior.
- If measured audience size makes one snapshot transaction impractical, revisit snapshot construction as a separately approved future design.
- Snapshot persistence must complete before delivery begins.
