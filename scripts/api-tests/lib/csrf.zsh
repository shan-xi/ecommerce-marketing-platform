# Requires lib/common.zsh.

api_csrf_fetch() {
  local jar="$1" label="$2"
  local output="$API_TEST_DIR/${label}-csrf.json"
  local code

  code=$(api_request GET "$API_BASE_URL/auth/csrf" "$jar" "$output")
  if [[ "$code" != "200" ]]; then
    api_record "Get CSRF ($label)" "$code" "200"
    return 1
  fi

  CSRF_TOKEN=$("$PYTHON_BIN" -c '
import json, sys
print(json.load(open(sys.argv[1]))["data"]["csrfToken"])
' "$output") || return 1

  if [[ -z "$CSRF_TOKEN" ]]; then
    print "FAIL: CSRF response did not contain a token ($label)"
    (( api_fail += 1 ))
    return 1
  fi

  if "$PYTHON_BIN" -c '
import sys
jar, token = sys.argv[1], sys.argv[2]
for line in open(jar):
    if line.startswith("#") or not line.strip():
        continue
    fields = line.rstrip("\n").split("\t")
    if len(fields) >= 7 and fields[5] == "XSRF-TOKEN" and fields[6] == token:
        sys.exit(0)
sys.exit(1)
' "$jar" "$CSRF_TOKEN"; then
    print "PASS: CSRF token and XSRF-TOKEN cookie match ($label)"
    (( api_pass += 1 ))
    export CSRF_TOKEN
  else
    print "FAIL: CSRF token and XSRF-TOKEN cookie do not match ($label)"
    (( api_fail += 1 ))
    return 1
  fi
}
