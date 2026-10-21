package com.dp.plat.deviceops.server.desktop;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

import java.net.URI;
import java.util.Set;

@Profile("desktop")
@ConfigurationProperties("device-ops.desktop")
public record DesktopProperties(String serverUrl, String webOrigin) {
    public DesktopProperties {
        serverUrl = validate(serverUrl, false);
        webOrigin = validate(webOrigin, true);
    }

    private static String validate(String value, boolean originOnly) {
        try {
            var uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || originOnly && !Set.of("", "/").contains(uri.getPath())) throw new IllegalArgumentException();
            return value.replaceAll("/+$", "");
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("DESKTOP_SERVER_OR_ORIGIN_INVALID");
        }
    }
}
