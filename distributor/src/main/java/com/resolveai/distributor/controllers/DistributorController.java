package com.resolveai.distributor.controllers;

import com.resolveai.distributor.models.LogPacket;
import com.resolveai.distributor.services.DistributorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class DistributorController {
    private final DistributorService distributorService;

    public DistributorController(DistributorService distributorService) {
        this.distributorService = distributorService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Distributor is online.\n");
    }

    @PostMapping("/distribute")
    public ResponseEntity<Void> receiveLogPacket(@RequestBody LogPacket logPacket) {
        log.info("Log packet received: {}", logPacket.getPacketId());
        distributorService.distributePacket(logPacket);
        return ResponseEntity.accepted().build();
    }
}
