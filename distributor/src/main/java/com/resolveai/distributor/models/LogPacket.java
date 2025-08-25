package com.resolveai.distributor.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The {@code LogPacket} class describes the data model of an object which will be a packet on the network consisting of
 * several different logs. The log packet also contains a {@code checkSum} field for integrity verification.
 */
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

