package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationGrant;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DevicePageReadScope;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DeviceJoinedPageSqlTest {
    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 23, 12, 0);

    static Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        for (var resource : List.of("mapper/authorization/ProjectViewGrantReadSql.xml",
                "mapper/projectscope/ProjectViewReadScopeSql.xml", "mapper/contract/ContractDeviceReadScopeSql.xml",
                "mapper/device/DeviceQueryMapper.xml", "mapper/device/DeviceJoinedPageMapper.xml")) {
            try (var input = DeviceJoinedPageSqlTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                // H2方言适配只用于内存失败测试；MySQL原文另以真实驱动只读复验。
                var xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("b'0'", "FALSE").replace("b'1'", "TRUE")
                        .replace(" AS BINARY)", " AS VARBINARY(256))");
                new XMLMapperBuilder(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        config, resource, config.getSqlFragments()).parse();
            }
        }
        return config;
    }

    static VisibleDevicePageQuery query(ProjectViewReadScopeApi.ReadScope projectScope) {
        return VisibleDevicePageQuery.builder().tenantId(1L).pageNo(1).pageSize(30)
                .organizationGrants(List.of(new DeviceOrganizationGrant(88L, 99L, "D99")))
                .readScope(new DevicePageReadScope(projectScope, new ContractDeviceVisibilityApi.ReadScope(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, 99L, "D99")))))
                .build();
    }

    static ProjectViewReadScopeApi.ReadScope scope(ProjectViewReadScopeApi.Mode mode) {
        return new ProjectViewReadScopeApi.ReadScope(1L, 7L, mode, AT,
                List.of(new ProjectViewReadScopeApi.Tree(10L, 7L), new ProjectViewReadScopeApi.Tree(20L, 8L)));
    }

    @Test void authorizationIsJoinedWithoutProjectOrContractParameterExpansionAndParsesWithTenantInterceptor() throws Exception {
        var q = query(scope(ProjectViewReadScopeApi.Mode.SCOPED));
        var bound = configuration().getMappedStatement(DeviceMapper.class.getName() + ".selectJoinedDevicePageRows")
                .getBoundSql(java.util.Map.of("query", q));
        assertNull(q.visibleProjectIds());
        assertNull(q.visibleContractNumbers());
        assertTrue(bound.getParameterMappings().size() < 60);
        assertFalse(bound.getSql().toUpperCase().contains("CONVERT("));
        assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(bound.getSql()));
    }

    @Test void pageAndCountUseTheSameProjectContractAndPairedOrganizationAuthorization() throws Exception {
        withDatabase(mapper -> {
            var page = mapper.selectJoinedDevicePage(query(scope(ProjectViewReadScopeApi.Mode.SCOPED)));
            assertEquals(6L, page.getTotal());
            assertEquals(List.of(15L, 8L, 3L, 2L, 1L, 16L).stream().sorted(java.util.Comparator.reverseOrder()).toList(),
                    page.getList().stream().map(item -> item.deviceId()).toList());
            assertEquals(2L, page.getList().stream().filter(item -> item.deviceId() == 1L).findFirst().orElseThrow().extendedCount());
        });
    }

    @Test void missingOwnersAndForeignProjectContextCloseOnlyTheirAuthorizationBranches() throws Exception {
        withDatabase(mapper -> {
            var q = query(null);
            assertEquals(Set.of(3L, 8L, 16L), ids(mapper, q));
            assertEquals(Set.of(3L, 8L, 16L), ids(mapper, query(new ProjectViewReadScopeApi.ReadScope(
                    2L, 7L, ProjectViewReadScopeApi.Mode.TENANT_ADMIN, AT, List.of()))));
            assertEquals(Set.of(8L), ids(mapper, q.toBuilder().readScope(new DevicePageReadScope(null, null)).build()));
        });
    }

    @Test void companyCodeFromDepartmentScopeCannotAuthorizeAnotherDepartmentsContract() throws Exception {
        withDatabase(sql -> sql.execute("UPDATE com_contract SET department_code='OTHER' WHERE contract_no='C_OK'"), mapper -> {
            var q = query(null).toBuilder().sn("CONTRACT").build();
            assertTrue(ids(mapper, q).isEmpty(), "合同公司匹配不能绕过同一范围行的部门约束");
        });
    }

    @Test void companyLevelGrantCoversOtherDepartmentsButNeverAnotherCompany() throws Exception {
        withDatabase(sql -> sql.execute("UPDATE com_contract SET department_code='OTHER' WHERE contract_no='C_OK'"), mapper -> {
            var q = query(null).toBuilder().readScope(new DevicePageReadScope(null,
                    new ContractDeviceVisibilityApi.ReadScope(List.of(new ContractDeviceVisibilityApi.OrganizationGrant(88L, null, null)))))
                    .build();
            assertEquals(Set.of(3L, 8L, 16L), ids(mapper, q));
        });
    }

    @Test void explicitProjectStillAuthorizesDeviceOutsideOrganizationButContractRelationAloneDoesNot() throws Exception {
        withDatabase(mapper -> {
            var q = query(scope(ProjectViewReadScopeApi.Mode.SCOPED));
            assertTrue(ids(mapper, q).contains(1L));
            assertFalse(ids(mapper, q).contains(5L));
        });
    }

    @Test void cannotCombineCompanyFromOneGrantWithDepartmentFromAnother() throws Exception {
        withDatabase(mapper -> {
            var q = query(null).toBuilder().readScope(new DevicePageReadScope(null,
                    new ContractDeviceVisibilityApi.ReadScope(List.of(
                            new ContractDeviceVisibilityApi.OrganizationGrant(88L, 100L, "D100"),
                            new ContractDeviceVisibilityApi.OrganizationGrant(89L, 99L, "D99")))))
                    .sn("CONTRACT").build();
            assertTrue(ids(mapper, q).isEmpty());
        });
    }

    @Test void emptyAndOutOfRangePagesRetainTheCorrectTotalWithoutReturningAnEmptyDeviceRow() throws Exception {
        withDatabase(mapper -> {
            var q = query(scope(ProjectViewReadScopeApi.Mode.SCOPED));
            var outside = mapper.selectJoinedDevicePage(q.toBuilder().pageNo(100).build());
            assertEquals(6L, outside.getTotal());
            assertTrue(outside.getList().isEmpty());
            var empty = mapper.selectJoinedDevicePage(q.toBuilder().sn("DOES_NOT_EXIST").build());
            assertEquals(0L, empty.getTotal());
            assertTrue(empty.getList().isEmpty());
        });
    }

    @Test void tenantAdministratorStillRequiresALiveProjectAndDoesNotInheritForeignOrDeletedProjects() throws Exception {
        withDatabase(mapper -> {
            var ids = ids(mapper, query(scope(ProjectViewReadScopeApi.Mode.TENANT_ADMIN)));
            assertTrue(ids.contains(4L));
            assertFalse(ids.contains(13L));
            assertFalse(ids.contains(17L));
        });
    }

    @Test void ineffectiveMembershipsAndGrantsCannotAuthorizeTheOtherProject() throws Exception {
        withDatabase(sql -> {
            sql.execute("INSERT INTO proj_project_member_assignment VALUES (1,21,7,'ACTIVE',true,'2099-01-01',NULL,false),(1,21,7,'ACTIVE',true,NULL,'2026-10-23 12:00:00',false),(1,21,7,'INACTIVE',true,NULL,NULL,false),(2,21,7,'ACTIVE',true,NULL,NULL,false),(1,21,8,'ACTIVE',true,NULL,NULL,false),(1,21,7,'ACTIVE',true,NULL,NULL,true)");
            sql.execute("INSERT INTO plt_authorization_grant VALUES (1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',1,'2099-01-01',NULL,false),(1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',1,'2020-01-01','2026-10-23 12:00:00',false),(1,'USER',7,'PROJ','PROJECT',21,'PROJECT_EDIT','CURRENT_PROJECT','ACTIVE',1,'2020-01-01',NULL,false),(2,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',1,'2020-01-01',NULL,false),(1,'USER',8,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',1,'2020-01-01',NULL,false),(1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','PATH_PLACEHOLDER','ACTIVE',1,'2020-01-01',NULL,false),(1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',1,NULL,NULL,false),(1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',1,'2020-01-01',NULL,true)");
        }, mapper -> assertFalse(ids(mapper, query(scope(ProjectViewReadScopeApi.Mode.SCOPED))).contains(4L)));
    }

    @Test void descendantGrantRequiresTheSameVersionedTreePathAndNeverUsesAPlaceholder() throws Exception {
        withDatabase(sql -> sql.execute("INSERT INTO plt_authorization_grant VALUES (1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','PROJECT_AND_DESCENDANTS','ACTIVE',1,'2020-01-01',NULL,false)"),
                mapper -> assertFalse(ids(mapper, query(scope(ProjectViewReadScopeApi.Mode.SCOPED))).contains(4L)));
        withDatabase(sql -> {
            sql.execute("INSERT INTO plt_authorization_grant VALUES (1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','PROJECT_AND_DESCENDANTS','ACTIVE',1,'2020-01-01',NULL,false)");
            sql.execute("INSERT INTO proj_project_tree_path VALUES (1,20,8,21,21,false)");
        }, mapper -> assertTrue(ids(mapper, query(scope(ProjectViewReadScopeApi.Mode.SCOPED))).contains(4L)));
    }

    @Test void anEmptyCurrentTreeScopeCannotBecomeAllProjects() throws Exception {
        withDatabase(mapper -> assertEquals(Set.of(3L, 8L, 16L), ids(mapper, query(
                new ProjectViewReadScopeApi.ReadScope(1L, 7L, ProjectViewReadScopeApi.Mode.SCOPED, AT, List.of())))));
    }

    static Set<Long> ids(DeviceMapper mapper, VisibleDevicePageQuery query) {
        return mapper.selectJoinedDevicePage(query).getList().stream().map(item -> item.deviceId()).collect(Collectors.toSet());
    }

    interface Check { void run(DeviceMapper mapper) throws Exception; }
    interface Setup { void run(java.sql.Statement sql) throws Exception; }

    static void withDatabase(Check check) throws Exception {
        withDatabase(sql -> {}, check);
    }

    static void withDatabase(Setup setup, Check check) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE proj_project(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,creator VARCHAR(64))");
            sql.execute("INSERT INTO proj_project VALUES (10,1,false,'0'),(11,1,false,'0'),(12,1,false,'7'),(20,1,false,'0'),(21,1,false,'0'),(22,1,true,'7'),(30,2,false,'7')");
            sql.execute("CREATE TABLE proj_project_tree_path(tenant_id BIGINT,root_project_id BIGINT,tree_version BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,deleted BOOLEAN)");
            sql.execute("INSERT INTO proj_project_tree_path VALUES (1,10,7,10,10,false),(1,10,7,10,11,false),(1,10,7,10,12,false),(1,10,7,11,11,false),(1,10,7,11,12,false),(1,20,8,20,20,false),(1,20,8,20,21,false),(1,20,6,20,22,false)");
            sql.execute("CREATE TABLE proj_project_member_assignment(tenant_id BIGINT,project_id BIGINT,user_id BIGINT,status VARCHAR(20),authorization_confirmed BOOLEAN,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("INSERT INTO proj_project_member_assignment VALUES (1,11,7,'ACTIVE',true,NULL,NULL,false),(1,21,7,'ACTIVE',false,NULL,NULL,false),(1,22,7,'ACTIVE',true,NULL,NULL,false)");
            sql.execute("CREATE TABLE plt_authorization_grant(tenant_id BIGINT,subject_type_code VARCHAR(20),subject_id BIGINT,resource_context_code VARCHAR(20),resource_type_code VARCHAR(20),resource_id BIGINT,action_code VARCHAR(40),scope_code VARCHAR(40),status_code VARCHAR(20),current_marker INT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("INSERT INTO plt_authorization_grant VALUES (1,'USER',7,'PROJ','PROJECT',20,'PROJECT_MANAGE','CURRENT_PROJECT','ACTIVE',1,'2020-01-01',NULL,false),(1,'USER',7,'PROJ','PROJECT',11,'PROJECT_VIEW','PROJECT_AND_DESCENDANTS','ACTIVE',1,'2020-01-01',NULL,false),(1,'USER',7,'PROJ','PROJECT',21,'PROJECT_VIEW','CURRENT_PROJECT','ACTIVE',NULL,'2020-01-01',NULL,false)");
            sql.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(64),company_code VARCHAR(64),deleted BOOLEAN)");
            sql.execute("INSERT INTO com_contract VALUES (1,1,'C_OK','C01',false),(2,1,'C_PROJECT','OTHER',false),(3,1,'C_CONFLICT','C01',false)");
            sql.execute("ALTER TABLE com_contract ADD company_id BIGINT");
            sql.execute("ALTER TABLE com_contract ADD department_code VARCHAR(64)");
            sql.execute("UPDATE com_contract SET company_id=CASE WHEN company_code='C01' THEN 88 ELSE 89 END,department_code='D99'");
            sql.execute("CREATE TABLE com_shipment_contract_reference(tenant_id BIGINT,contract_no VARCHAR(64),company_code VARCHAR(64),deleted BOOLEAN)");
            sql.execute("INSERT INTO com_shipment_contract_reference VALUES (1,'C_CONFLICT','OTHER',false)");
            sql.execute("ALTER TABLE com_shipment_contract_reference ADD company_id BIGINT");
            sql.execute("ALTER TABLE com_shipment_contract_reference ADD department_id BIGINT");
            sql.execute("ALTER TABLE com_shipment_contract_reference ADD department_code VARCHAR(64)");
            sql.execute("CREATE TABLE com_project_contract_relation(tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,status VARCHAR(20),effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("INSERT INTO com_project_contract_relation VALUES (1,20,2,'ACTIVE',NULL,false)");
            sql.execute("CREATE TABLE ast_device (id BIGINT,tenant_id BIGINT,sn VARCHAR(64),name VARCHAR(128),status VARCHAR(20),product_code VARCHAR(64),product_model VARCHAR(64),product_name VARCHAR(64),shipment_time TIMESTAMP,package_no VARCHAR(64),contract_no VARCHAR(64),shipment_record_id BIGINT,project_id BIGINT,customer_id BIGINT,warranty_start_date DATE,warranty_end_date DATE,warranty_status VARCHAR(20),conp_version VARCHAR(64),conp_type VARCHAR(64),conp_series VARCHAR(64),conp_mark VARCHAR(64),sync_status VARCHAR(20),location_snapshot VARCHAR(1024),location_resolution_status VARCHAR(20),company_id BIGINT,company_name VARCHAR(64),department_id BIGINT,department_code VARCHAR(64),department_name VARCHAR(64),organization_source VARCHAR(20),deleted BOOLEAN)");
            sql.execute("INSERT INTO ast_device(id,tenant_id,sn,project_id,contract_no,company_id,department_id,deleted) VALUES (1,1,'ONE',11,NULL,NULL,NULL,false),(2,1,'RELATED',NULL,NULL,NULL,NULL,false),(3,1,'CONTRACT',NULL,'C_OK',NULL,NULL,false),(4,1,'HIDDEN',21,NULL,NULL,NULL,false),(5,1,'PROJECT_CONTRACT',NULL,'C_PROJECT',NULL,NULL,false),(6,1,'LOWER',NULL,'c_ok',NULL,NULL,false),(7,1,'SPACE',NULL,'C_OK ',NULL,NULL,false),(8,1,'ORGANIZATION',NULL,NULL,88,99,false),(9,1,'OTHER_DEPT',NULL,NULL,88,100,false),(10,1,'FUTURE',NULL,NULL,NULL,NULL,false),(11,1,'EXPIRED',NULL,NULL,NULL,NULL,false),(12,1,'DELETED',11,NULL,NULL,NULL,true),(13,2,'FOREIGN',11,NULL,NULL,NULL,false),(14,1,'OLD_TREE',22,NULL,NULL,NULL,false),(15,1,'CREATOR',12,NULL,NULL,NULL,false),(16,1,'CONTRACT_AGAIN',NULL,'C_OK',NULL,NULL,false),(17,1,'DELETED_PROJECT',22,NULL,NULL,NULL,false),(18,1,'CONFLICT',NULL,'C_CONFLICT',NULL,NULL,false)");
            sql.execute("CREATE TABLE ast_device_project_relationship(tenant_id BIGINT,device_sn VARCHAR(64),project_id BIGINT,effective_from TIMESTAMP,effective_to TIMESTAMP,deleted BOOLEAN)");
            sql.execute("INSERT INTO ast_device_project_relationship VALUES (1,'RELATED',12,'2020-01-01',NULL,false),(1,'FUTURE',11,'2099-01-01',NULL,false),(1,'EXPIRED',11,'2020-01-01','2021-01-01',false)");
            sql.execute("CREATE TABLE ast_device_network_version(tenant_id BIGINT,device_sn VARCHAR(64),source_system VARCHAR(40),sync_status VARCHAR(20),boot_version VARCHAR(64),cpld_version VARCHAR(64),deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_device_warranty_record(tenant_id BIGINT,device_sn VARCHAR(64),extended BOOLEAN,deleted BOOLEAN)");
            sql.execute("INSERT INTO ast_device_warranty_record VALUES (1,'ONE',true,false),(1,'ONE',true,false),(2,'ONE',true,false),(1,'ONE',true,true),(1,'ONE',false,false)");
            setup.run(sql);
        }
        var config = configuration();
        config.setEnvironment(new Environment("joined-page", new JdbcTransactionFactory(), source));
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            check.run(session.getMapper(DeviceMapper.class));
        }
    }
}
