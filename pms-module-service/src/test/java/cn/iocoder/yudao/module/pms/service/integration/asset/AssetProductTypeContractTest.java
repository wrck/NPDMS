package cn.iocoder.yudao.module.pms.service.integration.asset;

import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssetProductTypeContractTest {

    @Test
    void shouldConsumeOnlyDedicatedInspectionQueries() {
        Method[] declaredMethods = InspectionAssetProductTypeApi.class.getDeclaredMethods();
        Set<String> methods = Arrays.stream(declaredMethods)
                .map(AssetProductTypeContractTest::methodDescriptor)
                .collect(Collectors.toSet());

        assertEquals(2, declaredMethods.length);
        assertEquals(
                Set.of(
                        "getByCodes(ProductTypeCodesQuery):List<ProductTypeCodeResult>",
                        "getAuthorizedDeviceProductType(AuthorizedDeviceProductTypeQuery):List<AuthorizedDeviceProductTypeResult>"),
                methods);
        assertEquals(List.of(), new ProductTypeCodesQuery(null).productTypeCodes());
        assertEquals(List.of(), new AuthorizedDeviceProductTypeQuery(9L, null).deviceIds());
    }

    @Test
    void shouldKeepTenantAndServiceIdentityOutOfQueries() {
        Set<String> productTypeFields = recordComponentNames(ProductTypeCodesQuery.class);
        Set<String> deviceFields = recordComponentNames(AuthorizedDeviceProductTypeQuery.class);

        assertEquals(Set.of("productTypeCodes"), productTypeFields);
        assertEquals(Set.of("subjectUserId", "deviceIds"), deviceFields);
        assertFalse(productTypeFields.contains("tenantId"));
        assertFalse(productTypeFields.contains("serviceIdentity"));
        assertFalse(deviceFields.contains("tenantId"));
        assertFalse(deviceFields.contains("serviceIdentity"));
    }

    @Test
    void shouldExposeFactsRequiredByLaterInspectionConsumers() {
        assertEquals(
                Set.of(
                        "productTypeCode",
                        "exists",
                        "enabled",
                        "displayName",
                        "sourceSystem",
                        "sourceVersion",
                        "syncStatus",
                        "lastSuccessfulSyncTime",
                        "fromLastSuccessfulCopy"),
                recordComponentNames(ProductTypeCodeResult.class));
        assertEquals(
                Set.of(
                        "deviceId",
                        "productTypeCode",
                        "displayName",
                        "enabled",
                        "sourceVersion",
                        "resolutionStatus",
                        "syncStatus",
                        "lastSuccessfulSyncTime",
                        "fromLastSuccessfulCopy"),
                recordComponentNames(AuthorizedDeviceProductTypeResult.class));
    }

    private static String methodDescriptor(Method method) {
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        String resultType = ((Class<?>) returnType.getActualTypeArguments()[0]).getSimpleName();
        return method.getName()
                + "(" + parameters + ")"
                + ":" + method.getReturnType().getSimpleName()
                + "<" + resultType + ">";
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
