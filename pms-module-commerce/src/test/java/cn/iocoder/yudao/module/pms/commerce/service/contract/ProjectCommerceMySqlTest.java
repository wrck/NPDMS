package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production SELECT bodies against MySQL CTE fixtures; business tables are never modified. */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectCommerceMySqlTest {
    private Connection connection;
    private SqlSession session;
    private ProjectCommerceMapper mapper;

    @BeforeEach void fixtures() throws Exception {
        assertEquals("npdms_domain_test", System.getenv("DB_NAME"));
        assertEquals("24306", System.getenv("MYSQL_PORT"));
        connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        connection.setReadOnly(true);
        try (var sql = connection.createStatement()) {
            sql.execute("SET collation_connection = 'utf8mb4_unicode_ci'");
        }
        // MySQL cannot reopen one TEMPORARY table under multiple aliases. CTE literal
        // fixtures shadow each business table without changing the production SELECT bodies.
        String fixtures = """
WITH com_contract (id,tenant_id,company_code,contract_no,deleted) AS (
SELECT 101,1,'A','C1',0
UNION ALL SELECT 102,1,'A','C2',0
UNION ALL SELECT 103,1,'A','C3',0
UNION ALL SELECT 104,1,'A','FUTURE',0
UNION ALL SELECT 105,1,'A','EXPIRED',0
UNION ALL SELECT 106,2,'A','C1',0
UNION ALL SELECT 107,1,'B','C1',0
UNION ALL SELECT 108,1,'A','c1',0
UNION ALL SELECT 109,1,'A','C1 ',0
UNION ALL SELECT 110,1,'A','INACTIVE',0
UNION ALL SELECT 111,1,'A','DELETED-REL',0
UNION ALL SELECT 112,1,'A','DELETED-CONTRACT',1
),
com_project_contract_relation (id,tenant_id,project_id,contract_id,status,effective_from,effective_to,deleted) AS (
SELECT 1,1,10,101,'ACTIVE',NULL,NULL,0
UNION ALL SELECT 2,1,10,104,'ACTIVE','2100-01-01',NULL,0
UNION ALL SELECT 3,1,10,105,'ACTIVE',NULL,'2000-01-01',0
UNION ALL SELECT 4,2,10,106,'ACTIVE',NULL,NULL,0
UNION ALL SELECT 5,1,10,110,'INACTIVE',NULL,NULL,0
UNION ALL SELECT 6,1,10,111,'ACTIVE',NULL,NULL,1
UNION ALL SELECT 7,1,10,112,'ACTIVE',NULL,NULL,0
UNION ALL SELECT 8,1,10,999,'ACTIVE',NULL,NULL,0
),
com_sales_order (id,tenant_id,company_code,contract_no,deleted,order_create_time) AS (
SELECT 201,1,'A','C1',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 202,1,'A','C2',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 203,1,'A','OLD',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 204,2,'A','C1',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 205,1,'B','C1',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 206,1,'A','c1',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 207,1,'A','C1 ',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 208,1,'A','C1',1,CAST(NULL AS DATETIME)
UNION ALL SELECT 209,1,'A','LINKED',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 210,1,'A','NO-MASTER',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 211,1,'A','EXPIRED-LINK',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 212,1,'A','DELETED-LINK',0,CAST(NULL AS DATETIME)
UNION ALL SELECT 213,1,'A','FUTURE-LINK',0,CAST(NULL AS DATETIME)
),
com_order_contract_relation (id,tenant_id,order_id,contract_id,effective_from,effective_to,deleted) AS (
SELECT 1,1,203,103,'2000-01-01',NULL,0
UNION ALL SELECT 2,1,209,101,'2000-01-01',NULL,0
UNION ALL SELECT 3,1,210,999,'2000-01-01',NULL,0
UNION ALL SELECT 4,1,211,101,'2000-01-01','2001-01-01',0
UNION ALL SELECT 5,1,212,101,'2000-01-01',NULL,1
UNION ALL SELECT 6,1,213,101,'2100-01-01',NULL,0
),
com_sales_order_line (id,tenant_id,order_id,deleted) AS (
SELECT 301,1,201,0
UNION ALL SELECT 302,1,202,0
UNION ALL SELECT 303,1,203,0
UNION ALL SELECT 304,1,205,0
UNION ALL SELECT 305,1,208,0
UNION ALL SELECT 306,1,204,0
UNION ALL SELECT 307,1,206,1
),
com_delivery_scope (id,tenant_id,project_id,order_line_id,scope_status,effective_to,deleted) AS (
SELECT 401,1,10,301,'ACTIVE',NULL,0
UNION ALL SELECT 402,1,10,302,'ACTIVE',NULL,0
UNION ALL SELECT 403,1,10,303,'ACTIVE',NULL,0
UNION ALL SELECT 404,1,10,304,'INACTIVE',NULL,0
UNION ALL SELECT 405,1,10,304,'ACTIVE','2100-01-01',0
UNION ALL SELECT 406,1,10,304,'ACTIVE',NULL,1
UNION ALL SELECT 407,1,10,305,'ACTIVE',NULL,0
UNION ALL SELECT 408,1,10,306,'ACTIVE',NULL,0
UNION ALL SELECT 409,1,10,307,'ACTIVE',NULL,0
),
com_crm_execution_order (id,tenant_id,primary_project_id,execution_no,status,deleted) AS (
SELECT 901,1,10,'E1','ACTIVE',0
UNION ALL SELECT 902,2,10,'E2','ACTIVE',0
UNION ALL SELECT 903,1,11,'E3','ACTIVE',0
UNION ALL SELECT 904,1,10,'E4','ACTIVE',1
UNION ALL SELECT 905,1,10,'E5','INACTIVE',0
)
""";
        var configuration = new Configuration(new Environment("project-commerce", new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        configuration.setMapUnderscoreToCamelCase(true);
        try (var xml = getClass().getResourceAsStream("/mapper/contract/ProjectCommerceMapper.xml")) {
            String source = new String(xml.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            source = source.replaceAll("(<select\\b[^>]*>)", "$1" + fixtures);
            new XMLMapperBuilder(new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)), configuration,
                    "project-commerce", configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(ProjectCommerceMapper.class);
    }

    @AfterEach void close() throws Exception {
        if (session != null) session.close();
        if (connection != null) connection.close();
    }

    @Test void contractsKeepAllThreeAssociationPathsAndDeduplicate() {
        assertEquals(List.of(101L, 102L, 103L), mapper.selectContracts(new ProjectCommerceQuery(1L, 10L)).stream().map(row -> row.getId()).toList());
    }

    @Test void ordersPreserveExactCompanyAndContractMatchingAndLegacyExplicitLinks() {
        assertEquals(List.of(201L, 202L, 203L, 209L, 210L), mapper.selectOrders(new ProjectCommerceQuery(1L, 10L)).stream().map(row -> row.getId()).toList());
    }

    @Test void tenantAppliesToBothCandidatesAndReturnedMasters() {
        var query = new ProjectCommerceQuery(2L, 10L);
        assertEquals(List.of(106L), mapper.selectContracts(query).stream().map(row -> row.getId()).toList());
        assertEquals(List.of(204L), mapper.selectOrders(query).stream().map(row -> row.getId()).toList());
        assertEquals(List.of(902L), mapper.selectExecutionOrders(query).stream().map(row -> row.getId()).toList());
    }

    @Test void missingProjectDoesNotReadUnrelatedBusinessRows() {
        var query = new ProjectCommerceQuery(1L, 99999L);
        assertTrue(mapper.selectContracts(query).isEmpty());
        assertTrue(mapper.selectOrders(query).isEmpty());
        assertTrue(mapper.selectExecutionOrders(query).isEmpty());
    }

    @Test void executionOrdersRemainCurrentUndeletedAndProjectScoped() {
        assertEquals(List.of(901L), mapper.selectExecutionOrders(new ProjectCommerceQuery(1L, 10L)).stream().map(row -> row.getId()).toList());
        assertEquals(List.of(903L), mapper.selectExecutionOrders(new ProjectCommerceQuery(1L, 11L)).stream().map(row -> row.getId()).toList());
    }
}
