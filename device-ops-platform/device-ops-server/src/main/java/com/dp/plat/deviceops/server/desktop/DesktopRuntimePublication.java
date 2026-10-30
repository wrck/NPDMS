package com.dp.plat.deviceops.server.desktop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Public, non-secret runtime metadata for native launchers. */
@Component
@Profile("desktop")
class DesktopRuntimePublication {
    private final String file;
    private final DesktopIdentity identity;
    DesktopRuntimePublication(@Value("${device-ops.desktop.runtime-file:}") String file, DesktopIdentity identity) {
        this.file = file; this.identity = identity;
    }
    @EventListener
    void listening(WebServerInitializedEvent event) throws Exception {
        int port = event.getWebServer().getPort();
        if (port < 8280 || port > 8289) throw new IllegalStateException("Desktop port outside candidate range");
        if (file.isBlank()) return;
        var target = Path.of(file); var pending = target.resolveSibling(target.getFileName() + ".new");
        Files.writeString(pending, "port=" + port + "\npid=" + ProcessHandle.current().pid()
                + "\npublicKey=" + identity.publicKey() + "\ninstanceId=" + identity.instanceId() + "\n", StandardCharsets.UTF_8);
        Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
