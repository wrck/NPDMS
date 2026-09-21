package com.dp.deviceops.adapter.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("device-ops.embed")
public class EmbedSecurityProperties {
    private List<String> frameAncestors = List.of("'self'");
    public List<String> getFrameAncestors() { return frameAncestors; }
    public void setFrameAncestors(List<String> value) {
        frameAncestors = List.copyOf(value);
        if (frameAncestors.isEmpty() || frameAncestors.stream().anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("*"))) {
            throw new IllegalArgumentException("frame-ancestors must be an explicit non-wildcard allowlist");
        }
    }
    public String contentSecurityPolicy() { return "frame-ancestors " + String.join(" ", frameAncestors); }
}
