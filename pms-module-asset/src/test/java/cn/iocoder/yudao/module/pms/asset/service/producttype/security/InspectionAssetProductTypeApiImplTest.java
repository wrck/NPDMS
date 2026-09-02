package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.AssetProductTypeApi;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionAssetProductTypeApiImplTest {

    @Mock
    private AssetProductTypeApi assetProductTypeApi;
    private InspectionAssetProductTypeApiImpl api;

    @BeforeEach
    void setUp() {
        api = new InspectionAssetProductTypeApiImpl(assetProductTypeApi);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        AssetProductTypeCallerContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void shouldDelegateBothQueriesInsideInspectionContext() {
        ProductTypeCodesQuery codesQuery = new ProductTypeCodesQuery(List.of("TYPE-A"));
        ProductTypeCodeResult codeResult = new ProductTypeCodeResult(
                "TYPE-A", true, true, "Type A", "CRM", "v1", "FRESH", null, false);
        when(assetProductTypeApi.getByCodes(codesQuery)).thenAnswer(invocation -> {
            assertInspectionCaller();
            return List.of(codeResult);
        });
        AuthorizedDeviceProductTypeQuery deviceQuery =
                new AuthorizedDeviceProductTypeQuery(9L, List.of(10L));
        AuthorizedDeviceProductTypeResult deviceResult = new AuthorizedDeviceProductTypeResult(
                10L, "TYPE-A", "Type A", true, "v1", "RESOLVED", "FRESH", null, false);
        when(assetProductTypeApi.getAuthorizedDeviceProductType(deviceQuery)).thenAnswer(invocation -> {
            assertInspectionCaller();
            return List.of(deviceResult);
        });

        assertEquals(List.of(codeResult), api.getByCodes(codesQuery));
        assertEquals(List.of(deviceResult), api.getAuthorizedDeviceProductType(deviceQuery));
        assertNull(AssetProductTypeCallerContext.get());
        verify(assetProductTypeApi).getByCodes(codesQuery);
        verify(assetProductTypeApi).getAuthorizedDeviceProductType(deviceQuery);
    }

    @Test
    void shouldRejectMissingTenantAndClearContextAfterDelegateFailure() {
        TenantContextHolder.clear();
        ServiceException missingTenant = assertThrows(ServiceException.class,
                () -> api.getByCodes(new ProductTypeCodesQuery(List.of("TYPE-A"))));
        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), missingTenant.getCode());

        TenantContextHolder.setTenantId(1L);
        ProductTypeCodesQuery query = new ProductTypeCodesQuery(List.of("TYPE-B"));
        RuntimeException failure = new RuntimeException("failed");
        when(assetProductTypeApi.getByCodes(query)).thenAnswer(invocation -> {
            assertInspectionCaller();
            throw failure;
        });

        assertSame(failure, assertThrows(RuntimeException.class, () -> api.getByCodes(query)));
        assertNull(AssetProductTypeCallerContext.get());
    }

    private static void assertInspectionCaller() {
        AssetProductTypeCaller caller = AssetProductTypeCallerContext.get();
        assertEquals(AssetProductTypeCallerContext.INSPECTION, caller.consumerCode());
        assertEquals(1L, caller.tenantId());
    }
}
