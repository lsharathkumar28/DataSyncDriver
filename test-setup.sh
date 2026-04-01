#!/bin/bash
set -e

BASE="http://localhost:8081/api/v1/config"

echo "=== Step 1: Create connections ==="
curl -s -X POST "$BASE/connections" \
  -H "Content-Type: application/json" \
  -d '{"name":"internal-idvault","systemType":"INTERNAL","connectionType":"REST","baseUrl":"http://localhost:8080"}'
echo ""

curl -s -X POST "$BASE/connections" \
  -H "Content-Type: application/json" \
  -d '{"name":"external-csv","systemType":"EXTERNAL","connectionType":"CSV"}'
echo ""

echo "=== Step 2: Create schema mappings ==="
curl -s -X POST "$BASE/mappings/batch" \
  -H "Content-Type: application/json" \
  -d '[
    {"mappingGroupName":"user-to-csv","sourceSystem":"internal-idvault","targetSystem":"external-csv","sourceField":"userId","targetField":"user_id","dataType":"UUID","required":true},
    {"mappingGroupName":"user-to-csv","sourceSystem":"internal-idvault","targetSystem":"external-csv","sourceField":"name","targetField":"full_name","dataType":"STRING","required":true},
    {"mappingGroupName":"user-to-csv","sourceSystem":"internal-idvault","targetSystem":"external-csv","sourceField":"emailId","targetField":"email_address","dataType":"STRING"},
    {"mappingGroupName":"user-to-csv","sourceSystem":"internal-idvault","targetSystem":"external-csv","sourceField":"phoneNumber","targetField":"phone","dataType":"STRING"},
    {"mappingGroupName":"user-to-csv","sourceSystem":"internal-idvault","targetSystem":"external-csv","sourceField":"attributes.department","targetField":"dept","dataType":"STRING","transformExpression":"UPPER","defaultValue":"UNKNOWN"}
  ]'
echo ""

echo "=== Step 3: Create sync rules (phone BLOCKED) ==="
curl -s -X POST "$BASE/sync-rules/batch" \
  -H "Content-Type: application/json" \
  -d '[
    {"ruleName":"csv-allow-id","connectorName":"CSV File","attributeName":"user_id","syncEnabled":true,"direction":"OUTBOUND"},
    {"ruleName":"csv-allow-name","connectorName":"CSV File","attributeName":"full_name","syncEnabled":true,"direction":"OUTBOUND"},
    {"ruleName":"csv-allow-email","connectorName":"CSV File","attributeName":"email_address","syncEnabled":true,"direction":"BIDIRECTIONAL"},
    {"ruleName":"csv-block-phone","connectorName":"CSV File","attributeName":"phone","syncEnabled":false,"direction":"OUTBOUND"},
    {"ruleName":"csv-allow-dept","connectorName":"CSV File","attributeName":"dept","syncEnabled":true,"direction":"OUTBOUND"}
  ]'
echo ""

echo "=== SETUP COMPLETE ==="
echo ""
echo "Mappings configured:"
curl -s "$BASE/mappings/group/user-to-csv" | python3 -c "
import sys, json
for m in json.load(sys.stdin):
    print(f'  {m[\"sourceField\"]:25s} -> {m[\"targetField\"]:20s} [{m.get(\"transformExpression\",\"-\")}]')
"

echo ""
echo "Sync rules configured:"
curl -s "$BASE/sync-rules/connector/CSV%20File" | python3 -c "
import sys, json
for r in json.load(sys.stdin):
    status = 'ENABLED' if r['syncEnabled'] else 'BLOCKED'
    print(f'  {r[\"attributeName\"]:25s} {status:8s} [{r[\"direction\"]}]')
"

