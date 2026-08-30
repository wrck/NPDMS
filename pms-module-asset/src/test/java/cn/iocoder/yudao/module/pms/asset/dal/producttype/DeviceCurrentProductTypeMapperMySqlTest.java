package cn.iocoder.yudao.module.pms.asset.dal.producttype;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.projection.AuthorizedDeviceProductTypeProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.AuthorizedDeviceProductTypesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypesByCodesQuery;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DeviceCurrentProductTypeMapperMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeviceCurrentProductTypeMapperMySqlTest {

    @Resource private AssetProductTypeMapper productTypeMapper;
    @Resource private AssetProductTypeSourceMappingMapper sourceMappingMapper;
    @Resource private DeviceCurrentProductTypeMapper currentTypeMapper;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private SqlSession sqlSession;

    private long idBase;
    private LocalDateTime effectiveAt;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> values = environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(values, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(values, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.asset");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        idBase = 986_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 100L;
        effectiveAt = LocalDateTime.of(2026, 8, 30, 12, 0);
        insertProductType(idBase + 1, 1L, "PT-A", "产品类型A", true);
        insertProductType(idBase + 2, 1L, "PT-B", "产品类型B", false);
        insertProductType(idBase + 3, 2L, "PT-A", "跨租户产品类型A", true);
        insertMapping(idBase + 11, 1L, "mapping-a", idBase + 1);
        insertDevice(idBase + 21, 1L, "RESOLVED", 701L);
        insertDevice(idBase + 22, 1L, "UNKNOWN", 999L);
        insertDevice(idBase + 23, 1L, "CONFLICT", 999L);
        insertDevice(idBase + 24, 1L, "UNRESOLVED", 999L);
        insertDevice(idBase + 25, 1L, "EXPIRED", 999L);
        insertDevice(idBase + 26, 1L, "FUTURE", 999L);
        insertDevice(idBase + 27, 1L, "ENDED", 999L);
        insertDevice(idBase + 28, 1L, "DELETED", 999L);
        insertDevice(idBase + 29, 2L, "OTHER-TENANT", 701L);
        insertCurrentType(idBase + 31, idBase + 21, "RESOLVED", idBase + 1, "PT-A", idBase + 11, 1L);
        insertCurrentType(idBase + 32, idBase + 22, "UNKNOWN", null, null, null, 1L);
        insertCurrentType(idBase + 33, idBase + 23, "CONFLICT", null, null, null, 1L);
        insertCurrentType(idBase + 34, idBase + 24, "UNRESOLVED", null, null, null, 1L);
        insertRelationship(idBase + 41, idBase + 22, 701L, effectiveAt, null, false);
        insertRelationship(idBase + 42, idBase + 23, 701L, effectiveAt.minusHours(1), effectiveAt.plusHours(1), false);
        insertRelationship(idBase + 43, idBase + 24, 701L, effectiveAt.minusHours(1), null, false);
        insertRelationship(idBase + 44, idBase + 25, 701L, effectiveAt.minusHours(2), effectiveAt.minusHours(1), false);
        insertRelationship(idBase + 45, idBase + 26, 701L, effectiveAt.plusHours(1), null, false);
        insertRelationship(idBase + 46, idBase + 27, 701L, effectiveAt.minusHours(1), effectiveAt, false);
        insertRelationship(idBase + 47, idBase + 28, 701L, effectiveAt.minusHours(1), null, true);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM ast_device_current_product_type WHERE id BETWEEN ? AND ?", idBase + 31, idBase + 39);
            jdbcTemplate.update("DELETE FROM ast_device_project_relationship WHERE id BETWEEN ? AND ?", idBase + 41, idBase + 49);
            jdbcTemplate.update("DELETE FROM ast_product_type_source_mapping WHERE id BETWEEN ? AND ?", idBase + 11, idBase + 19);
            jdbcTemplate.update("DELETE FROM ast_product_type WHERE id BETWEEN ? AND ?", idBase + 1, idBase + 9);
            jdbcTemplate.update("DELETE FROM ast_device WHERE id BETWEEN ? AND ?", idBase + 21, idBase + 29);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void selectsProductTypesByTenantAndCodes() {
        jdbcTemplate.update("UPDATE ast_product_type SET deleted=b'1' WHERE id=?", idBase + 2);

        List<AssetProductTypeDO> result = productTypeMapper.selectByCodes(
                new ProductTypesByCodesQuery(1L, Set.of("PT-A", "PT-B", "PT-MISSING")));

        assertEquals(1, result.size());
        assertEquals("PT-A", result.getFirst().getTypeCode());
        assertEquals("产品类型A", result.getFirst().getDisplayName());
        assertTrue(result.getFirst().getEnabled());
    }

    @Test
    @Transactional
    void locksSourceMappingByStableTenantSourceKey() {
        AssetProductTypeSourceMappingDO result = sourceMappingMapper.selectForUpdate(
                new ProductTypeSourceMappingLockQuery(1L, "CRM", "mapping-a"));
        AssetProductTypeSourceMappingDO otherTenant = sourceMappingMapper.selectForUpdate(
                new ProductTypeSourceMappingLockQuery(2L, "CRM", "mapping-a"));
        jdbcTemplate.update("UPDATE ast_product_type_source_mapping SET deleted=b'1' WHERE id=?", idBase + 11);
        sqlSession.clearCache();
        AssetProductTypeSourceMappingDO deleted = sourceMappingMapper.selectForUpdate(
                new ProductTypeSourceMappingLockQuery(1L, "CRM", "mapping-a"));

        assertEquals(idBase + 11, result.getId());
        assertEquals(idBase + 1, result.getProductTypeId());
        assertNull(otherTenant);
        assertNull(deleted);
    }

    @Test
    void selectsCurrentProjectionAndEffectiveRelationshipsWithResolutionStates() {
        Set<Long> requestedDeviceIds = Set.of(
                idBase + 21, idBase + 22, idBase + 23, idBase + 24,
                idBase + 25, idBase + 26, idBase + 27, idBase + 28, idBase + 29);

        List<AuthorizedDeviceProductTypeProjection> result = currentTypeMapper.selectAuthorizedCurrent(
                new AuthorizedDeviceProductTypesQuery(1L, requestedDeviceIds, Set.of(701L), effectiveAt));

        assertEquals(List.of(idBase + 21, idBase + 22, idBase + 23, idBase + 24),
                result.stream().map(AuthorizedDeviceProductTypeProjection::deviceId).toList());
        AuthorizedDeviceProductTypeProjection resolved = result.getFirst();
        assertEquals("PT-A", resolved.productTypeCode());
        assertEquals("产品类型A", resolved.displayName());
        assertTrue(resolved.enabled());
        assertEquals("FRESH", resolved.syncStatus());
        assertEquals("RESOLVED", resolved.resolutionStatus());
        assertEquals("UNKNOWN", result.get(1).resolutionStatus());
        assertEquals("CONFLICT", result.get(2).resolutionStatus());
        assertEquals("UNRESOLVED", result.get(3).resolutionStatus());
        assertTrue(result.subList(1, 4).stream().allMatch(item -> item.productTypeCode() == null));
        assertFalse(result.stream().anyMatch(item -> item.deviceId().equals(idBase + 29)));
    }

    private void insertProductType(long id, long tenantId, String code, String name, boolean enabled) {
        jdbcTemplate.update("INSERT INTO ast_product_type "
                        + "(id,type_code,display_name,enabled,source_system,source_key,source_version,source_updated_at,"
                        + "payload_hash,sync_status,synced_at,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?, 'CRM',?, '1', ?, ?, 'FRESH', ?,0,'mysql-it','mysql-it',b'0',?)",
                id, code, name, enabled, "it-fast002-type-" + id, effectiveAt.minusDays(1), "a".repeat(64),
                effectiveAt.minusHours(1), tenantId);
    }

    private void insertMapping(long id, long tenantId, String sourceKey, long productTypeId) {
        jdbcTemplate.update("INSERT INTO ast_product_type_source_mapping "
                        + "(id,source_system,source_key,source_version,source_updated_at,payload_hash,product_type_id,"
                        + "mapping_status,synced_at,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,'CRM',?,'1',?, ?,?,'RESOLVED',?,0,'mysql-it','mysql-it',b'0',?)",
                id, sourceKey, effectiveAt.minusDays(1), "b".repeat(64), productTypeId,
                effectiveAt.minusHours(1), tenantId);
    }

    private void insertDevice(long id, long tenantId, String suffix, Long projectId) {
        jdbcTemplate.update("INSERT INTO ast_device "
                        + "(id,sn,name,project_id,project_assignment_version,status,source_system,source_key,sync_status,"
                        + "version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,0,'ACTIVE','PMS',?,'FRESH',0,'mysql-it','mysql-it',b'0',?)",
                id, "IT-FAST002-" + idBase + "-" + suffix, "FAST002 " + suffix, projectId,
                "it-fast002-device-" + id, tenantId);
    }

    private void insertCurrentType(long id, long deviceId, String status, Long productTypeId,
                                   String productTypeCode, Long sourceMappingId, long tenantId) {
        jdbcTemplate.update("INSERT INTO ast_device_current_product_type "
                        + "(id,device_id,product_type_id,product_type_code,source_mapping_id,resolution_status,"
                        + "source_version,source_updated_at,effective_from,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,'1',?,?,0,'mysql-it','mysql-it',b'0',?)",
                id, deviceId, productTypeId, productTypeCode, sourceMappingId, status,
                effectiveAt.minusDays(1), effectiveAt.minusDays(1), tenantId);
    }

    private void insertRelationship(long id, long deviceId, long projectId, LocalDateTime from,
                                    LocalDateTime to, boolean deleted) {
        String deviceSn = jdbcTemplate.queryForObject(
                "SELECT sn FROM ast_device WHERE id=?", String.class, deviceId);
        jdbcTemplate.update("INSERT INTO ast_device_project_relationship "
                        + "(id,device_sn,project_id,relationship_type,effective_from,effective_to,assignment_version,"
                        + "reason,operation_id,source_system,source_key,source_version,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,'LEASE',?,?,1,'FAST002','it-fast002-op','PMS',?,'1',0,'mysql-it','mysql-it',?,1)",
                id, deviceSn, projectId, from, to, "it-fast002-relation-" + id, deleted);
    }

    private static Map<String, String> environment() {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path dotenv = findDotenv();
        if (dotenv == null) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) {
                    continue;
                }
                int separator = value.indexOf('=');
                values.putIfAbsent(value.substring(0, separator).trim(),
                        unquote(value.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("读取真实MySQL集成测试环境失败", ex);
        }
    }

    private static Path findDotenv() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("compose.yaml"))) {
                return Files.isRegularFile(path.resolve(".env")) ? path.resolve(".env") : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan("cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
