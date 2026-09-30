package com.dp.deviceops.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeConfigController {
    private final RuntimeConfigProperties properties;
    private final TelnetProperties telnet;
    private final SerialProperties serial;
    public RuntimeConfigController(RuntimeConfigProperties properties, TelnetProperties telnet, SerialProperties serial) {
        this.properties = properties;
        this.telnet = telnet;
        this.serial = serial;
    }
    @GetMapping("/api/v1/runtime-config")
    public RuntimeConfig get() {
        return new RuntimeConfig(properties.getAuthMode(), properties.getOidcAuthority(), properties.getClientId(), properties.getScope(),
                properties.getApiBaseUrl(), properties.getProjectClaim(), telnet.isEnabled(), serial.isEnabled());
    }
    public record RuntimeConfig(String authMode, String oidcAuthority, String oidcClientId, String oidcScope,
                                String apiBaseUrl, String projectClaim, boolean telnetEnabled, boolean serialEnabled) { }
}
