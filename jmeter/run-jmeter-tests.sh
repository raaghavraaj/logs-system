#!/bin/bash

# JMeter Load Testing Script for Logs Distribution System
# This script runs comprehensive load tests using Apache JMeter

set -e

echo "🚀 JMETER LOAD TESTING FOR LOGS DISTRIBUTION SYSTEM"
echo "=================================================="

# Configuration
JMETER_HOME=${JMETER_HOME:-/opt/apache-jmeter}
TEST_PLAN="load-test-plan.jmx"
RESULTS_DIR="results"
TIMESTAMP=$(date "+%Y%m%d_%H%M%S")

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Create results directory
mkdir -p $RESULTS_DIR

echo -e "\n${BLUE}Pre-Test System Check:${NC}"

# Check if JMeter is installed
if ! command -v jmeter &> /dev/null; then
    echo -e "${RED}❌ JMeter not found. Please install Apache JMeter and add it to your PATH.${NC}"
    echo -e "${YELLOW}Install instructions:${NC}"
    echo "1. Download from: https://jmeter.apache.org/download_jmeter.cgi"
    echo "2. Extract and add bin/ directory to PATH"
    echo "3. Or install via package manager (brew install jmeter / apt install jmeter)"
    exit 1
fi

echo -e "${GREEN}✅ JMeter found: $(jmeter --version | head -1)${NC}"

# Check if system is running
echo "Checking if logs distribution system is running..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo -e "${RED}❌ Logs distribution system not running on localhost:8080${NC}"
    echo -e "${YELLOW}Please start the system with: docker-compose up -d${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Logs distribution system is running${NC}"

# Check analyzers
for port in 8081 8082 8083 8084; do
    if curl -s http://localhost:$port/actuator/health > /dev/null; then
        echo -e "${GREEN}✅ Analyzer on port $port is healthy${NC}"
    else
        echo -e "${YELLOW}⚠️ Analyzer on port $port not responding${NC}"
    fi
done

echo -e "\n${BLUE}Starting JMeter Load Tests:${NC}"

# Function to run JMeter test
run_jmeter_test() {
    local test_name="$1"
    local test_plan="$2"
    local output_file="$RESULTS_DIR/${test_name}_${TIMESTAMP}"
    
    echo -e "\n${YELLOW}Running: $test_name${NC}"
    echo "Output files: ${output_file}.jtl"
    
    # Run JMeter in non-GUI mode
    jmeter -n -t "$test_plan" \
           -l "${output_file}.jtl" \
           -e -o "${output_file}_html_report" \
           -j "${output_file}.log" \
           2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $test_name completed successfully${NC}"
        echo -e "   📊 HTML Report: ${output_file}_html_report/index.html"
        echo -e "   📋 Raw Results: ${output_file}.jtl"
        echo -e "   📝 Log File: ${output_file}.log"
    else
        echo -e "${RED}❌ $test_name failed${NC}"
        return 1
    fi
}

# Run the load test
run_jmeter_test "Load_Test_Plan" "$TEST_PLAN"

echo -e "\n${BLUE}Post-Test System Analysis:${NC}"

# Check final system metrics
echo "Final system performance metrics:"
curl -s http://localhost:8080/api/v1/metrics/performance | jq . 2>/dev/null || curl -s http://localhost:8080/api/v1/metrics/performance

echo -e "\n${BLUE}Distribution Analysis:${NC}"
echo "Checking message distribution across analyzers:"

for i in {1..4}; do
    port=$((8080 + i))
    echo -e "\n${YELLOW}Analyzer-$i (Port $port):${NC}"
    curl -s http://localhost:$port/api/v1/stats | grep -E "(Total Messages Processed|jmeter)" | head -5
done

echo -e "\n${GREEN}🎉 JMeter Load Testing Complete!${NC}"
echo -e "\n${YELLOW}View Results:${NC}"
echo "1. Open HTML reports in $RESULTS_DIR/"
echo "2. Analyze .jtl files for detailed metrics"
echo "3. Check system logs: docker-compose logs"

echo -e "\n${BLUE}Next Steps:${NC}"
echo "• Review HTML reports for throughput and response times"
echo "• Verify message distribution is proportional to weights"
echo "• Check for any errors in system logs"
echo "• Scale up thread counts for higher load testing"

exit 0
