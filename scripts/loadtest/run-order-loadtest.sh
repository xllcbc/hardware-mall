#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
JMETER_VERSION="${JMETER_VERSION:-5.6.3}"
JAVA_PATH="$(command -v java 2>/dev/null || true)"
if [[ -z "${JMETER_CACHE_DIR:-}" && -d /mnt/c/Java && -n "${JAVA_PATH}" && "$(readlink -f "${JAVA_PATH}")" == /mnt/c/* ]]; then
  JMETER_CACHE_DIR="/mnt/c/Java/apache-jmeter-${JMETER_VERSION}"
else
  JMETER_CACHE_DIR="${JMETER_CACHE_DIR:-${SCRIPT_DIR}/apache-jmeter-${JMETER_VERSION}}"
fi
JMETER_BIN="${JMETER_BIN:-${JMETER_CACHE_DIR}/bin/jmeter}"
JMX_FILE="${SCRIPT_DIR}/order-create.jmx"
RESULT_ROOT="${RESULT_ROOT:-${SCRIPT_DIR}/results}"

if [[ ! -x "${JMETER_BIN}" ]]; then
  JMETER_CACHE_DIR="${JMETER_CACHE_DIR}" "${SCRIPT_DIR}/download-jmeter.sh"
fi
if [[ ! -x "${JMETER_BIN}" ]]; then
  echo "JMeter executable not found: ${JMETER_BIN}" >&2
  exit 1
fi

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-8080}"
BASE_URL="http://${HOST}:${PORT}"

# Resolve an authenticated user and address unless the caller supplied them.
if [[ -z "${JWT_TOKEN:-}" ]]; then
  echo "JWT_TOKEN not set; logging in with a test_ code to prepare a load-test user..."
  code="test_jmeter_$(date +%s)"
  login_json="$(curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "{\"code\":\"${code}\"}" "${BASE_URL}/api/user/login")"
  JWT_TOKEN="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])' <<<"${login_json}")"
  address_json="$(curl -fsS --max-time 10 -H "Authorization: Bearer ${JWT_TOKEN}" -H 'Content-Type: application/json' -d '{"consignee":"JMeter Test","phone":"13800138000","province":"Beijing","city":"Beijing","district":"Chaoyang","detail":"JMeter CLI Load Test Address","postalCode":"100000","isDefault":0}' "${BASE_URL}/api/user/address")"
  ADDRESS_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])' <<<"${address_json}")"
  echo "Prepared load-test address: address_id=${ADDRESS_ID}"
fi

ADDRESS_ID="${ADDRESS_ID:-}"
SKU_ID="${SKU_ID:-}"
LOGISTICS_ID="${LOGISTICS_ID:-}"
QUANTITY="${QUANTITY:-1}"

# Discover a SKU and logistics row when not supplied.
if [[ -z "${SKU_ID}" ]]; then
  spu_json="$(curl -fsS --max-time 10 "${BASE_URL}/api/user/product/list?page=1&limit=1")"
  spu_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["records"][0]["id"])' <<<"${spu_json}")"
  sku_json="$(curl -fsS --max-time 10 "${BASE_URL}/api/user/product/${spu_id}/skus")"
  SKU_ID="$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["data"][0]["id"])' <<<"${sku_json}")"
  echo "Auto-discovered sku_id=${SKU_ID}"
fi
if [[ -z "${LOGISTICS_ID}" ]]; then
  logistics_json="$(curl -fsS --max-time 10 "${BASE_URL}/api/user/logistics/list")"
  LOGISTICS_ID="$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["data"][0]["id"])' <<<"${logistics_json}")"
  echo "Auto-discovered logistics_id=${LOGISTICS_ID}"
fi

if [[ -z "${ADDRESS_ID}" || -z "${SKU_ID}" || -z "${LOGISTICS_ID}" ]]; then
  echo "ADDRESS_ID, SKU_ID, LOGISTICS_ID must be resolvable." >&2
  echo "Run with: JWT_TOKEN=<token> ADDRESS_ID=<id> SKU_ID=<id> LOGISTICS_ID=<id> bash $0" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
result_dir="${RESULT_ROOT}/${timestamp}"
mkdir -p "${result_dir}/report"

# Invoke JMeter through a path relative to the project root so its jar remains
# accessible to Windows java.exe. Convert the data and report paths separately.
relative_jmeter="$(realpath --relative-to="${PROJECT_ROOT}" "${JMETER_CACHE_DIR}")"
relative_jmx="$(realpath --relative-to="${PROJECT_ROOT}" "${JMX_FILE}")"
relative_result="$(realpath --relative-to="${PROJECT_ROOT}" "${result_dir}")"

if [[ -n "${JAVA_PATH}" && "$(readlink -f "${JAVA_PATH}")" == /mnt/c/* && -x "$(command -v wslpath 2>/dev/null || true)" ]]; then
  jmx_arg="$(wslpath -w "${JMX_FILE}")"
  result_arg="$(wslpath -w "${result_dir}")"
else
  jmx_arg="${relative_jmx}"
  result_arg="${relative_result}"
fi

echo "Running load test: threads=${THREADS:-100} ramp_up=${RAMP_UP:-30} duration=${DURATION:-60} sku_id=${SKU_ID} logistics_id=${LOGISTICS_ID} quantity=${QUANTITY}"

cd "${PROJECT_ROOT}"
exec env JMETER_HOME="${relative_jmeter}" "${relative_jmeter}/bin/jmeter" -n \
  -t "${jmx_arg}" \
  -Jtoken="${JWT_TOKEN}" \
  -Jaddress_id="${ADDRESS_ID}" \
  -Jsku_id="${SKU_ID}" \
  -Jlogistics_id="${LOGISTICS_ID}" \
  -Jquantity="${QUANTITY}" \
  -Jthreads="${THREADS:-100}" \
  -Jramp_up="${RAMP_UP:-30}" \
  -Jduration="${DURATION:-60}" \
  -Jhost="${HOST}" \
  -Jport="${PORT}" \
  -l "${result_arg}/results.jtl" \
  -e -o "${result_arg}/report"
