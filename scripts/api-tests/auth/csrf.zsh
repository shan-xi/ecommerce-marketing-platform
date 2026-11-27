#!/bin/zsh
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"

jar=$(api_new_jar "csrf-single")
api_csrf_fetch "$jar" "csrf-single" || { api_summary; exit 1; }

# A CSRF cookie alone must not authorize a state-changing request without its header.
output="$API_TEST_DIR/csrf-negative-login.json"
code=$(api_request POST "$API_BASE_URL/auth/login" "$jar" "$output" \
  -H "Content-Type: application/json" \
  --data '{"email":"admin@example.test","password":"not-used"}')
api_record "Login without CSRF header" "$code" "403"

api_summary
exit $?
