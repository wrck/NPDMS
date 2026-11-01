package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AssetProductTypePrincipalBindingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AssetProductTypeServicePrincipalProperties.class)
    @Import(TrustedAssetProductTypeServicePrincipalRegistry.class)
    static class BindingConfiguration { }

    @Test void dockerDacConfigurationRegistersInspectionReadActionsWithoutGrantingImport() throws Exception {
        deploymentConfiguration().run(context -> {
            assertNull(context.getStartupFailure());
            assertReadOnly(context.getBean(TrustedAssetProductTypeServicePrincipalRegistry.class), 970477601L);
        });
    }

    @Test void operatorIdentityOverridesTheDefaultWithoutBeingRewritten() throws Exception {
        deploymentConfiguration()
                .withPropertyValues("pms.asset-product-type.trusted-service-principals[INSPECTION].principal-id=970477602",
                        "pms.asset-product-type.trusted-service-principals[INSPECTION].allowed-actions[0]=PRODUCT_TYPE_READ_CODES",
                        "pms.asset-product-type.trusted-service-principals[INSPECTION].allowed-actions[1]=DEVICE_PRODUCT_TYPE_READ")
                .run(context -> assertReadOnly(context.getBean(TrustedAssetProductTypeServicePrincipalRegistry.class), 970477602L));
    }

    @Test void incompleteOperatorIdentityRemainsFailClosed() throws Exception {
        deploymentConfiguration()
                .withPropertyValues("pms.asset-product-type.trusted-service-principals[INSPECTION].principal-id=970477602")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertThrows(RuntimeException.class, () -> context.getBean(TrustedAssetProductTypeServicePrincipalRegistry.class)
                            .resolve("INSPECTION", AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
                });
    }

    @Test void deploymentDefaultsRetainTheReadOnlyIdentityFromBaseConfiguration() throws Exception {
        var runner = deploymentConfiguration();
        String defaults = System.getProperty("npdms.deployment.defaults");
        if (defaults != null) {
            var sources = new PropertiesPropertySourceLoader().load("actual-release-defaults", new FileSystemResource(defaults));
            runner = runner.withInitializer(context -> sources.forEach(source -> context.getEnvironment().getPropertySources().addFirst(source)));
        }
        runner.run(context -> assertReadOnly(context.getBean(TrustedAssetProductTypeServicePrincipalRegistry.class), 970477601L));
    }

    private static ApplicationContextRunner deploymentConfiguration() throws Exception {
        var sources = new ArrayList<PropertySource<?>>();
        for (String file : new String[] {"application.yaml", "application-docker.yaml", "application-dac.yaml"}) {
            sources.addAll(new YamlPropertySourceLoader().load(file,
                    new FileSystemResource(Path.of("../yudao-server/src/main/resources", file))));
        }
        Collections.reverse(sources);
        return new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class)
                .withInitializer(context -> sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source)));
    }

    private static void assertReadOnly(TrustedAssetProductTypeServicePrincipalRegistry registry, long principal) {
        assertEquals(principal, registry.resolve("INSPECTION", AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
        assertEquals(principal, registry.resolve("INSPECTION", AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ));
        assertThrows(RuntimeException.class, () -> registry.resolve("INSPECTION", AssetProductTypeActionCodes.PRODUCT_TYPE_CONTROLLED_IMPORT));
        assertThrows(RuntimeException.class, () -> registry.resolve("OTHER", AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
    }

    @Test void localConfigurationRegistersInspectionReadActionsWithoutGrantingImport() throws Exception {
        var sources = new ArrayList<PropertySource<?>>();
        for (String file : new String[] {"application.yaml", "application-local.yaml"}) {
            sources.addAll(new YamlPropertySourceLoader().load(file,
                    new FileSystemResource(Path.of("../yudao-server/src/main/resources", file))));
        }
        Collections.reverse(sources);
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
