package com.resolveai.analyzer.controllers;

import com.resolveai.analyzer.models.LogPacket;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class AnalyzerController {
    
    // Cumulative statistics tracking
    private final AtomicLong totalPacketsProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final Map<String, AtomicLong> messagesByLevel = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> messagesByAgent = new ConcurrentHashMap<>();
    
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
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("=== ANALYZER %s STATISTICS ===\n", analyzerId));
        stats.append(String.format("Total Packets Processed: %d\n", totalPacketsProcessed.get()));
        stats.append(String.format("Total Messages Processed: %d\n", totalMessagesProcessed.get()));
        stats.append("Messages by Level:\n");
        messagesByLevel.forEach((level, count) -> 
            stats.append(String.format("  %s: %d\n", level, count.get())));
        stats.append("Top Agents:\n");
        messagesByAgent.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
            .limit(5)
            .forEach(entry -> 
                stats.append(String.format("  %s: %d messages\n", entry.getKey(), entry.getValue().get())));
        return ResponseEntity.ok(stats.toString());
    }

    @PostMapping("/analyze")
    public ResponseEntity<Void> analyzeLogPacket(@RequestBody LogPacket logPacket) {
        log.info("Received log packet {} with {} messages from agent {}",
                logPacket.getPacketId(),
                logPacket.getMessages().size(),
                logPacket.getAgentId());

        processLogPacket(logPacket);

        return ResponseEntity.accepted().build();
    }

    private void processLogPacket(@NonNull LogPacket logPacket) {
        // Update packet counter
        long packetCount = totalPacketsProcessed.incrementAndGet();
        
        // Process each message and update counters
        int messageCount = logPacket.getMessages().size();
        long totalMessages = totalMessagesProcessed.addAndGet(messageCount);
        
        // Count messages by level
        logPacket.getMessages().forEach(msg -> {
            String level = msg.getLevel().name();
            messagesByLevel.computeIfAbsent(level, k -> new AtomicLong(0)).incrementAndGet();
        });
        
        // Count messages by agent
        messagesByAgent.computeIfAbsent(logPacket.getAgentId(), k -> new AtomicLong(0))
                      .addAndGet(messageCount);
        
        // Calculate percentages for debug counts
        long debugCount = logPacket.getMessages().stream()
                .filter(msg -> msg.getLevel().name().equals("DEBUG"))
                .count();
        long errorCount = logPacket.getMessages().stream()
                .filter(msg -> msg.getLevel().name().equals("ERROR"))
                .count();

        // Enhanced logging with cumulative stats
        log.info("Packet {} processed: {} messages (DEBUG: {}, ERROR: {}) | " +
                 "ANALYZER {}: Total Packets: {}, Total Messages: {}", 
                logPacket.getPacketId(), messageCount, debugCount, errorCount,
                analyzerId, packetCount, totalMessages);
    }
}