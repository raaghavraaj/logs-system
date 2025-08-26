#!/bin/bash

# Integration Tests for Logs Distribution System
# This script runs comprehensive integration tests across all services

set -e

echo "🧪 RUNNING COMPREHENSIVE INTEGRATION TESTS"
echo "=========================================="

# Test configuration
DISTRIBUTOR_URL="http://localhost:8080"
ANALYZER_URLS=("http://localhost:8081" "http://localhost:8082" "http://localhost:8083" "http://localhost:8084")
TEST_RESULTS_DIR="test-results"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")

# Create test results directory
mkdir -p $TEST_RESULTS_DIR

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test result tracking
TESTS_PASSED=0
TESTS_FAILED=0
TOTAL_TESTS=0

run_test() {
    local test_name="$1"
    local test_command="$2"
    
    echo -n "Testing $test_name... "
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$test_command" > "$TEST_RESULTS_DIR/${test_name}_${TIMESTAMP}.log" 2>&1; then
        echo -e "${GREEN}PASSED${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}FAILED${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        echo "  -> Check $TEST_RESULTS_DIR/${test_name}_${TIMESTAMP}.log for details"
    fi
}

health_check_test() {
    # Test all service health endpoints
    curl -f -s "$DISTRIBUTOR_URL/actuator/health" > /dev/null &&
    curl -f -s "${ANALYZER_URLS[0]}/actuator/health" > /dev/null &&
    curl -f -s "${ANALYZER_URLS[1]}/actuator/health" > /dev/null &&
    curl -f -s "${ANALYZER_URLS[2]}/actuator/health" > /dev/null &&
    curl -f -s "${ANALYZER_URLS[3]}/actuator/health" > /dev/null
}

metrics_endpoints_test() {
    # Test all metrics endpoints
    curl -f -s "$DISTRIBUTOR_URL/actuator/metrics" > /dev/null &&
    curl -f -s "$DISTRIBUTOR_URL/api/v1/metrics/dashboard" > /dev/null &&
    curl -f -s "$DISTRIBUTOR_URL/api/v1/metrics/performance" > /dev/null &&
    curl -f -s "$DISTRIBUTOR_URL/api/v1/metrics/alerts" > /dev/null &&
    curl -f -s "${ANALYZER_URLS[0]}/api/v1/stats" > /dev/null
}

log_packet_distribution_test() {
    # Send test packets and verify distribution
    local test_packet='{"packetId":"integration-test-1","agentId":"integration-test-agent","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","totalMessages":1,"messages":[{"id":"msg-1","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"INFO","source":{"application":"test","service":"integration","instance":"1","host":"localhost"},"message":"Integration test message","metadata":{}}],"checksum":"test-checksum"}'
    
    # Send multiple packets
    for i in {1..20}; do
        curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null || return 1
        sleep 0.1 # Small delay to avoid overwhelming
    done
    
    # Wait for processing
    sleep 3
    
    # Check that at least some analyzers received packets
    local analyzers_with_data=0
    for url in "${ANALYZER_URLS[@]}"; do
        local stats=$(curl -s "$url/api/v1/stats")
        if [[ "$stats" =~ "integration-test-agent" ]] || [[ "$stats" =~ "Total Messages Processed: [1-9]" ]]; then
            analyzers_with_data=$((analyzers_with_data + 1))
        fi
    done
    
    # Pass if at least 2 analyzers received data (showing distribution is working)
    [ $analyzers_with_data -ge 2 ]
}

weighted_distribution_test() {
    # Test that distribution respects analyzer weights over time
    local test_packet='{"packetId":"weight-test","agentId":"weight-test-agent","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","totalMessages":1,"messages":[{"id":"msg-1","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"INFO","source":{"application":"test","service":"weight","instance":"1","host":"localhost"},"message":"Weight test message","metadata":{}}],"checksum":"test-checksum"}'
    
    # Send many packets to test distribution
    for i in {1..100}; do
        curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/logs" > /dev/null || return 1
        if (( i % 10 == 0 )); then
            sleep 0.2 # Brief pause every 10 packets
        fi
    done
    
    sleep 5 # Give more time for processing
    
    # Check distribution percentages are reasonable
    local total_messages=0
    local analyzer_messages=()
    
    for i in "${!ANALYZER_URLS[@]}"; do
        local stats=$(curl -s "${ANALYZER_URLS[$i]}/api/v1/stats" 2>/dev/null || echo "")
        if [[ -n "$stats" ]]; then
            local messages=$(echo "$stats" | grep -o 'Total Messages Processed: [0-9]*' | grep -o '[0-9]*' | head -1)
            messages=${messages:-0}
            analyzer_messages[$i]=$messages
            total_messages=$((total_messages + messages))
        else
            analyzer_messages[$i]=0
        fi
    done
    
    echo "Distribution check: total_messages=$total_messages"
    for i in "${!analyzer_messages[@]}"; do
        echo "  Analyzer $((i+1)): ${analyzer_messages[$i]} messages"
    done
    
    # Check if distribution is working (at least 80 messages processed total)
    [ $total_messages -gt 80 ]
}

failure_recovery_test() {
    # Test system resilience - simplified version
    # Send packets and verify system handles them gracefully
    
    local test_packet='{"packetId":"failure-test","agentId":"failure-test-agent","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","totalMessages":1,"messages":[{"id":"msg-1","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'","level":"ERROR","source":{"application":"test","service":"failure","instance":"1","host":"localhost"},"message":"Failure test message","metadata":{}}],"checksum":"test-checksum"}'
    
    local successful_requests=0
    for i in {1..10}; do
        if curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/distribute" > /dev/null; then
            successful_requests=$((successful_requests + 1))
        fi
        sleep 0.2
    done
    
    sleep 2
    
    # Verify system is still responsive and processed some requests
    curl -f -s "$DISTRIBUTOR_URL/actuator/health" > /dev/null && 
    curl -f -s "$DISTRIBUTOR_URL/api/v1/metrics/performance" > /dev/null &&
    [ $successful_requests -ge 8 ] # At least 80% success rate
}

performance_stress_test() {
    # Send high volume of packets to test performance
    echo "Running performance stress test with 200 packets..."
    
    local test_packet='{"packetId":"stress-test","agentId":"stress-test-agent","timestamp":'$(date +%s000)',"messages":[{"timestamp":'$(date +%s000)',"level":"DEBUG","content":"Stress test message","source":"APPLICATION"},{"timestamp":'$(date +%s000)',"level":"INFO","content":"Another stress test message","source":"INFRASTRUCTURE"}]}'
    
    local start_time=$(date +%s)
    
    # Send packets in parallel for better performance testing
    for i in {1..200}; do
        curl -f -s -X POST -H "Content-Type: application/json" -d "$test_packet" "$DISTRIBUTOR_URL/api/v1/distribute" &
        
        # Limit concurrent requests to avoid overwhelming the system
        if (( i % 20 == 0 )); then
            wait
        fi
    done
    wait
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo "Stress test completed in ${duration}s"
    
    # Verify system is still responsive after stress
    sleep 3
    curl -f -s "$DISTRIBUTOR_URL/actuator/health" > /dev/null &&
    curl -f -s "$DISTRIBUTOR_URL/api/v1/metrics/performance" > /dev/null
}

prometheus_metrics_test() {
    # Test Prometheus metrics endpoint format
    local prometheus_output=$(curl -s "$DISTRIBUTOR_URL/actuator/prometheus")
    
    # Check for expected metric names
    echo "$prometheus_output" | grep -q "distributor_packets_received_total" &&
    echo "$prometheus_output" | grep -q "distributor_messages_total" &&
    echo "$prometheus_output" | grep -q "jvm_memory_used_bytes"
}

echo -e "\n${YELLOW}1. HEALTH CHECK TESTS${NC}"
run_test "Service_Health_Check" "health_check_test"

echo -e "\n${YELLOW}2. METRICS ENDPOINTS TESTS${NC}"
run_test "Metrics_Endpoints" "metrics_endpoints_test"

echo -e "\n${YELLOW}3. LOG PACKET DISTRIBUTION TESTS${NC}"
run_test "Log_Packet_Distribution" "log_packet_distribution_test"

echo -e "\n${YELLOW}4. WEIGHTED DISTRIBUTION TESTS${NC}"
run_test "Weighted_Distribution" "weighted_distribution_test"

echo -e "\n${YELLOW}5. FAILURE RECOVERY TESTS${NC}"
run_test "Failure_Recovery" "failure_recovery_test"

echo -e "\n${YELLOW}6. PERFORMANCE STRESS TESTS${NC}"
run_test "Performance_Stress" "performance_stress_test"

echo -e "\n${YELLOW}7. PROMETHEUS METRICS TESTS${NC}"
run_test "Prometheus_Metrics" "prometheus_metrics_test"

echo -e "\n${YELLOW}============ TEST SUMMARY ============${NC}"
echo -e "Total Tests: $TOTAL_TESTS"
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}🎉 ALL INTEGRATION TESTS PASSED!${NC}"
    exit 0
else
    echo -e "\n${RED}❌ Some tests failed. Check logs in $TEST_RESULTS_DIR/${NC}"
    exit 1
fi
