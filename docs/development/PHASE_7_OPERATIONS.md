# Phase 7 — Worker Deployment and Recovery Runbook

## Deployment constraint

Run exactly **one** backend application instance/replica for this MVP. The delivery worker is in-process, and startup recovery changes every `PROCESSING` row in `campaign_delivery_jobs` back to `PENDING`. If a second instance starts while the first is actively delivering, its recovery can reset work owned by the live instance. The current design has no lease or multi-instance ownership protocol.

Do not scale the backend deployment horizontally until a separately reviewed and approved worker-ownership design is implemented. Frontend replicas are not subject to this worker constraint.

## Startup behavior

After the application becomes ready, `MessageDeliveryWorker` runs `recoverInterruptedJobs()`. A successful recovery is logged. If the recovery database operation fails, the worker logs an actionable error and throws an `IllegalStateException`; startup must fail rather than continue serving while previously `PROCESSING` jobs may be stranded.

## Recovery failure procedure

1. Keep the backend instance stopped; do not start a second instance as a workaround.
2. Inspect the startup log and identify the underlying database error.
3. Verify PostgreSQL is reachable and the application database user can update `campaign_delivery_jobs`.
4. Resolve the database/network/permission issue.
5. Restart the single backend instance. Startup recovery will reset interrupted `PROCESSING` jobs to `PENDING`, preserving each job's committed `cursor_member_id` and retry count.
6. Verify the log contains `Message delivery startup recovery completed.`
7. Inspect Admin Delivery Status and confirm affected jobs resume and eventually reach `COMPLETED` or a documented `FAILED` state.

## Operational cautions

- Do not manually reset all job rows while a worker is running.
- Do not run two backend instances against the same database with Phase 7 worker scheduling enabled.
- If a batch transaction fails, inbox inserts and cursor advancement roll back together. If failure-state persistence also fails, the row may remain `PROCESSING`; stop the single instance, resolve the database issue, and restart so startup recovery can return the job to `PENDING`.
- Multi-instance deployment, worker leases, `SKIP LOCKED`, and external queue infrastructure are outside the approved Phase 7 scope.
