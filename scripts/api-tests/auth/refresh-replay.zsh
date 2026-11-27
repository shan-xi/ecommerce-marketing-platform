#!/bin/zsh
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

api_prompt_password || exit 1
jar=$(api_new_jar "replay-current")
api_login_admin "$jar" "replay-login" || { api_summary; exit 1; }
api_csrf_fetch "$jar" "replay-csrf" || { api_summary; exit 1; }

# Preserve the original refresh cookie and its matching CSRF cookie before rotation.
old_jar="$API_TEST_DIR/replay-old.cookies"
cp "$jar" "$old_jar"
chmod 600 "$old_jar"
old_csrf="$CSRF_TOKEN"

code=$(api_request POST "$API_BASE_URL/auth/refresh" "$jar" "$API_TEST_DIR/replay-first-refresh.json" \
  -H "X-CSRF-TOKEN: $old_csrf")
api_record "First refresh" "$code" "200"

if [[ "$code" == "200" ]]; then
  code=$(api_request POST "$API_BASE_URL/auth/refresh" "$old_jar" "$API_TEST_DIR/replay-rejected.json" \
    -H "X-CSRF-TOKEN: $old_csrf")
  api_record "Refresh token replay" "$code" "401"
fi

api_summary
exit $?
