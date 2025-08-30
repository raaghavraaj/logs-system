#!/bin/bash

# k6 Load Testing Runner for Logs Distribution System

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 k6 LOAD TESTING FOR LOGS DISTRIBUTION SYSTEM${NC}"
echo "=================================================="

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}❌ k6 not found. Installing k6...${NC}"
    echo -e "${YELLOW}Installation instructions:${NC}"
    echo "macOS: brew install k6"
    echo "Ubuntu: sudo apt-get install k6"
    echo "Docker: docker run --rm -i grafana/k6:latest run - <script.js"
    echo ""
    echo "Or install from: https://k6.io/docs/get-started/installation/"
    exit 1
fi

echo -e "${GREEN}✅ k6 found: $(k6 version)${NC}"

# Check if system is running
echo "Checking if logs distribution system is running..."
if ! curl -s http://localhost:8080/api/v1/health > /dev/null; then
    echo -e "${RED}❌ Logs distribution system not running on localhost:8080${NC}"
    echo -e "${YELLOW}Please start the system with: docker-compose up -d${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Logs distribution system is running${NC}"

# Function to run k6 test
run_k6_test() {
    local test_name="$1"
    local test_file="$2"
    local output_dir="results"
    
    mkdir -p $output_dir
    
    echo -e "\n${YELLOW}Running: $test_name${NC}"
    echo "Test file: $test_file"
    echo "Results will be saved to: $output_dir/"
    
    # Run k6 test with JSON output
    k6 run --out json=$output_dir/${test_name}_$(date +%Y%m%d_%H%M%S).json $test_file
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $test_name completed successfully${NC}"
    else
        echo -e "${RED}❌ $test_name failed${NC}"
        return 1
    fi
}

# Main test execution
echo -e "\n${BLUE}Starting k6 Load Tests:${NC}"

case "${1:-all}" in
    "baseline")
        run_k6_test "Baseline_Load_Test" "k6/load-test.js"
        ;;
    "spike")
        run_k6_test "Spike_Test" "k6/spike-test.js"
        ;;
    "all"|*)
        run_k6_test "Load_Test" "k6/load-test.js"
        echo ""
        run_k6_test "Spike_Test" "k6/spike-test.js"
        ;;
esac

echo -e "\n${BLUE}Post-Test Analysis:${NC}"

# Check final system metrics
echo "Final system health check:"
curl -s http://localhost:8080/api/v1/health

echo -e "\n${BLUE}Distribution Analysis:${NC}"
echo "Checking message distribution across analyzers:"

for i in {1..4}; do
    port=$((8080 + i))
    echo -e "\n${YELLOW}Analyzer-$i (Port $port):${NC}"
    total_messages=$(curl -s http://localhost:$port/api/v1/stats | grep -o 'Total Messages Processed: [0-9]*' | cut -d' ' -f4)
    echo "Total Messages Processed: $total_messages"
done

echo -e "\n${GREEN}🎉 k6 Load Testing Complete!${NC}"
echo -e "\n${YELLOW}View Results:${NC}"
echo "1. Check console output above for real-time metrics"
echo "2. Analyze JSON files in results/ directory" 
echo "3. Check system logs: docker-compose logs"

echo -e "\n${BLUE}Next Steps:${NC}"
echo "• Review performance metrics and response times"
echo "• Verify message distribution is proportional to weights"
echo "• Check for any errors in system logs"
echo "• Scale up VUs (virtual users) for higher load testing"

exit 0
