#!/bin/bash

echo "Hitting GET endpoints"
for i in $(seq 1 100); do
  curl -s -o /dev/null "http://localhost:8080/user/usr-$(printf '%04d' $i)"
  sleep 0.05
done
echo "Done"
