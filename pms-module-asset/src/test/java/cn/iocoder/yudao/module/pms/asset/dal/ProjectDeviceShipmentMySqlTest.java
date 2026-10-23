package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ShippedProjectQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Real production SQL, with connection-private fixtures and no changes to existing assets. */
@EnabledIfSystemProperty(named="skipITs",matches="false")
class ProjectDeviceShipmentMySqlTest {
    private java.sql.Connection connection;
    private SqlSession session;
    private ProjectDeviceShipmentMapper mapper;
    @BeforeEach void fixtures() throws Exception {
        String database = System.getenv("NPDMS_DB_NAME");
        assertTrue("npdms_domain_test".equals(database) || database != null && database.startsWith("npdms_cut_issues_"));
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));
        connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        try(var sql=connection.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE ast_device (id BIGINT PRIMARY KEY, tenant_id BIGINT, project_id BIGINT, sn VARCHAR(50), shipment_time DATETIME, deleted BIT)");
            sql.execute("INSERT INTO ast_device VALUES (1,1,10,'shipped','2026-10-01',b'0'),(2,1,10,'unshipped',NULL,b'0'),(3,1,20,'unshipped-only',NULL,b'0'),(4,1,30,NULL,'2026-10-01',b'0'),(5,1,30,'   ','2026-10-01',b'0'),(6,1,40,'deleted','2026-10-01',b'1'),(7,2,20,'foreign','2026-10-01',b'0'),(8,1,99,'outside','2026-10-01',b'0'),(9,1,NULL,'unassigned','2026-10-01',b'0'),(10,1,10,'another-shipped','2026-10-02',b'0')");
            sql.execute("ALTER TABLE ast_device ADD product_code VARCHAR(50), ADD product_model VARCHAR(50)");
            sql.execute("UPDATE ast_device SET product_code=CONCAT('CODE-',id), product_model=CONCAT('MODEL-',id)");
        }
        Configuration config=new Configuration(new Environment("shipment",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
        config.setMapUnderscoreToCamelCase(true);
        try(var xml=getClass().getResourceAsStream("/mapper/device/ProjectDeviceShipmentMapper.xml")) {
            new XMLMapperBuilder(xml,config,"shipment",config.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(config).openSession();
        mapper=session.getMapper(ProjectDeviceShipmentMapper.class);
    }
    @AfterEach void close() throws Exception { if(session!=null)session.close(); if(connection!=null)connection.close(); }
    @Test void shippedRequiresSerialAndShipmentTimeAndExcludesDeletedAndOtherTenant() {
        assertEquals(Set.of(10L),Set.copyOf(mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,Set.of(10L,20L,30L,40L)))));
    }
    @Test void mixedShipmentProjectIsShippedRatherThanUnshipped() {
        assertEquals(java.util.List.of(10L),mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,Set.of(10L))));
        assertEquals(java.util.List.of(),mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,Set.of(20L))));
    }
    @Test void missingAndEmptyCandidateScopeReturnNothing() {
        assertEquals(java.util.List.of(),mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,null)));
        assertEquals(java.util.List.of(),mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,Set.of())));
    }
    @Test void candidateScopeAndTenantAreBothApplied() {
        assertEquals(java.util.List.of(),mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,Set.of(20L,40L))));
        assertEquals(java.util.List.of(20L),mapper.selectShippedProjectIds(new ShippedProjectQuery(2L,Set.of(20L))));
    }

    @Test void devicePageIncludesOnlyCurrentProjectShippedSerialsAndMapsProductColumns() {
        var query = cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                .tenantId(1L).projectId(10L).pageNo(1).pageSize(1).build();
        assertEquals(2L, mapper.countProjectDevices(query));
        var first = mapper.selectProjectDevices(query);
        assertEquals(java.util.List.of(10L), first.stream().map(value -> value.getId()).toList());
        assertEquals("CODE-10", first.getFirst().getProductCode());
        assertEquals("MODEL-10", first.getFirst().getProductModel());
        var second = cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                .tenantId(1L).projectId(10L).pageNo(2).pageSize(1).build();
        assertEquals(java.util.List.of(1L), mapper.selectProjectDevices(second).stream().map(value -> value.getId()).toList());
        for (Long projectId : new Long[]{20L, 30L, 40L, null}) {
            var empty = cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                    .tenantId(1L).projectId(projectId).pageNo(1).pageSize(20).build();
            assertEquals(0L, mapper.countProjectDevices(empty));
            assertTrue(mapper.selectProjectDevices(empty).isEmpty());
        }
    }

    @Test void keywordSearchAppliesToSerialProductCodeAndModelWithinProjectAndTenant() {
        for (String keyword : java.util.List.of("another-shipped", "CODE-10", "MODEL-10")) {
            var query = cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                    .tenantId(1L).projectId(10L).keyword(keyword).pageNo(1).pageSize(20).build();
            assertEquals(1L, mapper.countProjectDevices(query));
            assertEquals(10L, mapper.selectProjectDevices(query).getFirst().getId());
        }
    }
}
