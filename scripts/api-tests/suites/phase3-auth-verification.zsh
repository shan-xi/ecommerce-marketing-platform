#!/bin/zsh
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

api_prompt_password || exit 1

print ""
print "=========================================="
print " Phase 3 Authentication Verification"
print "=========================================="

# 1. CSRF negative test
anonymous=$(api_new_jar "suite-anonymous")
api_csrf_fetch "$anonymous" "suite-anonymous" || { api_summary; exit 1; }

body="$API_TEST_DIR/suite-no-csrf-request.json"
api_make_login_body "$API_TEST_ADMIN_EMAIL" "$body" || { api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/auth/login" "$anonymous" "$API_TEST_DIR/suite-no-csrf.json" \
  -H "Content-Type: application/json" --data-binary @"$body")
rm -f "$body"
api_record "Login without CSRF header" "$code" "403"

# 2. Admin login and role denial
admin=$(api_new_jar "suite-admin")
api_login_admin "$admin" "suite-admin-login" || { api_summary; exit 1; }
api_get_me "$admin" "suite-admin" "200"
code=$(api_request GET "$API_BASE_URL/member/probe" "$admin" "$API_TEST_DIR/suite-admin-member.json")
api_record "Admin accessing Member API" "$code" "403"

# 3. Refresh token rotation
api_refresh "$admin" "suite-admin"
api_get_me "$admin" "suite-admin-after-refresh" "200"

# 4. Logout
api_logout "$admin" "suite-admin"
api_get_me "$admin" "suite-admin-after-logout" "401"

# 5. Anonymous authorization
code=$(api_request GET "$API_BASE_URL/admin/probe" "$anonymous" "$API_TEST_DIR/suite-anonymous-admin.json")
api_record "Anonymous accessing Admin API" "$code" "401"
code=$(api_request GET "$API_BASE_URL/member/probe" "$anonymous" "$API_TEST_DIR/suite-anonymous-member.json")
api_record "Anonymous accessing Member API" "$code" "401"

# 6. Member role boundary
member=$(api_new_jar "suite-member")
api_login_member "$member" "suite-member-login" || { api_summary; exit 1; }
api_get_me "$member" "suite-member" "200"
code=$(api_request GET "$API_BASE_URL/admin/probe" "$member" "$API_TEST_DIR/suite-member-admin.json")
api_record "Member accessing Admin API" "$code" "403"

# 7. Replay test uses a completely separate login session.
replay=$(api_new_jar "suite-replay")
api_login_admin "$replay" "suite-replay-login" || { api_summary; exit 1; }
api_csrf_fetch "$replay" "suite-replay" || { api_summary; exit 1; }
old_jar="$API_TEST_DIR/suite-replay-old.cookies"
cp "$replay" "$old_jar"
chmod 600 "$old_jar"
old_csrf="$CSRF_TOKEN"

code=$(api_request POST "$API_BASE_URL/auth/refresh" "$replay" "$API_TEST_DIR/suite-replay-first.json" \
  -H "X-CSRF-TOKEN: $old_csrf")
api_record "First refresh" "$code" "200"

if [[ "$code" == "200" ]]; then
  code=$(api_request POST "$API_BASE_URL/auth/refresh" "$old_jar" "$API_TEST_DIR/suite-replay-second.json" \
    -H "X-CSRF-TOKEN: $old_csrf")
  api_record "Refresh token replay" "$code" "401"
fi

unset API_TEST_PASSWORD
api_summary
exit $?
