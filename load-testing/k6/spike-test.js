import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export let errorRate = new Rate('errors');

// Spike test configuration - sudden load increases
export let options = {
  stages: [
    { duration: '10s', target: 10 },   // Normal load
    { duration: '5s', target: 200 },   // Sudden spike!
    { duration: '10s', target: 200 },  // Stay at spike
    { duration: '5s', target: 10 },    // Back to normal
    { duration: '10s', target: 10 },   // Normal operation
    { duration: '5s', target: 300 },   // Even bigger spike!
    { duration: '10s', target: 300 },  // Stay at high spike
    { duration: '10s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<1000'], // 99% under 1s during spikes
    http_req_failed: ['rate<0.05'],    // Allow 5% failures during spikes
  },
};

function generateLogPacket() {
  return {
    packetId: `spike-${Math.random().toString(36).substr(2, 9)}`,
    agentId: `k6-spike-agent-${__VU}`,
    timestamp: new Date().toISOString(),
    messages: [
      {
        id: `spike-msg-${Math.random().toString(36).substr(2, 9)}`,
        level: 'ERROR',
        source: 'spike-test',
        message: `SPIKE TEST: Heavy load from VU ${__VU} - testing system resilience`
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

  let response = http.post('http://localhost:8080/api/v1/distribute', payload, params);
  
  let success = check(response, {
    'status is 202': (r) => r.status === 202,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });
  
  errorRate.add(!success);
  sleep(0.05); // Shorter sleep for spike testing
}

export function setup() {
  console.log('⚡ Starting k6 SPIKE test - testing system resilience under sudden load');
}
