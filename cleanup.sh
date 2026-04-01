#!/bin/bash
pkill -9 -f DataSyncDriverApp 2>/dev/null
pkill -9 -f "spring-boot:run" 2>/dev/null
sleep 2
cd /Users/sharathkumarlakshmanarao/IdeaProjects/DataSyncDriver
rm -f sync-output.csv
echo "CLEANUP DONE"

