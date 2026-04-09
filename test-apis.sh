#!/bin/bash
# test-apis.sh — Test microservices APIs using Postman collection

set -e

BASE_URL=${1:-"http://localhost:8080"}

echo "Checking API Gateway health at ${BASE_URL}..."
MAX_RETRIES=30
RETRY_COUNT=0

until $(curl -sf "${BASE_URL}/actuator/health" > /dev/null); do
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo "API Gateway failed to become healthy in time."
        exit 1
    fi
    echo "Waiting for API Gateway... ($((RETRY_COUNT+1))/${MAX_RETRIES})"
    RETRY_COUNT=$((RETRY_COUNT+1))
    sleep 5
done

echo "API Gateway is healthy!"
echo "Running API tests with Newman..."

npx -y newman run ecommerce-api-collection.json \
    --global-var "baseUrl=${BASE_URL}" \
    --reporters cli \
    --folder "Auth"

echo "Auth tests complete. Running full collection..."
npx -y newman run ecommerce-api-collection.json \
    --global-var "baseUrl=${BASE_URL}" \
    --reporters cli
