#!/usr/bin/env bash
set -euo pipefail

VERSION="${JMETER_VERSION:-5.6.3}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_PATH="$(command -v java 2>/dev/null || true)"
if [[ -z "${JMETER_CACHE_DIR:-}" && -d /mnt/c/Java && -n "${JAVA_PATH}" && "$(readlink -f "${JAVA_PATH}")" == /mnt/c/* ]]; then
  CACHE_DIR="/mnt/c/Java/apache-jmeter-${VERSION}"
else
  CACHE_DIR="${JMETER_CACHE_DIR:-${SCRIPT_DIR}/apache-jmeter-${VERSION}}"
fi
ARCHIVE="${CACHE_DIR}.tgz"
BASE_URL="https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-${VERSION}.tgz"
CHECKSUM_URL="${BASE_URL}.sha512"

if [[ -x "${CACHE_DIR}/bin/jmeter" ]]; then
  (cd "${CACHE_DIR}" && JMETER_HOME=. ./bin/jmeter --version)
  exit 0
fi

mkdir -p "$(dirname "${CACHE_DIR}")"
echo "Downloading Apache JMeter ${VERSION}..."
curl --fail --location --show-error --silent --output "${ARCHIVE}" "${BASE_URL}"
curl --fail --location --show-error --silent "${CHECKSUM_URL}" \
  | tr -d '\r\n' | awk '{print $1 "  '"${ARCHIVE}"'"}' > "${ARCHIVE}.sha512"
sha512sum --check "${ARCHIVE}.sha512"

tar --extract --gzip --file "${ARCHIVE}" --directory "$(dirname "${CACHE_DIR}")"
rm -f "${ARCHIVE}" "${ARCHIVE}.sha512"
(cd "${CACHE_DIR}" && JMETER_HOME=. ./bin/jmeter --version)
