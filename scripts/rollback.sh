#!/bin/bash
# rollback.sh — roll back a service to a specific version tag
# Usage: ./rollback.sh user-service sha-a1b2c3d4
#
# LEARNING POINT — Why tag images with SHA?
#   latest = current version (changes on every deploy)
#   sha-a1b2c3 = specific commit (never changes)
#   If a deploy breaks production:
#     docker pull ghcr.io/owner/nazir-user-service:sha-prev
#     docker compose up -d --no-deps user-service
#   You need the sha tag to roll back to exactly the previous version.

set -euo pipefail

SERVICE=${1:?Usage: ./rollback.sh <service> <tag>}
TAG=${2:?Usage: ./rollback.sh <service> <tag>}
REGISTRY="ghcr.io"
OWNER="${GHCR_OWNER:?Set GHCR_OWNER env variable}"

echo "Rolling back $SERVICE to $TAG..."
docker pull "${REGISTRY}/${OWNER}/nazir-${SERVICE}:${TAG}"

# Override the image tag temporarily in compose
IMAGE="${REGISTRY}/${OWNER}/nazir-${SERVICE}:${TAG}" \
  docker compose up -d --no-deps --force-recreate "${SERVICE}"

echo "✅ Rollback complete: $SERVICE → $TAG"
