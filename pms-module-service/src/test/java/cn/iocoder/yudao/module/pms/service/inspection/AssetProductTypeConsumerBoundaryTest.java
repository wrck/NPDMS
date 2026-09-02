package cn.iocoder.yudao.module.pms.service.inspection;

import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetProductTypeConsumerBoundaryTest {

    @Test
    void shouldExposeOnlyApprovedInspectionQueriesAndConsumerFacts() {
        Set<String> methods = Arrays.stream(InspectionAssetProductTypeApi.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("getByCodes", "getAuthorizedDeviceProductType"), methods);
        assertTrue(isClosedInput(new ProductTypeCodeResult(
                "UNKNOWN", false, false, null, null, null, "NOT_AVAILABLE", null, false)));
        assertTrue(isClosedInput(new ProductTypeCodeResult(
                "DISABLED", true, false, "Disabled", "CRM", "v1", "FRESH", null, false)));
        assertFalse(isClosedInput(new ProductTypeCodeResult(
                "ACTIVE", true, true, "Active", "CRM", "v1", "FRESH", null, false)));
        assertTrue(isClosedInput(new AuthorizedDeviceProductTypeResult(
                10L, null, null, false, null, "UNRESOLVED", "NOT_AVAILABLE", null, false)));
    }

    @Test
    void inspectionProductionCodeMustNotBypassDedicatedApiBoundary() throws Exception {
        Path productionRoot = Path.of("src", "main", "java");
        try (var files = Files.walk(productionRoot)) {
            List<String> sources = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("inspection"))
                    .map(AssetProductTypeConsumerBoundaryTest::read)
                    .toList();
            assertTrue(sources.stream().noneMatch(source -> source.contains(
                    "cn.iocoder.yudao.module.pms.asset.api.producttype.AssetProductTypeApi")));
            assertTrue(sources.stream().noneMatch(source -> source.contains(".dal.")
                    || source.contains("Mapper")
                    || source.contains("DO;")
                    || source.contains(".service.producttype")
                    || source.contains("ast_")));
        }
    }

    @Test
    void dedicatedApiMustNotExposeCallerOrAuthorizationParameters() {
        Set<String> forbiddenNames = Set.of(
                "consumerCode", "principalId", "actionCode", "tenantId", "context");
        List<String> componentNames = Arrays.stream(InspectionAssetProductTypeApi.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .filter(Class::isRecord)
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .toList();

        assertTrue(componentNames.stream().noneMatch(forbiddenNames::contains));
    }

    private static boolean isClosedInput(ProductTypeCodeResult result) {
        return !result.exists() || !result.enabled();
    }

    private static boolean isClosedInput(AuthorizedDeviceProductTypeResult result) {
        return result.productTypeCode() == null || !result.enabled();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
