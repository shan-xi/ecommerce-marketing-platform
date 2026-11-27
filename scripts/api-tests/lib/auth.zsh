# Requires lib/common.zsh and lib/csrf.zsh.

api_login() {
  local email="$1" jar="$2" label="$3" expected_role="${4:-}"
  local body="$API_TEST_DIR/${label}-login-request.json"
  local output="$API_TEST_DIR/${label}-login.json"
  local code

  api_csrf_fetch "$jar" "$label" || return 1
  api_make_login_body "$email" "$body" || return 1

  code=$(api_request POST "$API_BASE_URL/auth/login" "$jar" "$output" \
    -H "Content-Type: application/json" \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
    --data-binary @"$body")

  rm -f "$body"
  api_record "Login ($label)" "$code" "200"
  [[ "$code" == "200" ]] || return 1

  if [[ -n "$expected_role" ]]; then
    api_assert_role "$output" "$expected_role"
  fi
}

api_login_admin() {
  api_login "$API_TEST_ADMIN_EMAIL" "$1" "${2:-admin}" "ADMIN"
}

api_login_member() {
  api_login "$API_TEST_MEMBER_EMAIL" "$1" "${2:-member}" "MEMBER"
}

api_get_me() {
  local jar="$1" label="$2" expected="$3"
  local output="$API_TEST_DIR/${label}-me.json"
  local code

  code=$(api_request GET "$API_BASE_URL/auth/me" "$jar" "$output")
  api_record "/auth/me ($label)" "$code" "$expected"
}

api_refresh() {
  local jar="$1" label="$2" expected="${3:-200}"
  local output="$API_TEST_DIR/${label}-refresh.json"
  local code

  api_csrf_fetch "$jar" "${label}-refresh" || return 1
  code=$(api_request POST "$API_BASE_URL/auth/refresh" "$jar" "$output" \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN")
  api_record "Refresh token ($label)" "$code" "$expected"
}

api_logout() {
  local jar="$1" label="$2" expected="${3:-204}"
  local output="$API_TEST_DIR/${label}-logout.json"
  local code

  api_csrf_fetch "$jar" "${label}-logout" || return 1
  code=$(api_request POST "$API_BASE_URL/auth/logout" "$jar" "$output" \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN")
  api_record "Logout ($label)" "$code" "$expected"
}
