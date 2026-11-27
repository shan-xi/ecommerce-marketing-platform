#!/bin/zsh
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

api_prompt_password || exit 1

anonymous=$(api_new_jar "role-anonymous")
admin=$(api_new_jar "role-admin")
member=$(api_new_jar "role-member")

code=$(api_request GET "$API_BASE_URL/admin/probe" "$anonymous" "$API_TEST_DIR/role-anonymous-admin.json")
api_record "Anonymous accessing Admin API" "$code" "401"
code=$(api_request GET "$API_BASE_URL/member/probe" "$anonymous" "$API_TEST_DIR/role-anonymous-member.json")
api_record "Anonymous accessing Member API" "$code" "401"

api_login_admin "$admin" "role-admin-login" || { api_summary; exit 1; }
code=$(api_request GET "$API_BASE_URL/member/probe" "$admin" "$API_TEST_DIR/role-admin-member.json")
api_record "Admin accessing Member API" "$code" "403"

api_login_member "$member" "role-member-login" || { api_summary; exit 1; }
code=$(api_request GET "$API_BASE_URL/admin/probe" "$member" "$API_TEST_DIR/role-member-admin.json")
api_record "Member accessing Admin API" "$code" "403"

api_summary
exit $?
