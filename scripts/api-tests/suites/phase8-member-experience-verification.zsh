#!/bin/zsh
# Phase 8 non-destructive verification. Requires an existing published campaign
# with a completed delivery to the configured demo member.
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

print "\n=========================================="
print " Phase 8 Member Experience Verification"
print "=========================================="
if [[ -z "${PHASE8_PUBLISHED_CAMPAIGN_ID:-}" ]]; then
  print -u2 "ERROR: PHASE8_PUBLISHED_CAMPAIGN_ID is required."
  print -u2 "Set it to an existing published campaign with a delivered message for the configured demo member."
  exit 2
fi
cid="$PHASE8_PUBLISHED_CAMPAIGN_ID"
if ! "$PYTHON_BIN" -c 'import sys,uuid;uuid.UUID(sys.argv[1])' "$cid" 2>/dev/null; then
  print -u2 "ERROR: PHASE8_PUBLISHED_CAMPAIGN_ID must be a UUID."
  exit 2
fi
api_prompt_password || exit 1
admin_jar=$(api_new_jar "phase8-admin")
member_jar=$(api_new_jar "phase8-member")
anon_jar=$(api_new_jar "phase8-anon")
api_login_admin "$admin_jar" "phase8-admin-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
api_login_member "$member_jar" "phase8-member-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }

code=$(api_request GET "$API_BASE_URL/campaigns/$cid" "$anon_jar" "$API_TEST_DIR/phase8-anon-campaign.json")
api_record "Anonymous reads published campaign landing page" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if d.get("id")==sys.argv[2] and "memberState" not in d else 1)' "$API_TEST_DIR/phase8-anon-campaign.json" "$cid"; then
  print "PASS: Anonymous campaign response omits memberState"; (( api_pass += 1 ))
else
  print "FAIL: Anonymous campaign response leaked memberState or had invalid identity"; (( api_fail += 1 ))
fi
code=$(api_request GET "$API_BASE_URL/campaigns/$cid/coupons?page=0&size=100" "$anon_jar" "$API_TEST_DIR/phase8-anon-coupons.json")
api_record "Anonymous reads public coupon summaries" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1]));sys.exit(0 if "memberState" not in d and isinstance(d.get("data"),list) else 1)' "$API_TEST_DIR/phase8-anon-coupons.json"; then
  print "PASS: Anonymous coupon response omits memberState"; (( api_pass += 1 ))
else
  print "FAIL: Anonymous coupon response shape is invalid"; (( api_fail += 1 ))
fi

code=$(api_request GET "$API_BASE_URL/campaigns/$cid" "$member_jar" "$API_TEST_DIR/phase8-member-campaign.json")
api_record "MEMBER reads campaign landing page" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if isinstance(d.get("memberState",{}).get("eligible"),bool) else 1)' "$API_TEST_DIR/phase8-member-campaign.json"; then
  print "PASS: MEMBER response includes server-derived eligibility"; (( api_pass += 1 ))
else
  print "FAIL: MEMBER response omitted eligibility"; (( api_fail += 1 ))
fi
code=$(api_request GET "$API_BASE_URL/campaigns/$cid/coupons?page=0&size=100" "$member_jar" "$API_TEST_DIR/phase8-member-coupons.json")
api_record "MEMBER reads coupon claim state" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1]));sys.exit(0 if isinstance(d.get("memberState",{}).get("eligible"),bool) else 1)' "$API_TEST_DIR/phase8-member-coupons.json"; then
  print "PASS: MEMBER coupon response includes server-derived eligibility"; (( api_pass += 1 ))
else
  print "FAIL: MEMBER coupon response omitted eligibility"; (( api_fail += 1 ))
fi
code=$(api_request GET "$API_BASE_URL/campaigns/$cid" "$admin_jar" "$API_TEST_DIR/phase8-admin-public-campaign.json")
api_record "ADMIN can read public campaign content" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",{});sys.exit(0 if "memberState" not in d else 1)' "$API_TEST_DIR/phase8-admin-public-campaign.json"; then
  print "PASS: ADMIN public response omits MEMBER state"; (( api_pass += 1 ))
else
  print "FAIL: ADMIN public response contains MEMBER state"; (( api_fail += 1 ))
fi

code=$(api_request GET "$API_BASE_URL/admin/campaigns/$cid" "$anon_jar" "$API_TEST_DIR/phase8-draft-probe.json")
# This route is intentionally an Admin-only route; it is not the public landing endpoint.
api_record "Anonymous cannot read Admin campaign configuration" "$code" "401"

inbox_code=$(api_request GET "$API_BASE_URL/member/messages?page=0&size=100" "$member_jar" "$API_TEST_DIR/phase8-inbox.json")
api_record "MEMBER reads own delivered inbox" "$inbox_code" "200"
if [[ "$inbox_code" != "200" ]]; then unset API_TEST_PASSWORD; api_summary; exit 1; fi
message_id=$("$PYTHON_BIN" -c 'import json,sys;d=json.load(open(sys.argv[1])).get("data",[]);m=next((x for x in d if x.get("campaignId")==sys.argv[2]),None);print(m.get("id","") if m else "")' "$API_TEST_DIR/phase8-inbox.json" "$cid")
if [[ -z "$message_id" ]]; then
  print -u2 "ERROR: No delivered inbox row for this campaign exists for the configured member."
  print -u2 "Complete Phase 7 delivery to this member before running Phase 8 event checks."
  unset API_TEST_PASSWORD; api_summary; exit 1
fi

# Authenticated event writes require both MEMBER authorization and CSRF.
api_csrf_fetch "$anon_jar" "phase8-anon-event" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
print -r -- '{"eventId":"'$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')'","eventType":"LANDING_PAGE_VIEW","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-anon-event.json"
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$anon_jar" "$API_TEST_DIR/phase8-anon-event-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-anon-event.json")
api_record "Anonymous cannot submit member-attributed events" "$code" "401"

api_csrf_fetch "$admin_jar" "phase8-admin-event" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
print -r -- '{"eventId":"'$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')'","eventType":"LANDING_PAGE_VIEW","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-admin-event.json"
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$admin_jar" "$API_TEST_DIR/phase8-admin-event-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-admin-event.json")
api_record "ADMIN cannot submit MEMBER events" "$code" "403"

# A missing CSRF header is rejected before the event controller is invoked.
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-no-csrf.json" -H "Content-Type: application/json" --data-binary '{}')
api_record "Event write requires CSRF" "$code" "403"

api_csrf_fetch "$member_jar" "phase8-member-event" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
event_id=$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')
print -r -- '{"eventId":"'$event_id'","eventType":"IMPRESSION","messageId":"'$message_id'","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-impression.json"
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-impression-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-impression.json")
api_record "MEMBER records impression for own delivered message" "$code" "202"
api_csrf_fetch "$member_jar" "phase8-impression-replay" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-impression-replay-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-impression.json")
api_record "Duplicate event ID is accepted idempotently" "$code" "202"

print -r -- '{"eventId":"'$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')'","eventType":"LANDING_PAGE_VIEW","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-landing-view.json"
api_csrf_fetch "$member_jar" "phase8-landing-view" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-landing-view-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-landing-view.json")
api_record "MEMBER records authenticated landing-page view" "$code" "202"

print -r -- '{"eventId":"'$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')'","eventType":"CLICK","messageId":"'$message_id'","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-click.json"
api_csrf_fetch "$member_jar" "phase8-click" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-click-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-click.json")
api_record "MEMBER records click for own delivered message" "$code" "202"

wrong_message=$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')
print -r -- '{"eventId":"'$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')'","eventType":"CLICK","messageId":"'$wrong_message'","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-wrong-message.json"
api_csrf_fetch "$member_jar" "phase8-wrong-message" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-wrong-message-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-wrong-message.json")
api_record "Event rejects message not delivered to current member" "$code" "400"

print -r -- '{"eventId":"'$("$PYTHON_BIN" -c 'import uuid;print(uuid.uuid4())')'","eventType":"CLAIM_ATTEMPT","occurredAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}' > "$API_TEST_DIR/phase8-unsupported-event.json"
api_csrf_fetch "$member_jar" "phase8-unsupported-event" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
code=$(api_request POST "$API_BASE_URL/campaigns/$cid/events" "$member_jar" "$API_TEST_DIR/phase8-unsupported-event-response.json" -H "Content-Type: application/json" -H "X-CSRF-TOKEN: $CSRF_TOKEN" --data-binary @"$API_TEST_DIR/phase8-unsupported-event.json")
api_record "Client cannot submit CLAIM_ATTEMPT" "$code" "400"

unset API_TEST_PASSWORD
api_summary
