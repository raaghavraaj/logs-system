package com.resolveai.distributor.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * {@code LogSource} is a supporting data model class which encapsulates the source details of the log message.
 */
@Builder
@Data
public class LogSource {
    @JsonProperty("application")
    private String application;

    @JsonProperty("service")
    private String service;

    @JsonProperty("instance")
    private String instance;

    @JsonProperty("host")
    private String host;
}