#!/bin/zsh
# End-to-end Phase 4 Audience Segment API verification.
# Run from repository root: zsh scripts/api-tests/suites/phase4-audience-verification.zsh

SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

api_prompt_password || exit 1

print ""
print "=========================================="
print " Phase 4 Audience API Verification"
print "=========================================="

admin_jar=$(api_new_jar "phase4-admin")
anonymous_jar=$(api_new_jar "phase4-anonymous")
member_jar=$(api_new_jar "phase4-member")

# 1. Authorization boundaries: anonymous and MEMBER cannot access ADMIN APIs.
code=$(api_request GET "$API_BASE_URL/admin/audience-segments" "$anonymous_jar" "$API_TEST_DIR/phase4-anonymous-list.json")
api_record "Anonymous listing Audience Segments" "$code" "401"

api_login_member "$member_jar" "phase4-member-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request GET "$API_BASE_URL/admin/audience-segments" "$member_jar" "$API_TEST_DIR/phase4-member-list.json")
api_record "MEMBER listing Audience Segments" "$code" "403"

# 2. ADMIN login and CSRF setup for state-changing requests.
api_login_admin "$admin_jar" "phase4-admin-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
api_get_me "$admin_jar" "phase4-admin" "200"

# Cookie-authenticated state changes still require the CSRF request header.
csrf_negative_body="$API_TEST_DIR/phase4-preview-no-csrf-request.json"
"$PYTHON_BIN" -c '
import json,sys
p={"version":1,"match":"ALL","conditions":[{"field":"age","operator":">=","value":18}]}
with open(sys.argv[1],"w") as f: json.dump(p,f)
' "$csrf_negative_body"
code=$(api_request POST "$API_BASE_URL/admin/audience-segments/preview" "$admin_jar" "$API_TEST_DIR/phase4-preview-no-csrf.json" \
  -H "Content-Type: application/json" --data-binary @"$csrf_negative_body")
api_record "Audience preview without CSRF header" "$code" "403"
rm -f "$csrf_negative_body"

api_csrf_fetch "$admin_jar" "phase4-admin-create" || { unset API_TEST_PASSWORD; api_summary; exit 1; }

# Unique name avoids collisions. The API has no delete endpoint, so the created
# verification segment is intentionally retained and can be inspected in the UI.
segment_name="API Verification $(date '+%Y%m%d-%H%M%S')"
create_body="$API_TEST_DIR/phase4-create-request.json"
create_output="$API_TEST_DIR/phase4-create.json"
"$PYTHON_BIN" -c '
import json, sys
name, path = sys.argv[1], sys.argv[2]
payload = {
  "name": name,
  "description": "Created by Phase 4 API verification script",
  "rules": {"version": 1, "match": "ALL", "conditions": [
    {"field": "membershipTier", "operator": "=", "value": "GOLD"}
  ]}
}
with open(path, "w") as f: json.dump(payload, f)
' "$segment_name" "$create_body" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
chmod 600 "$create_body"

code=$(api_request POST "$API_BASE_URL/admin/audience-segments" "$admin_jar" "$create_output" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$create_body")
api_record "ADMIN creates Audience Segment" "$code" "201"
rm -f "$create_body"

segment_id=""
if [[ "$code" == "201" ]]; then
  segment_id=$("$PYTHON_BIN" -c '
import json, sys
try:
    data=json.load(open(sys.argv[1]))
    value=data.get("data", {}).get("id", "")
    print(value if isinstance(value, str) else "")
except Exception:
    print("")
' "$create_output")
  if [[ -n "$segment_id" ]]; then
    print "PASS: Create response contains Segment ID"
    (( api_pass += 1 ))
  else
    print "FAIL: Create response does not contain data.id"
    (( api_fail += 1 ))
  fi
else
  print "SKIP: Segment detail/update checks (create did not return HTTP 201)"
fi

if [[ -n "$segment_id" ]]; then
  # 3. List and detail.
  code=$(api_request GET "$API_BASE_URL/admin/audience-segments?page=0&size=20" "$admin_jar" "$API_TEST_DIR/phase4-list.json")
  api_record "ADMIN lists Audience Segments" "$code" "200"
  if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c '
import json,sys
x=json.load(open(sys.argv[1])); sid=sys.argv[2]
rows=x.get("data", [])
sys.exit(0 if isinstance(rows,list) and any(r.get("id")==sid for r in rows) else 1)
' "$API_TEST_DIR/phase4-list.json" "$segment_id"; then
    print "PASS: Created Segment appears in list"
    (( api_pass += 1 ))
  else
    print "FAIL: Created Segment was not found in list response"
    (( api_fail += 1 ))
  fi

  code=$(api_request GET "$API_BASE_URL/admin/audience-segments/$segment_id" "$admin_jar" "$API_TEST_DIR/phase4-get.json")
  api_record "ADMIN gets created Segment" "$code" "200"
  if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c '
import json,sys
x=json.load(open(sys.argv[1])).get("data",{})
sys.exit(0 if x.get("id")==sys.argv[2] and x.get("name")==sys.argv[3] else 1)
' "$API_TEST_DIR/phase4-get.json" "$segment_id" "$segment_name"; then
    print "PASS: Segment detail fields match create request"
    (( api_pass += 1 ))
  else
    print "FAIL: Segment detail fields do not match create request"
    (( api_fail += 1 ))
  fi

  # 4. Update the same resource.
  api_csrf_fetch "$admin_jar" "phase4-admin-update" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
  update_name="$segment_name Updated"
  update_body="$API_TEST_DIR/phase4-update-request.json"
  "$PYTHON_BIN" -c '
import json,sys
name,path=sys.argv[1],sys.argv[2]
payload={"name":name,"description":"Updated by Phase 4 API verification script",
"rules":{"version":1,"match":"ALL","conditions":[{"field":"age","operator":">=","value":18}]}}
with open(path,"w") as f: json.dump(payload,f)
' "$update_name" "$update_body"
  chmod 600 "$update_body"
  code=$(api_request PUT "$API_BASE_URL/admin/audience-segments/$segment_id" "$admin_jar" "$API_TEST_DIR/phase4-update.json" \
    -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$update_body")
  api_record "ADMIN updates Audience Segment" "$code" "200"
  rm -f "$update_body"
  if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c '
import json,sys
x=json.load(open(sys.argv[1])).get("data",{})
sys.exit(0 if x.get("name")==sys.argv[2] and x.get("rules",{}).get("conditions",[{}])[0].get("field")=="age" else 1)
' "$API_TEST_DIR/phase4-update.json" "$update_name"; then
    print "PASS: Updated Segment contains the new name and rule"
    (( api_pass += 1 ))
  else
    print "FAIL: Updated Segment response does not match update request"
    (( api_fail += 1 ))
  fi
fi

# 5. Preview audience rules. Verify response shape, not a hard-coded count.
api_csrf_fetch "$admin_jar" "phase4-admin-preview" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
preview_body="$API_TEST_DIR/phase4-preview-request.json"
"$PYTHON_BIN" -c '
import json,sys
payload={"version":1,"match":"ALL","conditions":[{"field":"membershipTier","operator":"=","value":"GOLD"}]}
with open(sys.argv[1],"w") as f: json.dump(payload,f)
' "$preview_body"
chmod 600 "$preview_body"
code=$(api_request POST "$API_BASE_URL/admin/audience-segments/preview" "$admin_jar" "$API_TEST_DIR/phase4-preview.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$preview_body")
api_record "ADMIN previews audience rules" "$code" "200"
rm -f "$preview_body"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c '
import json,sys
x=json.load(open(sys.argv[1])).get("data",{})
sys.exit(0 if isinstance(x.get("count"),int) and isinstance(x.get("sample"),list) else 1)
' "$API_TEST_DIR/phase4-preview.json"; then
  print "PASS: Preview response contains numeric count and sample array"
  (( api_pass += 1 ))
else
  print "FAIL: Preview response has an unexpected JSON shape"
  (( api_fail += 1 ))
fi

# 6. Validation errors: unsupported field and unsupported operator must be HTTP 400.
api_csrf_fetch "$admin_jar" "phase4-admin-invalid-field" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
invalid_field="$API_TEST_DIR/phase4-invalid-field-request.json"
"$PYTHON_BIN" -c '
import json,sys
p={"name":"Invalid field test","description":"","rules":{"version":1,"match":"ALL","conditions":[{"field":"unknownField","operator":"=","value":1}]}}
with open(sys.argv[1],"w") as f: json.dump(p,f)
' "$invalid_field"
chmod 600 "$invalid_field"
code=$(api_request POST "$API_BASE_URL/admin/audience-segments" "$admin_jar" "$API_TEST_DIR/phase4-invalid-field.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$invalid_field")
api_record "Rejects unsupported audience field" "$code" "400"
rm -f "$invalid_field"

api_csrf_fetch "$admin_jar" "phase4-admin-invalid-operator" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
invalid_operator="$API_TEST_DIR/phase4-invalid-operator-request.json"
"$PYTHON_BIN" -c '
import json,sys
p={"name":"Invalid operator test","description":"","rules":{"version":1,"match":"ALL","conditions":[{"field":"age","operator":"LIKE","value":18}]}}
with open(sys.argv[1],"w") as f: json.dump(p,f)
' "$invalid_operator"
chmod 600 "$invalid_operator"
code=$(api_request POST "$API_BASE_URL/admin/audience-segments" "$admin_jar" "$API_TEST_DIR/phase4-invalid-operator.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$invalid_operator")
api_record "Rejects unsupported audience operator" "$code" "400"
rm -f "$invalid_operator"

unset API_TEST_PASSWORD
api_summary
exit $?
