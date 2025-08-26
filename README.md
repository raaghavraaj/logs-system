# 🚀 High-Throughput Logs Distribution System

A distributed logging system built with Spring Boot that demonstrates weighted load balancing, failure handling, and high-throughput processing capabilities.

## 📋 System Architecture

```
┌─────────────┐    HTTP POST     ┌─────────────────┐    HTTP POST    ┌─────────────────┐
│ Log Emitters│ ────────────────▶│   Distributor   │ ──────────────▶ │   Analyzer-1    │
│  (Agents)   │                  │   (Port 8080)   │                 │  (Port 8081)    │
└─────────────┘                  │                 │                 │   Weight: 0.1   │
                                 │  • Weighted     │                 └─────────────────┘
                                 │    Load Balancer│                 ┌─────────────────┐
                                 │  • Failure      │ ──────────────▶ │   Analyzer-2    │
                                 │    Detection    │                 │  (Port 8082)    │
                                 │  • Recovery     │                 │   Weight: 0.2   │
                                 │    Handling     │                 └─────────────────┘
                                 │  • Multi-       │                 ┌─────────────────┐
                                 │    threading    │ ──────────────▶ │   Analyzer-3    │
                                 └─────────────────┘                 │  (Port 8083)    │
                                                                     │   Weight: 0.3   │
                                                                     └─────────────────┘
                                                                     ┌─────────────────┐
                                                                     │   Analyzer-4    │
                                                                     │  (Port 8084)    │
                                                                     │   Weight: 0.4   │
                                                                     └─────────────────┘
```

## 🎯 Key Features

### Core Functionality
- **Weighted Distribution**: Messages distributed proportionally based on analyzer weights (0.1, 0.2, 0.3, 0.4)
- **High Throughput**: Multi-threaded, non-blocking architecture using ExecutorService
- **Failure Detection**: Automatic detection of analyzer failures with configurable thresholds
- **Auto Recovery**: Failed analyzers automatically brought back online after timeout
- **Health Monitoring**: Built-in health checks for all services

### Technical Implementation
- **RESTful APIs**: Standard HTTP endpoints for log ingestion and health checks
- **Docker Ready**: Full containerization with Docker Compose orchestration
- **Spring Boot**: Modern Java framework with embedded Tomcat
- **Thread Safety**: Concurrent data structures and atomic operations
- **Configurable**: Environment-based configuration for different deployment scenarios

## 📊 Message-Based Weight Distribution

**CRITICAL**: The load balancing is based on the **total number of log messages processed** by each analyzer, **NOT the number of packets**.

### How It Works
- Each **log packet contains multiple log messages** (1-20 messages per packet)
- **Complete packets** are sent to one analyzer (packets are never split)
- **Weight distribution** is calculated based on total messages processed across all analyzers
- **Algorithm** dynamically selects the analyzer that will best maintain target proportions

### Weight Examples
With 10,000 total log messages processed:
- **Analyzer-1** (weight 0.1): Should process ~1,000 messages (10%)
- **Analyzer-2** (weight 0.2): Should process ~2,000 messages (20%)
- **Analyzer-3** (weight 0.3): Should process ~3,000 messages (30%)
- **Analyzer-4** (weight 0.4): Should process ~4,000 messages (40%)

### Verification Commands
```bash
# Check message-based distribution 
./test-system.sh distribution

# View detailed analyzer statistics
curl http://localhost:8081/api/v1/stats  # Analyzer-1
curl http://localhost:8082/api/v1/stats  # Analyzer-2
curl http://localhost:8083/api/v1/stats  # Analyzer-3
curl http://localhost:8084/api/v1/stats  # Analyzer-4
```

## 🏁 Quick Start

### Prerequisites
- Docker and Docker Compose
- curl (for testing)
- Bash shell (for test scripts)

### 1. Clone and Build
```bash
git clone <repository-url>
cd logs-system
```

### 2. Start the System
```bash
# Build and start all services
docker-compose up --build

# Or run in background
docker-compose up --build -d
```

This starts:
- **Distributor** on port 8080
- **Analyzer-1** on port 8081 (weight: 0.1)
- **Analyzer-2** on port 8082 (weight: 0.2)  
- **Analyzer-3** on port 8083 (weight: 0.3)
- **Analyzer-4** on port 8084 (weight: 0.4)

### 3. Test the System
```bash
# Run comprehensive tests
./test-system.sh

# Or individual tests
./test-system.sh health          # Check service health
./test-system.sh send            # Send single packet
./test-system.sh load 100 10     # Load test: 100 packets, 10 concurrent agents
```

### 4. Manual Testing
```bash
# Send a log packet
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "packetId": "test-123",
    "agentId": "manual-test",
    "totalMessages": 2,
    "messages": [
      {
        "level": "ERROR",
        "source": {
          "application": "test-app",
          "service": "auth-service", 
          "instance": "auth-1",
          "host": "prod-server"
        },
        "message": "Authentication failed",
        "metadata": {"userId": "12345"}
      },
      {
        "level": "INFO",
        "source": {
          "application": "test-app",
          "service": "user-service",
          "instance": "user-1", 
          "host": "prod-server"
        },
        "message": "User login successful",
        "metadata": {"userId": "12345"}
      }
    ],
    "checksum": "abc123"
  }'
```

## 📊 Demo Scenarios

### 1. Weight Distribution Demo
```bash
# Send 100 packets and observe distribution
./test-system.sh load 100 5

# View distribution in logs
docker-compose logs distributor | grep "processed:"
```

Expected distribution (approximately):
- Analyzer-1: ~10% of messages (weight 0.1)
- Analyzer-2: ~20% of messages (weight 0.2)
- Analyzer-3: ~30% of messages (weight 0.3)
- Analyzer-4: ~40% of messages (weight 0.4)

### 2. Failure Handling Demo
```bash
# Stop an analyzer
docker stop logs-analyzer-1

# Send packets (traffic redistributed to remaining analyzers)
./test-system.sh load 50 3

# Restart analyzer (automatically detected and brought back online)
docker start logs-analyzer-1

# Send more packets (traffic includes recovered analyzer)
./test-system.sh load 50 3
```

### 3. High Throughput Demo
```bash
# High-volume test
./test-system.sh load 1000 20

# Monitor performance
docker-compose logs -f distributor
```

## 🔧 Configuration

### Environment Variables

#### Distributor
- `SERVER_PORT`: Service port (default: 8080)
- `SPRING_PROFILES_ACTIVE`: Deployment profile (docker/local)

#### Analyzer
- `SERVER_PORT`: Service port (default: 8080)
- `ANALYZER_ID`: Unique analyzer identifier
- `ANALYZER_WEIGHT`: Processing weight (for reference)

### Failure Handling Parameters
- **Max Consecutive Failures**: 3 (before marking offline)
- **Recovery Timeout**: 30 seconds
- **Health Check Interval**: 30 seconds

## 📈 Performance Characteristics

### Throughput
- **Thread Pool**: 4 × CPU cores for concurrent processing
- **Non-blocking**: Asynchronous HTTP calls to analyzers
- **Batch Processing**: Individual packets processed independently

### Latency
- **Best Effort**: No strict SLA, optimized for throughput
- **Acceptable Delay**: Some latency acceptable for reliability
- **Async Processing**: Client requests return immediately (202 Accepted)

### Reliability
- **Circuit Breaker**: Failed analyzers automatically taken offline
- **Auto Recovery**: Periodic retry attempts for offline analyzers
- **Graceful Degradation**: Remaining analyzers handle load when others fail

## 🧪 Testing Strategy

### Manual Testing
1. **Individual Components**: Health checks for each service
2. **Distribution Logic**: Verify weighted routing works correctly  
3. **Failure Scenarios**: Stop/start analyzers, verify handling
4. **Load Testing**: High-volume packet transmission

### Automated Testing
- **Health Checks**: Automated service availability verification
- **Load Testing**: Configurable volume and concurrency tests
- **Distribution Verification**: Log analysis for weight compliance

## 📝 API Documentation

### Distributor Endpoints

#### `POST /api/v1/logs`
Accepts log packets for distribution.

**Request Body:**
```json
{
  "packetId": "unique-packet-id",
  "agentId": "emitter-agent-id",
  "totalMessages": 3,
  "messages": [
    {
      "level": "ERROR|WARN|INFO|DEBUG|FATAL",
      "source": {
        "application": "app-name",
        "service": "service-name",
        "instance": "instance-id",
        "host": "hostname"
      },
      "message": "Log message text",
      "metadata": {}
    }
  ],
  "checksum": "integrity-hash"
}
```

**Response:** `202 Accepted`

#### `GET /api/v1/health`
Service health check.

**Response:** `200 OK` - "Distributor is online."

### Analyzer Endpoints

#### `POST /api/v1/analyze`  
Processes log packets (same request format as distributor).

**Response:** `202 Accepted`

#### `GET /api/v1/health`
Analyzer health check.

**Response:** `200 OK` - "Analyzer is online and healthy"

## 🏗 System Assumptions & Design Decisions

### Core Assumptions
1. **Security**: Authentication/authorization out of scope
2. **Scalability**: System designed for large number of emitters (10k+)
3. **Real-time**: Some latency acceptable, no strict real-time guarantees
4. **Weights**: Static analyzer weights (no runtime changes)
5. **Ordering**: Packet processing order not required
6. **Reliability**: Best-effort delivery, no persistence layer required

### Technical Decisions
1. **Packet Granularity**: Entire packets sent to single analyzer (not split)
2. **Load Balancing**: Weighted round-robin based on message count deviation
3. **Failure Detection**: Count-based with exponential backoff
4. **Recovery Strategy**: Time-based automatic retry
5. **Threading Model**: Fixed thread pool with async processing

## 🚧 Future Improvements

### Performance Enhancements
- **Connection Pooling**: Reuse HTTP connections to analyzers
- **Batch Processing**: Group multiple packets for efficiency  
- **Async HTTP Client**: WebClient for better non-blocking performance
- **Metrics Collection**: Detailed performance monitoring

### Reliability Features  
- **Persistent Queues**: Message durability during failures
- **Dead Letter Queue**: Handle repeatedly failing packets
- **Circuit Breaker Library**: Hystrix/Resilience4j integration
- **Distributed Health**: Consensus-based failure detection

### Scalability Features
- **Dynamic Configuration**: Runtime analyzer management
- **Service Discovery**: Automatic analyzer registration  
- **Horizontal Scaling**: Multiple distributor instances
- **Priority Queues**: Critical log message prioritization

### Monitoring & Operations
- **Metrics Dashboard**: Real-time system monitoring
- **Alerting**: Failure notifications and SLA monitoring
- **Distributed Tracing**: Request flow tracking
- **Performance Profiling**: Bottleneck identification

## 📄 Architecture Notes

### Design Patterns Used
- **Strategy Pattern**: Pluggable load balancing algorithms
- **Circuit Breaker**: Failure isolation and recovery
- **Observer Pattern**: Health state change notifications
- **Factory Pattern**: Service endpoint configuration

### Technology Stack
- **Framework**: Spring Boot 3.5.x
- **Java Version**: OpenJDK 17
- **Build Tool**: Maven 3.8+
- **Containerization**: Docker + Docker Compose
- **HTTP Client**: Spring RestTemplate (upgradable to WebClient)

### Monitoring Points
- **Message Distribution**: Per-analyzer message counts
- **Failure Rates**: Success/failure ratios per analyzer
- **Response Times**: HTTP call latencies
- **System Health**: Service availability status

---

## 🎯 Getting Started Checklist

- [ ] Docker and Docker Compose installed
- [ ] Repository cloned
- [ ] Run `docker-compose up --build`
- [ ] Verify all services healthy: `./test-system.sh health`
- [ ] Run basic test: `./test-system.sh`
- [ ] Test weight distribution with load test
- [ ] Verify failure handling by stopping/starting analyzers
- [ ] Review logs for message distribution patterns

**🎉 Ready to demonstrate high-throughput distributed log processing!**
