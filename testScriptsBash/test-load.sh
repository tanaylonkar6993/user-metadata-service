#!/bin/bash

BASE_URL="http://localhost:8080"
TOTAL_REQUESTS=200
SUCCESS=0
FAIL=0

echo "Starting load test — $TOTAL_REQUESTS requests"
echo "============================================="

for i in $(seq 1 $TOTAL_REQUESTS); do

  # Generate unique user and idempotency key per request
  USER_ID="usr-$(printf '%04d' $i)"
  IDEM_KEY=$(cat /proc/sys/kernel/random/uuid)
  EMAIL="user${i}@loadtest.com"

  RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/user" \
    -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: $IDEM_KEY" \
    -d "{
      \"user_id\": \"$USER_ID\",
      \"name\":    \"Load Test User $i\",
      \"email\":   \"$EMAIL\",
      \"phone\":   \"+9199999$(printf '%05d' $i)\"
    }")

  if [ "$RESPONSE" == "201" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi

  # Print progress every 10 requests
  if [ $((i % 10)) -eq 0 ]; then
    echo "Progress: $i/$TOTAL_REQUESTS | Success: $SUCCESS | Failed: $FAIL"
  fi

  # Small delay to see metrics build up on Grafana
  sleep 0.1

done

echo "============================================="
echo "DONE — Total: $TOTAL_REQUESTS | Success: $SUCCESS | Failed: $FAIL"
