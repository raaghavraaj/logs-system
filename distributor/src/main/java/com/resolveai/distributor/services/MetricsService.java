package com.resolveai.distributor.services;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Distribution Metrics
    private final Counter packetsReceived;
    private final Counter packetsProcessed;
    private final Counter packetsDropped;
    private final Counter packetsFailedToSend;
    private final Timer packetProcessingTime;
    private final Timer httpRequestTime;
    private final Gauge queueSize;
    private final Gauge totalMessagesProcessed;
    
    // Per-analyzer metrics
    private final ConcurrentMap<String, Counter> messagesPerAnalyzer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> packetsPerAnalyzer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> failuresPerAnalyzer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> latencyPerAnalyzer = new ConcurrentHashMap<>();
    
    // System performance metrics
    private final AtomicLong currentQueueSize = new AtomicLong(0);
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize core metrics
        this.packetsReceived = Counter.builder("distributor.packets.received")
                .description("Total number of log packets received")
                .register(meterRegistry);
                
        this.packetsProcessed = Counter.builder("distributor.packets.processed")
                .description("Total number of packets successfully processed")
                .register(meterRegistry);
                
        this.packetsDropped = Counter.builder("distributor.packets.dropped")
                .description("Total number of packets dropped due to queue overflow")
                .register(meterRegistry);
                
        this.packetsFailedToSend = Counter.builder("distributor.packets.failed")
                .description("Total number of packets that failed to send to analyzers")
                .register(meterRegistry);
                
        this.packetProcessingTime = Timer.builder("distributor.packet.processing.time")
                .description("Time taken to process and route a packet")
                .register(meterRegistry);
                
        this.httpRequestTime = Timer.builder("distributor.http.request.time")
                .description("Time taken for HTTP requests to analyzers")
                .register(meterRegistry);
                
        // Register gauges using a different approach
        Gauge.builder("distributor.queue.size", currentQueueSize, AtomicLong::get)
                .description("Current queue size")
                .register(meterRegistry);
                
        Gauge.builder("distributor.messages.total", totalMessages, AtomicLong::get)
                .description("Total messages processed across all analyzers")
                .register(meterRegistry);
                
        // These are placeholders to keep the fields for compatibility
        this.queueSize = null;
        this.totalMessagesProcessed = null;
    }
    
    // Packet metrics
    public void recordPacketReceived() {
        packetsReceived.increment();
    }
    
    public void recordPacketProcessed(Duration processingTime) {
        packetsProcessed.increment();
        packetProcessingTime.record(processingTime);
    }
    
    public void recordPacketDropped() {
        packetsDropped.increment();
    }
    
    public void recordPacketFailed() {
        packetsFailedToSend.increment();
    }
    
    public void recordHttpRequest(Duration requestTime) {
        httpRequestTime.record(requestTime);
    }
    
    // Analyzer-specific metrics
    public void recordMessagesSentToAnalyzer(String analyzerId, int messageCount) {
        messagesPerAnalyzer.computeIfAbsent(analyzerId, id ->
                Counter.builder("distributor.messages.per.analyzer")
                        .description("Messages sent to specific analyzer")
                        .tag("analyzer", id)
                        .register(meterRegistry))
                .increment(messageCount);
                
        packetsPerAnalyzer.computeIfAbsent(analyzerId, id ->
                Counter.builder("distributor.packets.per.analyzer")
                        .description("Packets sent to specific analyzer")
                        .tag("analyzer", id)
                        .register(meterRegistry))
                .increment();
                
        totalMessages.addAndGet(messageCount);
    }
    
    public void recordAnalyzerFailure(String analyzerId, Duration requestTime) {
        failuresPerAnalyzer.computeIfAbsent(analyzerId, id ->
                Counter.builder("distributor.failures.per.analyzer")
                        .description("Failures for specific analyzer")
                        .tag("analyzer", id)
                        .register(meterRegistry))
                .increment();
                
        latencyPerAnalyzer.computeIfAbsent(analyzerId, id ->
                Timer.builder("distributor.latency.per.analyzer")
                        .description("Request latency for specific analyzer")
                        .tag("analyzer", id)
                        .register(meterRegistry))
                .record(requestTime);
    }
    
    public void recordAnalyzerSuccess(String analyzerId, Duration requestTime) {
        latencyPerAnalyzer.computeIfAbsent(analyzerId, id ->
                Timer.builder("distributor.latency.per.analyzer")
                        .description("Request latency for specific analyzer")
                        .tag("analyzer", id)
                        .register(meterRegistry))
                .record(requestTime);
    }
    
    // Queue metrics
    public void updateQueueSize(long size) {
        currentQueueSize.set(size);
    }
    
    // Getter methods for gauges
    public double getCurrentQueueSize() {
        return currentQueueSize.get();
    }
    
    public double getTotalMessages() {
        return totalMessages.get();
    }
    
    // System metrics
    public double getPacketsPerSecond() {
        long uptimeMs = System.currentTimeMillis() - startTime.get();
        if (uptimeMs == 0) return 0.0;
        return (packetsProcessed.count() * 1000.0) / uptimeMs;
    }
    
    public double getMessagesPerSecond() {
        long uptimeMs = System.currentTimeMillis() - startTime.get();
        if (uptimeMs == 0) return 0.0;
        return (totalMessages.get() * 1000.0) / uptimeMs;
    }
    
    public double getErrorRate() {
        double total = packetsReceived.count();
        if (total == 0) return 0.0;
        return (packetsFailedToSend.count() + packetsDropped.count()) / total;
    }
}
