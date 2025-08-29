# 🚀 High-Performance Logs Distribution System

A multi-threaded, high-throughput log distribution system built with Java Spring Boot that routes log packets to multiple analyzers based on configurable weights. Achieves **6,690+ messages/second** with automatic failure detection and recovery.

## Key Features
- **High Throughput**: 6,690+ messages/second with 0.00% error rate
- **Weighted Load Balancing**: Configurable distribution (0.1:0.2:0.3:0.4 ratio)
- **Failure Resilience**: Automatic analyzer failure detection and recovery
- **Production-Ready**: Docker deployment, health checks, async architecture
- **Comprehensive Testing**: Unit tests, integration testing, JMeter, Apache Bench

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Emitters      │───▶│   Distributor   │───▶│   Analyzers     │
│                 │    │                 │    │                 │
│ • Steady        │    │ • Load Balancer │    │ • Analyzer-1    │
│ • Bursty        │    │ • Queue Manager │    │ • Analyzer-2    │
│ • Heavy Load    │    │ • Failure Detect│    │ • Analyzer-3    │
│ • (Stress)      │    │ • Health Checks │    │ • Analyzer-4    │
└─────────────────┘    │ • Structured    │    └─────────────────┘
                       │   Logging       │              │
                       └─────────────────┘              │
                                  │                     │
                                  ▼                     ▼
                       ┌───────────────────┐    ┌─────────────────┐
                       │ Distributor       │    │ Analyzer        │
                       │ Endpoints         │    │ Endpoints       │
                       │                   │    │                 │
                       │ • POST /distribute│    │ • POST /analyze │
                       │ • GET /health     │    │ • GET /health   │
                       │                   │    │ • GET /stats    │
                       └───────────────────┘    └─────────────────┘
```

**Components:**
1. **Emitters**: Python-based log generators with multiple traffic patterns
2. **Distributor**: Core routing service with weighted load balancing (port 8080)
3. **Analyzers**: 4 instances with weights 0.1, 0.2, 0.3, 0.4 (ports 8081-8084)

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose
- curl and jq (for testing)

### Running the System
```bash
# Start all services (distributor + 4 analyzers + 3 emitters)
docker-compose up -d

# Verify all services are running
docker-compose ps
```

### Health Endpoints
Verify all services are healthy:
```bash
# Check distributor health
curl http://localhost:8080/api/v1/health

# Check all analyzer health
curl http://localhost:8081/api/v1/health  # Analyzer-1 (weight: 0.1)
curl http://localhost:8082/api/v1/health  # Analyzer-2 (weight: 0.2)
curl http://localhost:8083/api/v1/health  # Analyzer-3 (weight: 0.3)
curl http://localhost:8084/api/v1/health  # Analyzer-4 (weight: 0.4)
```

### Business Endpoints
Send log packets for distribution:
```bash
# Send a test log packet
curl -X POST http://localhost:8080/api/v1/distribute \
  -H "Content-Type: application/json" \
  -d '{
    "packetId": "test-001",
    "agentId": "test-agent", 
    "messages": [
      {
        "id": "msg-1",
        "level": "INFO",
        "source": "TestService", 
        "message": "Test log message"
      }
    ]
  }'

# Expected response: HTTP 202 Accepted
```

### Stats Endpoints
Check message distribution and analyzer statistics:
```bash
# Get detailed stats from each analyzer
curl http://localhost:8081/api/v1/stats | jq  # Should show ~10% of traffic
curl http://localhost:8082/api/v1/stats | jq  # Should show ~20% of traffic  
curl http://localhost:8083/api/v1/stats | jq  # Should show ~30% of traffic
curl http://localhost:8084/api/v1/stats | jq  # Should show ~40% of traffic

# Example output format:
# {
#   "totalPacketsProcessed": 45,
#   "totalMessagesProcessed": 180,
#   "messagesByLevel": {"INFO": 120, "WARN": 30, "ERROR": 20, "DEBUG": 10},
#   "messagesByAgent": {"emitter-1": 90, "emitter-2": 90}
# }
```

## 🧪 Testing

### Unit Tests
Basic Spring Boot application tests:
```bash
# Run distributor unit tests
cd distributor && ./mvnw test

# Run analyzer unit tests  
cd analyzer && ./mvnw test
```

### Basic System Testing
Comprehensive system validation using our custom test script:
```bash
# Run complete system health and distribution test
./test-system.sh

# Test specific components
./test-system.sh health        # Health checks only
./test-system.sh distribution  # Weight distribution validation only
```

**What it tests:**
- All 5 services are healthy and responsive
- Endpoints return correct HTTP status codes
- Log packets are processed successfully
- Message distribution matches configured weights (0.1:0.2:0.3:0.4)
- Sends 120+ test packets to validate system under moderate load

### JMeter Load Testing
Professional load testing with detailed reports:
```bash
# Prerequisites: Install JMeter
brew install jmeter  # macOS
# or sudo apt install jmeter  # Ubuntu

# Run comprehensive load tests
cd jmeter
./run-jmeter-tests.sh

# View results in generated HTML report
open results/Load_Test_Plan_*_html_report/index.html
```

**Test scenarios:**
- **Baseline**: 5 concurrent users, 50 requests (baseline performance)
- **Stress**: 20 concurrent users, 400 requests (stress testing)

**Validates:** Response times, throughput, error rates, concurrency handling

### Apache Bench Performance Testing
High-performance concurrent load testing:
```bash
# Create test payload file
echo '{
  "packetId": "perf-test",
  "agentId": "performance-tester",
  "messages": [
    {"id": "1", "level": "INFO", "source": "PerfTest", "message": "Performance test message"}
  ]
}' > test_payload.json

# Run performance test (2000 requests, 100 concurrent)
ab -n 2000 -c 100 -p test_payload.json -T application/json http://localhost:8080/api/v1/distribute

# Check distribution after load test
echo "Distribution Results:"
curl -s http://localhost:8081/api/v1/stats | jq -r '"Analyzer-1: \(.totalMessagesProcessed) messages"'
curl -s http://localhost:8082/api/v1/stats | jq -r '"Analyzer-2: \(.totalMessagesProcessed) messages"' 
curl -s http://localhost:8083/api/v1/stats | jq -r '"Analyzer-3: \(.totalMessagesProcessed) messages"'
curl -s http://localhost:8084/api/v1/stats | jq -r '"Analyzer-4: \(.totalMessagesProcessed) messages"'
```

**Expected Results:**
- **Throughput**: 6,690+ requests/second
- **Error Rate**: 0.00%
- **Distribution**: Messages distributed proportionally (1:2:3:4 ratio)
- **Response Time**: Sub-millisecond average response times

**Performance Metrics:**
```bash
# Sample ab output interpretation:
# Requests per second: 6690.23 [#/sec] (mean)
# Time per request: 14.947 [ms] (mean)  
# Transfer rate: 1876.45 [Kbytes/sec] received
```