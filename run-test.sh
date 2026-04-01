#!/bin/bash
cd /Users/sharathkumarlakshmanarao/IdeaProjects/DataSyncDriver

# Kill any running instances
pkill -9 -f DataSyncDriverApp 2>/dev/null
pkill -9 -f "spring-boot:run" 2>/dev/null
sleep 3

# Clean old data
rm -f sync-output.csv

# Start the app in background
./mvnw spring-boot:run &
APP_PID=$!
echo "Starting app PID=$APP_PID..."

# Wait for startup
for i in $(seq 1 40); do
    if curl -s http://localhost:8081/api/v1/config/connections > /dev/null 2>&1; then
        echo "App is UP after ${i}s"
        break
    fi
    sleep 1
done

# Run setup
echo ""
echo "=== Setting up configuration ==="
bash /Users/sharathkumarlakshmanarao/IdeaProjects/DataSyncDriver/test-setup.sh

echo ""
echo "=== ALL DONE ==="

