package com.resolveai.logsdistributor.controllers;

import com.resolveai.logsdistributor.models.LogPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class LogsController {

    @PostMapping("/logs")
    public ResponseEntity<Void> receiveLogPacket(@RequestBody LogPacket logPacket) {
        log.info("Log packet received: {}", logPacket.getPacketId());
        return ResponseEntity.accepted().build();
    }
}
