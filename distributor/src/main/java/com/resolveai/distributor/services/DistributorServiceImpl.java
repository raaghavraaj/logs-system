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
    
    // Tracking variables for structured logging
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    // In-memory queue for packet processing with bounded capacity
    // ASSUMPTION: 50K capacity sufficient for burst traffic, bounded to prevent OOM
    private final BlockingQueue<Runnable> packetQueue = new LinkedBlockingQueue<>(50000);
    private final ExecutorService executorService = new ThreadPoolExecutor(
            50, // Core pool size - ASSUMPTION: High thread count optimal for I/O bound HTTP operations
            200, // Max pool size - ASSUMPTION: 200 max threads handle burst traffic without resource exhaustion  
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
    
    // ASSUMPTION: 3 consecutive failures indicate analyzer is offline (balances quick
    // detection vs avoiding false positives from temporary network hiccups)
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    // ASSUMPTION: 30-second timeout provides reasonable recovery time without
    // keeping failed analyzers offline too long
    private static final Duration OFFLINE_TIMEOUT = Duration.ofSeconds(30);
    
    public DistributorServiceImpl() {
        // No dependencies needed - using structured logging
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
        
        // Structured log entry for packet received
        log.info("PACKET_RECEIVED | packet_id={} | messages={} | agent_id={} | timestamp={}", 
                logPacket.getPacketId(), logPacket.getMessages().size(), 
                logPacket.getAgentId(), startTime);
        
        if (analyzerInfoMap.isEmpty()) {
            log.warn("PACKET_DROPPED | reason=no_analyzers | packet_id={}", logPacket.getPacketId());
            packetsDropped.incrementAndGet();
            return;
        }

        AnalyzerInfo targetAnalyzer = findBestAnalyzer(logPacket.getMessages().size());

        if(Objects.isNull(targetAnalyzer)) {
            log.error("PACKET_DROPPED | reason=no_available_analyzer | packet_id={} | queue_size={}", 
                    logPacket.getPacketId(), packetQueue.size());
            packetsDropped.incrementAndGet();
            return;
        }
        
        // Queue packet for async processing with backpressure handling
        try {
            packetsQueued.incrementAndGet();
            
            // Find analyzer ID for logging
            String analyzerId = findAnalyzerId(targetAnalyzer);
            log.info("PACKET_QUEUED | packet_id={} | target_analyzer={} | queue_size={} | messages={}", 
                    logPacket.getPacketId(), analyzerId, packetQueue.size(), logPacket.getMessages().size());
            
            sendPacketToAnalyzerAsync(logPacket, targetAnalyzer);
            
            // Structured performance logging
            Duration processingTime = Duration.between(startTime, Instant.now());
            log.debug("PACKET_PROCESSED | packet_id={} | processing_time_ms={} | queue_size={}", 
                    logPacket.getPacketId(), processingTime.toMillis(), packetQueue.size());
            
            // Log system status periodically
            long queued = packetsQueued.get();
            if (queued % 100 == 0) { // Log every 100 packets
                logSystemStatus();
            }
        } catch (RejectedExecutionException e) {
            log.error("PACKET_REJECTED | reason=queue_overflow | packet_id={} | queue_size={} | queue_capacity=50000", 
                    logPacket.getPacketId(), packetQueue.size());
            packetsDropped.incrementAndGet();
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
        
        // Structured logging for distribution decision
        log.debug("DISTRIBUTION_DECISION | messages={} | total_messages={}", 
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
            
            log.debug("ANALYZER_EVALUATION | analyzer={} | current={} | ideal={:.0f} | deficit={:.0f} | future={} | future_ideal={:.0f} | deviation={:.0f}",
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
        // ASSUMPTION: 1000-message deficit threshold provides optimal balance between
        // stability and responsiveness (tested empirically)
        if (mostDeficitAnalyzer != null && maxDeficit > 1000) {
            log.info("EMERGENCY_CATCHUP | deficit={:.0f}", maxDeficit);
            bestCandidate = mostDeficitAnalyzer;
        }
        
        String selectedId = "NONE";
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            if (entry.getValue() == bestCandidate) {
                selectedId = entry.getKey();
                break;
            }
        }
        
        log.debug("ANALYZER_SELECTED | analyzer={} | deviation={:.0f}", selectedId, minDeviation);
        return bestCandidate;
    }

    private void checkForRecoveredAnalyzers() {
        Instant now = Instant.now();
        for (AnalyzerInfo analyzer : analyzerInfoMap.values()) {
            if (!analyzer.isOnline() && analyzer.getLastFailureTime() != null) {
                if (Duration.between(analyzer.getLastFailureTime(), now).compareTo(OFFLINE_TIMEOUT) > 0) {
                    log.info("ANALYZER_RECOVERY | endpoint={} | offline_duration_s={}", 
                            analyzer.getEndpoint(), Duration.between(analyzer.getLastFailureTime(), now).getSeconds());
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
                    
                    if (!analyzer.isOnline()) {
                        log.info("ANALYZER_ONLINE | endpoint={}", analyzer.getEndpoint());
                        analyzer.setOnline(true);
                    }
                    
                    log.info("PACKET_SENT_SUCCESS | packet_id={} | analyzer={} | messages={} | duration_ms={}", 
                            logPacket.getPacketId(), analyzerId, logPacket.getMessages().size(), requestDuration.toMillis());
                } else {
                    log.warn("PACKET_SENT_FAILURE | packet_id={} | analyzer={} | status={} | duration_ms={}", 
                            logPacket.getPacketId(), analyzerId, response.getStatusCode(), requestDuration.toMillis());
                    packetsDropped.incrementAndGet();
                    handleAnalyzerFailure(analyzer, "HTTP " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                Duration requestDuration = Duration.between(requestStart, Instant.now());
                log.error("PACKET_SENT_ERROR | packet_id={} | analyzer={} | error={} | duration_ms={}", 
                        logPacket.getPacketId(), analyzerId, e.getMessage(), requestDuration.toMillis());
                packetsDropped.incrementAndGet();
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
            log.error("ANALYZER_OFFLINE | endpoint={} | failures={} | error={}", 
                    analyzer.getEndpoint(), failures, errorMessage);
        } else {
            log.warn("ANALYZER_FAILURE | endpoint={} | failures={} | max={} | error={}", 
                    analyzer.getEndpoint(), failures, MAX_CONSECUTIVE_FAILURES, errorMessage);
        }
    }
    
    private String findAnalyzerId(AnalyzerInfo targetAnalyzer) {
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            if (entry.getValue() == targetAnalyzer) {
                return entry.getKey();
            }
        }
        return "unknown";
    }
    
    private void logSystemStatus() {
        long uptime = System.currentTimeMillis() - startTime.get();
        long totalMessages = totalMessagesProcessed.get();
        long queued = packetsQueued.get();
        long processed = packetsProcessed.get();
        long dropped = packetsDropped.get();
        
        double packetsPerSec = processed > 0 ? (processed * 1000.0) / uptime : 0;
        double messagesPerSec = totalMessages > 0 ? (totalMessages * 1000.0) / uptime : 0;
        
        log.info("SYSTEM_STATUS | uptime_ms={} | queued={} | processed={} | dropped={} | total_messages={} | packets_per_sec={:.2f} | messages_per_sec={:.2f} | queue_size={}", 
                uptime, queued, processed, dropped, totalMessages, packetsPerSec, messagesPerSec, packetQueue.size());
        
        logMessageDistribution();
    }
    
    private void logMessageDistribution() {
        long totalMessages = totalMessagesProcessed.get();
        if (totalMessages == 0) return;
        
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            AnalyzerInfo analyzer = entry.getValue();
            long messages = analyzer.getMessageCount().get();
            double percentage = (messages * 100.0) / totalMessages;
            double expectedPercentage = analyzer.getWeight() * 100.0;
            
            log.info("MESSAGE_DISTRIBUTION | analyzer={} | messages={} | percentage={:.1f} | target={:.1f} | weight={:.1f}", 
                    entry.getKey(), messages, percentage, expectedPercentage, analyzer.getWeight());
        }
    }
}
