#!/usr/bin/env bash
cd "$(dirname "$0")/../.."
TARGET="${1:-backend}"
docker compose -f docker-compose.prod.yml logs -f --tail=200 "$TARGET"
