# API Test Scripts

macOS **zsh** scripts for repeatable local HTTP/API verification. Shared HTTP, CSRF, authentication, cookie-jar, and
assertion helpers are under `lib/`; feature-specific entry points are under `auth/`; combined workflows are under
`suites/`.

## Requirements

- macOS with zsh
- `curl`
- `python3`
- Project backend running locally at `http://localhost:8080`
- Local demo accounts initialized using `DEMO_SEED_PASSWORD`

The scripts use the project's existing API routes. They do not change backend/frontend source code or database schema.

## Configure

Optional:

```zsh
cp scripts/api-tests/config/local.example.zsh scripts/api-tests/config/local.zsh
```

Edit `local.zsh` if your local API URL or seed account emails differ. `local.zsh` is ignored by Git. You can also
override settings with environment variables:

```zsh
export API_BASE_URL="http://localhost:8080/api/v1"
export API_TEST_ADMIN_EMAIL="admin@example.test"
export API_TEST_MEMBER_EMAIL="member.one@example.test"
```

The password is not stored in the config. Each script that needs authentication prompts for it without echoing input.
The password is read from standard input by the JSON-body helper rather than passed as a command-line argument.
Temporary request bodies and cookie jars are created with restrictive permissions under
`/tmp/marketing-platform-api-test/`. The shared helper sets `umask 077` and enforces directory mode `700`.

## Run from the repository root

Complete Phase 3 authentication suite:

```zsh
zsh scripts/api-tests/suites/phase3-auth-verification.zsh
```

Individual tests:

```zsh
zsh scripts/api-tests/auth/csrf.zsh
zsh scripts/api-tests/auth/login.zsh
zsh scripts/api-tests/auth/me.zsh
zsh scripts/api-tests/auth/refresh.zsh
zsh scripts/api-tests/auth/logout.zsh
zsh scripts/api-tests/auth/role-boundary.zsh
zsh scripts/api-tests/auth/refresh-replay.zsh
```

Each individual script creates its own cookie jar and is independently executable. The complete suite uses separate
sessions for anonymous, admin, member, and refresh-replay scenarios.

## Test coverage

The Phase 3 suite checks:

1. CSRF cookie/header token match.
2. Login without CSRF header is rejected with HTTP 403.
3. Admin login and role in response.
4. Authenticated `/auth/me`.
5. Admin is forbidden from Member API (403).
6. Refresh token rotation and continued authenticated access.
7. Logout (204), followed by `/auth/me` (401).
8. Anonymous Admin and Member API access (401).
9. Member login and authenticated `/auth/me`.
10. Member is forbidden from Admin API (403).
11. Refresh-token replay is rejected (401).

## Safety

- The default target guard allows only `localhost` and `127.0.0.1`.
- Do not set `API_TEST_ALLOW_NONLOCAL=true` for production. This override exists only for deliberate non-production
  environments.
- Refresh replay intentionally resubmits an old refresh token. The server may revoke the associated token family; the
  replay scenario therefore uses a dedicated session.
- Cookie jars contain authentication cookies. Do not paste or upload files from the temporary test directory.
- The scripts print response status and PASS/FAIL only; they do not print cookie or JWT values.
- This suite is complementary to `./mvnw -f backend/pom.xml verify`; it does not replace backend integration tests.

## Shared helper API

| Helper           | Responsibility                                                                                          |
|------------------|---------------------------------------------------------------------------------------------------------|
| `lib/common.zsh` | Environment, local-target guard, curl/Python discovery, test output, cookie jar, request and assertions |
| `lib/csrf.zsh`   | Fetch CSRF token and verify it matches `XSRF-TOKEN` cookie                                              |
| `lib/auth.zsh`   | Login, Admin/Member login, `/me`, refresh and logout                                                    |
| `auth/`          | Independently runnable authentication tests                                                             |
| `suites/`        | Combined test workflows                                                                                 |

Future feature tests should add their own feature folder and reuse `lib/common.zsh` plus the relevant shared helpers.
Keep business-specific assertions in feature test scripts rather than duplicating curl, cookie, or PASS/FAIL logic.
