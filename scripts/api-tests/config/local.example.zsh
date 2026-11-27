# Optional per-developer API test settings.
# Copy this file to config/local.zsh (the copy is git-ignored).
# Use := so environment variables exported in the shell take precedence.

: ${API_BASE_URL:=http://localhost:8080/api/v1}
: ${API_TEST_ADMIN_EMAIL:=admin@example.test}
: ${API_TEST_MEMBER_EMAIL:=member.one@example.test}

# Keep false for local-only testing. Do not point destructive/security tests at production.
: ${API_TEST_ALLOW_NONLOCAL:=false}
