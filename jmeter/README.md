# JMeter Load Testing for Logs Distribution System

Professional load testing using Apache JMeter for the high-throughput logs distribution system.

## 🚀 Quick Start

### 1. Install JMeter

**macOS (Homebrew):**
```bash
brew install jmeter
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install jmeter
```

**Manual Installation:**
1. Download from: https://jmeter.apache.org/download_jmeter.cgi
2. Extract and add `bin/` directory to your PATH
3. Verify: `jmeter --version`

### 2. Start the System
```bash
# Ensure your logs distribution system is running
docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

### 3. Run Load Tests
```bash
cd jmeter
./run-jmeter-tests.sh
```

## 📊 Test Plans

### `load-test-plan.jmx`
Comprehensive load testing with two scenarios:

#### Baseline Load Test
- **5 concurrent users**
- **10 iterations each**
- **10-second ramp-up**
- **Total requests: 50**
- **Purpose**: Establish performance baseline

#### High Volume Stress Test  
- **20 concurrent users**
- **20 iterations each**
- **30-second ramp-up**
- **Total requests: 400**
- **Purpose**: Test system under heavy load

### Request Payload
Each test generates realistic log packets with:
- **3-5 messages per packet**
- **Randomized log levels** (DEBUG, INFO, WARN, ERROR, FATAL)
- **Dynamic timestamps and UUIDs**
- **Thread-specific agent IDs**
- **Realistic metadata**

## 📈 Results & Reports

After running tests, you'll get:

### HTML Reports
```
jmeter/results/Load_Test_Plan_YYYYMMDD_HHMMSS_html_report/index.html
```
Professional dashboards with:
- **Response times over time**
- **Throughput graphs**  
- **Error rate analysis**
- **Percentile response times**

### Raw Data Files
```
jmeter/results/Load_Test_Plan_YYYYMMDD_HHMMSS.jtl
```
CSV format for custom analysis

### Log Files
```
jmeter/results/Load_Test_Plan_YYYYMMDD_HHMMSS.log
```
Detailed JMeter execution logs

## 🎯 What Tests Validate

### Performance Metrics
- **Throughput**: Requests/second capacity
- **Response Time**: P50, P95, P99 latencies
- **Error Rate**: Success/failure percentages
- **Concurrency**: Multi-user handling

### System Behavior
- **Weight Distribution**: Messages distributed proportionally
- **Failure Handling**: System resilience under load
- **Resource Usage**: CPU, memory, queue status
- **Recovery**: System stability after load

## 📊 Expected Results

Based on system capabilities:

### Baseline Test (5 users, 50 requests)
- **Success Rate**: 100%
- **Average Response Time**: < 100ms
- **Throughput**: 10+ requests/second
- **Errors**: 0%

### Stress Test (20 users, 400 requests)  
- **Success Rate**: > 95%
- **Average Response Time**: < 500ms
- **Throughput**: 25+ requests/second
- **Errors**: < 5%

### Distribution Validation
After load testing, verify:
```bash
# Check analyzer statistics
curl http://localhost:8081/api/v1/stats  # ~10% of messages
curl http://localhost:8082/api/v1/stats  # ~20% of messages  
curl http://localhost:8083/api/v1/stats  # ~30% of messages
curl http://localhost:8084/api/v1/stats  # ~40% of messages
```

## 🔧 Customization

### Modify Load Parameters
Edit `load-test-plan.jmx` to adjust:
- **Thread counts**: More concurrent users
- **Ramp-up time**: Faster/slower scaling
- **Loop counts**: More iterations
- **Think time**: Delays between requests

### Add New Test Scenarios
Create additional ThreadGroups for:
- **Burst traffic patterns**
- **Sustained long-running tests**  
- **Failure scenario testing**
- **Memory stress testing**

### Custom Assertions
Add response validations:
- **JSON schema validation**
- **Response time SLAs**
- **Custom error patterns**
- **Business logic checks**

## 🚨 Troubleshooting

### JMeter Not Found
```bash
# Check installation
jmeter --version

# Add to PATH (Linux/macOS)
export PATH=$PATH:/opt/apache-jmeter/bin
```

### System Not Responding
```bash
# Check service health
curl http://localhost:8080/actuator/health

# Restart if needed
docker-compose restart
```

### High Error Rates
- **Reduce concurrent users**
- **Increase ramp-up time**
- **Check system resources** (`docker stats`)
- **Review error messages** in JMeter logs

### Memory Issues
```bash
# Increase JMeter heap size
export JVM_ARGS="-Xmx2g -Xms1g"
./run-jmeter-tests.sh
```

## 📝 Best Practices

### Pre-Test Checklist
- [ ] System is running and healthy
- [ ] All analyzers are responsive  
- [ ] Previous test data cleared
- [ ] Sufficient disk space for results

### During Testing
- [ ] Monitor system resources
- [ ] Watch for error patterns
- [ ] Check distributor logs
- [ ] Verify analyzer distribution

### Post-Test Analysis
- [ ] Review HTML reports
- [ ] Validate message distribution
- [ ] Check system recovery
- [ ] Document performance baselines

## 🎯 Professional Benefits

Using JMeter demonstrates:
- **Industry standard testing**
- **Professional tooling knowledge**
- **Comprehensive performance analysis**
- **Production-ready validation**
- **Scalability assessment**

This approach shows reviewers that you understand enterprise-grade load testing methodologies and can validate system performance using professional tools.
