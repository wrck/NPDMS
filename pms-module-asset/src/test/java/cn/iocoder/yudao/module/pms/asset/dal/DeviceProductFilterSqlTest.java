package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceProductQuery;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterGroup;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterMatchMode;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the production XML: product matching must happen in SQL, not a Java predicate replica. */
class DeviceProductFilterSqlTest {
    SqlSession session;
    DeviceMapper devices;
    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:installation_filter_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE ast_product_official_info(id BIGINT PRIMARY KEY,tenant_id BIGINT,product_code VARCHAR(64),product_model VARCHAR(64),product_desc VARCHAR(64),product_category VARCHAR(64),product_type VARCHAR(64),source_system VARCHAR(20),status VARCHAR(20),deleted INT)");
            sql.execute("INSERT INTO ast_product_official_info VALUES "
                    + "(1,1,'CODE','MODEL-A','设备说明','CAT-A','TYPE-A','CRM','ACTIVE',0),"
                    + "(2,1,'OTHER','OTHER','OTHER','OTHER','OTHER','CRM','ACTIVE',0),"
                    + "(3,1,'DUP','M1','D','C','T','CRM','ACTIVE',0),"
                    + "(4,2,'FOREIGN','M','D','C','T','CRM','ACTIVE',0),(5,1,'INACTIVE','M','D','C','T','CRM','INACTIVE',0),"
                    + "(6,1,'DELETED','M','D','C','T','CRM','ACTIVE',1),(7,1,'LEGACY','M','D','C','T','DPPMS','ACTIVE',0),"
                    + "(8,1,'PAIR','RIGHT','D','C','T','CRM','ACTIVE',0),"
                    + "(9,1,'A_10%','x.y','D','C','T','CRM','ACTIVE',0),(10,1,'B','xay','D','C','T','CRM','ACTIVE',0)");
            sql.execute("CREATE TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,product_code VARCHAR(64),deleted INT)");
            sql.execute("INSERT INTO ast_device VALUES(1,1,10,'CODE',0),(2,1,10,'OTHER',0),(3,1,10,'DUP',0),(4,1,10,'FOREIGN',0),(5,1,10,'INACTIVE',0),(6,1,10,'DELETED',0),(7,1,10,'LEGACY',0),(8,1,10,'PAIR',0),(9,1,10,NULL,0),(10,1,10,'A_10%',0),(11,1,10,'B',0),(12,1,10,'MISSING',0),(97,1,10,'OTHER',1),(98,1,20,'OTHER',0),(99,2,10,'OTHER',0)");
        }
        var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setEnvironment(new Environment("installation-filter", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(config, ""),
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO.class);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        var interceptor = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
        config.addInterceptor(interceptor);
        try (var productFilterSql = getClass().getResourceAsStream("/mapper/product/ProductFilterSql.xml")) {
            new XMLMapperBuilder(productFilterSql, config, "product-filter", config.getSqlFragments()).parse();
        }
        try (var deviceFilterSql = getClass().getResourceAsStream("/mapper/device/DeviceProductFilterMapper.xml")) {
            new XMLMapperBuilder(deviceFilterSql, config, "device-product-filter", config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        devices = session.getMapper(DeviceMapper.class);
    }
    @AfterEach void close() { if (session != null) session.close(); cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    ProductFilterGroup group(String value) { return JsonUtils.parseObject(value, ProductFilterGroup.class); }
    List<ProductFilterGroup> parseGroups(String... groups) { return Arrays.stream(groups).map(this::group).toList(); }
    List<Long> exempt(String... groups) {
        return devices.selectProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(1L, 10L,
                parseGroups(groups), ProductFilterMatchMode.NOT_MATCHING));
    }
    List<Long> matching(String... groups) {
        return devices.selectProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(1L, 10L, parseGroups(groups)));
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"productCodeLike\":\"C%\"}", "{\"productModelLike\":\"MODEL-_\"}", "{\"productDescLike\":\"_备说明\"}",
            "{\"productCategoryLike\":\"CAT-%\"}", "{\"productTypeLike\":\"TYPE-%\"}",
            "{\"productCodeIn\":[\"CODE\"]}", "{\"productModelIn\":[\"MODEL-A\"]}", "{\"productDescIn\":[\"设备说明\"]}",
            "{\"productCategoryIn\":[\"CAT-A\"]}", "{\"productTypeIn\":[\"TYPE-A\"]}",
            "{\"productCodeNotIn\":[\"OTHER\"]}", "{\"productModelNotIn\":[\"OTHER\"]}", "{\"productDescNotIn\":[\"OTHER\"]}",
            "{\"productCategoryNotIn\":[\"OTHER\"]}", "{\"productTypeNotIn\":[\"OTHER\"]}"
    }) void everyFieldAndOperatorFiltersInsideSql(String group) {
        var ids = exempt(group);
        assertFalse(ids.contains(1L)); assertTrue(ids.contains(2L));
        assertTrue(Collections.disjoint(ids, Set.of(97L, 98L, 99L)));
    }
    @Test void groupsAreOrFieldsAndAndNestedAlternativesUseTheirOwnParameters() {
        var ids = exempt("{\"productCodeLike\":\"C%\",\"productTypeNotIn\":[\"TYPE-A\"]}",
                "{\"productModelIn\":[\"OTHER\"],\"productCategoryIn\":[\"OTHER\"]}");
        assertTrue(ids.contains(1L)); assertFalse(ids.contains(2L));
        ids = exempt("{\"productCodeLike\":\"C%\",\"alternativeGroups\":[{\"productModelIn\":[\"MODEL-A\"],\"productTypeIn\":[\"NO-MATCH\"]},{\"productCategoryIn\":[\"CAT-A\"]}]}");
        assertFalse(ids.contains(1L)); assertTrue(ids.contains(2L));
        ids = exempt("{\"productCodeLike\":\"C%\",\"alternativeGroups\":[{\"productModelIn\":[\"OTHER\"]}]}");
        assertTrue(ids.contains(1L));
    }
    @Test void missingDeletedAndCrossTenantMetadataNeverParticipateInEitherMode() {
        var ids = exempt("{\"productTypeIn\":[\"TYPE-A\"]}");
        assertFalse(ids.contains(1L)); assertTrue(ids.contains(2L));
        assertTrue(Collections.disjoint(ids, Set.of(4L,6L,9L,12L)));
        ids = exempt("{\"productTypeNotIn\":[\"OTHER\"]}");
        assertTrue(Collections.disjoint(ids, Set.of(4L,6L,9L,12L)));
        assertTrue(exempt("{\"productCodeIn\":[\"CODE\"]}").contains(3L));
    }
    @Test void inactiveAndLegacySourceProductMastersCanMatchAndSqlWildcardsRemainEffective() {
        var ids = matching("{\"productTypeIn\":[\"T\"]}");
        assertTrue(ids.containsAll(Set.of(3L,5L,7L)));
        assertTrue(Collections.disjoint(ids, Set.of(4L,6L,9L,12L)));
        assertFalse(exempt("{\"productCodeLike\":\"A\\\\_10\\\\%\"}").contains(10L));
        ids = exempt("{\"productModelLike\":\"x.y\"}");
        assertFalse(ids.contains(10L)); assertTrue(ids.contains(11L));
    }
    @Test void emptyAndForeignTenantScopesStayClosedThroughTenantInterceptor() {
        assertTrue(devices.selectProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(1L, 10L, List.of())).isEmpty());
        assertTrue(devices.selectProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(2L, 10L, List.of(group("{\"productCodeIn\":[\"CODE\"]}")))).isEmpty());
    }
    @Test void opticalModuleDefaultUsesAuthoritativeTypeAndNeverExemptsNullOrMissingMetadata() throws Exception {
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO ast_product_official_info VALUES "
                    + "(20,1,'OPTICAL','M','光模块','C','光模块','CRM','ACTIVE',0),"
                    + "(21,1,'02010257','M','名称含光模块','C','外购件','CRM','ACTIVE',0),"
                    + "(22,1,'02011299','M','光模块','C','FW','CRM','ACTIVE',0),"
                    + "(23,1,'UNKNOWN-TYPE','M','光模块','C',NULL,'CRM','ACTIVE',0)");
            sql.execute("INSERT INTO ast_device VALUES (20,1,10,'OPTICAL',0),(21,1,10,'02010257',0),"
                    + "(22,1,10,'02011299',0),(23,1,10,'UNKNOWN-TYPE',0),(24,1,10,'NO-MASTER',0)");
        }
        var filter="{\"productTypeNotIn\":[\"光模块\"]}";
        assertEquals(List.of(20L), exempt(filter));
        assertTrue(matching(filter).containsAll(Set.of(21L,22L)));
        assertTrue(Collections.disjoint(matching(filter), Set.of(20L,23L,24L)));
    }
}
