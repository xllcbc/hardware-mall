#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/backend-admin-deploy.yml"
fail=0

if grep -nE '\.sql([^[:alnum:]_]|$)' "$workflow"; then
  echo "deploy workflow must not reference .sql files" >&2
  fail=1
fi
if grep -niE 'pricing|idempotency|order_item|wholesale' "$workflow"; then
  echo "deploy workflow must not contain issue-specific logic" >&2
  fail=1
fi
if grep -n 'src/main/resources/db' "$workflow"; then
  echo "deploy workflow must not reference backend db resources" >&2
  fail=1
fi

exit "$fail"
