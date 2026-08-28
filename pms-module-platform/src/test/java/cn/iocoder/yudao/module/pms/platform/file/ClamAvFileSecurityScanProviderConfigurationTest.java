package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.service.file.ClamAvFileSecurityScanProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClamAvFileSecurityScanProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void doesNotLoadClamAvProviderByDefault() {
        contextRunner.run(context -> assertEquals(0,
                context.getBeansOfType(ClamAvFileSecurityScanProvider.class).size()));
    }

    @Test
    void loadsExactlyOneClamAvProviderWhenScanningIsEnabled() {
        contextRunner.withPropertyValues("pms.file.scan.enabled=true")
                .run(context -> assertEquals(1,
                        context.getBeansOfType(ClamAvFileSecurityScanProvider.class).size()));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ClamAvFileSecurityScanProvider.class)
    static class TestConfiguration {
    }
}
