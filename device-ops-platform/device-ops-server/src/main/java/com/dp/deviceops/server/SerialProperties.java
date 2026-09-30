package com.dp.deviceops.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("device-ops.serial")
public class SerialProperties {

    private boolean enabled = true;
    private int maxOutputBytes = 8_388_608;
    private int eventChunkBytes = 8_192;
    private int maxPages = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getEventChunkBytes() {
        return eventChunkBytes;
    }

    public void setEventChunkBytes(int eventChunkBytes) {
        if (eventChunkBytes < 1 || eventChunkBytes > 8_192) {
            throw new IllegalArgumentException("eventChunkBytes must be between 1 and 8192");
        }
        this.eventChunkBytes = eventChunkBytes;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        this.maxPages = maxPages;
    }
}
