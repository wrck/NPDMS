package com.dp.plat.deviceops.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.dp.plat.deviceops")
@ConfigurationPropertiesScan(basePackages = "com.dp.plat.deviceops.server")
public class DeviceOpsServerApplication {
    public static void main(String[] args) {
        try {
            SpringApplication.run(DeviceOpsServerApplication.class, args);
        } catch (RuntimeException failure) {
            // Only the installed desktop host understands this exit code. Other startup failures never rotate ports.
            if (java.util.Arrays.asList(args).contains("--spring.profiles.active=desktop")) {
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    if (cause instanceof org.springframework.boot.web.server.PortInUseException) System.exit(98);
                }
            }
            throw failure;
        }
    }
}
