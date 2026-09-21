package com.dp.deviceops.adapter.web.callback;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties("device-ops.callback")
public class CallbackProperties {
    private boolean enabled;
    private String tokenUri, clientId, clientSecret, scope;
    private Duration pollInterval = Duration.ofSeconds(5), lease = Duration.ofSeconds(30), baseDelay = Duration.ofSeconds(5), maxDelay = Duration.ofMinutes(5), connectTimeout = Duration.ofSeconds(3), readTimeout = Duration.ofSeconds(10);
    private int batchSize = 20, maxAttempts = 5;
    private List<String> allowedHosts = List.of();
    private String npdmsDestination, npdmsSigningKey;
    private String npdmsNamespacePrefix = "npdms-";

    public void validate() {
        if (!enabled) return;
        if (blank(npdmsDestination)) {
            URI token = uri(tokenUri);
            if (!("http".equalsIgnoreCase(token.getScheme()) || "https".equalsIgnoreCase(token.getScheme())) || token.getHost() == null
                    || blank(clientId) || blank(clientSecret)) throw invalid();
        } else {
            URI destination = uri(npdmsDestination);
            if (destination.getHost() == null || destination.getUserInfo() != null
                    || !("https".equals(destination.getScheme()) || ("http".equals(destination.getScheme())
                    && List.of("localhost", "127.0.0.1").contains(destination.getHost())))
                    || npdmsSigningKey == null || npdmsSigningKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32
                    || blank(npdmsNamespacePrefix)) throw invalid();
        }
        allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream().filter(host -> !blank(host)).map(host -> host.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        if (allowedHosts.isEmpty() || !positive(pollInterval) || !positive(lease) || !positive(baseDelay) || !positive(maxDelay)
                || !positive(connectTimeout) || !positive(readTimeout) || batchSize <= 0 || maxAttempts <= 0 || baseDelay.compareTo(maxDelay) > 0) throw invalid();
        Duration requestBudget = connectTimeout.plus(readTimeout).multipliedBy(2);
        if (lease.compareTo(requestBudget) < 0) throw invalid();
    }

    private static URI uri(String value) { try { return URI.create(value); } catch (Exception ex) { throw invalid(); } }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean positive(Duration value) { return value != null && !value.isNegative() && !value.isZero(); }
    private static IllegalStateException invalid() { return new IllegalStateException("invalid device-ops callback configuration"); }

    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; }
    public String getTokenUri() { return tokenUri; } public void setTokenUri(String v) { tokenUri = v; }
    public String getClientId() { return clientId; } public void setClientId(String v) { clientId = v; }
    public String getClientSecret() { return clientSecret; } public void setClientSecret(String v) { clientSecret = v; }
    public String getScope() { return scope; } public void setScope(String v) { scope = v; }
    public Duration getPollInterval() { return pollInterval; } public void setPollInterval(Duration v) { pollInterval = v; }
    public Duration getLease() { return lease; } public void setLease(Duration v) { lease = v; }
    public Duration getBaseDelay() { return baseDelay; } public void setBaseDelay(Duration v) { baseDelay = v; }
    public Duration getMaxDelay() { return maxDelay; } public void setMaxDelay(Duration v) { maxDelay = v; }
    public Duration getConnectTimeout() { return connectTimeout; } public void setConnectTimeout(Duration v) { connectTimeout = v; }
    public Duration getReadTimeout() { return readTimeout; } public void setReadTimeout(Duration v) { readTimeout = v; }
    public int getBatchSize() { return batchSize; } public void setBatchSize(int v) { batchSize = v; }
    public int getMaxAttempts() { return maxAttempts; } public void setMaxAttempts(int v) { maxAttempts = v; }
    public List<String> getAllowedHosts() { return allowedHosts; } public void setAllowedHosts(List<String> v) { allowedHosts = v; }
    public String getNpdmsDestination() { return npdmsDestination; } public void setNpdmsDestination(String v) { npdmsDestination = v; }
    public String getNpdmsSigningKey() { return npdmsSigningKey; } public void setNpdmsSigningKey(String v) { npdmsSigningKey = v; }
    public String getNpdmsNamespacePrefix() { return npdmsNamespacePrefix; } public void setNpdmsNamespacePrefix(String v) { npdmsNamespacePrefix = v; }
}
