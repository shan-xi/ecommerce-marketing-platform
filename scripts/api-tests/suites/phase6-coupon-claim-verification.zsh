#!/bin/zsh
# Phase 6 Coupon API verification.
# Requires PHASE6_DRAFT_CAMPAIGN_ID for an existing disposable DRAFT campaign.
# The test creates a coupon and intentionally leaves it on that campaign.
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

print "\n=========================================="
print " Phase 6 Coupon API Verification"
print "=========================================="

if [[ -z "${PHASE6_DRAFT_CAMPAIGN_ID:-}" ]]; then
  print -u2 "ERROR: PHASE6_DRAFT_CAMPAIGN_ID is required."
  print -u2 "Set it to an existing disposable DRAFT campaign UUID, then rerun this script."
  exit 2
fi

cid="$PHASE6_DRAFT_CAMPAIGN_ID"
if ! "$PYTHON_BIN" -c 'import sys,uuid; uuid.UUID(sys.argv[1])' "$cid" 2>/dev/null; then
  print -u2 "ERROR: PHASE6_DRAFT_CAMPAIGN_ID is not a valid UUID: $cid"
  exit 2
fi

api_prompt_password || exit 1
admin_jar=$(api_new_jar "phase6-admin")
member_jar=$(api_new_jar "phase6-member")
anon_jar=$(api_new_jar "phase6-anon")

show_body_on_failure() {
  local label="$1" code="$2" expected="$3" file="$4"
  if [[ "$code" != "$expected" ]]; then
    print "Response body ($label):"
    if [[ -f "$file" ]]; then cat "$file"; else print "<no response body>"; fi
    print ""
  fi
}

api_login_admin "$admin_jar" "phase6-admin-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
api_login_member "$member_jar" "phase6-member-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }

# Fail early with a clear message if the supplied campaign is missing or not DRAFT.
code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid" "$admin_jar" "$API_TEST_DIR/phase6-campaign-detail.json")
api_record "ADMIN reads target campaign" "$code" "200"
show_body_on_failure "campaign detail" "$code" "200" "$API_TEST_DIR/phase6-campaign-detail.json"
if [[ "$code" != "200" ]]; then unset API_TEST_PASSWORD; api_summary; exit 1; fi
if "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if d.get("id")==sys.argv[2] and d.get("status")=="DRAFT" else 1)' "$API_TEST_DIR/phase6-campaign-detail.json" "$cid"; then
  print "PASS: Target campaign exists and is DRAFT"
  (( api_pass += 1 ))
else
  print -u2 "FAIL: Target campaign is not DRAFT or response ID does not match. Use a disposable DRAFT campaign."
  (( api_fail += 1 ))
  unset API_TEST_PASSWORD
  api_summary
  exit 1
fi

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/coupons" "$anon_jar" "$API_TEST_DIR/phase6-anon-list.json")
api_record "Anonymous cannot list Admin coupons" "$code" "401"
show_body_on_failure "anonymous list" "$code" "401" "$API_TEST_DIR/phase6-anon-list.json"

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/coupons" "$member_jar" "$API_TEST_DIR/phase6-member-admin-list.json")
api_record "MEMBER cannot list Admin coupons" "$code" "403"
show_body_on_failure "member list" "$code" "403" "$API_TEST_DIR/phase6-member-admin-list.json"

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/coupons" "$admin_jar" "$API_TEST_DIR/phase6-list-before.json")
api_record "ADMIN lists campaign coupons" "$code" "200"
show_body_on_failure "admin list before create" "$code" "200" "$API_TEST_DIR/phase6-list-before.json"
if [[ "$code" != "200" ]]; then unset API_TEST_PASSWORD; api_summary; exit 1; fi

body="$API_TEST_DIR/phase6-coupon-request.json"
"$PYTHON_BIN" -c 'import json,sys,datetime; n=datetime.datetime.now(datetime.timezone.utc); json.dump({"name":"Phase 6 verification coupon "+n.strftime("%Y%m%d%H%M%S%f"),"description":"Created by Phase 6 API verification","discountType":"FIXED_AMOUNT","discountValue":100,"minimumOrderAmount":500,"totalQuantity":25,"startsAt":(n-datetime.timedelta(days=1)).isoformat().replace("+00:00","Z"),"endsAt":(n+datetime.timedelta(days=30)).isoformat().replace("+00:00","Z")},open(sys.argv[1],"w"))' "$body"
chmod 600 "$body"

# Invalid coupon data must be rejected before persistence.
"$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1]));d["discountValue"]=0;d["name"]="Invalid Phase 6 coupon";json.dump(d,open(sys.argv[2],"w"))' "$body" "$API_TEST_DIR/phase6-invalid-coupon-request.json"
api_csrf_fetch "$admin_jar" "phase6-admin-invalid-create" || { rm -f "$body"; unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns/$cid/coupons" "$admin_jar" "$API_TEST_DIR/phase6-invalid-create.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-invalid-coupon-request.json")
api_record "ADMIN coupon validation rejects zero discount" "$code" "400"
show_body_on_failure "invalid coupon create" "$code" "400" "$API_TEST_DIR/phase6-invalid-create.json"

api_csrf_fetch "$admin_jar" "phase6-admin-create" || { rm -f "$body"; unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns/$cid/coupons" "$admin_jar" "$API_TEST_DIR/phase6-create.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$body")
api_record "ADMIN creates campaign coupon" "$code" "201"
show_body_on_failure "coupon create" "$code" "201" "$API_TEST_DIR/phase6-create.json"
rm -f "$body"

coupon_id=""
if [[ "$code" == "201" ]]; then
  coupon_id=$("$PYTHON_BIN" -c 'import json,sys;print(json.load(open(sys.argv[1])).get("data",{}).get("id", ""))' "$API_TEST_DIR/phase6-create.json")
fi
if [[ -z "$coupon_id" ]]; then
  print "FAIL: Coupon create did not return data.id; remaining checks skipped."
  (( api_fail += 1 ))
  unset API_TEST_PASSWORD
  api_summary
  exit 1
fi
print "Created coupon ID: $coupon_id"

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/coupons" "$admin_jar" "$API_TEST_DIR/phase6-list-after.json")
api_record "ADMIN lists newly created coupon" "$code" "200"
show_body_on_failure "admin list after create" "$code" "200" "$API_TEST_DIR/phase6-list-after.json"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",[]);sys.exit(0 if any(x.get("id")==sys.argv[2] and x.get("claimedQuantity")==0 for x in d) else 1)' "$API_TEST_DIR/phase6-list-after.json" "$coupon_id"; then
  print "PASS: Coupon list includes the created coupon and claimedQuantity=0"
  (( api_pass += 1 ))
else
  print "FAIL: Coupon list does not contain the created coupon with claimedQuantity=0"
  (( api_fail += 1 ))
fi

# Update as ADMIN; refresh CSRF immediately before each state-changing request.
"$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});p={k:d.get(k) for k in ("name","description","discountType","discountValue","minimumOrderAmount","totalQuantity","startsAt","endsAt")};p["description"]="Updated by Phase 6 API verification";json.dump(p,open(sys.argv[2],"w"))' "$API_TEST_DIR/phase6-create.json" "$API_TEST_DIR/phase6-update-request.json"
chmod 600 "$API_TEST_DIR/phase6-update-request.json"
api_csrf_fetch "$admin_jar" "phase6-admin-update" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request PUT "$API_BASE_URL/admin/campaigns/$cid/coupons/$coupon_id" "$admin_jar" "$API_TEST_DIR/phase6-update.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-update-request.json")
api_record "ADMIN updates DRAFT campaign coupon" "$code" "200"
show_body_on_failure "admin update" "$code" "200" "$API_TEST_DIR/phase6-update.json"

# Include a valid CSRF token so this 403 proves role authorization, not CSRF rejection.
api_csrf_fetch "$member_jar" "phase6-member-update" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request PUT "$API_BASE_URL/admin/campaigns/$cid/coupons/$coupon_id" "$member_jar" "$API_TEST_DIR/phase6-member-update.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-update-request.json")
api_record "MEMBER cannot update Admin coupon (valid CSRF)" "$code" "403"
show_body_on_failure "member update" "$code" "403" "$API_TEST_DIR/phase6-member-update.json"

# Claim verification uses a separately prepared ACTIVE campaign whose audience
# snapshot contains API_TEST_MEMBER_EMAIL's member profile. The second coupon
# is optional and enables the cross-target idempotency assertion.
if [[ -n "${PHASE6_ACTIVE_CAMPAIGN_ID:-}" && -n "${PHASE6_ACTIVE_COUPON_ID:-}" ]]; then
  active_id="$PHASE6_ACTIVE_CAMPAIGN_ID"
  active_coupon_id="$PHASE6_ACTIVE_COUPON_ID"
  if ! "$PYTHON_BIN" -c 'import sys,uuid;uuid.UUID(sys.argv[1]);uuid.UUID(sys.argv[2])' "$active_id" "$active_coupon_id" 2>/dev/null; then
    print -u2 "ERROR: PHASE6_ACTIVE_CAMPAIGN_ID / PHASE6_ACTIVE_COUPON_ID must be UUIDs."
    (( api_fail += 1 ))
  else
    code=$(api_request GET "$API_BASE_URL/admin/campaigns/$active_id" "$admin_jar" "$API_TEST_DIR/phase6-active-campaign.json")
    api_record "ADMIN reads ACTIVE claim fixture" "$code" "200"
    show_body_on_failure "active campaign fixture" "$code" "200" "$API_TEST_DIR/phase6-active-campaign.json"
    if [[ "$code" == "200" ]] && ! "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if d.get("id")==sys.argv[2] and d.get("status")=="ACTIVE" else 1)' "$API_TEST_DIR/phase6-active-campaign.json" "$active_id"; then
      print "FAIL: Claim fixture campaign must be ACTIVE"
      (( api_fail += 1 ))
    elif [[ "$code" == "200" ]]; then
      # An ACTIVE campaign must reject coupon reconfiguration even when the
      # supplied coupon ID is otherwise valid for this campaign.
      if [[ -n "$active_coupon_id" ]]; then
        api_csrf_fetch "$admin_jar" "phase6-active-coupon-update-guard" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
        code=$(api_request PUT "$API_BASE_URL/admin/campaigns/$active_id/coupons/$active_coupon_id" "$admin_jar" "$API_TEST_DIR/phase6-active-coupon-update-guard.json" \
          -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-update-request.json")
        api_record "ACTIVE campaign coupon configuration is locked" "$code" "409"
        show_body_on_failure "active coupon update guard" "$code" "409" "$API_TEST_DIR/phase6-active-coupon-update-guard.json"
      fi
      # An authenticated ADMIN must be forbidden from the MEMBER claim endpoint.
      claim_key=$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')
      "$PYTHON_BIN" -c 'import json,sys;json.dump({"idempotencyKey":sys.argv[1]},open(sys.argv[2],"w"))' "$claim_key" "$API_TEST_DIR/phase6-claim-request.json"
      chmod 600 "$API_TEST_DIR/phase6-claim-request.json"
      api_csrf_fetch "$admin_jar" "phase6-admin-claim-forbidden" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
      code=$(api_request POST "$API_BASE_URL/campaigns/$active_id/coupons/$active_coupon_id/claims" "$admin_jar" "$API_TEST_DIR/phase6-admin-claim-forbidden.json" \
        -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-claim-request.json")
      api_record "ADMIN cannot use MEMBER claim endpoint" "$code" "403"
      show_body_on_failure "admin claim forbidden" "$code" "403" "$API_TEST_DIR/phase6-admin-claim-forbidden.json"

      # Fetch cookie/token, then deliberately omit the header to verify CSRF enforcement.
      api_csrf_fetch "$member_jar" "phase6-member-claim-no-csrf" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
      code=$(api_request POST "$API_BASE_URL/campaigns/$active_id/coupons/$active_coupon_id/claims" "$member_jar" "$API_TEST_DIR/phase6-member-claim-no-csrf.json" \
        -H "Content-Type: application/json" --data-binary @"$API_TEST_DIR/phase6-claim-request.json")
      api_record "MEMBER claim without CSRF header is rejected" "$code" "403"
      show_body_on_failure "member claim without CSRF" "$code" "403" "$API_TEST_DIR/phase6-member-claim-no-csrf.json"

      api_csrf_fetch "$member_jar" "phase6-member-claim" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
      code=$(api_request POST "$API_BASE_URL/campaigns/$active_id/coupons/$active_coupon_id/claims" "$member_jar" "$API_TEST_DIR/phase6-claim.json" \
        -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-claim-request.json")
      api_record "Eligible MEMBER claims coupon" "$code" "201"
      show_body_on_failure "member claim" "$code" "201" "$API_TEST_DIR/phase6-claim.json"
      if [[ "$code" == "201" ]]; then
        claim_id=$("$PYTHON_BIN" -c 'import json,sys;print(json.load(open(sys.argv[1])).get("data",{}).get("claimId", ""))' "$API_TEST_DIR/phase6-claim.json")
        api_csrf_fetch "$member_jar" "phase6-member-claim-replay" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
        code=$(api_request POST "$API_BASE_URL/campaigns/$active_id/coupons/$active_coupon_id/claims" "$member_jar" "$API_TEST_DIR/phase6-claim-replay.json" \
          -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-claim-request.json")
        api_record "Same-key claim is idempotently replayed" "$code" "201"
        if [[ "$code" == "201" ]] && [[ -n "$claim_id" ]] && "$PYTHON_BIN" -c 'import json,sys;sys.exit(0 if json.load(open(sys.argv[1])).get("data",{}).get("claimId")==sys.argv[2] else 1)' "$API_TEST_DIR/phase6-claim-replay.json" "$claim_id"; then
          print "PASS: Replay returns the original claim ID"
          (( api_pass += 1 ))
        else
          print "FAIL: Replay did not return the original claim ID"
          (( api_fail += 1 ))
        fi

        # A new key for the same member/coupon must not create a second claim.
        duplicate_key=$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')
        "$PYTHON_BIN" -c 'import json,sys;json.dump({"idempotencyKey":sys.argv[1]},open(sys.argv[2],"w"))' "$duplicate_key" "$API_TEST_DIR/phase6-duplicate-claim-request.json"
        api_csrf_fetch "$member_jar" "phase6-member-duplicate-claim" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
        code=$(api_request POST "$API_BASE_URL/campaigns/$active_id/coupons/$active_coupon_id/claims" "$member_jar" "$API_TEST_DIR/phase6-duplicate-claim.json" \
          -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-duplicate-claim-request.json")
        api_record "Different-key duplicate claim is rejected" "$code" "409"
        show_body_on_failure "duplicate claim" "$code" "409" "$API_TEST_DIR/phase6-duplicate-claim.json"

        code=$(api_request GET "$API_BASE_URL/member/coupons" "$member_jar" "$API_TEST_DIR/phase6-member-coupons.json")
        api_record "MEMBER lists own claimed coupons" "$code" "200"
        if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",[]);sys.exit(0 if any(x.get("claimId")==sys.argv[2] for x in d) else 1)' "$API_TEST_DIR/phase6-member-coupons.json" "$claim_id"; then
          print "PASS: Member coupon list includes the successful claim"
          (( api_pass += 1 ))
        else
          print "FAIL: Member coupon list did not include the successful claim"
          (( api_fail += 1 ))
        fi

        if [[ -n "${PHASE6_ACTIVE_COUPON_ALT_ID:-}" ]]; then
          if ! "$PYTHON_BIN" -c 'import sys,uuid;uuid.UUID(sys.argv[1]);sys.exit(1 if sys.argv[1]==sys.argv[2] else 0)' "$PHASE6_ACTIVE_COUPON_ALT_ID" "$active_coupon_id" 2>/dev/null; then
            print "FAIL: PHASE6_ACTIVE_COUPON_ALT_ID must be a UUID different from PHASE6_ACTIVE_COUPON_ID"
            (( api_fail += 1 ))
          else
            api_csrf_fetch "$member_jar" "phase6-cross-target-idempotency" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
            code=$(api_request POST "$API_BASE_URL/campaigns/$active_id/coupons/$PHASE6_ACTIVE_COUPON_ALT_ID/claims" "$member_jar" "$API_TEST_DIR/phase6-cross-target-idempotency.json" \
              -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase6-claim-request.json")
            api_record "Idempotency key cannot target another coupon" "$code" "409"
            show_body_on_failure "cross-target idempotency" "$code" "409" "$API_TEST_DIR/phase6-cross-target-idempotency.json"
          fi
        else
          print "SKIP: Cross-target idempotency check (set PHASE6_ACTIVE_COUPON_ALT_ID to a second coupon in the same ACTIVE campaign)."
        fi
      fi
    fi
  fi
else
  print "SKIP: Member claim tests (set PHASE6_ACTIVE_CAMPAIGN_ID and PHASE6_ACTIVE_COUPON_ID to an ACTIVE campaign/coupon fixture whose audience snapshot includes the test member)."
fi

unset API_TEST_PASSWORD
api_summary
exit $?
