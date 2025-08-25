package com.resolveai.analyzer.controllers;

import com.resolveai.analyzer.models.LogPacket;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class AnalyzerController {

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Analyzer is online and healthy\n");
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
        long debugCount = logPacket.getMessages().stream()
                .filter(msg -> msg.getLevel().name().equals("DEBUG"))
                .count();
        long errorCount = logPacket.getMessages().stream()
                .filter(msg -> msg.getLevel().name().equals("ERROR"))
                .count();

        log.info("Packet {} processed: {} total messages, {} DEBUG, {} ERROR",
                logPacket.getPacketId(),
                logPacket.getMessages().size(),
                debugCount,
                errorCount);
    }
}