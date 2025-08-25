package com.resolveai.distributor.services;

import com.resolveai.distributor.models.LogPacket;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
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
    private final RestTemplate restTemplate = new RestTemplate();
    
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final Duration OFFLINE_TIMEOUT = Duration.ofSeconds(30);

    @PostConstruct
    public void init() {
        // Use Docker container names when running in Docker, localhost otherwise
        String profilesActive = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profilesActive == null) {
            profilesActive = System.getProperty("spring.profiles.active", "");
        }
        boolean isDockerProfile = profilesActive != null && profilesActive.contains("docker");
        
        if (isDockerProfile) {
            // Docker container hostnames
            addAnalyzer("analyzer-1", "http://analyzer-1:8080/api/v1/analyze", 0.1);
            addAnalyzer("analyzer-2", "http://analyzer-2:8080/api/v1/analyze", 0.2);
            addAnalyzer("analyzer-3", "http://analyzer-3:8080/api/v1/analyze", 0.3);
            addAnalyzer("analyzer-4", "http://analyzer-4:8080/api/v1/analyze", 0.4);
        } else {
            // Local development hostnames
            addAnalyzer("analyzer-1", "http://localhost:8081/api/v1/analyze", 0.1);
            addAnalyzer("analyzer-2", "http://localhost:8082/api/v1/analyze", 0.2);
            addAnalyzer("analyzer-3", "http://localhost:8083/api/v1/analyze", 0.3);
            addAnalyzer("analyzer-4", "http://localhost:8084/api/v1/analyze", 0.4);
        }
        
        log.info("Initialized with {} analyzers (docker profile: {})", 
                analyzerInfoMap.size(), isDockerProfile);
        
        analyzerInfoMap.forEach((id, info) -> 
                log.info("Analyzer {}: endpoint={}, weight={}", id, info.getEndpoint(), info.getWeight())
        );
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
        @Builder.Default
        private volatile Instant lastFailureTime = null;
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
     * to their actual weight distribution, considering only online analyzers
     */
    synchronized AnalyzerInfo findBestAnalyzer(int packetMessageCount) {
        // Check if any offline analyzers should be reconsidered
        checkForRecoveredAnalyzers();

        // Calculate current total that will exist AFTER we send this packet
        long currentTotalMessages = totalMessagesProcessed.get() + packetMessageCount;
        AnalyzerInfo bestCandidate = null;
        double minDeviation = Double.MAX_VALUE;
        
        // Debug logging for distribution analysis
        log.debug("Finding best analyzer for {} messages. Current total will be: {}", 
                packetMessageCount, currentTotalMessages);

        for (AnalyzerInfo analyzer : analyzerInfoMap.values()) {
            if (!analyzer.isOnline()) {
                continue; // Skip offline analyzers
            }
            
            // Calculate ideal message count for this analyzer based on future total
            double idealMessageCount = currentTotalMessages * analyzer.getWeight();
            
            // Calculate what this analyzer would have AFTER receiving this packet
            long analyzerFutureCount = analyzer.getMessageCount().get() + packetMessageCount;
            
            // Calculate deviation from ideal distribution
            double deviation = Math.abs(analyzerFutureCount - idealMessageCount);
            
            log.debug("Analyzer {}: current={}, future={}, ideal={:.1f}, deviation={:.1f}", 
                    analyzer.getEndpoint(), analyzer.getMessageCount().get(), 
                    analyzerFutureCount, idealMessageCount, deviation);
            
            if (deviation < minDeviation || (deviation == minDeviation && bestCandidate == null)) {
                minDeviation = deviation;
                bestCandidate = analyzer;
            }
        }
        
        if (bestCandidate != null) {
            log.debug("Selected analyzer: {} with deviation {:.1f}", 
                    bestCandidate.getEndpoint(), minDeviation);
        }
        
        return bestCandidate;
    }

    private void checkForRecoveredAnalyzers() {
        Instant now = Instant.now();
        for (AnalyzerInfo analyzer : analyzerInfoMap.values()) {
            if (!analyzer.isOnline() && analyzer.getLastFailureTime() != null) {
                if (Duration.between(analyzer.getLastFailureTime(), now).compareTo(OFFLINE_TIMEOUT) > 0) {
                    log.info("Attempting to bring analyzer {} back online after timeout", analyzer.getEndpoint());
                    analyzer.setOnline(true);
                    analyzer.getConsecutiveFailures().set(0);
                }
            }
        }
    }

    void sendPacketToAnalyzerAsync(LogPacket logPacket, AnalyzerInfo analyzer) {
        executorService.submit(() -> {
            try {
                log.debug("Sending packet {} to analyzer at {} with {} messages",
                        logPacket.getPacketId(), analyzer.getEndpoint(), logPacket.getMessages().size());
                
                // Prepare HTTP request
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<LogPacket> request = new HttpEntity<>(logPacket, headers);
                
                // Make the HTTP POST request
                ResponseEntity<Void> response = restTemplate.postForEntity(
                    analyzer.getEndpoint(), 
                    request, 
                    Void.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    // On success, update the message count and reset failure count
                    analyzer.getMessageCount().addAndGet(logPacket.getMessages().size());
                    totalMessagesProcessed.addAndGet(logPacket.getMessages().size());
                    analyzer.getConsecutiveFailures().set(0);
                    
                    if (!analyzer.isOnline()) {
                        log.info("Analyzer {} is back online", analyzer.getEndpoint());
                        analyzer.setOnline(true);
                    }
                    
                    log.debug("Successfully sent packet {} to analyzer {}", 
                            logPacket.getPacketId(), analyzer.getEndpoint());
                } else {
                    log.warn("Analyzer {} returned non-success status: {}", 
                            analyzer.getEndpoint(), response.getStatusCode());
                    handleAnalyzerFailure(analyzer, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to send packet {} to analyzer {}: {}", 
                        logPacket.getPacketId(), analyzer.getEndpoint(), e.getMessage());
                handleAnalyzerFailure(analyzer, e.getMessage());
            }
        });
    }
    
    private void handleAnalyzerFailure(AnalyzerInfo analyzer, String errorMessage) {
        int failures = analyzer.getConsecutiveFailures().incrementAndGet();
        analyzer.setLastFailureTime(Instant.now());
        
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            analyzer.setOnline(false);
            log.error("Marking analyzer {} as offline after {} consecutive failures. Last error: {}", 
                    analyzer.getEndpoint(), failures, errorMessage);
        } else {
            log.warn("Analyzer {} failed ({}/{}): {}", 
                    analyzer.getEndpoint(), failures, MAX_CONSECUTIVE_FAILURES, errorMessage);
        }
    }
}
