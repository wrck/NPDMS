package cn.iocoder.yudao.module.pms.asset.api.producttype;

import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetProductTypeApiContractTest {

    @Test
    void shouldExposeOnlyTheTwoApprovedQueries() {
        Set<String> methods = Arrays.stream(AssetProductTypeApi.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("getByCodes", "getAuthorizedDeviceProductType"), methods);
    }

    @Test
    void shouldExposeRecordsWithoutPersistenceTypes() {
        List<Class<?>> types = List.of(
                ProductTypeCodesQuery.class,
                ProductTypeCodeResult.class,
                AuthorizedDeviceProductTypeQuery.class,
                AuthorizedDeviceProductTypeResult.class);

        assertTrue(types.stream().allMatch(Class::isRecord));
        assertEquals(List.of("productTypeCodes"), Arrays.stream(ProductTypeCodesQuery.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList());
        assertEquals(List.of("subjectUserId", "deviceIds"),
                Arrays.stream(AuthorizedDeviceProductTypeQuery.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList());
        assertTrue(types.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getType)
                .noneMatch(type -> type.getSimpleName().endsWith("DO")
                        || type.getSimpleName().endsWith("Mapper")
                        || type.getSimpleName().endsWith("Service")));
    }

    @Test
    void shouldNotExposeCallerContextFromApiModule() throws Exception {
        Path apiRoot = Path.of("pms-module-asset-api", "src", "main", "java");
        try (var files = Files.walk(apiRoot)) {
            List<String> sources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(sources.stream().noneMatch(source -> source.contains("AssetProductTypeCallerContext")
                    || source.contains("callAsInspection")
                    || source.contains("runAs(")));
        }
    }

    @Test
    void shouldNormalizeAndCopyQueryCollections() {
        List<String> codes = new ArrayList<>(Arrays.asList(" FW ", null, "", "FW", "sw"));
        List<Long> deviceIds = new ArrayList<>(Arrays.asList(7L, null, 7L, 8L));
        ProductTypeCodesQuery codeQuery = new ProductTypeCodesQuery(codes);
        AuthorizedDeviceProductTypeQuery deviceQuery =
                new AuthorizedDeviceProductTypeQuery(9L, deviceIds);

        codes.add("SW");
        deviceIds.add(9L);

        assertEquals(List.of("FW", "sw"), codeQuery.productTypeCodes());
        assertEquals(List.of(7L, 8L), deviceQuery.deviceIds());
        assertEquals(List.of(), new ProductTypeCodesQuery(null).productTypeCodes());
        assertEquals(List.of(), new AuthorizedDeviceProductTypeQuery(9L, null).deviceIds());
        assertThrows(UnsupportedOperationException.class, () -> codeQuery.productTypeCodes().add("SW"));
        assertThrows(UnsupportedOperationException.class, () -> deviceQuery.deviceIds().add(9L));
    }
}
