package cn.iocoder.yudao.module.pms.integration.umc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class UmcCredentialBindingTest {
    @TempDir Path directory;
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties(UmcInspectionProperties.class)
    static class Config { }
    @Test void namedSystemdConfigtreeCredentialBindsWithoutRewritingOperatorSettings() throws Exception {
        Path credentials=directory.resolve("credentials");Files.createDirectory(credentials);
        Files.writeString(credentials.resolve("pms.integration.umc.password"),"synthetic-only-password");
        Path operator=directory.resolve("operator.properties");
        Files.writeString(operator,"pms.integration.umc.base-url=https://operator-umc.example\ncustom.operator.value=keep\n");
        byte[] before=Files.readAllBytes(operator);
        try(var ctx=new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false).registerShutdownHook(false)
                .properties("spring.config.import=configtree:"+credentials.toAbsolutePath().toString().replace('\\','/')+"/,"+operator.toUri(),"pms.integration.umc.base-url=https://default-umc.example","pms.integration.umc.username=fixture")
                .run()) {
            var properties=ctx.getBean(UmcInspectionProperties.class);
            assertEquals("synthetic-only-password",properties.getPassword());assertEquals("https://operator-umc.example",properties.getBaseUrl());assertEquals("keep",ctx.getEnvironment().getProperty("custom.operator.value"));
            assertArrayEquals(before,Files.readAllBytes(operator));
        }
    }
}
