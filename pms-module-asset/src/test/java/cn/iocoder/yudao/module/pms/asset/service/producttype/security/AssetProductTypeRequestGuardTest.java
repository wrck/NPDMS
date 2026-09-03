package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetProductTypeRequestGuardTest {

    @AfterEach
    void clearContext() {
        AssetProductTypeCallerContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void shouldResolveOnlyAllowedActionFromCallerContext() {
        TrustedAssetProductTypeServicePrincipalRegistry registry = registry(
                AssetProductTypeCallerContext.INSPECTION,
                21L,
                AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES);
        AssetProductTypeRequestGuard guard = new AssetProductTypeRequestGuard(registry);
        TenantContextHolder.setTenantId(1L);

        Long principalId = AssetProductTypeCallerContext.callAsInspection(
                () -> guard.requireTrustedPrincipal(AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
        ServiceException error = assertThrows(ServiceException.class,
                () -> AssetProductTypeCallerContext.callAsInspection(
                        () -> guard.requireTrustedPrincipal(AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ)));

        assertEquals(21L, principalId);
        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), error.getCode());
    }

    @Test
    void shouldRejectMissingCallerOrTenantMismatch() {
        TrustedAssetProductTypeServicePrincipalRegistry registry = registry(
                AssetProductTypeCallerContext.INSPECTION,
                21L,
                AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES);
        AssetProductTypeRequestGuard guard = new AssetProductTypeRequestGuard(registry);
        TenantContextHolder.setTenantId(1L);

        ServiceException missing = assertThrows(ServiceException.class,
                () -> guard.requireTrustedPrincipal(AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
        ServiceException mismatch = assertThrows(ServiceException.class,
                () -> AssetProductTypeCallerContext.callAsInspection(() -> {
                    TenantContextHolder.setTenantId(2L);
                    return guard.requireTrustedPrincipal(AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES);
                }));

        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), missing.getCode());
        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), mismatch.getCode());
    }

    @Test
    void shouldRejectUnregisteredConsumerAndInvalidPrincipal() {
        AssetProductTypeRequestGuard unregisteredGuard = new AssetProductTypeRequestGuard(
                new TrustedAssetProductTypeServicePrincipalRegistry(
                        new AssetProductTypeServicePrincipalProperties()));
        AssetProductTypeRequestGuard invalidPrincipalGuard = new AssetProductTypeRequestGuard(registry(
                AssetProductTypeCallerContext.INSPECTION,
                0L,
                AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
        TenantContextHolder.setTenantId(1L);

        ServiceException unregistered = assertThrows(ServiceException.class,
                () -> AssetProductTypeCallerContext.callAsInspection(
                        () -> unregisteredGuard.requireTrustedPrincipal(
                                AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES)));
        ServiceException invalidPrincipal = assertThrows(ServiceException.class,
                () -> AssetProductTypeCallerContext.callAsInspection(
                        () -> invalidPrincipalGuard.requireTrustedPrincipal(
                                AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES)));

        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), unregistered.getCode());
        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), invalidPrincipal.getCode());
    }

    @Test
    void shouldRejectInvalidConsumerAndAction() {
        TrustedAssetProductTypeServicePrincipalRegistry registry = registry(
                AssetProductTypeCallerContext.INSPECTION,
                21L,
                AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES);

        ServiceException wrongConsumer = assertThrows(ServiceException.class,
                () -> registry.resolve("OTHER", AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES));
        ServiceException missingAction = assertThrows(ServiceException.class,
                () -> registry.resolve(AssetProductTypeCallerContext.INSPECTION, null));
        ServiceException unknownAction = assertThrows(ServiceException.class,
                () -> registry.resolve(AssetProductTypeCallerContext.INSPECTION, "UNKNOWN"));

        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), wrongConsumer.getCode());
        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), missingAction.getCode());
        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), unknownAction.getCode());
    }

    @Test
    void shouldRejectNonPositiveSubjectUser() {
        AssetProductTypeRequestGuard guard = new AssetProductTypeRequestGuard(
                new TrustedAssetProductTypeServicePrincipalRegistry(
                        new AssetProductTypeServicePrincipalProperties()));

        ServiceException missing = assertThrows(ServiceException.class, () -> guard.requireSubjectUser(null));
        ServiceException zero = assertThrows(ServiceException.class, () -> guard.requireSubjectUser(0L));
        ServiceException negative = assertThrows(ServiceException.class, () -> guard.requireSubjectUser(-1L));

        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), missing.getCode());
        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), zero.getCode());
        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), negative.getCode());
    }

    @Test
    void shouldKeepRegistryPrivateAndImmutableAtRuntime() {
        assertFalse(Modifier.isPublic(TrustedAssetProductTypeServicePrincipalRegistry.class.getModifiers()));
        assertFalse(Arrays.stream(TrustedAssetProductTypeServicePrincipalRegistry.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("set"))
                .anyMatch(method -> Modifier.isPublic(method.getModifiers())));
    }

    @Test
    void shouldRejectInspectionRegistryWithImportAction() {
        TrustedAssetProductTypeServicePrincipalRegistry registry = registry(
                AssetProductTypeCallerContext.INSPECTION,
                21L,
                AssetProductTypeActionCodes.PRODUCT_TYPE_CONTROLLED_IMPORT);

        ServiceException error = assertThrows(ServiceException.class,
                () -> registry.resolve(AssetProductTypeCallerContext.INSPECTION,
                        AssetProductTypeActionCodes.PRODUCT_TYPE_CONTROLLED_IMPORT));

        assertEquals(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED.getCode(), error.getCode());
    }

    private static TrustedAssetProductTypeServicePrincipalRegistry registry(
            String consumerCode, Long principalId, String action) {
        AssetProductTypeServicePrincipalProperties properties =
                new AssetProductTypeServicePrincipalProperties();
        properties.setTrustedServicePrincipals(Map.of(consumerCode,
                new TrustedAssetProductTypeServicePrincipalRegistry.TrustedPrincipal(
                        principalId, Set.of(action))));
        return new TrustedAssetProductTypeServicePrincipalRegistry(properties);
    }
}
