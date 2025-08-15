package com.resolveai.logsdistributor.controllers;

import com.resolveai.logsdistributor.models.LogPacket;
import com.resolveai.logsdistributor.services.LogsDistributorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class LogsController {
    private final LogsDistributorService logsDistributorService;

    public LogsController(LogsDistributorService logsDistributorService) {
        this.logsDistributorService = logsDistributorService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Logs Distributor is online.\n");
    }

    @PostMapping("/logs")
    public ResponseEntity<Void> receiveLogPacket(@RequestBody LogPacket logPacket) {
        log.info("Log packet received: {}", logPacket.getPacketId());
        logsDistributorService.distributePacket(logPacket);
        return ResponseEntity.accepted().build();
    }
}
