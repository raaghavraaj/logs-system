package com.resolveai.analyzer.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.analyzer.models.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(AnalyzerController.class)
@ContextConfiguration(classes = {AnalyzerController.class, AnalyzerControllerTest.TestConfig.class})
class AnalyzerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Analyzer is online and healthy\n"));
    }

    @Test
    void testGetStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/stats"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ANALYZER")))
                .andExpect(content().string(containsString("Total Packets Processed")))
                .andExpect(content().string(containsString("Total Messages Processed")))
                .andExpect(content().string(containsString("Messages by Level")))
                .andExpect(content().string(containsString("Performance Metrics")));
    }

    @Test
    void testAnalyzeLogPacket() throws Exception {
        // Given
        LogPacket logPacket = createTestLogPacket("test-agent", 3);
        
        // When & Then
        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logPacket)))
                .andExpect(status().isAccepted());
    }

    @Test
    void testAnalyzeLogPacketWithVariousLevels() throws Exception {
        // Given
        LogPacket logPacket = createTestLogPacketWithLevels();
        
        // When & Then
        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logPacket)))
                .andExpect(status().isAccepted());
    }

    @Test
    void testAnalyzeLogPacketInvalidJson() throws Exception {
        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testStatsAfterProcessingPackets() throws Exception {
        // Given - process a few packets
        LogPacket packet1 = createTestLogPacket("agent-1", 5);
        LogPacket packet2 = createTestLogPacket("agent-2", 3);
        
        // When - process packets
        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(packet1)));
                
        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(packet2)));
        
        // Then - check stats reflect the processing
        mockMvc.perform(get("/api/v1/stats"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Total Packets Processed: 2")))
                .andExpect(content().string(containsString("Total Messages Processed: 8")))
                .andExpect(content().string(containsString("agent-1")))
                .andExpect(content().string(containsString("agent-2")));
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

    private LogPacket createTestLogPacketWithLevels() {
        List<LogMessage> messages = List.of(
            createLogMessage(LogLevel.DEBUG, "Debug message"),
            createLogMessage(LogLevel.INFO, "Info message"),
            createLogMessage(LogLevel.WARN, "Warning message"),
            createLogMessage(LogLevel.ERROR, "Error message"),
            createLogMessage(LogLevel.FATAL, "Fatal message")
        );
        
        return LogPacket.builder()
            .packetId("test-packet-levels")
            .agentId("test-agent")
            .timestamp(java.time.Instant.now())
            .totalMessages(messages.size())
            .messages(messages)
            .checksum("test")
            .build();
    }

    private LogMessage createLogMessage(LogLevel level, String content) {
        return LogMessage.builder()
            .id("msg-" + System.currentTimeMillis())
            .timestamp(java.time.Instant.now())
            .level(level)
            .source(LogSource.builder()
                .application("test-app")
                .service("test-service")
                .instance("test-1")
                .host("localhost")
                .build())
            .message(content)
            .metadata(new java.util.HashMap<>())
            .build();
    }
}
