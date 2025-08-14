package com.resolveai.logsdistributor.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Data
public class LogPacket {
    @JsonProperty("packetId")
    @Builder.Default
    private String packetId = UUID.randomUUID().toString();

    @JsonProperty("agentId")
    private String agentId;

    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("totalMessages")
    private int totalMessages;

    @JsonProperty("messages")
    private List<LogMessage> messages;

    @JsonProperty("checksum")
    private String checksum;
}

