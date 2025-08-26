#!/bin/bash

# Performance and Load Testing Script
# Tests system under various load conditions

set -e

echo "🚀 PERFORMANCE & LOAD TESTING"
echo "=============================="

# Configuration
DISTRIBUTOR_URL="http://localhost:8080"
RESULTS_DIR="performance-results"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")

# Create results directory
mkdir -p $RESULTS_DIR

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results tracking
PERFORMANCE_TESTS=0
PERFORMANCE_PASSED=0

run_performance_test() {
    local test_name="$1"
    local test_function="$2"
    
    echo -e "\n${BLUE}Running $test_name...${NC}"
    PERFORMANCE_TESTS=$((PERFORMANCE_TESTS + 1))
    
    if $test_function > "$RESULTS_DIR/${test_name}_${TIMESTAMP}.log" 2>&1; then
        echo -e "${GREEN}✅ $test_name PASSED${NC}"
        PERFORMANCE_PASSED=$((PERFORMANCE_PASSED + 1))
        cat "$RESULTS_DIR/${test_name}_${TIMESTAMP}.log"
    else
        echo -e "${RED}❌ $test_name FAILED${NC}"
        echo "Check $RESULTS_DIR/${test_name}_${TIMESTAMP}.log for details"
    fi
}

baseline_performance_test() {
    echo "📊 Baseline Performance Test"
    echo "Sending 100 packets with 1 message each..."
    
    local start_time=$(date +%s)
    local test_packet='{"packetId":"baseline-test","agentId":"baseline-agent","timestamp":'$(date +%s000)',"messages":[{"id":"msg-1","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"INFO","source":{"application":"test","service":"baseline","instance":"1","host":"localhost"},"message":"Baseline performance test","metadata":{}}]}'
    
    local successful_requests=0
    for i in {1..100}; do
        if curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null; then
            successful_requests=$((successful_requests + 1))
        fi
    done
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    local throughput=$(echo "scale=2; $successful_requests / $duration" | bc)
    
    echo "Baseline Results:"
    echo "  Duration: ${duration}s"
    echo "  Successful Requests: $successful_requests/100"
    echo "  Throughput: ${throughput} requests/second"
    
    # Check system metrics after test
    sleep 2
    local performance_metrics=$(curl -s "$DISTRIBUTOR_URL/api/v1/metrics/performance")
    echo "  System Metrics: $performance_metrics"
    
    # Pass if we got > 90% success rate
    [ $successful_requests -ge 90 ]
}

stress_test_burst_traffic() {
    echo "💥 Burst Traffic Stress Test"
    echo "Sending 500 packets in rapid succession..."
    
    local test_packet='{"packetId":"burst-test","agentId":"burst-agent","timestamp":'$(date +%s000)',"messages":[{"id":"msg-1","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"WARN","source":{"application":"test","service":"burst","instance":"1","host":"localhost"},"message":"Burst traffic test","metadata":{}},{"id":"msg-2","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"ERROR","source":{"application":"test","service":"burst","instance":"1","host":"localhost"},"message":"Another burst message","metadata":{}}]}'
    
    local start_time=$(date +%s.%3N)
    local successful_requests=0
    local failed_requests=0
    
    # Send requests in parallel batches
    for batch in {1..25}; do
        for i in {1..20}; do
            {
                if curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null; then
                    echo "success" >> "$RESULTS_DIR/burst_results_${TIMESTAMP}.tmp"
                else
                    echo "failure" >> "$RESULTS_DIR/burst_results_${TIMESTAMP}.tmp"
                fi
            } &
        done
        wait # Wait for batch to complete
        sleep 0.1 # Small delay between batches
    done
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    # Count results
    if [ -f "$RESULTS_DIR/burst_results_${TIMESTAMP}.tmp" ]; then
        successful_requests=$(grep -c "success" "$RESULTS_DIR/burst_results_${TIMESTAMP}.tmp" || echo "0")
        failed_requests=$(grep -c "failure" "$RESULTS_DIR/burst_results_${TIMESTAMP}.tmp" || echo "0")
        rm "$RESULTS_DIR/burst_results_${TIMESTAMP}.tmp"
    fi
    
    local total_requests=$((successful_requests + failed_requests))
    local success_rate=$(echo "scale=2; $successful_requests * 100 / $total_requests" | bc)
    local throughput=$(echo "scale=2; $successful_requests / $duration" | bc)
    
    echo "Burst Test Results:"
    echo "  Duration: ${duration}s"
    echo "  Total Requests: $total_requests"
    echo "  Successful: $successful_requests"
    echo "  Failed: $failed_requests"
    echo "  Success Rate: ${success_rate}%"
    echo "  Throughput: ${throughput} requests/second"
    
    # Check if system is still responsive
    sleep 3
    curl -f -s "$DISTRIBUTOR_URL/actuator/health" > /dev/null
    
    # Pass if success rate > 85%
    [ $(echo "$success_rate > 85" | bc) -eq 1 ]
}

sustained_load_test() {
    echo "🔄 Sustained Load Test"
    echo "Sending steady traffic for 30 seconds..."
    
    local test_packet='{"packetId":"sustained-test","agentId":"sustained-agent","timestamp":'$(date +%s000)',"messages":[{"id":"msg-1","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"DEBUG","source":{"application":"test","service":"sustained","instance":"1","host":"localhost"},"message":"Sustained load test message","metadata":{}}]}'
    
    local start_time=$(date +%s)
    local end_time=$((start_time + 30))
    local successful_requests=0
    local failed_requests=0
    
    while [ $(date +%s) -lt $end_time ]; do
        if curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null; then
            successful_requests=$((successful_requests + 1))
        else
            failed_requests=$((failed_requests + 1))
        fi
        sleep 0.1 # 10 requests per second target rate
    done
    
    local actual_end_time=$(date +%s)
    local duration=$((actual_end_time - start_time))
    local total_requests=$((successful_requests + failed_requests))
    local success_rate=$(echo "scale=2; $successful_requests * 100 / $total_requests" | bc)
    local throughput=$(echo "scale=2; $successful_requests / $duration" | bc)
    
    echo "Sustained Load Results:"
    echo "  Duration: ${duration}s"
    echo "  Total Requests: $total_requests"
    echo "  Successful: $successful_requests"
    echo "  Failed: $failed_requests"
    echo "  Success Rate: ${success_rate}%"
    echo "  Average Throughput: ${throughput} requests/second"
    
    # Check final system state
    sleep 2
    local final_metrics=$(curl -s "$DISTRIBUTOR_URL/api/v1/metrics/performance")
    echo "  Final System Metrics: $final_metrics"
    
    # Pass if success rate > 95%
    [ $(echo "$success_rate > 95" | bc) -eq 1 ]
}

memory_stress_test() {
    echo "🧠 Memory Stress Test"
    echo "Sending large packets to test memory handling..."
    
    # Create a packet with many messages
    local large_messages=""
    for i in {1..50}; do
        large_messages+="{\"id\":\"msg-$i\",\"timestamp\":\"$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")\",\"level\":\"INFO\",\"source\":{\"application\":\"test\",\"service\":\"memory\",\"instance\":\"$i\",\"host\":\"localhost\"},\"message\":\"Large memory test message with extra content to increase size - Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\",\"metadata\":{\"extra_data\":\"This is additional metadata to increase message size\",\"iteration\":$i,\"timestamp\":$(date +%s)}}"
        if [ $i -lt 50 ]; then
            large_messages+=","
        fi
    done
    
    local large_packet="{\"packetId\":\"memory-test\",\"agentId\":\"memory-agent\",\"timestamp\":$(date +%s000),\"messages\":[$large_messages]}"
    
    local start_time=$(date +%s)
    local successful_requests=0
    
    # Send 20 large packets
    for i in {1..20}; do
        if curl -f -s -X POST -H "Content-Type: application/json" -d "$large_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null; then
            successful_requests=$((successful_requests + 1))
        fi
        sleep 0.5 # Allow system to process
    done
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo "Memory Stress Results:"
    echo "  Duration: ${duration}s"
    echo "  Large Packets Sent: 20"
    echo "  Successful: $successful_requests"
    echo "  Messages per Packet: 50"
    echo "  Total Messages: $((successful_requests * 50))"
    
    # Check system is still responsive
    sleep 3
    curl -f -s "$DISTRIBUTOR_URL/actuator/health" > /dev/null
    
    # Pass if we successfully sent most packets
    [ $successful_requests -ge 18 ]
}

concurrent_agents_test() {
    echo "👥 Concurrent Agents Test"
    echo "Simulating 10 different agents sending simultaneously..."
    
    local start_time=$(date +%s)
    local temp_results="$RESULTS_DIR/concurrent_${TIMESTAMP}.tmp"
    
    # Start 10 concurrent agent simulations
    for agent_id in {1..10}; do
        {
            local agent_successful=0
            for i in {1..20}; do
                local test_packet="{\"packetId\":\"agent-$agent_id-packet-$i\",\"agentId\":\"concurrent-agent-$agent_id\",\"timestamp\":$(date +%s000),\"messages\":[{\"id\":\"msg-1\",\"timestamp\":\"$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")\",\"level\":\"INFO\",\"source\":{\"application\":\"test\",\"service\":\"agent-$agent_id\",\"instance\":\"1\",\"host\":\"host-$agent_id\"},\"message\":\"Concurrent agent $agent_id message $i\",\"metadata\":{\"agent_id\":$agent_id,\"iteration\":$i}}]}"
                
                if curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null; then
                    agent_successful=$((agent_successful + 1))
                fi
                sleep 0.1
            done
            echo "agent-$agent_id:$agent_successful" >> "$temp_results"
        } &
    done
    
    wait # Wait for all agents to complete
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    local total_successful=0
    
    if [ -f "$temp_results" ]; then
        echo "Agent Results:"
        while IFS=':' read -r agent success_count; do
            echo "  $agent: $success_count/20 successful"
            total_successful=$((total_successful + success_count))
        done < "$temp_results"
        rm "$temp_results"
    fi
    
    local success_rate=$(echo "scale=2; $total_successful * 100 / 200" | bc)
    local throughput=$(echo "scale=2; $total_successful / $duration" | bc)
    
    echo "Concurrent Agents Summary:"
    echo "  Duration: ${duration}s"
    echo "  Total Successful: $total_successful/200"
    echo "  Success Rate: ${success_rate}%"
    echo "  Throughput: ${throughput} requests/second"
    
    # Check distribution across analyzers
    sleep 3
    echo "Checking distribution across analyzers..."
    ./test-system.sh distribution | tail -10
    
    # Pass if success rate > 90%
    [ $(echo "$success_rate > 90" | bc) -eq 1 ]
}

echo -e "\n${YELLOW}🏁 STARTING PERFORMANCE TEST SUITE${NC}"

run_performance_test "Baseline_Performance" "baseline_performance_test"
run_performance_test "Burst_Traffic_Stress" "stress_test_burst_traffic"  
run_performance_test "Sustained_Load" "sustained_load_test"
run_performance_test "Memory_Stress" "memory_stress_test"
run_performance_test "Concurrent_Agents" "concurrent_agents_test"

echo -e "\n${YELLOW}============ PERFORMANCE TEST SUMMARY ============${NC}"
echo -e "Total Performance Tests: $PERFORMANCE_TESTS"
echo -e "${GREEN}Passed: $PERFORMANCE_PASSED${NC}"
echo -e "${RED}Failed: $((PERFORMANCE_TESTS - PERFORMANCE_PASSED))${NC}"

if [ $PERFORMANCE_PASSED -eq $PERFORMANCE_TESTS ]; then
    echo -e "\n${GREEN}🎉 ALL PERFORMANCE TESTS PASSED!${NC}"
    echo -e "${GREEN}System demonstrates excellent performance under various load conditions${NC}"
else
    echo -e "\n${YELLOW}⚠️  Some performance tests had issues. Check logs for details.${NC}"
fi

# Final system health check
echo -e "\n${BLUE}Final System Health Check:${NC}"
curl -s "$DISTRIBUTOR_URL/api/v1/metrics/dashboard"
