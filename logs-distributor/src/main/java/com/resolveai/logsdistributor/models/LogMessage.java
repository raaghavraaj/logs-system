package com.resolveai.logsdistributor.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code LogMessage} class describes the data model for a log message. It describes a source field which is
 * defined as another data model {@link LogSource}.
 */
@Builder
@Data
public class LogMessage {
    @JsonProperty("id")
    @Builder.Default
    private String id = UUID.randomUUID().toString();;

    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("level")
    private LogLevel level;

    @JsonProperty("source")
    private LogSource source;

    @JsonProperty("message")
    private String message;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
