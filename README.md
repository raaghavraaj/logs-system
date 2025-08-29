# High-Throughput Logs Distribution System

A production-ready, scalable logs distribution system with weighted load balancing, comprehensive monitoring, and real-time analytics.

> 📖 **For technical details, architectural decisions, and system trade-offs, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md)**

## 🎯 System Overview

This system implements a high-performance logs distributor that receives log packets from multiple emitters and routes them to analyzers based on configurable weights. The system features message-based distribution (not packet-based), structured logging for observability, and production-grade reliability.

### Key Features
- **Message-Based Weighted Distribution**: Ensures analyzers process log messages proportional to their weights
- **High-Throughput Processing**: 6,690+ messages/second with async HTTP and optimized threading
- **Structured Logging**: Easy-to-parse structured logs for monitoring and analytics
- **Production-Ready**: Docker deployment, health checks, failure detection and recovery
- **Simple Observability**: Log parsing scripts for real-time system monitoring
- **Async Architecture**: Java 11+ HttpClient with non-blocking operations for maximum performance

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

### Components
1. **Emitter Services**: 3+ Python-based log generators (steady, bursty, heavy, optional stress)
2. **Distributor Service**: Core routing service with weighted load balancing and structured logging
3. **Analyzer Services**: 4 instances with configurable weights (0.1, 0.2, 0.3, 0.4)  
4. **Testing & Validation**: Comprehensive testing suite (test-system.sh, Apache Bench, JMeter)
5. **Monitoring & Observability**: Docker-based log monitoring and structured log parsing

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 17+ (for local development)
- Python 3.11+ (for emitters)
- curl and jq (for testing)

### 1. Start the Complete System
```bash
# Clone and start all services (distributor + 4 analyzers + 3 emitters)
git clone <repository>
cd logs-system
docker-compose up -d

# Verify all services are running
docker-compose ps

# View real-time logs from all services
docker-compose logs -f
```

### 2. Check System Health
```bash
# Health check all services
curl -s http://localhost:8080/api/v1/health  # Distributor
curl -s http://localhost:8081/api/v1/health  # Analyzer-1
curl -s http://localhost:8082/api/v1/health  # Analyzer-2
curl -s http://localhost:8083/api/v1/health  # Analyzer-3
curl -s http://localhost:8084/api/v1/health  # Analyzer-4

# All should return: "Distributor is online" or "Analyzer is online and healthy"
```

### 3. Monitor System Performance
```bash
# System health and distribution overview
./test-system.sh health

# Parse recent activity (last 5 minutes)
docker logs logs-distributor --since 5m | grep -E "(PACKET_|ANALYZER_)" | tail -15

# Check distribution accuracy
./test-system.sh distribution
```
## 📊 Testing & Monitoring Commands

### Quick System Verification
```bash
# 1. Start the system
docker-compose up -d

# 2. Verify all services are healthy (should return "online")
curl -s http://localhost:8080/api/v1/health
curl -s http://localhost:8081/api/v1/health 
curl -s http://localhost:8082/api/v1/health
curl -s http://localhost:8083/api/v1/health
curl -s http://localhost:8084/api/v1/health

# 3. Check system health and performance
./test-system.sh health
```

### Real-time System Monitoring
```bash
# Complete system health check and weight distribution
./test-system.sh

# Example output:
# 🚀 LOGS SYSTEM MONITORING DASHBOARD
# 🏥 SYSTEM HEALTH: ✅ All services online  
# 📊 REAL-TIME PERFORMANCE: 6,690+ messages/second total
# ⚠️ ERROR MONITORING: ✅ No errors detected
# ⚖️ LOAD DISTRIBUTION: Showing per-analyzer packet counts
```

### Log Analysis & Metrics
```bash
# Analyze distributor activity (last 10 minutes)
docker logs logs-distributor --since 10m | grep -E "(PACKET_|SYSTEM_STATUS)" | tail -20

# Analyze specific analyzer processing (last 5 minutes)  
docker logs logs-analyzer-1 --since 5m | grep "processed:" | tail -10

# Expected output includes:
# 📦 PACKET METRICS: received/processed/dropped counts
# 🎯 ANALYZER DISTRIBUTION: packets per analyzer
# ⚠️ ERROR ANALYSIS: any errors or warnings
```

### Performance Testing
```bash
# 1. Manual packet test - verify system works
curl -X POST http://localhost:8080/api/v1/distribute \
  -H "Content-Type: application/json" \
  -d '{
    "packetId": "test-001",
    "agentId": "manual-test",
    "messages": [
      {"id": "msg-1", "level": "INFO", "source": "TestController.healthCheck", "message": "Test message 1"},
      {"id": "msg-2", "level": "ERROR", "source": "TestController.errorHandler", "message": "Test message 2"}
    ]
  }'

# 2. Functional testing - weight distribution and integration
./test-system.sh distribution

# 3. High-performance load testing - Apache Bench (recommended for reviewers)
# Test 2000 requests with 100 concurrent connections
ab -n 2000 -c 100 -p test_payload.json -T application/json http://localhost:8080/api/v1/distribute

# 4. Professional load testing with JMeter
cd jmeter && ./run-jmeter-tests.sh

# 5. Comprehensive system validation
./test-system.sh
```

### Get Detailed Statistics & Metrics
```bash
# Individual analyzer statistics with performance metrics
curl -s http://localhost:8081/api/v1/stats  # Analyzer-1 detailed stats
curl -s http://localhost:8082/api/v1/stats  # Analyzer-2 detailed stats
curl -s http://localhost:8083/api/v1/stats  # Analyzer-3 detailed stats
curl -s http://localhost:8084/api/v1/stats  # Analyzer-4 detailed stats

# Example output per analyzer:
# === ANALYZER analyzer-1 STATISTICS ===
# Total Packets Processed: 5,432
# Total Messages Processed: 15,876  
# Messages/sec: 85.4
# Messages by Level: ERROR: 1,234, INFO: 8,765, DEBUG: 5,432...
# Performance Metrics: Packets/sec: 32.1, Uptime: 186 seconds
```

### Export Metrics for External Tools
```bash
# Export structured logs for ELK Stack, Splunk, etc.
docker logs logs-distributor > distributor_logs.json
docker logs logs-analyzer-1 > analyzer1_logs.json

# Filter specific events for analysis
docker logs logs-distributor | grep "PACKET_RECEIVED" > packet_events.log
docker logs logs-distributor | grep "SYSTEM_STATUS" > performance_metrics.log

# Real-time streaming for external monitoring
docker logs logs-distributor -f | grep "PACKET_" | tee live_metrics.log

# Structured log format examples:
# PACKET_RECEIVED | packet_id=abc123 | messages=5 | agent_id=emitter-1 | timestamp=2025-08-28T17:12:35
# PACKET_QUEUED | packet_id=abc123 | target_analyzer=analyzer-2 | queue_size=1 | messages=5  
# PACKET_SENT_SUCCESS | packet_id=abc123 | analyzer=analyzer-2 | messages=5 | duration_ms=12
```

### Troubleshooting Commands
```bash
# Check for errors in the last hour
docker logs logs-distributor --since 60m | grep -E "(ERROR|WARN)" | tail -10

# Monitor system performance and health
./test-system.sh health && curl -s http://localhost:8080/api/v1/health

# Check Docker container resource usage
docker stats

# View recent distributor logs  
docker logs logs-distributor --tail 50

# Test network connectivity between services
docker exec logs-distributor curl -s http://analyzer-1:8080/api/v1/health

# Restart system if needed
docker-compose down && docker-compose up -d
```

### Advanced Testing Options
```bash
# High-performance concurrent load testing with Apache Bench
echo '{"packetId":"load-test","agentId":"ab-test","messages":[{"id":"msg-1","level":"INFO","source":"ab.test","message":"Apache Bench load test"}]}' > test_payload.json
ab -n 2000 -c 100 -p test_payload.json -T application/json http://localhost:8080/api/v1/distribute

# Test with custom analyzer weights
ANALYZERS_CONFIG="test-1:http://analyzer-1:8080/api/v1/analyze:0.3,test-2:http://analyzer-2:8080/api/v1/analyze:0.7" \
docker-compose up -d distributor

# Scale emitter services for load testing
docker-compose up -d --scale emitter-steady=3

# Test failure handling and recovery
docker-compose stop analyzer-1    # Simulate analyzer failure
./test-system.sh health          # Observe failure detection
docker-compose start analyzer-1   # Test automatic recovery  
./test-system.sh health          # Verify recovery

# View system health during tests
watch -n 2 './test-system.sh health'
```

### Complete Test Workflow
```bash
# 1. Full system startup and validation
docker-compose up -d
# Check system health and verify all services are ready
./test-system.sh health

# 2. Send test traffic and verify processing  
curl -X POST http://localhost:8080/api/v1/distribute -H "Content-Type: application/json" \
  -d '{"packetId":"test","agentId":"test","messages":[{"level":"INFO","source":"TestService","message":"Test"}]}'

# 3. Verify distribution accuracy
./test-system.sh distribution

# 4. Check performance metrics
for i in {1..4}; do 
  echo "Analyzer-$i stats:" 
  curl -s http://localhost:808$i/api/v1/stats | grep "Messages/sec\|Total Messages"
done

# 5. Run comprehensive tests
./test-system.sh
cd jmeter && ./run-jmeter-tests.sh

# 6. System cleanup
docker-compose down
echo "✅ Complete system test workflow finished!"
```

## 🧪 Testing & Validation

### Automated Test Suite
The system includes comprehensive testing capabilities:

#### 1. Run All Tests
```bash
# Run comprehensive system tests
./test-system.sh

# Run high-performance load testing (recommended)
ab -n 1000 -c 50 -p test_payload.json -T application/json http://localhost:8080/api/v1/distribute

# Check weight distribution
./test-system.sh distribution
```

#### 2. Manual Testing
```bash
# Test distributor health
curl http://localhost:8080/api/v1/health

# Test analyzer health  
curl http://localhost:8081/api/v1/health
curl http://localhost:8082/api/v1/health
curl http://localhost:8083/api/v1/health
curl http://localhost:8084/api/v1/health
```

#### 3. Send Test Log Packet
```bash
# Send a test log packet
curl -X POST http://localhost:8080/api/v1/distribute \
  -H "Content-Type: application/json" \
  -d '{
    "packetId": "test-001",
    "agentId": "manual-test",
    "messages": [{
      "id": "msg-1",
      "level": "INFO",
      "source": "test-app.test-service",
      "message": "Manual test message"
    }]
  }'
```

## ⚖️ Message-Based Weight Distribution

**Important**: This system distributes based on the **total number of log messages processed** by each analyzer, not the number of packets.

### Key Assumptions
- **Packet Atomicity**: All messages in a packet go to the same analyzer (no splitting)
- **Eventual Consistency**: ±2% weight distribution tolerance is acceptable
- **Network Reliability**: HTTP communication within Docker network is stable
- **Static Configuration**: Analyzer weights don't change during runtime
- **Resource Bounds**: 10K in-memory queue capacity with async processing for optimal performance
- **Performance Priority**: Throughput prioritized over strict latency guarantees

### Weight Configuration
- **Analyzer-1**: Weight 0.1 (10% of messages)
- **Analyzer-2**: Weight 0.2 (20% of messages)
- **Analyzer-3**: Weight 0.3 (30% of messages)
- **Analyzer-4**: Weight 0.4 (40% of messages)

### Distribution Algorithm
The system uses an intelligent distribution algorithm that ensures long-term weight compliance. For technical details on the emergency catch-up mechanism and distribution logic, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

### Verify Distribution
```bash
# Check current distribution
./test-system.sh distribution

# View detailed analyzer breakdown
curl http://localhost:8081/api/v1/stats
curl http://localhost:8082/api/v1/stats  
curl http://localhost:8083/api/v1/stats
curl http://localhost:8084/api/v1/stats
```

## 🐳 Docker Configuration

### Services Overview
```yaml
# docker-compose.yaml includes:
- distributor (port 8080)      # Main distribution service
- analyzer-1 (port 8081)      # Weight: 0.1
- analyzer-2 (port 8082)      # Weight: 0.2  
- analyzer-3 (port 8083)      # Weight: 0.3
- analyzer-4 (port 8084)      # Weight: 0.4
- emitter-steady              # Steady traffic generator
- emitter-bursty              # Burst traffic generator  
- emitter-heavy               # Heavy load generator
```

### Individual Service Management
```bash
# Start specific services
docker-compose up -d distributor analyzer-1

# View logs
docker-compose logs -f distributor

# Scale analyzers
docker-compose up -d --scale analyzer-1=2

# Stop all services
docker-compose down
```

### Health Checks
All services include Docker-native health checks:
```bash
# Check container health
docker-compose ps

# View health check logs
docker inspect logs-distributor --format='{{.State.Health.Status}}'
```

## 📈 Performance
- **Throughput**: 6,690+ messages/second (1,670+ packets/second)
- **Error Rate**: 0.00% under high concurrent load (Apache Bench testing)
- **Distribution Accuracy**: Converges to target weights within ±2%
- **Resource Usage**: <400MB memory, <10% CPU per service under sustained load

For detailed performance analysis and benchmarking methodology, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 🔧 Configuration

### Environment Variables
```bash
# Distributor Configuration
SPRING_PROFILES_ACTIVE=docker
ANALYZERS_CONFIG=analyzer-1:http://analyzer-1:8080/api/v1/analyze:0.1,analyzer-2:http://analyzer-2:8080/api/v1/analyze:0.2,analyzer-3:http://analyzer-3:8080/api/v1/analyze:0.3,analyzer-4:http://analyzer-4:8080/api/v1/analyze:0.4

# Analyzer Configuration  
ANALYZER_ID=analyzer-1
ANALYZER_WEIGHT=0.1
SERVER_PORT=8080

# Emitter Configuration
DISTRIBUTOR_URL=http://distributor:8080/api/v1/distribute
AGENT_ID=steady-emitter
EMISSION_RATE=2
EMISSION_MODE=steady
```

### Logging Configuration
```bash
# View distributor debug logs (includes distribution decisions)
docker-compose logs -f distributor | grep "DISTRIBUTION DECISION"

# View analyzer processing logs
docker-compose logs -f analyzer-1 | grep "processed:"
```

## 🛠️ Development

### Local Development Setup
```bash
# Start backing services only
docker-compose up -d analyzer-1 analyzer-2 analyzer-3 analyzer-4

# Run distributor locally
cd distributor  
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### API Endpoints

#### Distributor Endpoints
- `POST /api/v1/distribute` - Submit log packets for distribution
- `GET /api/v1/health` - Health check and status

#### Analyzer Endpoints  
- `POST /api/v1/analyze` - Process log packets from distributor
- `GET /api/v1/stats` - Detailed analyzer statistics and performance metrics
- `GET /api/v1/health` - Health check and status

### Adding New Analyzers
1. Update `docker-compose.yaml` with new analyzer service
2. Add to `ANALYZERS_CONFIG` environment variable
3. Restart distributor service
4. Verify in metrics dashboard

## 🚨 Troubleshooting

### Common Issues

#### Services Not Starting
```bash
# Check port conflicts
docker ps | grep 8080-8084

# Clean restart
docker-compose down
docker-compose up -d --build
```

#### Distribution Issues
```bash
# Check analyzer connectivity
curl http://localhost:8081/api/v1/health
curl http://localhost:8082/api/v1/health
curl http://localhost:8083/api/v1/health  
curl http://localhost:8084/api/v1/health

# View distribution analysis
./test-system.sh distribution

# Check for errors in recent logs
for service in distributor analyzer-1 analyzer-2 analyzer-3 analyzer-4; do
  docker logs logs-$service --since 5m | grep -E "(ERROR|WARN)" | tail -2
done
```

#### Performance Issues
```bash
# Check system health and performance
./test-system.sh health

# Monitor recent system activity
docker logs logs-distributor --since 10m | grep -E "(PACKET_|ERROR|WARN)" | tail -15

# Check resource usage
docker stats

# Check container logs
docker logs logs-distributor | tail -20
```

### Health Monitoring
The system includes automatic failure detection and recovery using structured logging. Use `./test-system.sh health` for system health monitoring. For details on circuit breaker patterns and recovery mechanisms, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 📝 Testing

### Test Coverage
- **Functional Tests**: Weight distribution accuracy and system integration
- **Performance Tests**: High-throughput testing with Apache Bench and JMeter
- **Load Tests**: Concurrent request handling and system stability
- **End-to-End Tests**: Complete workflow validation

### System Test Script (`test-system.sh`)
The `test-system.sh` script is the **primary system validation tool** that provides comprehensive testing of the logs distribution system:

**🎯 Purpose:**
- **Service Health Checks**: Verifies all 5 services (distributor + 4 analyzers) are up and healthy
- **Endpoint Validation**: Tests all REST API endpoints work as expected (`/health`, `/distribute`, `/stats`)
- **Functional Integration**: End-to-end validation of the complete log processing pipeline
- **Weight Distribution Testing**: Validates that analyzers receive messages proportional to their configured weights (0.1:0.2:0.3:0.4)
- **Moderate Load Testing**: Sends ~120 test packets with concurrent agents to verify system behavior

**📊 Test Phases:**
1. Health check all services (60s timeout per service)
2. Send individual test packets (3 packets)
3. Moderate load test (20 packets with 4 concurrent agents)
4. Weight distribution test (100 packets with 10 concurrent agents)
5. Analyze message distribution across analyzers

**💡 Usage:**
```bash
# Run complete system test suite
./test-system.sh

# Run specific test phases
./test-system.sh health       # Health checks only
./test-system.sh send         # Send single test packet
./test-system.sh load 50 5    # Load test: 50 packets, 5 concurrent agents
./test-system.sh distribution # Check weight distribution
```

This script is ideal for **continuous integration**, **deployment validation**, and **system verification** after configuration changes.

### Running Tests
```bash
# Functional weight distribution testing
./test-system.sh distribution

# High-performance load testing (recommended)
ab -n 2000 -c 100 -p test_payload.json -T application/json http://localhost:8080/api/v1/distribute

# Professional JMeter load tests  
cd jmeter && ./run-jmeter-tests.sh

# Complete system validation and weight distribution testing  
./test-system.sh

# High-performance benchmarking with Apache Bench
ab -n 2000 -c 100 -p test_payload.json -T application/json http://localhost:8080/api/v1/distribute
```

For detailed testing strategy and methodology, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 🔮 Extensibility
The system is designed for production scalability with support for:
- Dynamic analyzer registration
- Horizontal scaling
- Advanced monitoring integration
- Cloud deployment

For detailed improvement roadmap and scalability considerations, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 🆘 Support

For support and questions:
- Check the troubleshooting section above
- Review the comprehensive test suite
- Examine the monitoring dashboards
- Check Docker container logs

---

**System Status**: ✅ Production-ready with comprehensive monitoring, testing, and documentation.

**Last Updated**: $(date "+%Y-%m-%d") - Comprehensive metrics, testing, and monitoring implementation complete.