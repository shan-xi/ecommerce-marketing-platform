# Shared runtime for API test scripts. This file is sourced by entry-point scripts.

setopt pipefail
umask 077

# Optional ignored local config. Use : ${NAME:=value} in this file so exported
# environment variables retain precedence.
if [[ -n "${API_TEST_HOME:-}" && -f "$API_TEST_HOME/config/local.zsh" ]]; then
  source "$API_TEST_HOME/config/local.zsh"
fi

: "${API_BASE_URL:=http://localhost:8080/api/v1}"
: "${API_TEST_ADMIN_EMAIL:=admin@example.test}"
: "${API_TEST_MEMBER_EMAIL:=member.one@example.test}"
: "${API_TEST_ALLOW_NONLOCAL:=false}"
: "${API_TEST_DIR:=/tmp/marketing-platform-api-test}"

CURL_BIN="$(whence -p curl 2>/dev/null || true)"
PYTHON_BIN="$(whence -p python3 2>/dev/null || true)"

if [[ -z "$CURL_BIN" && -x /usr/bin/curl ]]; then
  CURL_BIN="/usr/bin/curl"
fi

if [[ -z "$CURL_BIN" ]]; then
  print -u2 "ERROR: curl was not found in PATH or /usr/bin/curl."
  return 1
fi

if [[ -z "$PYTHON_BIN" ]]; then
  print -u2 "ERROR: python3 was not found in PATH."
  return 1
fi

mkdir -p "$API_TEST_DIR" || return 1
chmod 700 "$API_TEST_DIR" || return 1

api_check_target() {
  local authority host
  authority="${API_BASE_URL#*://}"
  authority="${authority%%/*}"
  host="${authority%%:*}"

  if [[ "$host" != "localhost" && "$host" != "127.0.0.1" && "$API_TEST_ALLOW_NONLOCAL" != "true" ]]; then
    print -u2 "ERROR: Refusing non-local API target: $API_BASE_URL"
    print -u2 "Only localhost/127.0.0.1 is allowed by default."
    print -u2 "For a deliberate non-local test, set API_TEST_ALLOW_NONLOCAL=true."
    return 1
  fi
}

api_check_target || return 1

api_pass=0
api_fail=0

api_record() {
  local name="$1" actual="$2" expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    print "PASS: $name (HTTP $actual)"
    (( api_pass += 1 ))
  else
    print "FAIL: $name (expected HTTP $expected, got HTTP ${actual:-<empty>})"
    (( api_fail += 1 ))
  fi
}

api_summary() {
  print ""
  print "=========================================="
  print " API Test Summary"
  print "=========================================="
  print "PASS: $api_pass"
  print "FAIL: $api_fail"
  print "Detailed responses: $API_TEST_DIR"
  print "=========================================="
  (( api_fail == 0 ))
}

api_new_jar() {
  local name="$1"
  local jar="$API_TEST_DIR/${name}.cookies"
  : > "$jar"
  chmod 600 "$jar"
  print -r -- "$jar"
}

api_request() {
  local method="$1" url="$2" jar="$3" output="$4"
  shift 4

  "$CURL_BIN" -sS \
    -b "$jar" \
    -c "$jar" \
    -o "$output" \
    -w '%{http_code}' \
    -X "$method" \
    "$url" \
    "$@"
}

api_prompt_password() {
  if [[ -n "${API_TEST_PASSWORD:-}" ]]; then
    return 0
  fi

  read -r -s "API_TEST_PASSWORD?Enter DEMO_SEED_PASSWORD: "
  print ""

  if [[ -z "$API_TEST_PASSWORD" ]]; then
    print -u2 "ERROR: Password cannot be empty."
    return 1
  fi
}

api_make_login_body() {
  local email="$1" output="$2"

  # Read the password from stdin instead of placing it in Python's argv.
  printf '%s' "$API_TEST_PASSWORD" | "$PYTHON_BIN" -c '
import json, sys
email, output = sys.argv[1], sys.argv[2]
password = sys.stdin.read()
with open(output, "w") as f:
    json.dump({"email": email, "password": password}, f)
' "$email" "$output" || return 1

  chmod 600 "$output"
}

api_assert_role() {
  local file="$1" expected_role="$2"

  if "$PYTHON_BIN" -c '
import json, sys
data = json.load(open(sys.argv[1]))
sys.exit(0 if data.get("data", {}).get("role") == sys.argv[2] else 1)
' "$file" "$expected_role"; then
    print "PASS: Login response role is $expected_role"
    (( api_pass += 1 ))
  else
    print "FAIL: Login response role is not $expected_role"
    (( api_fail += 1 ))
  fi
}
