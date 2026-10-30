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
    private final DesktopIdentity identity;

    public DesktopStatusController(DesktopProperties properties, ApplicationAvailability availability, DesktopIdentity identity) {
        this.properties = properties;
        this.availability = availability;
        this.identity = identity;
    }

    @GetMapping(value = "/desktop-status", produces = "text/html;charset=UTF-8")
    public ResponseEntity<org.springframework.core.io.Resource> page() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
                .body(new org.springframework.core.io.ClassPathResource("desktop/status.html"));
    }

    @GetMapping("/api/v1/desktop/status")
    public ResponseEntity<Status> status(@org.springframework.web.bind.annotation.RequestParam(name = "nonce", required = false) String nonce,
            jakarta.servlet.http.HttpServletRequest request) throws Exception {
        if (nonce != null && !nonce.matches("[a-f0-9]{64}")) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        String state = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC ? "READY" : "STARTING";
        int port = request.getLocalPort();
        String signature = nonce == null ? null : java.util.Base64.getEncoder().encodeToString(identity.sign(
                "npdms-dac-discovery-v1\n" + nonce + "\n" + port + "\n" + identity.instanceId()
                + "\n3\n" + state + "\n" + properties.serverUrl() + "\n" + properties.webOrigin()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status("npdms-dac", 3,
                state, properties.serverUrl(), properties.webOrigin(), port, identity.publicKey(), identity.instanceId(), signature));
    }

    // The pairing fingerprint is intentionally absent from cross-origin discovery; the user reads it locally.
    @GetMapping("/desktop-pairing")
    public ResponseEntity<String> pairing() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identity.pairingCode());
    }

    public record Status(String product, int protocolVersion, String status, String serverUrl, String webOrigin,
                         int port, String publicKey, String instanceId, String signature) { }
}
