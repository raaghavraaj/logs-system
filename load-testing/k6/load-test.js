import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
export let errorRate = new Rate('errors');

// Test configuration options
export let options = {
  scenarios: {
    baseline_load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 10 },  // Ramp up to 10 users
        { duration: '30s', target: 10 },  // Stay at 10 users
        { duration: '10s', target: 0 },   // Ramp down
      ],
      tags: { test_type: 'baseline' },
    },
    stress_test: {
      executor: 'ramping-vus', 
      startTime: '1m',
      startVUs: 1,
      stages: [
        { duration: '15s', target: 50 },  // Ramp up to 50 users
        { duration: '45s', target: 50 },  // Stay at 50 users
        { duration: '30s', target: 100 }, // Ramp up to 100 users
        { duration: '30s', target: 100 }, // Stay at 100 users
        { duration: '15s', target: 0 },   // Ramp down
      ],
      tags: { test_type: 'stress' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95% of requests should be below 100ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
    errors: ['rate<0.01'],            // Custom error rate should be less than 1%
  },
};

// Test data generator
function generateLogPacket() {
  const levels = ['DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'];
  const sources = ['k6-load-test', 'performance-test', 'stress-test', 'baseline-test'];
  
  return {
    packetId: `k6-${Math.random().toString(36).substr(2, 9)}`,
    agentId: `k6-agent-${__VU}`,
    timestamp: new Date().toISOString(),
    messages: [
      {
        id: `msg-${Math.random().toString(36).substr(2, 9)}`,
        level: levels[Math.floor(Math.random() * levels.length)],
        source: sources[Math.floor(Math.random() * sources.length)],
        message: `k6 load test message from VU ${__VU} iteration ${__ITER}`
      },
      {
        id: `msg-${Math.random().toString(36).substr(2, 9)}`,
        level: levels[Math.floor(Math.random() * levels.length)],
        source: sources[Math.floor(Math.random() * sources.length)],
        message: `Secondary message for realistic packet size testing`
      }
    ]
  };
}

export default function() {
  const payload = JSON.stringify(generateLogPacket());
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Send log packet to distributor
  let response = http.post('http://localhost:8080/api/v1/distribute', payload, params);
  
  // Check response
  let success = check(response, {
    'status is 202': (r) => r.status === 202,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'response has no body': (r) => r.body === '',
  });
  
  errorRate.add(!success);
  
  // Small sleep to simulate realistic user behavior
  sleep(0.1);
}

// Setup function (runs once at the beginning)
export function setup() {
  console.log('🚀 Starting k6 load test for Logs Distribution System');
  console.log('📊 Test scenarios: Baseline (10 VUs) + Stress (50-100 VUs)');
  
  // Health check before starting
  let healthResponse = http.get('http://localhost:8080/api/v1/health');
  if (healthResponse.status !== 200) {
    throw new Error(`Health check failed: ${healthResponse.status}`);
  }
  console.log('✅ System health check passed');
}

// Teardown function (runs once at the end)
export function teardown(data) {
  console.log('🏁 k6 load test completed');
  console.log('📈 Check the results above for performance metrics');
  console.log('🔍 Verify message distribution across analyzers manually');
}
