#!/usr/bin/env bash
set -euo pipefail

TAG="${1:?Usage: $0 sha-<commit> [deploy-root]}"
DEPLOY_ROOT="${2:-/opt/mkcs}"

test -f "$DEPLOY_ROOT/.env"
test -f "$DEPLOY_ROOT/docker-compose.yml"

cd "$DEPLOY_ROOT"
IMAGE_TAG="$TAG" docker compose --env-file .env pull backend frontend
IMAGE_TAG="$TAG" docker compose --env-file .env up -d backend frontend nginx
docker compose --env-file .env ps
