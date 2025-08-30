#!/bin/bash

# Locust Load Testing Runner for Logs Distribution System

set -e

# Colors for output  
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🐍 LOCUST LOAD TESTING FOR LOGS DISTRIBUTION SYSTEM${NC}"
echo "=================================================="

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker not found. Please install Docker first.${NC}"
    echo "Install from: https://docs.docker.com/get-docker/"
    exit 1
fi

echo -e "${GREEN}✅ Docker found: $(docker --version)${NC}"

# Build Locust Docker image if it doesn't exist
echo "Building Locust Docker image..."
cd locust
if ! docker build -t locust-load-test . > /dev/null 2>&1; then
    echo -e "${RED}❌ Failed to build Locust Docker image${NC}"
    exit 1
fi
cd ..
echo -e "${GREEN}✅ Locust Docker image built successfully${NC}"

# Check if system is running
echo "Checking if logs distribution system is running..."
if ! curl -s http://localhost:8080/api/v1/health > /dev/null; then
    echo -e "${RED}❌ Logs distribution system not running on localhost:8080${NC}"
    echo -e "${YELLOW}Please start the system with: docker-compose up -d${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Logs distribution system is running${NC}"

# Function to run locust test using Docker
run_locust_test() {
    local test_type="$1"
    local users="$2" 
    local spawn_rate="$3"
    local time="$4"
    local user_class="$5"
    
    echo -e "\n${YELLOW}Running Locust $test_type Test (Docker):${NC}"
    echo "• Users: $users"
    echo "• Spawn rate: $spawn_rate users/second" 
    echo "• Duration: $time"
    echo "• User class: $user_class"
    
    # Create results directory if it doesn't exist
    mkdir -p results
    
    # Run locust in Docker with headless mode (simplified)
    docker run --rm --network host \
           -v "$(pwd)/results:/app/results" \
           locust-load-test \
           locust -f locustfile.py --host=http://localhost:8080 \
           --users=$users --spawn-rate=$spawn_rate --run-time=$time \
           --html=results/locust_${test_type}_$(date +%Y%m%d_%H%M%S).html \
           --csv=results/locust_${test_type}_$(date +%Y%m%d_%H%M%S) \
           --headless
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $test_type test completed successfully${NC}"
    else
        echo -e "${RED}❌ $test_type test failed${NC}"
        return 1
    fi
}

# Create results directory
mkdir -p results

echo -e "\n${BLUE}Starting Locust Load Tests:${NC}"

case "${1:-all}" in
    "baseline")
        run_locust_test "baseline" 20 5 "2m" "LogsDistributionUser"
        ;;
    "stress") 
        run_locust_test "stress" 100 10 "3m" "StressTestUser"
        ;;
    "mixed")
        run_locust_test "mixed" 50 5 "5m" "LogsDistributionUser"
        ;;
    "ui")
        echo -e "${YELLOW}Starting Locust Web UI (Docker)...${NC}"
        echo "1. Docker container starting with web interface..."
        echo "2. Open browser to http://localhost:8089"
        echo "3. Target host is pre-configured: http://localhost:8080"
        echo "4. Configure users and spawn rate in the web UI"
        echo "5. Press Ctrl+C to stop when done"
        echo ""
        docker run --rm --network host \
               -p 8089:8089 \
               -v "$(pwd)/results:/app/results" \
               locust-load-test \
               locust -f locustfile.py --host=http://localhost:8080
        ;;
    "all"|*)
        run_locust_test "baseline" 20 5 "2m" "LogsDistributionUser"
        echo ""
        run_locust_test "stress" 100 10 "2m" "StressTestUser" 
        ;;
esac

echo -e "\n${BLUE}Post-Test Analysis:${NC}"

# Check final system metrics
echo "Final system health check:"
curl -s http://localhost:8080/api/v1/health

echo -e "\n${BLUE}Distribution Analysis:${NC}"
echo "Checking message distribution across analyzers:"

total_messages=0
for i in {1..4}; do
    port=$((8080 + i))
    messages=$(curl -s http://localhost:$port/api/v1/stats | grep -o 'Total Messages Processed: [0-9]*' | cut -d' ' -f4)
    weight=$(echo "scale=1; $i / 10" | bc)
    echo -e "${YELLOW}Analyzer-$i (Weight $weight):${NC} $messages messages"
    total_messages=$((total_messages + messages))
done

echo "Total messages processed: $total_messages"

echo -e "\n${GREEN}🐍 Locust Load Testing Complete!${NC}"
echo -e "\n${YELLOW}View Results:${NC}"
echo "1. Open HTML reports in results/ directory"
echo "2. Analyze CSV files for detailed metrics"
echo "3. Check system logs: docker-compose logs"

echo -e "\n${BLUE}Next Steps:${NC}"
echo "• Review HTML reports for comprehensive metrics"
echo "• Analyze response time distributions and error rates"
echo "• Verify message distribution matches expected weights"
echo "• Run with different user patterns and loads"

exit 0
