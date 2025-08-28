#!/bin/bash

# System monitoring script using structured logs and /stats endpoints
# Usage: ./monitor_system.sh

echo "🚀 LOGS SYSTEM MONITORING DASHBOARD"
echo "==================================="
echo "$(date)"
echo

# System health check
echo "🏥 SYSTEM HEALTH:"
SERVICES=("distributor" "analyzer-1" "analyzer-2" "analyzer-3" "analyzer-4")
PORTS=(8080 8081 8082 8083 8084)

for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    PORT="${PORTS[$i]}"
    STATUS=$(curl -s --max-time 2 "http://localhost:$PORT/api/v1/health" | head -1)
    if [[ $? -eq 0 && "$STATUS" == *"online"* ]]; then
        echo "  ✅ $SERVICE: Online"
    else
        echo "  ❌ $SERVICE: Offline"
    fi
done

echo

# Performance overview  
echo "📊 REAL-TIME PERFORMANCE:"
TOTAL_THROUGHPUT=0
for i in 1 2 3 4; do
    PORT=$((8080 + i))
    MESSAGES_SEC=$(curl -s --max-time 2 "http://localhost:$PORT/api/v1/stats" | grep "Messages/sec:" | awk '{print $2}')
    TOTAL_MESSAGES=$(curl -s --max-time 2 "http://localhost:$PORT/api/v1/stats" | grep "Total Messages Processed:" | awk '{print $4}')
    if [ -n "$MESSAGES_SEC" ]; then
        echo "  Analyzer-$i: ${MESSAGES_SEC} msgs/sec (${TOTAL_MESSAGES} total)"
        TOTAL_THROUGHPUT=$(echo "$TOTAL_THROUGHPUT + $MESSAGES_SEC" | bc 2>/dev/null || echo "$TOTAL_THROUGHPUT")
    fi
done

echo "  📈 TOTAL SYSTEM: ${TOTAL_THROUGHPUT} messages/second"
echo

# Check recent errors from logs
echo "⚠️  ERROR MONITORING (last 5 minutes):"
ERROR_FOUND=false
for service in distributor analyzer-1 analyzer-2 analyzer-3 analyzer-4; do
    CONTAINER="logs-$service"
    if docker ps --format "table {{.Names}}" | grep -q "$CONTAINER"; then
        ERRORS=$(docker logs "$CONTAINER" --since "5m" 2>/dev/null | grep -c "ERROR\|WARN\|FAIL")
        if [ "$ERRORS" -gt 0 ]; then
            echo "  ⚠️  $service: $ERRORS error/warning entries"
            ERROR_FOUND=true
        fi
    fi
done

if [ "$ERROR_FOUND" = false ]; then
    echo "  ✅ No errors or warnings detected"
fi

echo

# Distribution balance check
echo "⚖️  LOAD DISTRIBUTION:"
./parse_logs.sh distributor 5 | grep "ANALYZER DISTRIBUTION" -A 10 | tail -4

echo
echo "🕐 Monitoring Complete - $(date)"
echo "📝 For detailed analysis, use: ./parse_logs.sh [service_name] [minutes]"
