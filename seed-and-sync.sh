#!/bin/bash
# =============================================================================
# seed-and-sync.sh — Creates sample users in DataSynchronizer and triggers
#                    initial sync on both driver instances.
#
# Prerequisites:
#   - DataSynchronizer running on port 8080 (with PostgreSQL + Kafka)
#   - DataSyncDriver containers running (docker compose up --build)
#
# Usage:
#   ./seed-and-sync.sh
# =============================================================================
set -e

DS_BASE="http://localhost:8080/api/v1"
CSV_DRIVER="http://localhost:8081/api/v1"
JSON_DRIVER="http://localhost:8082/api/v1"

# Wait for DataSynchronizer to be ready
echo "Waiting for DataSynchronizer..."
for i in $(seq 1 30); do
    if curl -s "$DS_BASE/users" > /dev/null 2>&1; then
        echo "DataSynchronizer is UP"
        break
    fi
    [ "$i" -eq 30 ] && echo "ERROR: DataSynchronizer not reachable at $DS_BASE" && exit 1
    sleep 2
done

# Wait for CSV driver
echo "Waiting for CSV Driver (port 8081)..."
for i in $(seq 1 30); do
    if curl -s "$CSV_DRIVER/config/connections" > /dev/null 2>&1; then
        echo "CSV Driver is UP"
        break
    fi
    [ "$i" -eq 30 ] && echo "ERROR: CSV Driver not reachable at $CSV_DRIVER" && exit 1
    sleep 2
done

# Wait for JSON driver
echo "Waiting for JSON Driver (port 8082)..."
for i in $(seq 1 30); do
    if curl -s "$JSON_DRIVER/config/connections" > /dev/null 2>&1; then
        echo "JSON Driver is UP"
        break
    fi
    [ "$i" -eq 30 ] && echo "WARN: JSON Driver not reachable — skipping (only CSV will sync)"
    sleep 2
done

echo ""
echo "=== Creating sample users in DataSynchronizer ==="

curl -s -X POST "$DS_BASE/users" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "firstName": "John",
    "middleName": "M",
    "lastName": "Doe",
    "emailId": "john.doe@example.com",
    "phoneNumber": "555-0101",
    "attributes": {"department": "Engineering", "title": "Senior Developer", "location": "New York"}
  }' | python3 -m json.tool
echo ""

curl -s -X POST "$DS_BASE/users" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Smith",
    "firstName": "Jane",
    "lastName": "Smith",
    "emailId": "jane.smith@example.com",
    "phoneNumber": "555-0202",
    "attributes": {"department": "Product", "title": "Product Manager", "location": "San Francisco"}
  }' | python3 -m json.tool
echo ""

curl -s -X POST "$DS_BASE/users" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bob Johnson",
    "firstName": "Bob",
    "lastName": "Johnson",
    "emailId": "bob.johnson@example.com",
    "attributes": {"department": "Marketing", "title": "Marketing Lead"}
  }' | python3 -m json.tool
echo ""

echo "=== Triggering initial sync on CSV Driver ==="
curl -s -X POST "$CSV_DRIVER/sync/initial" | python3 -m json.tool
echo ""

echo "=== Triggering initial sync on JSON Driver ==="
curl -s -X POST "$JSON_DRIVER/sync/initial" | python3 -m json.tool 2>/dev/null || echo "(JSON driver not available — skipped)"
echo ""

echo "=== Sync complete! ==="
echo ""
echo "CSV output:"
cat output/sync-output.csv 2>/dev/null || cat sync-output.csv 2>/dev/null || echo "(not found)"
echo ""
echo "JSON output:"
cat output/sync-output.json 2>/dev/null || cat sync-output.json 2>/dev/null || echo "(not found)"

