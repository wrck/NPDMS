package cn.iocoder.yudao.module.pms.asset.dal.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.AuthorizedDeviceProductTypesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypesByCodesQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DeviceCurrentProductTypeMapperTest {

    private static String productTypeXml;
    private static String sourceMappingXml;
    private static String currentTypeXml;

    @BeforeAll
    static void loadMapperXml() throws Exception {
        Path mapperRoot = locateRepositoryRoot().resolve(
                "pms-module-asset/src/main/resources/mapper/producttype");
        productTypeXml = Files.readString(
                mapperRoot.resolve("AssetProductTypeMapper.xml"), StandardCharsets.UTF_8);
        sourceMappingXml = Files.readString(
                mapperRoot.resolve("AssetProductTypeSourceMappingMapper.xml"), StandardCharsets.UTF_8);
        currentTypeXml = Files.readString(
                mapperRoot.resolve("DeviceCurrentProductTypeMapper.xml"), StandardCharsets.UTF_8);
    }

    @Test
    void usesScenarioQueriesAndCopiesCollections() throws Exception {
        Method byCodes = AssetProductTypeMapper.class.getMethod(
                "selectByCodes", ProductTypesByCodesQuery.class);
        Method lock = AssetProductTypeSourceMappingMapper.class.getMethod(
                "selectForUpdate", ProductTypeSourceMappingLockQuery.class);
        Method authorized = DeviceCurrentProductTypeMapper.class.getMethod(
                "selectAuthorizedCurrent", AuthorizedDeviceProductTypesQuery.class);
        assertEquals(1, byCodes.getParameterCount());
        assertEquals(1, lock.getParameterCount());
        assertEquals(1, authorized.getParameterCount());

        Set<String> codes = new java.util.HashSet<>(Set.of("PT-A"));
        ProductTypesByCodesQuery codesQuery = new ProductTypesByCodesQuery(1L, codes);
        codes.add("PT-B");
        assertEquals(Set.of("PT-A"), codesQuery.productTypeCodes());
        assertThrows(UnsupportedOperationException.class,
                () -> codesQuery.productTypeCodes().add("PT-C"));

        Set<Long> deviceIds = new java.util.HashSet<>(Set.of(11L));
        Set<Long> projectIds = new java.util.HashSet<>(Set.of(21L));
        AuthorizedDeviceProductTypesQuery authorizedQuery = new AuthorizedDeviceProductTypesQuery(
                1L, deviceIds, projectIds, LocalDateTime.of(2026, 8, 30, 12, 0));
        deviceIds.add(12L);
        projectIds.add(22L);
        assertEquals(Set.of(11L), authorizedQuery.deviceIds());
        assertEquals(Set.of(21L), authorizedQuery.visibleProjectIds());
    }

    @Test
    void returnsEmptyBeforeExecutingDynamicSqlForEmptyCollections() {
        AssetProductTypeMapper productTypeMapper = mock(AssetProductTypeMapper.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        DeviceCurrentProductTypeMapper currentTypeMapper = mock(DeviceCurrentProductTypeMapper.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        assertTrue(productTypeMapper.selectByCodes(
                new ProductTypesByCodesQuery(1L, Set.of())).isEmpty());
        assertTrue(currentTypeMapper.selectAuthorizedCurrent(
                new AuthorizedDeviceProductTypesQuery(
                        1L, Set.of(), Set.of(21L), LocalDateTime.now())).isEmpty());
        assertTrue(currentTypeMapper.selectAuthorizedCurrent(
                new AuthorizedDeviceProductTypesQuery(
                        1L, Set.of(11L), Set.of(), LocalDateTime.now())).isEmpty());
        assertTrue(currentTypeMapper.selectAuthorizedCurrent(
                new AuthorizedDeviceProductTypesQuery(
                        1L, null, Set.of(21L), LocalDateTime.now())).isEmpty());
        assertTrue(currentTypeMapper.selectAuthorizedCurrent(
                new AuthorizedDeviceProductTypesQuery(
                        1L, Set.of(11L), null, LocalDateTime.now())).isEmpty());
        org.mockito.Mockito.verify(productTypeMapper, org.mockito.Mockito.never())
                .selectByCodesInternal(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(currentTypeMapper, org.mockito.Mockito.never())
                .selectAuthorizedCurrentInternal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsLocksAndDynamicQueriesInXmlWithoutTextInterpolation() {
        assertTrue(sourceMappingXml.contains("FROM ast_product_type_source_mapping"));
        assertTrue(sourceMappingXml.contains("tenant_id = #{query.tenantId}"));
        assertTrue(sourceMappingXml.contains("source_system = #{query.sourceSystem}"));
        assertTrue(sourceMappingXml.contains("source_key = #{query.sourceKey}"));
        assertTrue(sourceMappingXml.contains("FOR UPDATE"));
        assertFalse(sourceMappingXml.contains("LIMIT 1"));
        assertTrue(productTypeXml.contains("FROM ast_product_type"));
        assertTrue(productTypeXml.contains("deleted = b'0'"));
        assertTrue(productTypeXml.contains("collection=\"query.productTypeCodes\""));
        assertFalse(allXml().contains("${"));
        assertFalse(allXml().contains("@Select"));
    }

    @Test
    void appliesTenantRequestAndEffectiveProjectVisibilityInOneLeftJoinQuery() {
        assertTrue(currentTypeXml.contains("FROM ast_device d"));
        assertTrue(currentTypeXml.contains("LEFT JOIN ast_device_current_product_type current_type"));
        assertTrue(currentTypeXml.contains("AND current_type.current_marker = 1"));
        assertTrue(currentTypeXml.contains("AND current_type.deleted = b'0'"));
        assertTrue(currentTypeXml.contains("LEFT JOIN ast_product_type product_type"));
        assertTrue(currentTypeXml.contains("AND product_type.deleted = b'0'"));
        assertTrue(currentTypeXml.contains("WHERE d.tenant_id = #{query.tenantId}"));
        assertTrue(currentTypeXml.contains("AND d.deleted = b'0'"));
        assertTrue(currentTypeXml.contains("collection=\"query.deviceIds\""));
        assertTrue(currentTypeXml.contains("d.project_id IN"));
        assertTrue(currentTypeXml.contains("FROM ast_device_project_relationship relation"));
        assertTrue(currentTypeXml.contains("relation.device_sn = d.sn"));
        assertTrue(currentTypeXml.contains("collection=\"query.visibleProjectIds\""));
        assertTrue(currentTypeXml.contains("relation.effective_from &lt;= #{query.effectiveAt}"));
        assertTrue(currentTypeXml.contains("relation.effective_to &gt; #{query.effectiveAt}"));
        assertTrue(currentTypeXml.contains("relation.deleted = b'0'"));
        assertFalse(currentTypeXml.contains("CURRENT_TIMESTAMP"));
        assertFalse(currentTypeXml.contains("relationship_type"));
        assertFalse(currentTypeXml.contains("conp_type"));
    }

    private static String allXml() {
        return productTypeXml + sourceMappingXml + currentTypeXml;
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/migrations"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法定位仓库根目录");
        }
        return current;
    }
}
