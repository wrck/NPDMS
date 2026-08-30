package cn.iocoder.yudao.module.pms.asset.api.producttype;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.projection.AuthorizedDeviceProductTypeProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.AuthorizedDeviceProductTypesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypesByCodesQuery;
import cn.iocoder.yudao.module.pms.asset.service.producttype.AssetProductTypeQueryService;
import cn.iocoder.yudao.module.pms.asset.service.producttype.security.AssetProductTypeRequestGuard;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.asset.service.producttype.security.AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ;
import static cn.iocoder.yudao.module.pms.asset.service.producttype.security.AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProductTypeApiImplTest {

    @Mock private AssetProductTypeRequestGuard requestGuard;
    @Mock private DeviceAccessScopeService accessScopeService;
    @Mock private AssetProductTypeMapper productTypeMapper;
    @Mock private DeviceCurrentProductTypeMapper currentProductTypeMapper;

    private AssetProductTypeQueryService queryService;
    private AssetProductTypeApiImpl api;

    @BeforeEach
    void setUp() {
        queryService = new AssetProductTypeQueryService(
                requestGuard, accessScopeService, productTypeMapper, currentProductTypeMapper);
        api = new AssetProductTypeApiImpl(queryService);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnCodeResultsInRequestedOrderWithUnknownDisabledAndStaleFacts() {
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 30, 10, 0);
        when(productTypeMapper.selectByCodes(
                new ProductTypesByCodesQuery(1L, Set.of("TYPE-A", "TYPE-B", "TYPE-X"))))
                .thenReturn(List.of(
                        productType("TYPE-B", false, "FAILED", syncedAt),
                        productType("TYPE-A", true, "FRESH", syncedAt)));

        List<ProductTypeCodeResult> result = api.getByCodes(
                new ProductTypeCodesQuery(List.of("TYPE-A", "TYPE-X", "TYPE-B")));

        verify(requestGuard).requireTrustedPrincipal(PRODUCT_TYPE_READ_CODES);
        assertEquals(List.of("TYPE-A", "TYPE-X", "TYPE-B"),
                result.stream().map(ProductTypeCodeResult::productTypeCode).toList());
        assertTrue(result.get(0).exists());
        assertTrue(result.get(0).enabled());
        assertFalse(result.get(0).fromLastSuccessfulCopy());
        assertFalse(result.get(1).exists());
        assertFalse(result.get(1).enabled());
        assertNull(result.get(1).displayName());
        assertEquals("NOT_AVAILABLE", result.get(1).syncStatus());
        assertTrue(result.get(2).exists());
        assertFalse(result.get(2).enabled());
        assertEquals("TYPE-B-name", result.get(2).displayName());
        assertEquals(syncedAt, result.get(2).lastSuccessfulSyncTime());
        assertTrue(result.get(2).fromLastSuccessfulCopy());
    }

    @Test
    void shouldValidateCodeActionBeforeReturningEmptyResult() {
        List<ProductTypeCodeResult> result = api.getByCodes(new ProductTypeCodesQuery(List.of()));

        assertTrue(result.isEmpty());
        verify(requestGuard).requireTrustedPrincipal(PRODUCT_TYPE_READ_CODES);
        verifyNoInteractions(productTypeMapper, accessScopeService, currentProductTypeMapper);
    }

    @Test
    void shouldReturnEmptyForEmptyDeviceRequestAfterIdentityAndSubjectValidation() {
        when(requestGuard.requireSubjectUser(7L)).thenReturn(7L);

        List<AuthorizedDeviceProductTypeResult> result = api.getAuthorizedDeviceProductType(
                new AuthorizedDeviceProductTypeQuery(7L, List.of()));

        assertTrue(result.isEmpty());
        verify(requestGuard).requireTrustedPrincipal(DEVICE_PRODUCT_TYPE_READ);
        verify(requestGuard).requireSubjectUser(7L);
        verifyNoInteractions(accessScopeService, currentProductTypeMapper);
    }

    @Test
    void shouldPropagateTrustedActionRejectionBeforeReadingCodes() {
        RuntimeException rejection = new RuntimeException("rejected");
        when(requestGuard.requireTrustedPrincipal(PRODUCT_TYPE_READ_CODES)).thenThrow(rejection);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> api.getByCodes(new ProductTypeCodesQuery(List.of())));

        assertSame(rejection, error);
        verifyNoInteractions(productTypeMapper, accessScopeService, currentProductTypeMapper);
    }

    @Test
    void shouldRejectInvalidSubjectBeforeResolvingDeviceScope() {
        RuntimeException rejection = new RuntimeException("invalid subject");
        when(requestGuard.requireSubjectUser(0L)).thenThrow(rejection);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> api.getAuthorizedDeviceProductType(
                        new AuthorizedDeviceProductTypeQuery(0L, List.of(10L))));

        assertSame(rejection, error);
        verify(requestGuard).requireTrustedPrincipal(DEVICE_PRODUCT_TYPE_READ);
        verifyNoInteractions(accessScopeService, currentProductTypeMapper);
    }

    @Test
    void shouldReturnEmptyWhenSubjectHasNoVisibleProjectScope() {
        when(requestGuard.requireSubjectUser(7L)).thenReturn(7L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of());

        List<AuthorizedDeviceProductTypeResult> result = api.getAuthorizedDeviceProductType(
                new AuthorizedDeviceProductTypeQuery(7L, List.of(10L)));

        assertTrue(result.isEmpty());
        verify(currentProductTypeMapper, never()).selectAuthorizedCurrent(any());
    }

    @Test
    void shouldReturnSameEmptyResultForInvisibleCrossTenantAndMissingDevices() {
        when(requestGuard.requireSubjectUser(7L)).thenReturn(7L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(100L));
        when(currentProductTypeMapper.selectAuthorizedCurrent(any())).thenReturn(List.of());

        List<AuthorizedDeviceProductTypeResult> result = api.getAuthorizedDeviceProductType(
                new AuthorizedDeviceProductTypeQuery(7L, List.of(10L, 11L, 12L)));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMapResolvedAndUnresolvedAuthorizedDevicesWithoutFallbackFacts() {
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 30, 10, 0);
        when(requestGuard.requireSubjectUser(7L)).thenReturn(7L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(100L));
        when(currentProductTypeMapper.selectAuthorizedCurrent(any())).thenReturn(List.of(
                new AuthorizedDeviceProductTypeProjection(
                        10L, "TYPE-A", "Type A", true, "v1", "RESOLVED", "STALE", syncedAt),
                new AuthorizedDeviceProductTypeProjection(
                        14L, "TYPE-B", "Type B", false, "v2", "RESOLVED", "FRESH", syncedAt),
                new AuthorizedDeviceProductTypeProjection(
                        11L, null, null, null, null, "CONFLICT", null, null),
                new AuthorizedDeviceProductTypeProjection(
                        12L, null, null, null, null, "UNKNOWN", null, null),
                new AuthorizedDeviceProductTypeProjection(
                        13L, null, null, null, null, "UNRESOLVED", null, null)));

        List<AuthorizedDeviceProductTypeResult> result = api.getAuthorizedDeviceProductType(
                new AuthorizedDeviceProductTypeQuery(7L, List.of(10L, 14L, 11L, 12L, 13L)));

        verify(requestGuard).requireTrustedPrincipal(DEVICE_PRODUCT_TYPE_READ);
        assertEquals(5, result.size());
        assertEquals("TYPE-A", result.get(0).productTypeCode());
        assertTrue(result.get(0).enabled());
        assertTrue(result.get(0).fromLastSuccessfulCopy());
        assertEquals("TYPE-B", result.get(1).productTypeCode());
        assertFalse(result.get(1).enabled());
        assertFalse(result.get(1).fromLastSuccessfulCopy());
        assertEquals("CONFLICT", result.get(2).resolutionStatus());
        assertNull(result.get(2).productTypeCode());
        assertNull(result.get(2).displayName());
        assertFalse(result.get(2).enabled());
        assertEquals("NOT_AVAILABLE", result.get(2).syncStatus());
        assertEquals("UNKNOWN", result.get(3).resolutionStatus());
        assertEquals("UNRESOLVED", result.get(4).resolutionStatus());
    }

    @Test
    void shouldUseOneEffectiveTimeAndNormalizedSetsForAuthorizedQuery() {
        when(requestGuard.requireSubjectUser(7L)).thenReturn(7L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(100L, 101L));
        when(currentProductTypeMapper.selectAuthorizedCurrent(any())).thenReturn(List.of());
        ArgumentCaptor<AuthorizedDeviceProductTypesQuery> captor =
                ArgumentCaptor.forClass(AuthorizedDeviceProductTypesQuery.class);

        api.getAuthorizedDeviceProductType(
                new AuthorizedDeviceProductTypeQuery(7L, List.of(10L, 11L)));

        verify(currentProductTypeMapper).selectAuthorizedCurrent(captor.capture());
        assertEquals(1L, captor.getValue().tenantId());
        assertEquals(Set.of(10L, 11L), captor.getValue().deviceIds());
        assertEquals(Set.of(100L, 101L), captor.getValue().visibleProjectIds());
        assertTrue(captor.getValue().effectiveAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void shouldDelegateBothApiMethodsToQueryService() {
        AssetProductTypeQueryService service = org.mockito.Mockito.mock(AssetProductTypeQueryService.class);
        AssetProductTypeApiImpl delegatingApi = new AssetProductTypeApiImpl(service);
        ProductTypeCodesQuery codesQuery = new ProductTypeCodesQuery(List.of("TYPE-A"));
        AuthorizedDeviceProductTypeQuery devicesQuery =
                new AuthorizedDeviceProductTypeQuery(7L, List.of(10L));
        List<ProductTypeCodeResult> codeResults = List.of(new ProductTypeCodeResult(
                "TYPE-A", true, true, "Type A", "MES", "v1", "FRESH", null, false));
        List<AuthorizedDeviceProductTypeResult> deviceResults = List.of(new AuthorizedDeviceProductTypeResult(
                10L, "TYPE-A", "Type A", true, "v1", "RESOLVED", "FRESH", null, false));
        when(service.getByCodes(codesQuery)).thenReturn(codeResults);
        when(service.getAuthorizedDeviceProductType(devicesQuery)).thenReturn(deviceResults);

        assertSame(codeResults, delegatingApi.getByCodes(codesQuery));
        assertSame(deviceResults, delegatingApi.getAuthorizedDeviceProductType(devicesQuery));
    }

    private static AssetProductTypeDO productType(
            String code, boolean enabled, String syncStatus, LocalDateTime syncedAt) {
        AssetProductTypeDO productType = new AssetProductTypeDO();
        productType.setTypeCode(code);
        productType.setDisplayName(code + "-name");
        productType.setEnabled(enabled);
        productType.setSourceSystem("MES");
        productType.setSourceVersion("v1");
        productType.setSyncStatus(syncStatus);
        productType.setSyncedAt(syncedAt);
        return productType;
    }
}
