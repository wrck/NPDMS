package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDeviceIdsQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BASE_PRODUCT_TEMP_MYSQL_TEST", matches = "true")
class DeviceTenantWideReadMySqlTest {
    @Test void nativePageAndBatchKeepTenantDeletionAndEmptyFilterBoundaries() throws Exception {
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()}, getClass().getClassLoader())) {
            var driver = (java.sql.Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, loader).getConstructor().newInstance();
            var properties = new Properties();
            properties.setProperty("user", System.getenv("BASE_PRODUCT_DB_USER"));
            properties.setProperty("password", System.getenv("BASE_PRODUCT_DB_PASSWORD"));
            try (var db = driver.connect(System.getenv("BASE_PRODUCT_DB_URL"), properties)) {
                var data = new SingleConnectionDataSource(db, true);
                var jdbc = new JdbcTemplate(data);
                var configuration = new Configuration();
                configuration.setMapUnderscoreToCamelCase(true);
                configuration.setEnvironment(new Environment("temporary", new SpringManagedTransactionFactory(), data));
                for (var resource : List.of("mapper/authorization/ProjectViewGrantReadSql.xml", "mapper/projectscope/ProjectViewReadScopeSql.xml",
                        "mapper/contract/ContractDeviceReadScopeSql.xml", "mapper/device/DeviceCurrentModelMapper.xml", "mapper/device/DeviceQueryMapper.xml", "mapper/device/DeviceJoinedPageMapper.xml")) {
                    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                        assertNotNull(input, resource);
                        new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
                    }
                }
                var mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(DeviceMapper.class);
                // Exercise the unchanged native pagination SQL against existing isolated data, read-only.
                // MySQL cannot reuse a TEMPORARY table through the page CTE twice.
                var query = VisibleDevicePageQuery.builder().tenantId(1L).tenantWideRead(true).pageNo(1).pageSize(1).build();
                db.setReadOnly(true);
                db.setAutoCommit(false);
                try {
                    long count = jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=1 AND deleted=0", Long.class);
                    var expected = jdbc.queryForList("SELECT id FROM ast_device WHERE tenant_id=1 AND deleted=0 ORDER BY id DESC LIMIT 1", Long.class);
                    var page = mapper.selectVisibleDevicePage(query);
                    assertEquals(count, page.getTotal());
                    assertEquals(expected, page.getList().stream().map(d -> d.deviceId()).toList());
                } finally {
                    db.rollback();
                    db.setAutoCommit(true);
                    db.setReadOnly(false);
                }
                // Connection-local shadows of the existing isolated test schema; no persistent writes.
                for (var table : List.of("ast_device", "ast_product_official_info", "ast_device_network_version", "ast_device_warranty_record")) {
                    jdbc.execute("CREATE TEMPORARY TABLE fixture_" + table + " LIKE " + table);
                    jdbc.execute("CREATE TEMPORARY TABLE " + table + " LIKE fixture_" + table);
                }
                jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,name,status,source_system,creator,updater,deleted) VALUES(1,1,'READ-1','unassigned','IN_USE','TEST','fixture','fixture',0),(2,1,'READ-2','unassigned','IN_USE','TEST','fixture','fixture',0),(3,2,'READ-FOREIGN','foreign','IN_USE','TEST','fixture','fixture',0),(4,1,'READ-DELETED','deleted','IN_USE','TEST','fixture','fixture',1)");
                assertEquals(2L, mapper.selectVisibleDeviceCount(query));
                assertEquals(1L, mapper.selectVisibleDeviceCount(query.toBuilder().sn("READ-1").build()));
                assertEquals(0L, mapper.selectVisibleDeviceCount(query.toBuilder().matchingProjectIds(Set.of()).build()));
                assertEquals(0L, mapper.selectVisibleDeviceCount(query.toBuilder().tenantWideRead(false).build()));
                assertEquals(List.of(1L), mapper.selectJoinedDeviceIds(new VisibleDeviceIdsQuery(1L, Set.of(1L, 3L, 4L), null, List.of(), null, true)).stream().map(d -> d.getId()).toList());
                assertTrue(mapper.selectJoinedDeviceIds(new VisibleDeviceIdsQuery(1L, Set.of(), null, List.of(), null, true)).isEmpty());
            }
        }
    }
}
