package com.resolveai.logsdistributor.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

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