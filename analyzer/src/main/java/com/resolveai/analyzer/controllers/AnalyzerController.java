package com.resolveai.analyzer.controllers;

import com.resolveai.analyzer.models.LogPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class AnalyzerController {
    
    // 🚀 HIGH-PERFORMANCE COUNTERS - LongAdder for reduced contention
    private final LongAdder totalPacketsProcessed = new LongAdder();
    private final LongAdder totalMessagesProcessed = new LongAdder();
    private final Map<String, LongAdder> messagesByLevel = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> messagesByAgent = new ConcurrentHashMap<>();
    private final LongAdder startTime = new LongAdder();
    
    private final String analyzerId = System.getenv().getOrDefault("ANALYZER_ID", "unknown-analyzer");
    
    public AnalyzerController() {
        startTime.add(System.currentTimeMillis());
        
        // Initialize level counters with LongAdder
        messagesByLevel.put("DEBUG", new LongAdder());
        messagesByLevel.put("INFO", new LongAdder());
        messagesByLevel.put("WARN", new LongAdder());
        messagesByLevel.put("ERROR", new LongAdder());
        messagesByLevel.put("FATAL", new LongAdder());
        
        log.info("🚀 HIGH-PERFORMANCE ANALYZER INITIALIZED | analyzer_id={}", analyzerId);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Analyzer is online and healthy\n");
    }
    
    @GetMapping("/stats")
    public ResponseEntity<String> getStatistics() {
        long uptime = System.currentTimeMillis() - startTime.sum();
        long packets = totalPacketsProcessed.sum();
        long messages = totalMessagesProcessed.sum();
        
        double packetsPerSec = packets > 0 ? (packets * 1000.0) / uptime : 0;
        double messagesPerSec = messages > 0 ? (messages * 1000.0) / uptime : 0;
        
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("=== ANALYZER %s STATISTICS ===\n", analyzerId));
        stats.append(String.format("Total Packets Processed: %d\n", packets));
        stats.append(String.format("Total Messages Processed: %d\n", messages));
        stats.append("Messages by Level:\n");
        
        // Sort by count (highest first)
        messagesByLevel.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
                .forEach(entry -> stats.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue().sum())));
        
        stats.append("Top Agents:\n");
        messagesByAgent.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
                .limit(5)
                .forEach(entry -> stats.append(String.format("  %s: %d messages\n", entry.getKey(), entry.getValue().sum())));
        
        stats.append("\nPerformance Metrics:\n");
        stats.append(String.format("  Packets/sec: %.2f\n", packetsPerSec));
        stats.append(String.format("  Messages/sec: %.2f\n", messagesPerSec));
        stats.append(String.format("  Uptime: %d seconds\n", uptime / 1000));
        
        return ResponseEntity.ok(stats.toString());
    }
    
    @PostMapping("/analyze")
    public ResponseEntity<String> analyzeLogPacket(@RequestBody LogPacket logPacket) {
        Instant startTime = Instant.now();
        
        // 🚀 MINIMAL LOGGING in hot path - only essential info
        if (log.isDebugEnabled()) {
            log.debug("PACKET_RECV | analyzer={} | id={} | msgs={}", 
                    analyzerId, logPacket.getPacketId(), logPacket.getMessages().size());
        }
        
        // 🚀 BATCH COUNTER UPDATES - More efficient than per-message updates
        int messageCount = logPacket.getMessages().size();
        totalPacketsProcessed.increment();
        totalMessagesProcessed.add(messageCount);
        
        // Process messages in batch
        String agentId = logPacket.getAgentId();
        messagesByAgent.computeIfAbsent(agentId, k -> new LongAdder()).add(messageCount);
        
        // Count by level efficiently
        Map<String, Long> levelCounts = new ConcurrentHashMap<>();
        logPacket.getMessages().forEach(message -> {
            String level = message.getLevel().toString();
            levelCounts.merge(level, 1L, Long::sum);
            
            // 🚀 VERY MINIMAL per-message logging - only for debugging
            if (log.isTraceEnabled()) {
                log.trace("MSG_PROC | analyzer={} | level={} | source={}", 
                        analyzerId, level, message.getSource());
            }
        });
        
        // 🚀 BATCH UPDATE level counters
        levelCounts.forEach((level, count) -> 
                messagesByLevel.computeIfAbsent(level, k -> new LongAdder()).add(count));
        
        Duration processingTime = Duration.between(startTime, Instant.now());
        
        // 🚀 REDUCED LOGGING frequency - only log significant packets or periodically
        long totalPackets = totalPacketsProcessed.sum();
        if (messageCount > 5 || totalPackets % 1000 == 0) {
            log.info("PACKET_PROCESSED | analyzer={} | id={} | msgs={} | processing_ms={} | total_packets={} | total_messages={}", 
                    analyzerId, logPacket.getPacketId(), messageCount, 
                    processingTime.toMillis(), totalPackets, totalMessagesProcessed.sum());
        }
        
        // 🚀 IMMEDIATE RESPONSE - Don't wait for logging
        return ResponseEntity.ok("Log packet processed successfully\n");
    }
}
