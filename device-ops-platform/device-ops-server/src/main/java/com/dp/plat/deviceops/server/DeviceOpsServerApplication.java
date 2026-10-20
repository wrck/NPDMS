package com.dp.plat.deviceops.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.dp.plat.deviceops")
@ConfigurationPropertiesScan(basePackages = "com.dp.plat.deviceops.server")
public class DeviceOpsServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceOpsServerApplication.class, args);
    }
}
