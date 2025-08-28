#!/bin/bash

# Simple log parser for structured logs
# Usage: ./parse_logs.sh [service_name] [time_range_minutes]

SERVICE=${1:-"distributor"}
TIME_RANGE=${2:-"5"}

echo "📊 LOG ANALYSIS for ${SERVICE} (last ${TIME_RANGE} minutes)"
echo "=============================================="

# Get the container name
CONTAINER="logs-${SERVICE}"

# Check if container exists
if ! docker ps --format "table {{.Names}}" | grep -q "$CONTAINER"; then
    echo "❌ Container $CONTAINER not found"
    exit 1
fi

echo "🔍 Parsing structured logs..."
echo

# Get logs for analysis
LOGS=$(docker logs "$CONTAINER" --since "${TIME_RANGE}m" 2>/dev/null)

# Parse packet metrics
echo "📦 PACKET METRICS:"
PACKETS_RECEIVED=$(echo "$LOGS" | grep -c "PACKET_RECEIVED")
PACKETS_PROCESSED=$(echo "$LOGS" | grep -c "PACKET_PROCESSED")
PACKETS_DROPPED=$(echo "$LOGS" | grep -c "PACKET_DROPPED")
TOTAL_MESSAGES=$(echo "$LOGS" | grep "PACKET_RECEIVED" | grep -o "messages=[0-9]*" | grep -o "[0-9]*" | awk '{sum+=$1} END {print sum}')

echo "  Received: $PACKETS_RECEIVED packets"
echo "  Processed: $PACKETS_PROCESSED packets"
echo "  Dropped: $PACKETS_DROPPED packets"
echo "  Total Messages: ${TOTAL_MESSAGES:-0}"

echo
echo "🎯 ANALYZER DISTRIBUTION:"
echo "$LOGS" | grep "PACKET_QUEUED" | grep -o "target_analyzer=[^|]*" | sort | uniq -c | while read count analyzer; do
    analyzer_name=$(echo "$analyzer" | cut -d'=' -f2)
    echo "  $analyzer_name: $count packets"
done

echo
echo "⚠️  ERROR ANALYSIS:"
ERROR_COUNT=$(echo "$LOGS" | grep -c "ERROR\|WARN")
if [ $ERROR_COUNT -eq 0 ]; then
    echo "  ✅ No errors or warnings found"
else
    echo "  ⚠️  Found $ERROR_COUNT error/warning entries:"
    echo "$LOGS" | grep "ERROR\|WARN" | head -3
fi

echo
echo "📈 RECENT PERFORMANCE:"
echo "$LOGS" | grep "SYSTEM_STATUS" | tail -1 | while IFS='|' read -r parts; do
    packets_sec=$(echo "$parts" | grep -o "packets_per_sec=[0-9.]*" | cut -d'=' -f2)
    messages_sec=$(echo "$parts" | grep -o "messages_per_sec=[0-9.]*" | cut -d'=' -f2)
    queue_size=$(echo "$parts" | grep -o "queue_size=[0-9]*" | cut -d'=' -f2)
    
    if [ -n "$packets_sec" ]; then
        echo "  Packets/sec: $packets_sec"
        echo "  Messages/sec: $messages_sec"
        echo "  Queue Size: $queue_size"
    else
        echo "  📊 Checking current analyzer stats..."
        curl -s http://localhost:8081/api/v1/stats | grep "Messages/sec:" | head -1
    fi
done

echo
echo "🕐 LOG ANALYSIS COMPLETE"