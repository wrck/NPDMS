package com.dp.plat.deviceops.server.desktop;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("desktop")
@RestController
public class DesktopStatusController {
    private final DesktopProperties properties;
    private final ApplicationAvailability availability;

    public DesktopStatusController(DesktopProperties properties, ApplicationAvailability availability) {
        this.properties = properties;
        this.availability = availability;
    }

    @GetMapping(value = "/desktop-status", produces = "text/html;charset=UTF-8")
    public ResponseEntity<org.springframework.core.io.Resource> page() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
                .body(new org.springframework.core.io.ClassPathResource("desktop/status.html"));
    }

    @GetMapping("/api/v1/desktop/status")
    public ResponseEntity<Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status("npdms-dac", 1,
                availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC ? "READY" : "STARTING",
                properties.serverUrl(), properties.webOrigin()));
    }

    public record Status(String product, int protocolVersion, String status, String serverUrl, String webOrigin) { }
}
