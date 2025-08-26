package com.resolveai.distributor.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServiceTest {

    private MetricsService metricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void testRecordPacketReceived() {
        // Given
        double initialCount = getCounterValue("distributor.packets.received");
        
        // When
        metricsService.recordPacketReceived();
        
        // Then
        double finalCount = getCounterValue("distributor.packets.received");
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testRecordPacketProcessed() {
        // Given
        Duration processingTime = Duration.ofMillis(50);
        double initialCount = getCounterValue("distributor.packets.processed");
        
        // When
        metricsService.recordPacketProcessed(processingTime);
        
        // Then
        double finalCount = getCounterValue("distributor.packets.processed");
        assertEquals(initialCount + 1, finalCount);
        
        Timer timer = meterRegistry.find("distributor.packet.processing.time").timer();
        assertNotNull(timer);
        assertTrue(timer.count() > 0);
    }

    @Test
    void testRecordPacketDropped() {
        // Given
        double initialCount = getCounterValue("distributor.packets.dropped");
        
        // When
        metricsService.recordPacketDropped();
        
        // Then
        double finalCount = getCounterValue("distributor.packets.dropped");
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testRecordPacketFailed() {
        // Given
        double initialCount = getCounterValue("distributor.packets.failed");
        
        // When
        metricsService.recordPacketFailed();
        
        // Then
        double finalCount = getCounterValue("distributor.packets.failed");
        assertEquals(initialCount + 1, finalCount);
    }

    @Test
    void testRecordMessagesSentToAnalyzer() {
        // Given
        String analyzerId = "test-analyzer";
        int messageCount = 5;
        
        // When
        metricsService.recordMessagesSentToAnalyzer(analyzerId, messageCount);
        
        // Then
        Counter messagesCounter = meterRegistry.find("distributor.messages.per.analyzer")
                .tag("analyzer", analyzerId)
                .counter();
        assertNotNull(messagesCounter);
        assertEquals(messageCount, messagesCounter.count());
        
        Counter packetsCounter = meterRegistry.find("distributor.packets.per.analyzer")
                .tag("analyzer", analyzerId)
                .counter();
        assertNotNull(packetsCounter);
        assertEquals(1, packetsCounter.count());
    }

    @Test
    void testRecordAnalyzerFailure() {
        // Given
        String analyzerId = "test-analyzer";
        Duration requestTime = Duration.ofMillis(100);
        
        // When
        metricsService.recordAnalyzerFailure(analyzerId, requestTime);
        
        // Then
        Counter failuresCounter = meterRegistry.find("distributor.failures.per.analyzer")
                .tag("analyzer", analyzerId)
                .counter();
        assertNotNull(failuresCounter);
        assertEquals(1, failuresCounter.count());
        
        Timer latencyTimer = meterRegistry.find("distributor.latency.per.analyzer")
                .tag("analyzer", analyzerId)
                .timer();
        assertNotNull(latencyTimer);
        assertTrue(latencyTimer.count() > 0);
    }

    @Test
    void testRecordAnalyzerSuccess() {
        // Given
        String analyzerId = "test-analyzer";
        Duration requestTime = Duration.ofMillis(50);
        
        // When
        metricsService.recordAnalyzerSuccess(analyzerId, requestTime);
        
        // Then
        Timer latencyTimer = meterRegistry.find("distributor.latency.per.analyzer")
                .tag("analyzer", analyzerId)
                .timer();
        assertNotNull(latencyTimer);
        assertTrue(latencyTimer.count() > 0);
        assertTrue(latencyTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void testUpdateQueueSize() {
        // Given
        long queueSize = 150;
        
        // When
        metricsService.updateQueueSize(queueSize);
        
        // Then
        assertEquals(queueSize, metricsService.getCurrentQueueSize());
    }

    @Test
    void testGetPacketsPerSecond() {
        // Given
        metricsService.recordPacketReceived();
        metricsService.recordPacketProcessed(Duration.ofMillis(10));
        
        // When
        double packetsPerSec = metricsService.getPacketsPerSecond();
        
        // Then
        assertTrue(packetsPerSec >= 0);
    }

    @Test
    void testGetMessagesPerSecond() {
        // Given
        metricsService.recordMessagesSentToAnalyzer("test", 10);
        
        // When
        double messagesPerSec = metricsService.getMessagesPerSecond();
        
        // Then
        assertTrue(messagesPerSec >= 0);
    }

    @Test
    void testGetErrorRate() {
        // Given - record some successes and failures
        metricsService.recordPacketReceived();
        metricsService.recordPacketReceived();
        metricsService.recordPacketFailed();
        
        // When
        double errorRate = metricsService.getErrorRate();
        
        // Then
        assertTrue(errorRate >= 0 && errorRate <= 1);
        assertEquals(0.5, errorRate, 0.01); // 1 failure out of 2 total = 50%
    }

    @Test
    void testGetErrorRateWithNoPackets() {
        // When
        double errorRate = metricsService.getErrorRate();
        
        // Then
        assertEquals(0.0, errorRate);
    }

    private double getCounterValue(String meterName) {
        Counter counter = meterRegistry.find(meterName).counter();
        return counter != null ? counter.count() : 0.0;
    }
}
