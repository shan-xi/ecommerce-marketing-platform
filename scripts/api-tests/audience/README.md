# Phase 4 Audience API verification

Run the complete Phase 4 HTTP/API suite from the repository root:

```zsh
zsh scripts/api-tests/suites/phase4-audience-verification.zsh
```

The suite reuses the shared CSRF, cookie-jar, login, request, and assertion helpers in `../lib/`.
It prompts for `DEMO_SEED_PASSWORD` without echoing it. The default target is local only:
`http://localhost:8080/api/v1`.

Coverage:

1. Anonymous access to the Admin Audience Segment API returns HTTP 401.
2. MEMBER access to the Admin Audience Segment API returns HTTP 403.
3. ADMIN login and `/auth/me`.
4. Create a reusable Audience Segment (HTTP 201).
5. List and retrieve the created Segment.
6. Update the created Segment.
7. Preview audience rules and validate the response shape (`count`, `sample`).
8. Reject an unsupported field and unsupported operator (HTTP 400).

The API does not currently expose a Segment delete/archive endpoint. Therefore the script creates one uniquely
named verification Segment per successful run and intentionally leaves it in the database. It can be viewed
from the Admin Audience page. Response bodies and cookie jars are stored under
`/tmp/marketing-platform-api-test/` with restrictive local permissions; do not share cookie files.

Optional local config and environment overrides are documented in `../README.md`.
