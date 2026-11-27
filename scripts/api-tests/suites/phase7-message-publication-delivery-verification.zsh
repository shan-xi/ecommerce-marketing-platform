#!/bin/zsh
# Phase 7 verification. Uses and permanently publishes/pauses a disposable DRAFT campaign.
# The campaign must already have a valid audience and at least one configured coupon.
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

print "\n=========================================="
print " Phase 7 Message / Publication Verification"
print "=========================================="
if [[ -z "${PHASE7_DRAFT_CAMPAIGN_ID:-}" ]]; then
  print -u2 "ERROR: PHASE7_DRAFT_CAMPAIGN_ID is required."
  print -u2 "Set it to an existing disposable DRAFT campaign UUID with an audience and at least one coupon."
  exit 2
fi
cid="$PHASE7_DRAFT_CAMPAIGN_ID"
if ! "$PYTHON_BIN" -c 'import sys,uuid;uuid.UUID(sys.argv[1])' "$cid" 2>/dev/null; then
  print -u2 "ERROR: PHASE7_DRAFT_CAMPAIGN_ID must be a UUID."
  exit 2
fi
api_prompt_password || exit 1
admin_jar=$(api_new_jar "phase7-admin")
member_jar=$(api_new_jar "phase7-member")
anon_jar=$(api_new_jar "phase7-anon")
api_login_admin "$admin_jar" "phase7-admin-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
api_login_member "$member_jar" "phase7-member-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid" "$admin_jar" "$API_TEST_DIR/phase7-campaign.json")
api_record "ADMIN reads disposable DRAFT campaign" "$code" "200"
if [[ "$code" != "200" ]] || ! "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if d.get("id")==sys.argv[2] and d.get("status")=="DRAFT" else 1)' "$API_TEST_DIR/phase7-campaign.json" "$cid"; then
  print -u2 "ERROR: Campaign is missing or not DRAFT; no lifecycle changes were made."
  unset API_TEST_PASSWORD; api_summary; exit 1
fi

# Publication requires startsAt <= current UTC instant < endsAt. Check this
# before changing the message so a bad fixture fails without partial side effects.
window_check=$("$PYTHON_BIN" -c 'import json,sys,datetime; d=json.load(open(sys.argv[1])).get("data",{}); now=datetime.datetime.now(datetime.timezone.utc); parse=lambda v: datetime.datetime.fromisoformat(v.replace("Z", "+00:00")) if v else None; start=parse(d.get("startsAt")); end=parse(d.get("endsAt")); ok=start is not None and end is not None and start <= now < end; print("ACTIVE" if ok else "INACTIVE"); print("startsAt="+(d.get("startsAt") or "<missing>")+"; endsAt="+(d.get("endsAt") or "<missing>")+"; now="+now.isoformat(), file=sys.stderr) if not ok else None' "$API_TEST_DIR/phase7-campaign.json" 2>"$API_TEST_DIR/phase7-window-diagnostic.txt")
if [[ "$window_check" != "ACTIVE" ]]; then
  print -u2 "ERROR: Campaign is DRAFT but its publication window is not active."
  cat "$API_TEST_DIR/phase7-window-diagnostic.txt" >&2
  print -u2 "Required: startsAt <= current UTC time < endsAt. Update the disposable DRAFT campaign schedule, then rerun this suite."
  print -u2 "No message configuration or lifecycle changes were made by this run."
  unset API_TEST_PASSWORD; api_summary; exit 1
fi

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/message" "$anon_jar" "$API_TEST_DIR/phase7-anon-message.json")
api_record "Anonymous cannot read Admin message configuration" "$code" "401"
code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/message" "$member_jar" "$API_TEST_DIR/phase7-member-message.json")
api_record "MEMBER cannot read Admin message configuration" "$code" "403"

print '{"title":"","body":"invalid"}' > "$API_TEST_DIR/phase7-invalid-message.json"
api_csrf_fetch "$admin_jar" "phase7-invalid-message" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request PUT "$API_BASE_URL/admin/campaigns/$cid/message" "$admin_jar" "$API_TEST_DIR/phase7-invalid-message-response.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase7-invalid-message.json")
api_record "Message validation rejects blank title" "$code" "400"

now=$(date -u +%Y%m%dT%H%M%SZ)
print -r -- "{\"title\":\"Phase 7 verification $now\",\"body\":\"This in-app message was created by the Phase 7 API verification suite.\"}" > "$API_TEST_DIR/phase7-message-request.json"
api_csrf_fetch "$admin_jar" "phase7-save-message" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request PUT "$API_BASE_URL/admin/campaigns/$cid/message" "$admin_jar" "$API_TEST_DIR/phase7-save-message.json" \
  -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase7-message-request.json")
api_record "ADMIN creates or updates single campaign message" "$code" "200"
code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/message" "$admin_jar" "$API_TEST_DIR/phase7-get-message.json")
api_record "ADMIN retrieves saved campaign message" "$code" "200"

api_csrf_fetch "$admin_jar" "phase7-publish" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns/$cid/publish" "$admin_jar" "$API_TEST_DIR/phase7-publish.json" \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary '{}')
api_record "ADMIN atomically publishes campaign" "$code" "202"
if [[ "$code" != "202" ]]; then
  print "Publish response:"; cat "$API_TEST_DIR/phase7-publish.json"
  unset API_TEST_PASSWORD; api_summary; exit 1
fi

delivery_status=""
for attempt in {1..60}; do
  code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid/delivery-status" "$admin_jar" "$API_TEST_DIR/phase7-delivery-status.json")
  if [[ "$code" == "200" ]]; then
    delivery_status=$("$PYTHON_BIN" -c 'import json,sys;print(json.load(open(sys.argv[1])).get("data",{}).get("status",""))' "$API_TEST_DIR/phase7-delivery-status.json")
    [[ "$delivery_status" == "COMPLETED" || "$delivery_status" == "FAILED" ]] && break
  fi
  sleep 0.5
done
api_record "Delivery worker completes the persisted audience job" "$delivery_status" "COMPLETED"

code=$(api_request GET "$API_BASE_URL/member/messages?page=0&size=20" "$member_jar" "$API_TEST_DIR/phase7-member-inbox.json")
api_record "MEMBER can read own inbox page" "$code" "200"
code=$(api_request GET "$API_BASE_URL/member/messages?page=0&size=20" "$anon_jar" "$API_TEST_DIR/phase7-anon-inbox.json")
api_record "Anonymous cannot read member inbox" "$code" "401"

api_csrf_fetch "$admin_jar" "phase7-pause" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns/$cid/pause" "$admin_jar" "$API_TEST_DIR/phase7-pause.json" \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary '{}')
api_record "ADMIN pauses ACTIVE campaign" "$code" "200"
if [[ "$code" != "200" ]]; then
  print "Pause response:"; cat "$API_TEST_DIR/phase7-pause.json"
  unset API_TEST_PASSWORD; api_summary; exit 1
fi

# Fetch a fresh CSRF token for each state-changing request. The pause request
# may rotate the CSRF cookie/token pair, so reusing its token for retry can
# produce HTTP 403 before the delivery-state rule is evaluated.
api_csrf_fetch "$admin_jar" "phase7-invalid-retry" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/admin/campaigns/$cid/delivery/retry" "$admin_jar" "$API_TEST_DIR/phase7-invalid-retry.json" \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary '{}')
api_record "COMPLETED delivery job cannot be manually retried" "$code" "409"
if [[ "$code" != "409" ]]; then
  print "Retry response:"; cat "$API_TEST_DIR/phase7-invalid-retry.json"
fi

unset API_TEST_PASSWORD
api_summary
