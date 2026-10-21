package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile("desktop")
class DesktopWiring {
    @Bean JdbcDesktopTaskStore desktopTasks(JdbcClient jdbc, TransactionTemplate tx,
            @Value("${device-ops.credentials.master-key}") String key) {
        var cipher = new AesGcmCredentialCipher(key);
        if (!cipher.available()) throw new IllegalStateException("desktop encryption key required");
        return new JdbcDesktopTaskStore(jdbc, tx, cipher);
    }
}
