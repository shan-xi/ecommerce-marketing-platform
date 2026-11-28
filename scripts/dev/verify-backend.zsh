#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

echo "[backend] Running a clean Maven verification to avoid stale target/classes..."
./mvnw -f backend/pom.xml clean verify
