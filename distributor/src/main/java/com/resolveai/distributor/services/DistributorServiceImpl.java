package com.resolveai.distributor.services;

import com.resolveai.distributor.models.LogPacket;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
public class DistributorServiceImpl implements DistributorService {
    
    // 🚀 HIGH-PERFORMANCE COUNTERS - Using LongAdder for reduced contention
    private final LongAdder startTime = new LongAdder();
    private final LongAdder totalMessagesProcessed = new LongAdder();
    private final LongAdder packetsQueued = new LongAdder();
    private final LongAdder packetsProcessed = new LongAdder();
    private final LongAdder packetsDropped = new LongAdder();
    
    // 🚀 OPTIMIZED THREAD POOL - Smaller pool for async HTTP operations
    private final ExecutorService executorService = new ThreadPoolExecutor(
            20, // Core pool size - REDUCED for async HTTP operations
            50, // Max pool size - REDUCED since HTTP calls don't block threads
            60L, TimeUnit.SECONDS, // Longer keep alive for stability
            new LinkedBlockingQueue<>(10000), // Smaller queue for better backpressure
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "async-packet-processor-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    
    // 🚀 JAVA 11+ HTTP CLIENT - Async, connection pooling, HTTP/2 support
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(ForkJoinPool.commonPool()) // Use ForkJoinPool for HTTP operations
            .build();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, AnalyzerInfo> analyzerInfoMap = new ConcurrentHashMap<>();
    
    // Failure handling constants
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final Duration OFFLINE_TIMEOUT = Duration.ofSeconds(30);
    
    public DistributorServiceImpl() {
        startTime.add(System.currentTimeMillis());
    }

    @PostConstruct
    public void init() {
        // Load analyzer configuration
        String analyzersConfig = System.getenv("ANALYZERS_CONFIG");
        
        if (analyzersConfig != null && !analyzersConfig.trim().isEmpty()) {
            parseAnalyzersConfig(analyzersConfig);
        } else {
            initDefaultAnalyzers();
        }
        
        log.info("🚀 HIGH-PERFORMANCE DISTRIBUTOR INITIALIZED | analyzers={} | http_client=Java11+ | threads=20-50", 
                analyzerInfoMap.size());
    }
    
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void distributePacket(LogPacket logPacket) {
        Instant startTime = Instant.now();
        
        // 🚀 MINIMAL LOGGING in hot path - only packet ID for tracing
        if (log.isDebugEnabled()) {
            log.debug("PACKET_RECV | id={} | msgs={}", 
                    logPacket.getPacketId(), logPacket.getMessages().size());
        }
        
        if (analyzerInfoMap.isEmpty()) {
            log.warn("PACKET_DROPPED | reason=no_analyzers | id={}", logPacket.getPacketId());
            packetsDropped.increment();
            return;
        }

        AnalyzerInfo targetAnalyzer = findBestAnalyzerOptimized(logPacket.getMessages().size());

        if (Objects.isNull(targetAnalyzer)) {
            log.error("PACKET_DROPPED | reason=no_available | id={}", logPacket.getPacketId());
            packetsDropped.increment();
            return;
        }
        
        // 🚀 ASYNC PROCESSING with CompletableFuture
        packetsQueued.increment();
        sendPacketAsyncOptimized(logPacket, targetAnalyzer);
        
        // 🚀 REDUCED logging frequency - every 1000 packets instead of 100
        if (packetsQueued.sum() % 1000 == 0) {
            logSystemStatus();
        }
    }

    /**
     * 🚀 OPTIMIZED ANALYZER SELECTION - Reduced overhead, no per-packet health checks
     */
    private AnalyzerInfo findBestAnalyzerOptimized(int packetMessageCount) {
        long currentTotalMessages = totalMessagesProcessed.sum();
        AnalyzerInfo bestCandidate = null;
        double minDeviation = Double.MAX_VALUE;
        AnalyzerInfo mostDeficitAnalyzer = null;
        double maxDeficit = 0.0;

        // 🚀 OPTIMIZED ITERATION - Only check online analyzers
        for (AnalyzerInfo analyzer : analyzerInfoMap.values()) {
            if (!analyzer.isOnline()) {
                continue; // Skip offline analyzers quickly
            }
            
            // Current state calculations
            long currentMessages = analyzer.getMessageCount().sum();
            double currentIdeal = currentTotalMessages * analyzer.getWeight();
            double currentDeficit = currentIdeal - currentMessages;
            
            // Future state calculations  
            long futureTotal = currentTotalMessages + packetMessageCount;
            double futureIdeal = futureTotal * analyzer.getWeight();
            long futureMessages = currentMessages + packetMessageCount;
            double futureDeviation = Math.abs(futureMessages - futureIdeal);
            
            // Track maximum deficit for emergency catch-up
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
        
        // Emergency catch-up logic (same threshold)
        if (mostDeficitAnalyzer != null && maxDeficit > 1000) {
            return mostDeficitAnalyzer;
        }
        
        return bestCandidate;
    }

    /**
     * 🚀 ASYNC HTTP with Java 11+ HttpClient and CompletableFuture
     */
    private void sendPacketAsyncOptimized(LogPacket logPacket, AnalyzerInfo analyzer) {
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    // Serialize JSON
                    String jsonBody = objectMapper.writeValueAsString(logPacket);
                    
                    // Build HTTP request
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(analyzer.getEndpoint()))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();
                    
                    return request;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to prepare request", e);
                }
            }, executorService)
            .thenCompose(request -> 
                // 🚀 ASYNC HTTP CALL - Non-blocking!
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            )
            .thenAccept(response -> {
                // 🚀 SUCCESS HANDLING
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    // Update counters with LongAdder
                    analyzer.getMessageCount().add(logPacket.getMessages().size());
                    totalMessagesProcessed.add(logPacket.getMessages().size());
                    analyzer.getConsecutiveFailures().set(0);
                    packetsProcessed.increment();
                    
                    if (!analyzer.isOnline()) {
                        log.info("ANALYZER_RECOVERED | endpoint={}", analyzer.getEndpoint());
                        analyzer.setOnline(true);
                    }
                    
                    // 🚀 MINIMAL SUCCESS LOGGING
                    if (log.isDebugEnabled()) {
                        log.debug("PACKET_SUCCESS | id={} | analyzer={} | status={}", 
                                logPacket.getPacketId(), getAnalyzerId(analyzer), response.statusCode());
                    }
                } else {
                    log.warn("PACKET_HTTP_ERROR | id={} | status={}", 
                            logPacket.getPacketId(), response.statusCode());
                    packetsDropped.increment();
                    handleAnalyzerFailure(analyzer, "HTTP " + response.statusCode());
                }
            })
            .exceptionally(throwable -> {
                // 🚀 EXCEPTION HANDLING
                log.error("PACKET_EXCEPTION | id={} | error={}", 
                        logPacket.getPacketId(), throwable.getMessage());
                packetsDropped.increment();
                handleAnalyzerFailure(analyzer, throwable.getMessage());
                return null;
            });
    }

    /**
     * 🚀 SCHEDULED HEALTH CHECKS - Moved out of hot path!
     */
    @Scheduled(fixedRate = 5000) // Every 5 seconds instead of per-packet
    public void checkAnalyzerHealth() {
        Instant now = Instant.now();
        int recoveredCount = 0;
        
        for (Map.Entry<String, AnalyzerInfo> entry : analyzerInfoMap.entrySet()) {
            AnalyzerInfo analyzer = entry.getValue();
            
            if (!analyzer.isOnline() && analyzer.getLastFailureTime() != null) {
                if (Duration.between(analyzer.getLastFailureTime(), now).compareTo(OFFLINE_TIMEOUT) > 0) {
                    analyzer.setOnline(true);
                    analyzer.getConsecutiveFailures().set(0);
                    recoveredCount++;
                    
                    log.info("SCHEDULED_RECOVERY | analyzer={} | offline_duration_s={}", 
                            entry.getKey(), Duration.between(analyzer.getLastFailureTime(), now).getSeconds());
                }
            }
        }
        
        if (recoveredCount > 0) {
            log.info("HEALTH_CHECK_COMPLETE | recovered_analyzers={}", recoveredCount);
        }
    }

    // Helper methods (optimized versions of existing code)
    
    private void parseAnalyzersConfig(String config) {
        try {
            String[] analyzerConfigs = config.split(",");
            for (String analyzerConfig : analyzerConfigs) {
                String trimmedConfig = analyzerConfig.trim();
                int lastColonIndex = trimmedConfig.lastIndexOf(":");
                
                if (lastColonIndex > 0 && lastColonIndex < trimmedConfig.length() - 1) {
                    String idAndEndpoint = trimmedConfig.substring(0, lastColonIndex);
                    String weightStr = trimmedConfig.substring(lastColonIndex + 1).trim();
                    
                    int firstColonIndex = idAndEndpoint.indexOf(":");
                    if (firstColonIndex > 0) {
                        String id = idAndEndpoint.substring(0, firstColonIndex).trim();
                        String endpoint = idAndEndpoint.substring(firstColonIndex + 1).trim();
                        double weight = Double.parseDouble(weightStr);
                        
                        addAnalyzer(id, endpoint, weight);
                        log.info("ANALYZER_CONFIG | id={} | endpoint={} | weight={}", id, endpoint, weight);
                    }
                }
            }
        } catch (Exception e) {
            log.error("CONFIG_PARSE_ERROR | error={}", e.getMessage());
            analyzerInfoMap.clear();
            initDefaultAnalyzers();
        }
    }
    
    private void initDefaultAnalyzers() {
        String profilesActive = System.getenv("SPRING_PROFILES_ACTIVE");
        boolean isDockerProfile = profilesActive != null && profilesActive.contains("docker");
        
        if (isDockerProfile) {
            addAnalyzer("analyzer-1", "http://analyzer-1:8080/api/v1/analyze", 0.1);
            addAnalyzer("analyzer-2", "http://analyzer-2:8080/api/v1/analyze", 0.2);
            addAnalyzer("analyzer-3", "http://analyzer-3:8080/api/v1/analyze", 0.3);
            addAnalyzer("analyzer-4", "http://analyzer-4:8080/api/v1/analyze", 0.4);
        } else {
            addAnalyzer("analyzer-1", "http://localhost:8081/api/v1/analyze", 0.1);
            addAnalyzer("analyzer-2", "http://localhost:8082/api/v1/analyze", 0.2);
            addAnalyzer("analyzer-3", "http://localhost:8083/api/v1/analyze", 0.3);
            addAnalyzer("analyzer-4", "http://localhost:8084/api/v1/analyze", 0.4);
        }
        
        log.info("DEFAULT_ANALYZER_CONFIG | docker_profile={}", isDockerProfile);
    }

    private void addAnalyzer(String id, String endpoint, double weight) {
        analyzerInfoMap.put(id, AnalyzerInfo.builder()
                .endpoint(endpoint)
                .weight(weight)
                .build());
    }

    private void handleAnalyzerFailure(AnalyzerInfo analyzer, String errorMessage) {
        int failures = analyzer.getConsecutiveFailures().incrementAndGet();
        analyzer.setLastFailureTime(Instant.now());
        
        if (failures >= MAX_CONSECUTIVE_FAILURES && analyzer.isOnline()) {
            analyzer.setOnline(false);
            log.error("ANALYZER_OFFLINE | endpoint={} | failures={} | error={}", 
                    analyzer.getEndpoint(), failures, errorMessage);
        }
    }
    
    private String getAnalyzerId(AnalyzerInfo analyzer) {
        return analyzerInfoMap.entrySet().stream()
                .filter(entry -> entry.getValue() == analyzer)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("unknown");
    }
    
    private void logSystemStatus() {
        long uptime = System.currentTimeMillis() - startTime.sum();
        long totalMessages = totalMessagesProcessed.sum();
        long queued = packetsQueued.sum();
        long processed = packetsProcessed.sum();
        long dropped = packetsDropped.sum();
        
        double messagesPerSec = totalMessages > 0 ? (totalMessages * 1000.0) / uptime : 0;
        
        log.info("SYSTEM_STATUS | uptime_ms={} | processed={} | dropped={} | total_msgs={} | msgs_per_sec={:.1f} | queue_size={}", 
                uptime, processed, dropped, totalMessages, messagesPerSec, 
                ((ThreadPoolExecutor) executorService).getQueue().size());
    }

    @Data
    @Builder
    private static class AnalyzerInfo {
        private final String endpoint;
        private final double weight;
        @Builder.Default
        private final LongAdder messageCount = new LongAdder(); // 🚀 LongAdder for high contention
        
        // Health management
        @Builder.Default
        private volatile boolean isOnline = true;
        @Builder.Default
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        @Builder.Default
        private volatile Instant lastFailureTime = null;
    }
}
