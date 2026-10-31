package cn.iocoder.yudao.module.pms.integration.umc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class UmcProviderSelectionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UmcInspectionProperties.class)
    @Import({JavaUmcInspectionGateway.class, NodeUmcInspectionGateway.class})
    static class Config { }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test void javaIsDefaultAndDoesNotRequireNodeExecutable() {
        runner.withPropertyValues("pms.integration.umc.node-executable=does-not-exist").run(context -> {
            assertThat(context).hasSingleBean(JavaUmcInspectionGateway.class).doesNotHaveBean(NodeUmcInspectionGateway.class);
        });
    }

    @Test void explicitNodeUsesOriginalImplementationOnly() {
        runner.withPropertyValues("pms.integration.umc.provider=node").run(context -> {
            assertThat(context).hasSingleBean(NodeUmcInspectionGateway.class).doesNotHaveBean(JavaUmcInspectionGateway.class);
        });
    }

    @Test void unknownProviderFailsBindingInsteadOfFallingBack() {
        runner.withPropertyValues("pms.integration.umc.provider=unknown").run(context -> {
            assertThat(context).hasFailed();
        });
    }
}
