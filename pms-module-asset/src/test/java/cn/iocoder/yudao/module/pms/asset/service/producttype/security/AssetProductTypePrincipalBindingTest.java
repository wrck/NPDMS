package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AssetProductTypePrincipalBindingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AssetProductTypeServicePrincipalProperties.class)
    @Import(TrustedAssetProductTypeServicePrincipalRegistry.class)
    static class BindingConfiguration { }

    @Test void localConfigurationRegistersInspectionReadActionsWithoutGrantingImport() throws Exception {
        var resource = new FileSystemResource(Path.of("../yudao-server/src/main/resources/application-local.yaml"));
        var sources = new YamlPropertySourceLoader().load("actual-local", resource);
        new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withInitializer(context -> sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source)))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var registry = context.getBean(TrustedAssetProductTypeServicePrincipalRegistry.class);
                    assertEquals(970477601L, registry.resolve("INSPECTION", AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
                    assertEquals(970477601L, registry.resolve("INSPECTION", AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ));
                    assertThrows(RuntimeException.class, () -> registry.resolve("INSPECTION", AssetProductTypeActionCodes.PRODUCT_TYPE_CONTROLLED_IMPORT));
                });
    }
}
