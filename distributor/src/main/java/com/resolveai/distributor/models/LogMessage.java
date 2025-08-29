package com.resolveai.distributor.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * The {@code LogMessage} class describes the data model for a single log message within a log packet.
 */
@Builder
@Data
public class LogMessage {
    @JsonProperty("id")
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @JsonProperty("level")
    private LogLevel level;

    @JsonProperty("source")
    private String source;

    @JsonProperty("message")
    private String message;
}
