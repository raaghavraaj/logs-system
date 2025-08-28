package com.resolveai.analyzer.controllers;

import com.resolveai.analyzer.models.LogPacket;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class AnalyzerController {
    
    // Simple statistics tracking for logs
    private final AtomicLong totalPacketsProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final Map<String, AtomicLong> messagesByLevel = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> messagesByAgent = new ConcurrentHashMap<>();
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    
    private final String analyzerId = System.getenv().getOrDefault("ANALYZER_ID", "unknown-analyzer");
    
    public AnalyzerController() {
        // Initialize level counters
        messagesByLevel.put("DEBUG", new AtomicLong(0));
        messagesByLevel.put("INFO", new AtomicLong(0));
        messagesByLevel.put("WARN", new AtomicLong(0));
        messagesByLevel.put("ERROR", new AtomicLong(0));
        messagesByLevel.put("FATAL", new AtomicLong(0));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Analyzer is online and healthy\n");
    }
    
    @GetMapping("/stats")
    public ResponseEntity<String> getStatistics() {
        long uptime = System.currentTimeMillis() - startTime.get();
        long packets = totalPacketsProcessed.get();
        long messages = totalMessagesProcessed.get();
        
        double packetsPerSec = packets > 0 ? (packets * 1000.0) / uptime : 0;
        double messagesPerSec = messages > 0 ? (messages * 1000.0) / uptime : 0;
        
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("=== ANALYZER %s STATISTICS ===\n", analyzerId));
        stats.append(String.format("Total Packets Processed: %d\n", packets));
        stats.append(String.format("Total Messages Processed: %d\n", messages));
        stats.append("Messages by Level:\n");
        
        messagesByLevel.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue((v1, v2) -> Long.compare(v2.get(), v1.get())))
                .forEach(entry -> stats.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue().get())));
        
        stats.append("Top Agents:\n");
        messagesByAgent.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue((v1, v2) -> Long.compare(v2.get(), v1.get())))
                .limit(5)
                .forEach(entry -> stats.append(String.format("  %s: %d messages\n", entry.getKey(), entry.getValue().get())));
        
        stats.append("\nPerformance Metrics:\n");
        stats.append(String.format("  Packets/sec: %.2f\n", packetsPerSec));
        stats.append(String.format("  Messages/sec: %.2f\n", messagesPerSec));
        stats.append(String.format("  Uptime: %d seconds\n", uptime / 1000));
        
        return ResponseEntity.ok(stats.toString());
    }
    
    @PostMapping("/analyze")
    public ResponseEntity<String> analyzeLogPacket(@RequestBody LogPacket logPacket) {
        Instant startTime = Instant.now();
        
        log.info("PACKET_RECEIVED | analyzer={} | packet_id={} | messages={} | agent_id={} | timestamp={}", 
                analyzerId, logPacket.getPacketId(), logPacket.getMessages().size(), 
                logPacket.getAgentId(), startTime);
        
        // Update counters
        totalPacketsProcessed.incrementAndGet();
        totalMessagesProcessed.addAndGet(logPacket.getMessages().size());
        
        // Process each message and track by level and agent
        logPacket.getMessages().forEach(message -> {
            String level = message.getLevel().toString();
            String agent = logPacket.getAgentId();
            
            // Update level counters
            messagesByLevel.computeIfAbsent(level, k -> new AtomicLong(0)).incrementAndGet();
            
            // Update agent counters
            messagesByAgent.computeIfAbsent(agent, k -> new AtomicLong(0)).incrementAndGet();
            
            log.debug("MESSAGE_PROCESSED | analyzer={} | level={} | agent={} | message={}", 
                    analyzerId, level, agent, message.getMessage());
        });
        
        Duration processingTime = Duration.between(startTime, Instant.now());
        
        log.info("PACKET_PROCESSED | analyzer={} | packet_id={} | messages={} | processing_ms={} | total_packets={} | total_messages={}", 
                analyzerId, logPacket.getPacketId(), logPacket.getMessages().size(), 
                processingTime.toMillis(), totalPacketsProcessed.get(), totalMessagesProcessed.get());
        
        return ResponseEntity.ok("Log packet processed successfully\n");
    }
}