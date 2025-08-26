package com.resolveai.distributor.controllers;

import com.resolveai.distributor.services.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {
    
    private final MetricsService metricsService;
    
    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("packetsPerSecond", metricsService.getPacketsPerSecond());
        metrics.put("messagesPerSecond", metricsService.getMessagesPerSecond());
        metrics.put("errorRate", metricsService.getErrorRate());
        metrics.put("queueSize", metricsService.getCurrentQueueSize());
        metrics.put("totalMessages", metricsService.getTotalMessages());
        
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<String> getMetricsDashboard() {
        StringBuilder dashboard = new StringBuilder();
        
        dashboard.append("=== LOGS DISTRIBUTOR METRICS DASHBOARD ===\n\n");
        
        dashboard.append("📊 PERFORMANCE METRICS:\n");
        dashboard.append(String.format("  Packets/sec: %.2f\n", metricsService.getPacketsPerSecond()));
        dashboard.append(String.format("  Messages/sec: %.2f\n", metricsService.getMessagesPerSecond()));
        dashboard.append(String.format("  Error Rate: %.2f%%\n", metricsService.getErrorRate() * 100));
        dashboard.append(String.format("  Queue Size: %.0f\n", metricsService.getCurrentQueueSize()));
        dashboard.append(String.format("  Total Messages: %.0f\n", metricsService.getTotalMessages()));
        
        dashboard.append("\n🔗 ADDITIONAL METRICS:\n");
        dashboard.append("  - Full Metrics: /actuator/metrics\n");
        dashboard.append("  - Prometheus: /actuator/prometheus\n");
        dashboard.append("  - Health Check: /actuator/health\n");
        dashboard.append("  - Application Info: /actuator/info\n");
        
        dashboard.append("\n🎯 ANALYZER METRICS:\n");
        dashboard.append("  - Per-analyzer metrics available in /actuator/metrics\n");
        dashboard.append("  - Filter by tag 'analyzer' for specific analyzer data\n");
        
        return ResponseEntity.ok(dashboard.toString());
    }
    
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts() {
        Map<String, Object> alerts = new HashMap<>();
        
        double errorRate = metricsService.getErrorRate();
        double queueSize = metricsService.getCurrentQueueSize();
        double packetsPerSec = metricsService.getPacketsPerSecond();
        
        // Error rate alerts
        if (errorRate > 0.05) { // 5% error rate
            alerts.put("HIGH_ERROR_RATE", String.format("Error rate is %.2f%% (threshold: 5%%)", errorRate * 100));
        }
        
        // Queue size alerts
        if (queueSize > 8000) { // 80% of max queue size (10000)
            alerts.put("HIGH_QUEUE_SIZE", String.format("Queue size is %.0f (threshold: 8000)", queueSize));
        }
        
        // Low throughput alerts
        if (packetsPerSec < 0.1 && metricsService.getTotalMessages() > 100) {
            alerts.put("LOW_THROUGHPUT", String.format("Low throughput: %.2f packets/sec", packetsPerSec));
        }
        
        if (alerts.isEmpty()) {
            alerts.put("status", "All metrics within normal ranges");
        }
        
        return ResponseEntity.ok(alerts);
    }
}
