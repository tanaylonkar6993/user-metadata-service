#!/bin/bash

BASE_URL="http://localhost:8080"
IDEM_KEY="replay-test-key-fixed-001"

echo "Testing idempotency — 50 replays of same key"
echo "============================================="

for i in $(seq 1 50); do
  RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/user" \
    -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: $IDEM_KEY" \
    -d '{
      "user_id": "usr-replay-001",
      "name":    "Replay Test User",
      "email":   "replay@loadtest.com",
      "phone":   "+919999900001"
    }')

  echo "Request $i: HTTP $RESPONSE"
  sleep 0.05
done
