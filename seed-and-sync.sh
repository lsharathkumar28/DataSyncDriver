#!/bin/bash
# Creates a few test users in DataSynchronizer and syncs them to the drivers.
# Run this after everything is up (DataSynchronizer + driver containers).

set -e

DS="http://localhost:8080/api/v1"

echo "Creating users in DataSynchronizer..."

curl -s -X POST "$DS/users" -H "Content-Type: application/json" -d '{
  "name": "John Doe", "firstName": "John", "middleName": "M", "lastName": "Doe",
  "emailId": "john.doe@example.com", "phoneNumber": "555-0101",
  "attributes": {"department": "Engineering", "title": "Senior Developer", "location": "New York"}
}'
echo ""

curl -s -X POST "$DS/users" -H "Content-Type: application/json" -d '{
  "name": "Jane Smith", "firstName": "Jane", "lastName": "Smith",
  "emailId": "jane.smith@example.com", "phoneNumber": "555-0202",
  "attributes": {"department": "Product", "title": "Product Manager", "location": "San Francisco"}
}'
echo ""

curl -s -X POST "$DS/users" -H "Content-Type: application/json" -d '{
  "name": "Bob Johnson", "firstName": "Bob", "lastName": "Johnson",
  "emailId": "bob.johnson@example.com",
  "attributes": {"department": "Marketing", "title": "Marketing Lead"}
}'
echo ""

echo "Triggering initial sync..."
curl -s -X POST http://localhost:8081/api/v1/sync/initial | python3 -m json.tool
curl -s -X POST http://localhost:8082/api/v1/sync/initial | python3 -m json.tool 2>/dev/null || true

echo ""
echo "Done. Check output/sync-output.csv and output/sync-output.json"
