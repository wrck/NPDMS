package com.dp.deviceops.server;

import com.dp.deviceops.adapter.serial.JdkSerialPortEnumerator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lists local serial port names for the connection form dropdown; no sensitive information. */
@RestController
public class SerialPortController {
    private final JdkSerialPortEnumerator ports;
    private final SerialProperties properties;

    public SerialPortController(JdkSerialPortEnumerator ports, SerialProperties properties) {
        this.ports = ports;
        this.properties = properties;
    }

    @GetMapping("/api/v1/serial-ports")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public SerialPorts list() {
        return new SerialPorts(properties.isEnabled() ? ports.list() : List.of());
    }

    public record SerialPorts(List<String> ports) { }
}
