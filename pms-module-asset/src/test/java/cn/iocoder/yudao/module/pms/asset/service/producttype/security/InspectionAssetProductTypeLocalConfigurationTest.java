package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED;
import static org.junit.jupiter.api.Assertions.*;

/** Binds the actual local deployment configuration without starting an application or database. */
class InspectionAssetProductTypeLocalConfigurationTest {
    private static final String PREFIX = "pms.asset-product-type";
    private static final Long INSPECTION_PRINCIPAL_ID = 970477601L;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearContexts() {
        AssetProductTypeCallerContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void repositoryLocalConfigurationGrantsBothInspectionReadActionsToTheStableTechnicalPrincipal() throws Exception {
        var guard = guard(bind(localProductTypeProperties()));

        assertAll(
                () -> assertEquals(INSPECTION_PRINCIPAL_ID, AssetProductTypeCallerContext.callAsInspection(
                        () -> guard.requireTrustedPrincipal(AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES))),
                () -> assertEquals(INSPECTION_PRINCIPAL_ID, AssetProductTypeCallerContext.callAsInspection(
                        () -> guard.requireTrustedPrincipal(AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ))));
    }

    @Test
    void repositoryLocalConfigurationDoesNotGrantInspectionControlledImport() throws Exception {
        var guard = guard(bind(localProductTypeProperties()));

        var failure = assertThrows(ServiceException.class, () -> AssetProductTypeCallerContext.callAsInspection(
                () -> guard.requireTrustedPrincipal(AssetProductTypeActionCodes.PRODUCT_TYPE_CONTROLLED_IMPORT)));

        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), failure.getCode());
    }

    @Test
    void absentConfigurationRemainsFailClosedForBothInspectionReadActions() {
        var guard = guard(bind(Map.of()));

        for (var action : new String[] { AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES,
                AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ }) {
            var failure = assertThrows(ServiceException.class, () -> AssetProductTypeCallerContext.callAsInspection(
                    () -> guard.requireTrustedPrincipal(action)));
            assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), failure.getCode());
        }
    }

    private static AssetProductTypeServicePrincipalProperties bind(Map<String, Object> values) {
        var source = new MapPropertySource("asset-product-type-local-only", values);
        var binder = new Binder(ConfigurationPropertySources.from(source));
        return binder.bind(PREFIX, Bindable.of(AssetProductTypeServicePrincipalProperties.class))
                .orElseGet(AssetProductTypeServicePrincipalProperties::new);
    }

    private static AssetProductTypeRequestGuard guard(AssetProductTypeServicePrincipalProperties properties) {
        return new AssetProductTypeRequestGuard(new TrustedAssetProductTypeServicePrincipalRegistry(properties));
    }

    private static Map<String, Object> localProductTypeProperties() throws IOException {
        var localYaml = locateRepositoryRoot().resolve("yudao-server/src/main/resources/application-local.yaml");
        assertTrue(Files.isRegularFile(localYaml), "必须读取当前仓库的实际 application-local.yaml");
        var values = new LinkedHashMap<String, Object>();
        var sources = new YamlPropertySourceLoader().load("repository-application-local", new FileSystemResource(localYaml));
        for (var source : sources) {
            assertInstanceOf(EnumerablePropertySource.class, source);
            var enumerable = (EnumerablePropertySource<?>) source;
            for (String name : enumerable.getPropertyNames()) {
                if (name.startsWith(PREFIX + ".")) values.put(name, source.getProperty(name));
            }
        }
        // Bind only AST service identities; datasource, secrets and unrelated local settings never enter the Binder.
        return values;
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/migrations"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("无法定位仓库根目录");
        return current;
    }
}
