# High-Throughput Logs Distribution System

A production-ready, scalable logs distribution system with weighted load balancing, comprehensive monitoring, and real-time analytics.

> 📖 **For technical details, architectural decisions, and system trade-offs, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md)**

## 🎯 System Overview

This system implements a high-performance logs distributor that receives log packets from multiple emitters and routes them to analyzers based on configurable weights. The system features message-based distribution (not packet-based), comprehensive metrics, real-time monitoring, and production-grade reliability.

### Key Features
- **Message-Based Weighted Distribution**: Ensures analyzers process log messages proportional to their weights
- **High-Throughput Processing**: 35+ messages/second with 0% error rate
- **Comprehensive Monitoring**: Real-time dashboards, metrics, and alerting
- **Production-Ready**: Docker deployment, health checks, circuit breakers
- **Extensive Testing**: Unit tests, integration tests, performance benchmarks
- **Prometheus Integration**: Industry-standard metrics export

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Emitters      │───▶│   Distributor   │───▶│   Analyzers     │
│                 │    │                 │    │                 │
│ • Steady        │    │ • Load Balancer │    │ • Analyzer-1    │
│ • Bursty        │    │ • Queue Manager │    │ • Analyzer-2    │  
│ • Heavy Load    │    │ • Metrics       │    │ • Analyzer-3    │
└─────────────────┘    │ • Health Checks │    │ • Analyzer-4    │
                       └─────────────────┘    └─────────────────┘

                              │
                              ▼
                       ┌─────────────────┐
                       │  Monitoring     │
                       │                 │
                       │ • Dashboards    │
                       │ • Prometheus    │
                       │ • Alerts        │
                       └─────────────────┘
```

### Components
1. **Distributor Service**: Core routing service with weighted load balancing
2. **Analyzer Services**: 4 instances with configurable weights (0.1, 0.2, 0.3, 0.4)
3. **Emitter Services**: 3 Python-based log generators (steady, bursty, heavy)
4. **Monitoring Stack**: Metrics, dashboards, health checks, alerting

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 17+ (for local development)
- Python 3.11+ (for emitters)
- curl and jq (for testing)

### 1. Start the Complete System
```bash
# Clone and start all services
git clone <repository>
cd logs-system
docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

### 2. Check System Health
```bash
# Health check all services
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

### 3. View Real-Time Dashboards
Open in your browser:
- **Main Dashboard**: http://localhost:8080/api/v1/metrics/dashboard
- **Performance Metrics**: http://localhost:8080/api/v1/metrics/performance
- **System Health**: http://localhost:8080/actuator/health
- **Analyzer Stats**: http://localhost:8081/api/v1/stats

## 📊 Monitoring & Dashboards

### Built-in Dashboards
The system includes comprehensive monitoring with multiple dashboard types:

#### 1. Main Metrics Dashboard
```bash
curl http://localhost:8080/api/v1/metrics/dashboard
```
**Example Output:**
```
=== LOGS DISTRIBUTOR METRICS DASHBOARD ===

📊 PERFORMANCE METRICS:
  Packets/sec: 12.01
  Messages/sec: 35.46
  Error Rate: 0.00%
  Queue Size: 1
  Total Messages: 66,854

🔗 ADDITIONAL METRICS:
  - Full Metrics: /actuator/metrics
  - Prometheus: /actuator/prometheus
  - Health Check: /actuator/health
  - Application Info: /actuator/info

🎯 ANALYZER METRICS:
  - Per-analyzer metrics available in /actuator/metrics
  - Filter by tag 'analyzer' for specific analyzer data
```

#### 2. Performance JSON API
```bash
curl http://localhost:8080/api/v1/metrics/performance | jq .
```
**Example Output:**
```json
{
  "queueSize": 1.0,
  "packetsPerSecond": 12.009433141601088,
  "totalMessages": 66854.0,
  "errorRate": 0.0,
  "messagesPerSecond": 35.46125247908919
}
```

#### 3. Individual Analyzer Statistics
```bash
curl http://localhost:8081/api/v1/stats
```
**Example Output:**
```
=== ANALYZER analyzer-1 STATISTICS ===
Total Packets Processed: 2948
Total Messages Processed: 7071
Messages by Level:
  ERROR: 538
  INFO: 2515
  DEBUG: 2790
  FATAL: 127
  WARN: 1101
Top Agents:
  heavy-load-emitter: 4371 messages
  steady-emitter: 1875 messages
  bursty-emitter: 825 messages
Performance Metrics:
  Packets/sec: 1.55
  Messages/sec: 3.72
  Uptime: 1901 seconds
```

#### 4. System Alerts
```bash
curl http://localhost:8080/api/v1/metrics/alerts | jq .
```

### Prometheus Integration
Export metrics to Prometheus/Grafana:
```bash
curl http://localhost:8080/actuator/prometheus
```

## 🧪 Testing & Validation

### Automated Test Suite
The system includes comprehensive testing capabilities:

#### 1. Run All Tests
```bash
# Run integration tests
./integration-tests.sh

# Run performance benchmarks  
./performance-test.sh

# Check weight distribution
./test-system.sh distribution
```

#### 2. Unit Tests
```bash
# Distributor service tests
cd distributor
./mvnw test

# Analyzer service tests  
cd ../analyzer
./mvnw test
```

#### 3. Manual Testing
```bash
# Send a test log packet
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "packetId": "test-001",
    "agentId": "manual-test",
    "timestamp": "2024-01-01T12:00:00.000Z",
    "totalMessages": 1,
    "messages": [{
      "id": "msg-1",
      "timestamp": "2024-01-01T12:00:00.000Z",
      "level": "INFO",
      "source": {
        "application": "test-app",
        "service": "test-service",
        "instance": "1",
        "host": "localhost"
      },
      "message": "Manual test message",
      "metadata": {}
    }],
    "checksum": "test-checksum"
  }'
```

## ⚖️ Message-Based Weight Distribution

**Important**: This system distributes based on the **total number of log messages processed** by each analyzer, not the number of packets.

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
- **Throughput**: 35+ messages/second
- **Error Rate**: <0.1% under normal load
- **Distribution Accuracy**: Converges to target weights within 5%
- **Resource Usage**: <512MB memory, <5% CPU per service

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
DISTRIBUTOR_URL=http://distributor:8080/api/v1/logs
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
- `POST /api/v1/logs` - Submit log packets
- `GET /api/v1/metrics/dashboard` - Main dashboard
- `GET /api/v1/metrics/performance` - Performance JSON
- `GET /api/v1/metrics/alerts` - System alerts
- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - All metrics
- `GET /actuator/prometheus` - Prometheus export

#### Analyzer Endpoints  
- `POST /api/v1/analyze` - Process log packets
- `GET /api/v1/stats` - Analyzer statistics
- `GET /api/v1/health` - Health check
- `GET /actuator/health` - Detailed health
- `GET /actuator/metrics` - Analyzer metrics

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
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health  
curl http://localhost:8084/actuator/health

# View distribution debug logs
docker-compose logs distributor | grep "DISTRIBUTION DECISION" | tail -10
```

#### Performance Issues
```bash
# Check system metrics
curl http://localhost:8080/api/v1/metrics/alerts | jq .

# Monitor queue size
curl http://localhost:8080/api/v1/metrics/performance | jq .queueSize

# Check resource usage
docker stats
```

### Health Monitoring
The system includes comprehensive health monitoring and automatic failure recovery. For details on circuit breaker patterns and recovery mechanisms, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 📝 Testing

### Test Coverage
- **Unit Tests**: Core distribution logic and metrics
- **Integration Tests**: Multi-service scenarios
- **Performance Tests**: Load and stress testing with JMeter
- **System Tests**: End-to-end validation

### Running Tests
```bash
# Integration tests
./integration-tests.sh

# Performance tests
./performance-test.sh

# JMeter load tests
cd jmeter && ./run-jmeter-tests.sh

# Weight distribution validation
./test-system.sh distribution
```

For detailed testing strategy and methodology, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 🔮 Extensibility
The system is designed for production scalability with support for:
- Dynamic analyzer registration
- Horizontal scaling
- Advanced monitoring integration
- Cloud deployment

For detailed improvement roadmap and scalability considerations, see [TECHNICAL_WRITEUP.md](./TECHNICAL_WRITEUP.md).

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Add comprehensive tests
4. Ensure all tests pass
5. Submit a pull request

## 🆘 Support

For support and questions:
- Check the troubleshooting section above
- Review the comprehensive test suite
- Examine the monitoring dashboards
- Check Docker container logs

---

**System Status**: ✅ Production-ready with comprehensive monitoring, testing, and documentation.

**Last Updated**: $(date "+%Y-%m-%d") - Comprehensive metrics, testing, and monitoring implementation complete.