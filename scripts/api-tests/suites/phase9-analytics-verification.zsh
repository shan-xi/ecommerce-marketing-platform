#!/bin/zsh
# Phase 9 read-only analytics API verification. Uses an existing campaign; no fixtures are created.
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

print "\n=========================================="
print " Phase 9 Campaign Analytics Verification"
print "=========================================="
if [[ -z "${PHASE9_CAMPAIGN_ID:-}" ]]; then
  print -u2 "ERROR: PHASE9_CAMPAIGN_ID is required. Set it to an existing campaign UUID."
  exit 2
fi
cid="$PHASE9_CAMPAIGN_ID"
if ! "$PYTHON_BIN" -c 'import sys,uuid;uuid.UUID(sys.argv[1])' "$cid" 2>/dev/null; then
  print -u2 "ERROR: PHASE9_CAMPAIGN_ID must be a UUID."
  exit 2
fi
api_prompt_password || exit 1
admin_jar=$(api_new_jar "phase9-admin")
member_jar=$(api_new_jar "phase9-member")
anon_jar=$(api_new_jar "phase9-anon")
api_login_admin "$admin_jar" "phase9-admin-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }
api_login_member "$member_jar" "phase9-member-login" || { unset API_TEST_PASSWORD; api_summary; exit 1; }

path="$API_BASE_URL/admin/campaigns/$cid/analytics"
code=$(api_request GET "$path" "$anon_jar" "$API_TEST_DIR/phase9-anon.json")
api_record "Anonymous cannot read campaign analytics" "$code" "401"
code=$(api_request GET "$path" "$member_jar" "$API_TEST_DIR/phase9-member.json")
api_record "MEMBER cannot read campaign analytics" "$code" "403"
code=$(api_request GET "$path" "$admin_jar" "$API_TEST_DIR/phase9-admin.json")
api_record "ADMIN reads campaign analytics" "$code" "200"
if [[ "$code" == "200" ]] && "$PYTHON_BIN" -c '
import json,sys
x=json.load(open(sys.argv[1])).get("data",{})
required={"campaignId","campaignName","targetMembers","deliveredMembers","deliveryRate","impressionCount","uniqueReach","reachRate","clickCount","uniqueClickers","clickRate","landingPageViews","uniqueLandingVisitors","targetLandingVisitorRate","couponClaims","uniqueClaimers","targetClaimRate","claimAttemptCount","failedClaimAttemptCount","claimAttemptsByOutcome","coupons"}
assert required.issubset(x), required-set(x)
assert x["campaignId"] == sys.argv[2]
for k in ("deliveryRate","reachRate","clickRate","targetLandingVisitorRate","targetClaimRate"):
 r=x[k]; assert set(("numerator","denominator","value")).issubset(r)
 if r["denominator"]==0: assert r["value"] is None
outcomes=x["claimAttemptsByOutcome"]
assert {"succeeded","failed","replayed"}.issubset(outcomes)
assert sum(outcomes[k] for k in ("succeeded","failed","replayed")) == x["claimAttemptCount"]
assert outcomes["failed"] == x["failedClaimAttemptCount"]
for c in x["coupons"]:
 assert {"couponId","name","totalQuantity","successfulClaims","remainingQuantity","uniqueClaimers","targetClaimRate"}.issubset(c)
 assert c["remainingQuantity"] == c["totalQuantity"] - c["successfulClaims"]
 assert {"numerator","denominator","value"}.issubset(c["targetClaimRate"])
def inspect(obj):
 if isinstance(obj, dict):
  for k,v in obj.items():
   assert not k.lower().endswith("memberid") and k not in {"metadata","idempotencyKey","rawEvents"}
   inspect(v)
 elif isinstance(obj, list):
  for v in obj: inspect(v)
inspect(x)
' "$API_TEST_DIR/phase9-admin.json" "$cid"; then
  print "PASS: Response includes defined metrics, coupon summaries, null-rate semantics, and no raw member data"; (( api_pass += 1 ))
else
  print "FAIL: Analytics response shape or privacy/rate semantics are invalid"; (( api_fail += 1 ))
fi
unset API_TEST_PASSWORD
api_summary
