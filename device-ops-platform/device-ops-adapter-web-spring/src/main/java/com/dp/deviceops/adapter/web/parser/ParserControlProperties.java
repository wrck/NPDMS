package com.dp.deviceops.adapter.web.parser;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties("device-ops.parser")
public class ParserControlProperties {

    private int activationMinimumCapableWorkers = 1;
    private String clientNamespaceClaim = "client_namespace";
    private int maxInputBytes = 8 * 1024 * 1024;
    private boolean automaticParsingEnabled = true;
    private String defaultLogType = "device-command-output";
    private Map<String, URI> resultConsumers = new LinkedHashMap<>();

    public int getActivationMinimumCapableWorkers() {
        return activationMinimumCapableWorkers;
    }

    public void setActivationMinimumCapableWorkers(int value) {
        this.activationMinimumCapableWorkers = value;
    }

    public String getClientNamespaceClaim() {
        return clientNamespaceClaim;
    }

    public void setClientNamespaceClaim(String value) {
        this.clientNamespaceClaim = value;
    }

    public int getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("maxInputBytes must be positive");
        }
        this.maxInputBytes = value;
    }

    public boolean isAutomaticParsingEnabled() {
        return automaticParsingEnabled;
    }

    public void setAutomaticParsingEnabled(boolean value) {
        this.automaticParsingEnabled = value;
    }

    public String getDefaultLogType() {
        return defaultLogType;
    }

    public void setDefaultLogType(String value) {
        this.defaultLogType = value;
    }

    public Map<String, URI> getResultConsumers() {
        return Map.copyOf(resultConsumers);
    }

    public void setResultConsumers(Map<String, URI> value) {
        this.resultConsumers = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
