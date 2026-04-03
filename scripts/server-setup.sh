#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# Server setup script — run once on a fresh Ubuntu 22.04 VPS
# Usage: bash server-setup.sh
#
#   What this script does:
#   1. Installs Docker + Docker Compose (the only runtime dependency needed)
#   2. Creates deployment user with SSH key
#   3. Creates /opt/ecommerce with correct permissions
#   4. Installs useful tools (curl, jq for health checks)
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail
echo "🚀 Setting up production server..."

# ── System updates ────────────────────────────────────────────────────────────
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq curl wget git jq unzip net-tools

# ── Docker ────────────────────────────────────────────────────────────────────
echo "Installing Docker..."
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

# Add deploy user to docker group (no sudo needed for docker commands)
usermod -aG docker ubuntu 2>/dev/null || true

# ── Docker Compose (plugin) ───────────────────────────────────────────────────
COMPOSE_VERSION="v2.24.0"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

echo "Docker version: $(docker --version)"
echo "Compose version: $(docker compose version)"

# ── Deployment directory ──────────────────────────────────────────────────────
mkdir -p /opt/ecommerce
mkdir -p /opt/ecommerce/infrastructure/prometheus
mkdir -p /opt/ecommerce/infrastructure/grafana
mkdir -p /opt/ecommerce/infrastructure/logstash/pipeline
mkdir -p /opt/ecommerce/logs

chown -R ubuntu:ubuntu /opt/ecommerce

# ── Firewall (UFW) ────────────────────────────────────────────────────────────
apt-get install -y ufw
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow 80/tcp    # HTTP (Nginx)
ufw allow 443/tcp   # HTTPS (Nginx)
ufw allow 8080/tcp  # API Gateway (direct, for testing — remove in strict prod)
ufw --force enable

echo "✅ Server setup complete!"
echo ""
echo "Next steps:"
echo "  1. Copy your .env file to /opt/ecommerce/.env"
echo "  2. Copy docker-compose.yml to /opt/ecommerce/"
echo "  3. Copy infrastructure/ configs to /opt/ecommerce/infrastructure/"
echo "  4. Run: cd /opt/ecommerce && docker compose up -d"
