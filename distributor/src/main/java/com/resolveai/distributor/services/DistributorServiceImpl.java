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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DistributorServiceImpl implements DistributorService {
    
    private final MetricsService metricsService;
    // In-memory queue for packet processing with bounded capacity
    private final BlockingQueue<Runnable> packetQueue = new LinkedBlockingQueue<>(50000);
    private final ExecutorService executorService = new ThreadPoolExecutor(
            50, // Core pool size - high for I/O bound operations
            200, // Max pool size - much higher for HTTP requests  
            30L, TimeUnit.SECONDS, // Keep alive time
            packetQueue, // Work queue
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "packet-processor-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure policy
    );
    
    private final ConcurrentMap<String, AnalyzerInfo> analyzerInfoMap = new ConcurrentHashMap<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong packetsQueued = new AtomicLong(0);
    private final AtomicLong packetsProcessed = new AtomicLong(0);
    private final AtomicLong packetsDropped = new AtomicLong(0);
    private final RestTemplate restTemplate = new RestTemplate();
    
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final Duration OFFLINE_TIMEOUT = Duration.ofSeconds(30);
    
    public DistributorServiceImpl(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostConstruct
    public void init() {
        // Load analyzer configuration from environment variables
        // Format: ANALYZERS_CONFIG=id1:endpoint1:weight1,id2:endpoint2:weight2,...
        String analyzersConfig = System.getenv("ANALYZERS_CONFIG");
        
        if (analyzersConfig != null && !analyzersConfig.trim().isEmpty()) {
            // Parse dynamic configuration
            parseAnalyzersConfig(analyzersConfig);
        } else {
            // Fallback to default configuration
            initDefaultAnalyzers();
        }
        
        log.info("Initialized with {} analyzers", analyzerInfoMap.size());
        
        analyzerInfoMap.forEach((id, info) -> 
                log.info("Analyzer {}: endpoint={}, weight={}", id, info.getEndpoint(), info.getWeight())
        );
    }
    
    private void parseAnalyzersConfig(String config) {
        try {
            String[] analyzerConfigs = config.split(",");
            for (String analyzerConfig : analyzerConfigs) {
                // Split on the last colon to handle URLs with colons
                String trimmedConfig = analyzerConfig.trim();
                int lastColonIndex = trimmedConfig.lastIndexOf(":");
                
                if (lastColonIndex > 0 && lastColonIndex < trimmedConfig.length() - 1) {
                    String idAndEndpoint = trimmedConfig.substring(0, lastColonIndex);
                    String weightStr = trimmedConfig.substring(lastColonIndex + 1).trim();
                    
                    // Split id and endpoint on the first colon
                    int firstColonIndex = idAndEndpoint.indexOf(":");
                    if (firstColonIndex > 0) {
                        String id = idAndEndpoint.substring(0, firstColonIndex).trim();
                        String endpoint = idAndEndpoint.substring(firstColonIndex + 1).trim();
                        double weight = Double.parseDouble(weightStr);
                        
                        addAnalyzer(id, endpoint, weight);
                        log.info("Added analyzer from config: id={}, endpoint={}, weight={}", 
                                id, endpoint, weight);
                    } else {
                        log.warn("Invalid analyzer config format: {}. Cannot find id separator", 
                                analyzerConfig);
                    }
                } else {
                    log.warn("Invalid analyzer config format: {}. Expected format: id:endpoint:weight", 
                            analyzerConfig);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing analyzers configuration: {}. Falling back to defaults.", e.getMessage());
            analyzerInfoMap.clear(); // Clear any partially loaded config
            initDefaultAnalyzers();
        }
    }
    
    private void initDefaultAnalyzers() {
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
        
        log.info("Using default analyzer configuration (docker profile: {})", isDockerProfile);
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
        Instant startTime = Instant.now();
        metricsService.recordPacketReceived();
        
        if (analyzerInfoMap.isEmpty()) {
            log.warn("No analyzers are registered to receive log packets.");
            packetsDropped.incrementAndGet();
            metricsService.recordPacketDropped();
            return;
        }

        AnalyzerInfo targetAnalyzer = findBestAnalyzer(logPacket.getMessages().size());

        if(Objects.isNull(targetAnalyzer)) {
            log.error("Could not find an available analyzer for packet: {}, dropping the packet", logPacket.getPacketId());
            packetsDropped.incrementAndGet();
            metricsService.recordPacketDropped();
            return;
        }
        
        // Queue packet for async processing with backpressure handling
        try {
            packetsQueued.incrementAndGet();
            sendPacketToAnalyzerAsync(logPacket, targetAnalyzer);
            
            // Record processing time and update metrics
            Duration processingTime = Duration.between(startTime, Instant.now());
            metricsService.recordPacketProcessed(processingTime);
            metricsService.updateQueueSize(packetQueue.size());
            
            // Log queue status and distribution periodically
            long queued = packetsQueued.get();
            if (queued % 100 == 0) { // Log every 100 packets
                log.info("Queue status: queued={}, processed={}, dropped={}, queue_size={}", 
                        queued, packetsProcessed.get(), packetsDropped.get(), packetQueue.size());
                logMessageDistribution();
            }
        } catch (RejectedExecutionException e) {
            log.error("Packet {} rejected due to queue overflow. Queue size: {}", 
                    logPacket.getPacketId(), packetQueue.size());
            packetsDropped.incrementAndGet();
            metricsService.recordPacketDropped();
        }
    }

    /**
     * This method chooses the best analyzer based on weighted distribution with aggressive catch-up
     * for severely underutilized analyzers. Uses lock-free algorithm for better concurrency.
     */
    AnalyzerInfo findBestAnalyzer(int packetMessageCount) {
        // Check if any offline analyzers should be reconsidered
        checkForRecoveredAnalyzers();

        long currentTotalMessages = totalMessagesProcessed.get();
        AnalyzerInfo bestCandidate = null;
        double minDeviation = Double.MAX_VALUE;
        AnalyzerInfo mostDeficitAnalyzer = null;
        double maxDeficit = 0.0;
        
        // Debug logging
        log.info("=== DISTRIBUTION DECISION for {} messages (total: {}) ===", 
                packetMessageCount, currentTotalMessages);

        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            String analyzerId = entry.getKey();
            AnalyzerInfo analyzer = entry.getValue();
            
            if (!analyzer.isOnline()) {
                log.debug("SKIPPING offline analyzer: {}", analyzerId);
                continue;
            }
            
            // Current state
            long currentMessages = analyzer.getMessageCount().get();
            double currentIdeal = currentTotalMessages * analyzer.getWeight();
            double currentDeficit = currentIdeal - currentMessages;
            
            // Future state after receiving this packet
            long futureTotal = currentTotalMessages + packetMessageCount;
            double futureIdeal = futureTotal * analyzer.getWeight();
            long futureMessages = currentMessages + packetMessageCount;
            double futureDeviation = Math.abs(futureMessages - futureIdeal);
            
            log.info("Analyzer {}: current={} (ideal={:.0f}, deficit={:.0f}), future={} (ideal={:.0f}, dev={:.0f})",
                    analyzerId, currentMessages, currentIdeal, currentDeficit, 
                    futureMessages, futureIdeal, futureDeviation);
            
            // Track analyzer with biggest deficit for emergency catch-up
            if (currentDeficit > maxDeficit) {
                maxDeficit = currentDeficit;
                mostDeficitAnalyzer = analyzer;
            }
            
            // Normal selection: minimize future deviation
            if (futureDeviation < minDeviation) {
                minDeviation = futureDeviation;
                bestCandidate = analyzer;
            }
        }
        
        // EMERGENCY CATCH-UP: If any analyzer is more than 1000 messages behind ideal, 
        // prioritize it regardless of normal selection
        if (mostDeficitAnalyzer != null && maxDeficit > 1000) {
            log.warn("EMERGENCY CATCH-UP: Selecting analyzer with deficit of {:.0f} messages", maxDeficit);
            bestCandidate = mostDeficitAnalyzer;
        }
        
        String selectedId = "NONE";
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            if (entry.getValue() == bestCandidate) {
                selectedId = entry.getKey();
                break;
            }
        }
        
        log.info("SELECTED: {} (deviation: {:.0f})", selectedId, minDeviation);
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
            Instant requestStart = Instant.now();
            String analyzerId = getAnalyzerId(analyzer);
            
            try {
                log.debug("Processing queued packet {} to analyzer at {} with {} messages",
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
                
                Duration requestDuration = Duration.between(requestStart, Instant.now());
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    // On success, update the message count and reset failure count
                    analyzer.getMessageCount().addAndGet(logPacket.getMessages().size());
                    totalMessagesProcessed.addAndGet(logPacket.getMessages().size());
                    analyzer.getConsecutiveFailures().set(0);
                    packetsProcessed.incrementAndGet();
                    
                    // Record metrics
                    metricsService.recordMessagesSentToAnalyzer(analyzerId, logPacket.getMessages().size());
                    metricsService.recordAnalyzerSuccess(analyzerId, requestDuration);
                    metricsService.recordHttpRequest(requestDuration);
                    
                    if (!analyzer.isOnline()) {
                        log.info("Analyzer {} is back online", analyzer.getEndpoint());
                        analyzer.setOnline(true);
                    }
                    
                    log.debug("Successfully sent packet {} to analyzer {}", 
                            logPacket.getPacketId(), analyzer.getEndpoint());
                } else {
                    log.warn("Analyzer {} returned non-success status: {}", 
                            analyzer.getEndpoint(), response.getStatusCode());
                    packetsDropped.incrementAndGet();
                    metricsService.recordPacketFailed();
                    metricsService.recordAnalyzerFailure(analyzerId, requestDuration);
                    handleAnalyzerFailure(analyzer, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                Duration requestDuration = Duration.between(requestStart, Instant.now());
                log.error("Failed to send packet {} to analyzer {}: {}", 
                        logPacket.getPacketId(), analyzer.getEndpoint(), e.getMessage());
                packetsDropped.incrementAndGet();
                metricsService.recordPacketFailed();
                metricsService.recordAnalyzerFailure(analyzerId, requestDuration);
                handleAnalyzerFailure(analyzer, e.getMessage());
            }
        });
    }
    
    private String getAnalyzerId(AnalyzerInfo analyzer) {
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            if (entry.getValue() == analyzer) {
                return entry.getKey();
            }
        }
        return "unknown";
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
    
    private void logMessageDistribution() {
        long totalMessages = totalMessagesProcessed.get();
        if (totalMessages == 0) return;
        
        StringBuilder distribution = new StringBuilder();
        distribution.append("MESSAGE DISTRIBUTION: ");
        
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            AnalyzerInfo analyzer = entry.getValue();
            long messages = analyzer.getMessageCount().get();
            double percentage = (messages * 100.0) / totalMessages;
            double expectedPercentage = analyzer.getWeight() * 100.0;
            
            distribution.append(String.format("%s: %d msgs (%.1f%%, target: %.1f%%) ", 
                    entry.getKey(), messages, percentage, expectedPercentage));
        }
        
        log.info(distribution.toString());
    }
}
