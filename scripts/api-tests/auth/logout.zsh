#!/bin/zsh
SCRIPT_DIR="${0:A:h}"
API_TEST_HOME="${SCRIPT_DIR:h}"
source "$API_TEST_HOME/lib/common.zsh" || exit 1
source "$API_TEST_HOME/lib/csrf.zsh"
source "$API_TEST_HOME/lib/auth.zsh"

api_prompt_password || exit 1
jar=$(api_new_jar "logout-single")
api_login_admin "$jar" "logout-single-login" || { api_summary; exit 1; }
api_logout "$jar" "logout-single"
api_get_me "$jar" "logout-single-after" "401"

api_summary
exit $?
