#!/bin/bash

# Test script for the logs distribution system
set -e

echo "🚀 Testing Logs Distribution System"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080"
DISTRIBUTOR_ENDPOINT="$BASE_URL/api/v1/logs"

# Function to check if services are ready
check_health() {
    local service=$1
    local url=$2
    echo -e "${YELLOW}Checking health of $service...${NC}"
    
    for i in {1..30}; do
        if curl -sf "$url/api/v1/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ $service is healthy${NC}"
            return 0
        fi
        echo "  Attempt $i/30: Waiting for $service..."
        sleep 2
    done
    
    echo -e "${RED}✗ $service failed to start${NC}"
    return 1
}

# Function to send a test log packet
send_log_packet() {
    local packet_id=$1
    local agent_id=$2
    local num_messages=${3:-3}
    
    echo -e "${YELLOW}Sending log packet $packet_id with $num_messages messages...${NC}"
    
    # Generate log messages
    messages=""
    for i in $(seq 1 $num_messages); do
        level=$(shuf -n1 -e "DEBUG" "INFO" "WARN" "ERROR" "FATAL")
        if [ $i -gt 1 ]; then
            messages+=","
        fi
        messages+='{
            "level": "'$level'",
            "source": {
                "application": "test-app-'$agent_id'",
                "service": "test-service",
                "instance": "instance-'$i'",
                "host": "test-host-'$agent_id'"
            },
            "message": "Test log message #'$i' from agent '$agent_id'",
            "metadata": {
                "packetId": "'$packet_id'",
                "testRun": "automated-test",
                "messageIndex": '$i'
            }
        }'
    done
    
    # Create the full packet
    local packet='{
        "packetId": "'$packet_id'",
        "agentId": "'$agent_id'",
        "totalMessages": '$num_messages',
        "messages": ['$messages'],
        "checksum": "test-checksum-'$packet_id'"
    }'
    
    # Send the packet
    local response=$(curl -s -w "%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$packet" \
        "$DISTRIBUTOR_ENDPOINT" \
        -o /dev/null)
    
    if [ "$response" = "202" ]; then
        echo -e "${GREEN}✓ Packet $packet_id sent successfully${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed to send packet $packet_id (HTTP $response)${NC}"
        return 1
    fi
}

# Function to run load test
run_load_test() {
    local num_packets=${1:-50}
    local concurrent=${2:-5}
    
    echo -e "${YELLOW}Running load test: $num_packets packets with $concurrent concurrent agents...${NC}"
    
    local success_count=0
    local total_count=$num_packets
    
    # Create background processes
    for i in $(seq 1 $concurrent); do
        {
            local packets_per_agent=$((num_packets / concurrent))
            for j in $(seq 1 $packets_per_agent); do
                local packet_id="load-test-agent-$i-packet-$j"
                local agent_id="load-agent-$i"
                local num_messages=$((RANDOM % 10 + 1))  # 1-10 messages per packet
                
                if send_log_packet "$packet_id" "$agent_id" $num_messages > /dev/null 2>&1; then
                    echo "✓"
                else
                    echo "✗"
                fi
            done
        } &
    done
    
    # Wait for all background processes
    wait
    
    echo -e "${GREEN}Load test completed${NC}"
}

# Function to demonstrate failure handling
test_failure_handling() {
    echo -e "${YELLOW}Testing failure handling...${NC}"
    echo "This would require stopping one of the analyzer containers"
    echo "You can manually test this by running:"
    echo "  docker stop logs-analyzer-1"
    echo "  # Send some packets"
    echo "  docker start logs-analyzer-1"
}

# Main test execution
main() {
    echo "Step 1: Checking service health..."
    check_health "Distributor" "$BASE_URL"
    check_health "Analyzer-1" "http://localhost:8081"
    check_health "Analyzer-2" "http://localhost:8082"
    check_health "Analyzer-3" "http://localhost:8083"
    check_health "Analyzer-4" "http://localhost:8084"
    
    echo
    echo "Step 2: Sending individual test packets..."
    send_log_packet "test-packet-1" "test-agent-1" 5
    send_log_packet "test-packet-2" "test-agent-2" 3
    send_log_packet "test-packet-3" "test-agent-3" 7
    
    echo
    echo "Step 3: Running load test..."
    run_load_test 20 4
    
    echo
    echo "Step 4: Weight distribution test..."
    echo "Sending 100 packets to see distribution..."
    run_load_test 100 10
    
    echo
    echo "Step 5: Failure handling info..."
    test_failure_handling
    
    echo
    echo -e "${GREEN}🎉 Test suite completed!${NC}"
    echo
    echo "To view logs and verify weight distribution:"
    echo "  docker-compose logs distributor"
    echo "  docker-compose logs analyzer-1"
    echo "  docker-compose logs analyzer-2"
    echo "  docker-compose logs analyzer-3"
    echo "  docker-compose logs analyzer-4"
}

# Handle command line arguments
case "${1:-main}" in
    "health")
        check_health "Distributor" "$BASE_URL"
        ;;
    "send")
        send_log_packet "${2:-test-packet}" "${3:-test-agent}" "${4:-3}"
        ;;
    "load")
        run_load_test "${2:-50}" "${3:-5}"
        ;;
    "main"|*)
        main
        ;;
esac
