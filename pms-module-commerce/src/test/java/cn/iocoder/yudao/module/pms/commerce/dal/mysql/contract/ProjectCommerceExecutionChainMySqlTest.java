package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Production read SQL against empty, connection-local clones; permanent business tables are untouched. */
@EnabledIfSystemProperty(named="npdms.commerce-execution-chain.mysql",matches="true")
class ProjectCommerceExecutionChainMySqlTest {
    private final long tenant=8_000_000_322L;
    private Connection db;
    private SqlSession session;
    private ProjectCommerceMapper mapper;

    @BeforeEach void fixture() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        for(String table:List.of("com_contract","com_project_contract_relation","com_sales_order",
                "com_order_contract_relation","com_crm_execution_order")) {
            String ddl;
            try(var sql=db.createStatement();var schema=sql.executeQuery("SHOW CREATE TABLE "+table)) {
                assertTrue(schema.next());ddl=schema.getString(2);
            }
            String create="CREATE TABLE `"+table+"`";assertTrue(ddl.startsWith(create));
            // MySQL temporary tables do not support foreign keys. Preserve columns, checks and indexes;
            // the read-query tests assert the tenant/company joins independently of permanent references.
            ddl=ddl.replaceAll("(?m)^\\s*CONSTRAINT[^\\n]*FOREIGN KEY[^\\n]*\\n","")
                    .replaceAll(",\\n\\)","\n)");
            try(var sql=db.createStatement()) {
                String clone=table.equals("com_crm_execution_order")?"test_com_crm_execution_order":table;
                sql.execute(ddl.replaceFirst(create,"CREATE TEMPORARY TABLE `"+clone+"`"));
                try(var rows=sql.executeQuery("SELECT COUNT(*) FROM "+clone)) {
                    assertTrue(rows.next());assertEquals(0,rows.getInt(1),"Expected an empty temporary clone");
                }
                for (int branch = 1; branch <= 2; branch++) {
                    sql.execute(ddl.replaceFirst(create,"CREATE TEMPORARY TABLE `test_b" + branch + "_" + table + "`"));
                }
            }
        }
        db.setAutoCommit(false);
        var configuration=new Configuration(new Environment("temporary-commerce-chain",new JdbcTransactionFactory(),new SingleConnectionDataSource(db,true)));
        configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(ProjectCommerceMapper.class);
        String path="mapper/contract/ProjectCommerceMapper.xml";
        try(var xml=getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(xml);
            // MySQL cannot reopen a temporary table in UNION. All branches read identical fixture rows
            // from separate local clones; predicates and the production UNION are kept unchanged.
            String text=new String(xml.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(text.contains("JOIN com_crm_execution_order e"));
            int begin = text.indexOf("    <select id=\"selectExecutionOrders\"");
            int end = text.indexOf("    </select>", begin);
            String[] branches = text.substring(begin, end).split("\\bUNION\\b");
            assertEquals(3, branches.length);
            branches[0] = branches[0].replace("com_crm_execution_order", "test_com_crm_execution_order");
            for (int branch = 1; branch < branches.length; branch++) {
                for (String table : List.of("com_contract", "com_project_contract_relation", "com_sales_order",
                        "com_order_contract_relation", "com_crm_execution_order")) {
                    branches[branch] = branches[branch].replace(table, "test_b" + branch + "_" + table);
                }
            }
            text = text.substring(0, begin) + String.join("UNION", branches) + text.substring(end);
            new XMLMapperBuilder(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),configuration,path,configuration.getSqlFragments()).parse();
        }
        session=new SqlSessionFactoryBuilder().build(configuration).openSession(db);assertSame(db,session.getConnection());
        mapper=session.getMapper(ProjectCommerceMapper.class);
        execute("INSERT INTO com_contract(id,tenant_id,company_code,contract_no,master_source_system,creator,updater) VALUES(10,?,'001','CONTRACT','DPPMS','fixture','fixture')",tenant);
        execute("INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,relation_role,source_system,source_table,source_record_key,status) VALUES(11,?,100,10,'RELATED','DPPMS','pm_project_contract','1:2','ACTIVE')",tenant);
        execute("INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_code,order_type,order_no,contract_no,execution_no,status,creator,updater) VALUES(20,?,'D365','ORDER-20','original','001','0','ORDER','CONTRACT','EXEC','ENABLED','fixture','fixture')",tenant);
        execute("INSERT INTO com_crm_execution_order(id,tenant_id,source_system,execution_no,company_code,project_code,status) VALUES(30,?,'SMS','EXEC','001','ORIGINAL-CRM-CODE','ACTIVE')",tenant);
    }

    @AfterEach void close() throws Exception {
        if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();
    }
    private void execute(String sql,Object... values) throws Exception {
        sql=sql.replace("com_crm_execution_order","test_com_crm_execution_order");
        try(var statement=db.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();
        }
        try(var statement=db.createStatement()) {
            for (String table : List.of("com_contract", "com_project_contract_relation", "com_sales_order",
                    "com_order_contract_relation", "com_crm_execution_order")) {
                String original = table.equals("com_crm_execution_order") ? "test_com_crm_execution_order" : table;
                for (int branch = 1; branch <= 2; branch++) {
                    String clone = "test_b" + branch + "_" + table;
                    statement.executeUpdate("DELETE FROM " + clone);
                    statement.executeUpdate("INSERT INTO " + clone + " SELECT * FROM " + original);
                }
            }
        }
        if(session!=null)session.clearCache();
    }
    private List<Long> ids() {return mapper.selectExecutionOrders(new ProjectCommerceQuery(tenant,100L)).stream().map(r->r.getId()).toList();}

    @Test void readsOriginalCrmCodeThroughFormalChainWithoutChangingPrimaryProject() throws Exception {
        var rows=mapper.selectExecutionOrders(new ProjectCommerceQuery(tenant,100L));
        assertEquals(List.of(30L),rows.stream().map(r->r.getId()).toList());
        assertEquals("ORIGINAL-CRM-CODE",rows.getFirst().getProjectCode());assertNull(rows.getFirst().getPrimaryProjectId());
        execute("UPDATE com_crm_execution_order SET primary_project_id=200 WHERE id=30");
        assertEquals(List.of(30L),ids());
        try(var sql=db.createStatement();var row=sql.executeQuery("SELECT primary_project_id FROM test_com_crm_execution_order WHERE id=30")) {
            assertTrue(row.next());assertEquals(200L,row.getLong(1));
        }
    }
    @Test void supportsExistingExplicitOrderContractRelation() throws Exception {
        execute("UPDATE com_sales_order SET contract_no=NULL WHERE id=20");assertTrue(ids().isEmpty());
        execute("INSERT INTO com_order_contract_relation(id,tenant_id,contract_id,order_id,effective_from,relation_source,source_system,sales_order_source_key,contract_source_key,source_version,source_evidence,creator,updater) VALUES(40,?,10,20,'2000-01-01','ERP','DPPMS','ORDER-20','CONTRACT-10','original','{}','fixture','fixture')",tenant);
        assertEquals(List.of(30L),ids());
        execute("UPDATE com_order_contract_relation SET effective_to='2001-01-01' WHERE id=40");assertTrue(ids().isEmpty());
    }
    @Test void rejectsCrossTenantCompanyAndCaseFoldedKeys() throws Exception {
        execute("UPDATE com_crm_execution_order SET tenant_id=? WHERE id=30",tenant+1);assertTrue(ids().isEmpty());
        execute("UPDATE com_crm_execution_order SET tenant_id=?,company_code='002' WHERE id=30",tenant);assertTrue(ids().isEmpty());
        execute("UPDATE com_crm_execution_order SET company_code='001',execution_no='exec' WHERE id=30");assertTrue(ids().isEmpty());
        execute("UPDATE com_crm_execution_order SET execution_no='EXEC',company_id=2 WHERE id=30");
        execute("UPDATE com_sales_order SET company_id=1 WHERE id=20");assertTrue(ids().isEmpty());
        execute("UPDATE com_crm_execution_order SET company_id=1 WHERE id=30");assertEquals(List.of(30L),ids());
        execute("UPDATE com_contract SET contract_no='contract' WHERE id=10");assertTrue(ids().isEmpty());
    }
    @Test void rejectsInactiveDeletedExpiredAndReturnOrderChain() throws Exception {
        execute("UPDATE com_sales_order SET order_type='1' WHERE id=20");assertTrue(ids().isEmpty());
        execute("UPDATE com_sales_order SET order_type='0',status='DISABLED' WHERE id=20");assertTrue(ids().isEmpty());
        execute("UPDATE com_sales_order SET status='ENABLED' WHERE id=20");
        execute("UPDATE com_project_contract_relation SET status='HISTORICAL' WHERE id=11");assertTrue(ids().isEmpty());
        execute("UPDATE com_project_contract_relation SET status='ACTIVE',effective_to='2001-01-01' WHERE id=11");assertTrue(ids().isEmpty());
        execute("UPDATE com_project_contract_relation SET effective_to=NULL,deleted=1 WHERE id=11");assertTrue(ids().isEmpty());
        execute("UPDATE com_project_contract_relation SET deleted=0 WHERE id=11");
        execute("UPDATE com_crm_execution_order SET status='DISABLED' WHERE id=30");assertTrue(ids().isEmpty());
    }
    @Test void preservesNormalPrimaryProjectPathAndDoesNotExtendNormalRelations() throws Exception {
        execute("UPDATE com_project_contract_relation SET source_system='PMS' WHERE id=11");assertTrue(ids().isEmpty());
        execute("UPDATE com_crm_execution_order SET primary_project_id=100 WHERE id=30");assertEquals(List.of(30L),ids());
        execute("UPDATE com_crm_execution_order SET deleted=1 WHERE id=30");assertTrue(ids().isEmpty());
    }

    @Test void overlappingPrimaryDirectAndExplicitPathsRemainDeduplicated() throws Exception {
        execute("UPDATE com_crm_execution_order SET primary_project_id=100 WHERE id=30");
        execute("INSERT INTO com_order_contract_relation(id,tenant_id,contract_id,order_id,effective_from,relation_source,source_system,sales_order_source_key,contract_source_key,source_version,source_evidence,creator,updater) VALUES(40,?,10,20,'2000-01-01','ERP','DPPMS','ORDER-20','CONTRACT-10','original','{}','fixture','fixture')",tenant);
        assertEquals(List.of(30L), ids());
    }
}
