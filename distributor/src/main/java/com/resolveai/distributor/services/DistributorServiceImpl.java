package com.resolveai.distributor.services;

import com.resolveai.distributor.models.LogPacket;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DistributorServiceImpl implements DistributorService {
    private final ExecutorService executorService =
            Executors.newFixedThreadPool(4 * Runtime.getRuntime().availableProcessors());
    private final ConcurrentMap<String, AnalyzerInfo> analyzerInfoMap = new ConcurrentHashMap<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);

    @PostConstruct
    public void init() {
        addAnalyzer("analyzer-1", "http://localhost:8081/api/v1/analyze", 0.1);
        addAnalyzer("analyzer-2", "http://localhost:8082/api/v1/analyze", 0.2);
        addAnalyzer("analyzer-3", "http://localhost:8083/api/v1/analyze", 0.3);
        addAnalyzer("analyzer-4", "http://localhost:8084/api/v1/analyze", 0.4);
        log.info("Initialized with {} analyzers.", analyzerInfoMap.size());
    }

    private void addAnalyzer(String id, String endpoint, double weight) {
        analyzerInfoMap.put(id, AnalyzerInfo.builder().endpoint(endpoint).weight(weight).build());
    }

    @Data
    @Builder
    private static class AnalyzerInfo {
        private final String endpoint;
        private final double weight;
        @Builder.Default
        private final AtomicLong messageCount = new AtomicLong(0);

        // Health Management Fields
        @Builder.Default
        private volatile boolean isOnline = true;
        @Builder.Default
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    }

    @Override
    public void distributePacket(LogPacket logPacket) {
        if (analyzerInfoMap.isEmpty()) {
            log.warn("No analyzers are registered to receive log packets.");
            return;
        }

        AnalyzerInfo targetAnalyzer = findBestAnalyzer(logPacket.getMessages().size());

        if(Objects.isNull(targetAnalyzer)) {
            log.error("Could not find an available analyzer for packet: {}, dropping the packet", logPacket.getPacketId());
            return;
        }
        sendPacketToAnalyzerAsync(logPacket, targetAnalyzer);

    }

    /**
     * This method chooses the best analyzer based on the condition that which analyzer would have the messages closest
     * to their actual
     */
    AnalyzerInfo findBestAnalyzer(int packetMessageCount) {
        long currentTotalMessages = totalMessagesProcessed.get();
        AnalyzerInfo bestCandidate = null;
        double minDeviation = Double.MAX_VALUE;

        for (AnalyzerInfo analyzer : analyzerInfoMap.values()) {
            double idealMessageCount = currentTotalMessages * analyzer.getWeight();
            double newDeviation = Math.abs((analyzer.getMessageCount().get() + packetMessageCount) - idealMessageCount);
            if (newDeviation < minDeviation) {
                minDeviation = newDeviation;
                bestCandidate = analyzer;
            }
        }
        return bestCandidate;
    }

    void sendPacketToAnalyzerAsync(LogPacket logPacket, AnalyzerInfo analyzer) {
        executorService.submit(() -> {
            try {
                // Your code to make an HTTP POST request to analyzer.getEndpoint()
                log.info("Sending packet {} to analyzer at {} with {} messages",
                        logPacket.getPacketId(), analyzer.getEndpoint(), logPacket.getMessages().size());

                // On success, update the message count
                analyzer.getMessageCount().addAndGet(logPacket.getMessages().size());
                totalMessagesProcessed.addAndGet(logPacket.getMessages().size());
            } catch (Exception e) {
                log.error("Failed to send packet {} to analyzer {}: {}", logPacket.getPacketId(), analyzer.getEndpoint(), e.getMessage());
            }
        });
    }
}
