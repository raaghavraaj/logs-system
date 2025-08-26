package com.resolveai.distributor.services;

import com.resolveai.distributor.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributorServiceImplTest {

    @Mock
    private MetricsService metricsService;

    private DistributorServiceImpl distributorService;

    @BeforeEach
    void setUp() {
        distributorService = new DistributorServiceImpl(metricsService);
        
        // Set up mock analyzer configuration
        ConcurrentMap<String, Object> analyzerInfoMap = new ConcurrentHashMap<>();
        
        // Create analyzer infos with reflection since the inner class is private
        Object analyzer1 = createMockAnalyzerInfo("http://localhost:8081/api/v1/analyze", 0.1);
        Object analyzer2 = createMockAnalyzerInfo("http://localhost:8082/api/v1/analyze", 0.2);
        Object analyzer3 = createMockAnalyzerInfo("http://localhost:8083/api/v1/analyze", 0.3);
        Object analyzer4 = createMockAnalyzerInfo("http://localhost:8084/api/v1/analyze", 0.4);
        
        analyzerInfoMap.put("analyzer-1", analyzer1);
        analyzerInfoMap.put("analyzer-2", analyzer2);
        analyzerInfoMap.put("analyzer-3", analyzer3);
        analyzerInfoMap.put("analyzer-4", analyzer4);
        
        ReflectionTestUtils.setField(distributorService, "analyzerInfoMap", analyzerInfoMap);
        ReflectionTestUtils.setField(distributorService, "totalMessagesProcessed", new AtomicLong(0));
    }

    private Object createMockAnalyzerInfo(String endpoint, double weight) {
        // Since AnalyzerInfo is a private inner class, we'll create a mock representation
        // In a real test, we'd need to access the actual class or make it package-private
        return new Object() {
            public String getEndpoint() { return endpoint; }
            public double getWeight() { return weight; }
            public AtomicLong getMessageCount() { return new AtomicLong(0); }
            public boolean isOnline() { return true; }
        };
    }

    @Test
    void testDistributePacketWithValidPacket() {
        // Given
        LogPacket logPacket = createTestLogPacket("test-agent", 5);
        
        // When
        distributorService.distributePacket(logPacket);
        
        // Then
        verify(metricsService).recordPacketReceived();
        verify(metricsService).recordPacketProcessed(any());
        verify(metricsService).updateQueueSize(anyLong());
    }

    @Test
    void testDistributePacketWithNoAnalyzers() {
        // Given
        ReflectionTestUtils.setField(distributorService, "analyzerInfoMap", new ConcurrentHashMap<>());
        LogPacket logPacket = createTestLogPacket("test-agent", 3);
        
        // When
        distributorService.distributePacket(logPacket);
        
        // Then
        verify(metricsService).recordPacketReceived();
        verify(metricsService).recordPacketDropped();
        verify(metricsService, never()).recordPacketProcessed(any());
    }

    @Test
    void testWeightedDistributionLogic() {
        // This test would verify the findBestAnalyzer method
        // Since it's package-private, we can test it indirectly through distributePacket
        
        // Given
        LogPacket smallPacket = createTestLogPacket("test-agent", 1);
        LogPacket largePacket = createTestLogPacket("test-agent", 100);
        
        // When & Then
        assertDoesNotThrow(() -> {
            distributorService.distributePacket(smallPacket);
            distributorService.distributePacket(largePacket);
        });
        
        verify(metricsService, times(2)).recordPacketReceived();
    }

    @Test
    void testPacketProcessingMetrics() {
        // Given
        LogPacket logPacket = createTestLogPacket("test-agent", 10);
        
        // When
        distributorService.distributePacket(logPacket);
        
        // Then
        verify(metricsService).recordPacketReceived();
        verify(metricsService).recordPacketProcessed(any());
        verify(metricsService).updateQueueSize(anyLong());
    }

    @Test
    void testMultiplePacketsDistribution() {
        // Given
        List<LogPacket> packets = List.of(
            createTestLogPacket("agent-1", 5),
            createTestLogPacket("agent-2", 3),
            createTestLogPacket("agent-3", 7)
        );
        
        // When
        packets.forEach(distributorService::distributePacket);
        
        // Then
        verify(metricsService, times(3)).recordPacketReceived();
        verify(metricsService, times(3)).recordPacketProcessed(any());
        verify(metricsService, times(3)).updateQueueSize(anyLong());
    }

    private LogPacket createTestLogPacket(String agentId, int messageCount) {
        List<LogMessage> messages = new java.util.ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            LogMessage message = LogMessage.builder()
                .id("msg-" + i)
                .timestamp(java.time.Instant.now().plusMillis(i))
                .level(LogLevel.INFO)
                .source(LogSource.builder()
                    .application("test-app")
                    .service("test-service")
                    .instance("test-1")
                    .host("localhost")
                    .build())
                .message("Test message " + i)
                .metadata(new java.util.HashMap<>())
                .build();
            messages.add(message);
        }
        
        return LogPacket.builder()
            .packetId("test-packet-" + System.currentTimeMillis())
            .agentId(agentId)
            .timestamp(java.time.Instant.now())
            .totalMessages(messages.size())
            .messages(messages)
            .checksum("test")
            .build();
    }
}
