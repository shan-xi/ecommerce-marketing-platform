#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

# Rebuild every backend class from source before launching. This prevents a
# stale/incomplete backend/target/classes directory from omitting newly added
# domain classes such as InvalidAudienceRuleException.
./mvnw -f backend/pom.xml clean compile
exec ./mvnw -f backend/pom.xml spring-boot:run
