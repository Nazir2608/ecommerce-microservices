#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# deploy.sh — called by GitHub Actions via SSH
# Usage: ./deploy.sh user-service
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

SERVICE=${1:-"all"}
DEPLOY_DIR="/opt/ecommerce"
LOG_FILE="${DEPLOY_DIR}/logs/deploy-$(date +%Y%m%d-%H%M%S).log"

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

cd "$DEPLOY_DIR"

log "🚀 Starting deployment: $SERVICE"

pull_image() {
  local svc=$1
  log "Pulling latest image for $svc..."
  docker compose pull "$svc" 2>&1 | tee -a "$LOG_FILE"
}

deploy_service() {
  local svc=$1
  local port=$2

  log "Deploying $svc..."
  docker compose up -d --no-deps --force-recreate "$svc" 2>&1 | tee -a "$LOG_FILE"

  log "Waiting for $svc health check on port $port..."
  for i in $(seq 1 18); do
    STATUS=$(curl -sf "http://localhost:${port}/actuator/health" \
             | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','DOWN'))" 2>/dev/null || echo "DOWN")
    if [ "$STATUS" = "UP" ]; then
      log "✅ $svc is UP"
      return 0
    fi
    log "Attempt $i/18 — $svc status: $STATUS"
    sleep 10
  done

  log "❌ $svc failed health check!"
  return 1
}

# Service → port mapping
declare -A PORTS=(
  ["user-service"]="8081"
  ["product-service"]="8085"
  ["order-service"]="8082"
  ["payment-service"]="8083"
  ["notification-service"]="8084"
  ["api-gateway"]="8080"
)

if [ "$SERVICE" = "all" ]; then
  for svc in "${!PORTS[@]}"; do
    pull_image "$svc"
    deploy_service "$svc" "${PORTS[$svc]}" || exit 1
  done
else
  pull_image "$SERVICE"
  deploy_service "$SERVICE" "${PORTS[$SERVICE]}" || exit 1
fi

log "✅ Deployment complete: $SERVICE"

# Print status of all services
log "Current service status:"
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>&1 | tee -a "$LOG_FILE"
