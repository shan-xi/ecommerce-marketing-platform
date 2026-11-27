#!/usr/bin/env sh
set -eu

MAVEN_VERSION="3.10.2"
MAVEN_HOME_BASE="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists"
MAVEN_HOME="${MAVEN_HOME_BASE}/apache-maven-${MAVEN_VERSION}"
MAVEN_URL="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  command -v curl >/dev/null 2>&1 || { echo "curl is required to bootstrap Maven ${MAVEN_VERSION}." >&2; exit 1; }
  command -v tar >/dev/null 2>&1 || { echo "tar is required to bootstrap Maven ${MAVEN_VERSION}." >&2; exit 1; }
  mkdir -p "${MAVEN_HOME_BASE}"
  TEMP_DIR="$(mktemp -d)"
  trap 'rm -rf "${TEMP_DIR}"' EXIT HUP INT TERM
  echo "Downloading Apache Maven ${MAVEN_VERSION}..."
  curl --fail --location --silent --show-error "${MAVEN_URL}" -o "${TEMP_DIR}/maven.tar.gz"
  curl --fail --location --silent --show-error "${MAVEN_URL}.sha512" -o "${TEMP_DIR}/maven.tar.gz.sha512"
  EXPECTED="$(awk '{print $1}' "${TEMP_DIR}/maven.tar.gz.sha512")"
  if command -v sha512sum >/dev/null 2>&1; then
    ACTUAL="$(sha512sum "${TEMP_DIR}/maven.tar.gz" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL="$(shasum -a 512 "${TEMP_DIR}/maven.tar.gz" | awk '{print $1}')"
  else
    echo "sha512sum or shasum is required to verify the Maven distribution." >&2
    exit 1
  fi
  [ "${EXPECTED}" = "${ACTUAL}" ] || { echo "Maven distribution checksum verification failed." >&2; exit 1; }
  tar -xzf "${TEMP_DIR}/maven.tar.gz" -C "${MAVEN_HOME_BASE}"
  [ -x "${MAVEN_HOME}/bin/mvn" ] || { echo "Maven extraction did not produce the expected executable." >&2; exit 1; }
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
