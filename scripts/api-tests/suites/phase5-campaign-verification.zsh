#!/bin/zsh
# Phase 5 Campaign Draft API verification. Run from repository root.
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"
api_prompt_password || exit 1
print "\n=========================================="
print " Phase 5 Campaign API Verification"
print "=========================================="
admin_jar=$(api_new_jar "phase5-admin")
anonymous_jar=$(api_new_jar "phase5-anonymous")
member_jar=$(api_new_jar "phase5-member")
code=$(api_request GET "$API_BASE_URL/admin/campaigns" "$anonymous_jar" "$API_TEST_DIR/phase5-anon-list.json")
api_record "Anonymous campaign listing denied" "$code" "401"
api_login_member "$member_jar" "phase5-member-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request GET "$API_BASE_URL/admin/campaigns" "$member_jar" "$API_TEST_DIR/phase5-member-list.json")
api_record "MEMBER campaign listing denied" "$code" "403"
api_login_admin "$admin_jar" "phase5-admin-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
api_csrf_fetch "$admin_jar" "phase5-admin-create" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
# CSRF must be enforced for writes.
no_csrf_body="$API_TEST_DIR/phase5-no-csrf.json"
"$PYTHON_BIN" -c 'import json,sys;json.dump({"name":"csrf test"},open(sys.argv[1],"w"))' "$no_csrf_body"
code=$(api_request POST "$API_BASE_URL/admin/campaigns" "$admin_jar" "$API_TEST_DIR/phase5-no-csrf-response.json" -H "Content-Type: application/json" --data-binary @"$no_csrf_body")
api_record "Campaign create without CSRF denied" "$code" "403"
rm -f "$no_csrf_body"
# Required audience definition is validated after CSRF succeeds.
api_csrf_fetch "$admin_jar" "phase5-admin-invalid-audience" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
"$PYTHON_BIN" -c 'import json,sys;json.dump({"name":"Invalid audience","startsAt":"2026-12-01T00:00:00Z","endsAt":"2026-12-02T00:00:00Z","landingPage":{"headline":"Headline","body":"Body"},"audience":{}},open(sys.argv[1],"w"))' "$API_TEST_DIR/phase5-invalid-audience.json"
code=$(api_request POST "$API_BASE_URL/admin/campaigns" "$admin_jar" "$API_TEST_DIR/phase5-invalid-audience-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase5-invalid-audience.json")
api_record "Rejects missing campaign audience definition" "$code" "400"
rm -f "$API_TEST_DIR/phase5-invalid-audience.json"
# Create a disposable-by-convention saved segment because the MVP has no segment delete endpoint.
segment_body="$API_TEST_DIR/phase5-segment-request.json"
"$PYTHON_BIN" -c 'import json,sys;json.dump({"name":"Phase 5 API Verification "+sys.argv[1],"description":"Phase 5 verification fixture","rules":{"version":1,"match":"ALL","conditions":[{"field":"age","operator":">=","value":0}]}},open(sys.argv[2],"w"))' "$(date '+%Y%m%d%H%M%S')" "$segment_body"
chmod 600 "$segment_body"
# Refresh CSRF after the preceding rejected campaign request. Some security
# configurations rotate/replace the token as part of request processing.
api_csrf_fetch "$admin_jar" "phase5-admin-create-segment" || { rm -f "$segment_body"; unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/audience-segments" "$admin_jar" "$API_TEST_DIR/phase5-segment-create.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$segment_body")
api_record "ADMIN creates verification audience segment" "$code" "201"
if [[ "$code" != "201" ]]; then
  print "Response body:"
  cat "$API_TEST_DIR/phase5-segment-create.json"
  print ""
fi
rm -f "$segment_body"
segment_id=""
if [[ "$code" == "201" ]]; then
  segment_id=$("$PYTHON_BIN" -c 'import json,sys;print(json.load(open(sys.argv[1])).get("data",{}).get("id",""))' "$API_TEST_DIR/phase5-segment-create.json")
fi
if [[ -z "$segment_id" ]]; then print "FAIL: Segment create did not return data.id"; ((api_fail+=1)); unset API_TEST_PASSWORD; api_summary; exit 1; fi
campaign_body="$API_TEST_DIR/phase5-campaign-request.json"
"$PYTHON_BIN" -c '
import json,sys,datetime
sid,path=sys.argv[1],sys.argv[2]
now=datetime.datetime.now(datetime.timezone.utc)
p={"name":"Phase 5 API Verification "+now.strftime("%Y%m%d%H%M%S"),"description":"Campaign created by Phase 5 API verification",
"startsAt":(now-datetime.timedelta(days=1)).isoformat().replace("+00:00","Z"),"endsAt":(now+datetime.timedelta(days=30)).isoformat().replace("+00:00","Z"),
"landingPage":{"headline":"Verification headline","body":"Verification body"},"audience":{"segmentId":sid}}
json.dump(p,open(path,"w"))
' "$segment_id" "$campaign_body"
chmod 600 "$campaign_body"
# Fetch a current token for this separate state-changing request too.
api_csrf_fetch "$admin_jar" "phase5-admin-create-campaign" || { rm -f "$campaign_body"; unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns" "$admin_jar" "$API_TEST_DIR/phase5-create.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$campaign_body")
api_record "ADMIN creates DRAFT campaign" "$code" "201"
if [[ "$code" != "201" ]]; then
  print "Response body:"
  cat "$API_TEST_DIR/phase5-create.json"
  print ""
fi
rm -f "$campaign_body"
campaign_id=""
if [[ "$code" == "201" ]]; then
 campaign_id=$("$PYTHON_BIN" -c 'import json,sys;print(json.load(open(sys.argv[1])).get("data",{}).get("id",""))' "$API_TEST_DIR/phase5-create.json")
fi
if [[ -z "$campaign_id" ]]; then print "FAIL: Campaign create did not return data.id"; ((api_fail+=1)); unset API_TEST_PASSWORD; api_summary; exit 1; fi
code=$(api_request GET "$API_BASE_URL/admin/campaigns?page=0&size=20&status=DRAFT" "$admin_jar" "$API_TEST_DIR/phase5-list.json")
api_record "ADMIN lists DRAFT campaigns" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1]));sys.exit(0 if any(x.get("id")==sys.argv[2] for x in d.get("data",[])) and isinstance(d.get("page"),dict) else 1)' "$API_TEST_DIR/phase5-list.json" "$campaign_id"; then
 print "PASS: Campaign appears in paginated DRAFT list"; ((api_pass+=1))
else print "FAIL: Campaign absent from list or page metadata"; ((api_fail+=1)); fi
detail_code=$(api_request GET "$API_BASE_URL/admin/campaigns/$campaign_id" "$admin_jar" "$API_TEST_DIR/phase5-detail.json")
api_record "ADMIN gets campaign detail" "$detail_code" "200"
code=$(api_request GET "$API_BASE_URL/admin/campaigns/00000000-0000-4000-8000-000000000099" "$admin_jar" "$API_TEST_DIR/phase5-not-found.json")
api_record "Unknown campaign detail returns 404" "$code" "404"
if [[ "$detail_code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if d.get("id")==sys.argv[2] and d.get("status")=="DRAFT" and d.get("landingPage",{}).get("headline")=="Verification headline" and d.get("audience",{}).get("segmentId") else 1)' "$API_TEST_DIR/phase5-detail.json" "$campaign_id"; then
 print "PASS: Detail includes saved landing page and audience configuration"; ((api_pass+=1))
else print "FAIL: Campaign detail fields mismatch"; ((api_fail+=1)); fi
# Update the draft; the returned record must retain DRAFT.
api_csrf_fetch "$admin_jar" "phase5-admin-update" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
"$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});p={k:d.get(k) for k in ("name","description","startsAt","endsAt","landingPage","audience")};p["name"]+=" Updated";p["landingPage"]["headline"]="Updated verification headline";(p["audience"].pop("rules",None) if isinstance(p.get("audience"),dict) and p["audience"].get("rules") is None else None);json.dump(p,open(sys.argv[2],"w"))' "$API_TEST_DIR/phase5-create.json" "$API_TEST_DIR/phase5-update-request.json"
code=$(api_request PUT "$API_BASE_URL/admin/campaigns/$campaign_id" "$admin_jar" "$API_TEST_DIR/phase5-update.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase5-update-request.json")
api_record "ADMIN updates DRAFT campaign" "$code" "200"
if [[ "$code" != "200" ]]; then
  print "Update response body:"
  cat "$API_TEST_DIR/phase5-update.json"
  print ""
  print "Update request body:"
  cat "$API_TEST_DIR/phase5-update-request.json"
  print ""
fi
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if d.get("status")=="DRAFT" and d.get("landingPage",{}).get("headline")=="Updated verification headline" else 1)' "$API_TEST_DIR/phase5-update.json"; then
 print "PASS: Updated campaign remains DRAFT and reflects changed headline"; ((api_pass+=1))
else print "FAIL: Updated campaign response did not match draft update"; ((api_fail+=1)); fi
# Preview endpoint reads the persisted audience; body is intentionally empty.
api_csrf_fetch "$admin_jar" "phase5-admin-preview" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns/$campaign_id/audience-preview" "$admin_jar" "$API_TEST_DIR/phase5-preview.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data '{}' )
api_record "ADMIN previews persisted campaign audience" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if isinstance(d.get("count"),int) and isinstance(d.get("sample"),list) and len(d["sample"])<=10 else 1)' "$API_TEST_DIR/phase5-preview.json"; then
 print "PASS: Campaign preview count/sample shape and sample limit"; ((api_pass+=1))
else print "FAIL: Campaign preview response shape mismatch"; ((api_fail+=1)); fi
# Invalid time window is rejected.
"$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1]));d["endsAt"]=d["startsAt"];json.dump(d,open(sys.argv[2],"w"))' "$API_TEST_DIR/phase5-update-request.json" "$API_TEST_DIR/phase5-invalid-window.json"
api_csrf_fetch "$admin_jar" "phase5-admin-invalid-window" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns" "$admin_jar" "$API_TEST_DIR/phase5-invalid-window-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase5-invalid-window.json")
api_record "Rejects endsAt not after startsAt" "$code" "400"
unset API_TEST_PASSWORD
api_summary
exit $?
