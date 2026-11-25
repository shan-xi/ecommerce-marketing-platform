# ADR-003: PostgreSQL Transactions and Constraints for Coupon Claims

- **Status:** Proposed — pending owner approval
- **Date:** 2026-11-25

## Context

Coupon inventory is limited and many members may claim concurrently. Successful claims must never exceed configured quantity, and one member may claim a campaign coupon only once.

## Decision

Use PostgreSQL as source of truth. Reserve inventory with one conditional atomic UPDATE and insert the claim record in the same transaction. Enforce uniqueness with a database constraint on `(campaign_id, campaign_coupon_id, member_id)`. Support request retries with an idempotency key.

## Alternatives considered

- **Read inventory then decrement in application:** rejected due to race conditions.
- **Redis counter/lock:** not needed for MVP and would introduce a second consistency boundary.
- **Kafka-based claim processing:** unnecessary for synchronous claim response and MVP scope.

## Consequences

- PostgreSQL row-level locking/conditional update handles contention.
- Integration and concurrency tests must run against PostgreSQL.
- Claim transaction must be carefully tested for rollback after inventory reservation.
