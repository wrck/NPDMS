package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceKeysQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractCollectionPlanMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.ContractCollectionPlanDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ErpOrderSyncQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL/XML. Every fixture lives in this connection's TEMPORARY tables only. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class DppmsCommerceApprovedMapperMySqlTest {
    Connection db;
    Configuration configuration;
    static final String AUTHORITY="cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.";
    static final String SYNC="cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper.";
    static final String ENTITY=DppmsCommerceEntityMapper.class.getName()+".";

    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        db.setAutoCommit(false);
        configuration=new Configuration();
        for(String resource:List.of("mapper/authority/SalesOrderAuthorityMapper.xml","mapper/authority/OrderLineAuthorityMapper.xml",
                "mapper/order/ErpOrderSyncMapper.xml","mapper/migration/DppmsCommerceEntityMapper.xml","mapper/migration/DppmsCommerceDetailMapper.xml",
                "mapper/migration/ContractCollectionPlanMapper.xml")) {
            try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE com_sales_order(id BIGINT,tenant_id BIGINT,source_system VARCHAR(32),source_record_key VARCHAR(128),order_no VARCHAR(64),deleted INT)");
            sql.execute("CREATE TEMPORARY TABLE com_sales_order_line(id BIGINT,tenant_id BIGINT,source_system VARCHAR(32),source_record_key VARCHAR(128),order_no VARCHAR(64),order_id BIGINT,line_no VARCHAR(32),deleted INT)");
            String executionSchema;
            try(var schema=sql.executeQuery("SHOW CREATE TABLE com_crm_execution_order")) {
                assertTrue(schema.next());executionSchema=schema.getString(2);
            }
            assertTrue(executionSchema.startsWith("CREATE TABLE `com_crm_execution_order`"));
            sql.execute(executionSchema.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
        }
        for(String table:List.of("com_sales_order","com_sales_order_line")) {
            try(var statement=db.prepareStatement("INSERT INTO "+table+"(id,tenant_id,source_system,source_record_key,order_no,deleted) VALUES(?,?,?,?,?,?)")) {
                fixture(statement,1,1,"D365","DPPMS|D365|01|0|SO",0);
                fixture(statement,2,1,"ERP","DPPMS|D365|001|0|SO",0);
                fixture(statement,3,2,"D365","DPPMS|D365|01|0|SO",0);
                fixture(statement,4,1,"D365","DPPMS|D365|001|0|SO-DELETED",1);
                fixture(statement,5,1,"SAP","DPPMS|D365|01|0|SO",0);
                fixture(statement,6,1,"SAP","DPPMS|SAP|001|0|SO",0);
                fixture(statement,7,1,"SMS","DPPMS|SMS|001|0|SO",0);
            }
        }
    }

    @AfterEach void cleanup() throws Exception {
        if(db!=null) { try { db.rollback(); } finally { db.close(); } }
    }

    private void fixture(java.sql.PreparedStatement statement,long id,long tenant,String system,String key,int deleted) throws Exception {
        statement.setLong(1,id);statement.setLong(2,tenant);statement.setString(3,system);statement.setString(4,key);
        statement.setString(5,"SO");statement.setInt(6,deleted);assertEquals(1,statement.executeUpdate());
    }

    @Test void authorityLocksReuseBothCategoriesWithoutCrossingTenantDeletedOrMismatchedErpIdentity() throws Exception {
        for(String name:List.of("SalesOrderAuthorityMapper","OrderLineAuthorityMapper")) {
            String prefix=AUTHORITY+name+".";
            assertEquals(List.of(1L),ids(prefix+"selectBySourceForUpdate",new AuthoritySourceQuery(1L,"ERP","DPPMS|D365|01|0|SO")));
            assertEquals(List.of(2L),ids(prefix+"selectBySourceForUpdate",new AuthoritySourceQuery(1L,"ERP","DPPMS|D365|001|0|SO")));
            assertEquals(List.of(3L),ids(prefix+"selectBySourceForUpdate",new AuthoritySourceQuery(2L,"ERP","DPPMS|D365|01|0|SO")));
            assertTrue(ids(prefix+"selectBySourceForUpdate",new AuthoritySourceQuery(1L,"ERP","DPPMS|D365|001|0|SO-DELETED")).isEmpty());
            assertTrue(ids(prefix+"selectBySourcesForUpdate",new AuthoritySourceKeysQuery(1L,"ERP",List.of())).isEmpty());
            assertTrue(ids(prefix+"selectBySourcesForUpdate",new AuthoritySourceKeysQuery(1L,"ERP",null)).isEmpty());
            assertEquals(List.of(1L,6L,7L),ids(prefix+"selectBySourcesForUpdate",new AuthoritySourceKeysQuery(1L,"ERP",
                    List.of("DPPMS|D365|01|0|SO","DPPMS|SAP|001|0|SO","DPPMS|SMS|001|0|SO"))));
        }
    }

    @Test void synchronizationIncludesDeletedIdentitiesWhileFormalResolutionExcludesThem() throws Exception {
        for(String name:List.of("selectOrders","selectLines")) {
            var actual=ids(SYNC+name,Map.of("query",new ErpOrderSyncQuery(1L,List.of("SO"))));
            assertEquals(java.util.Set.of(1L,2L,4L,6L,7L),new java.util.HashSet<>(actual));
            assertTrue(ids(SYNC+name,Map.of("query",new ErpOrderSyncQuery(1L,List.of()))).isEmpty());
        }
        for(String name:List.of("selectOrderIds","selectLineIds")) {
            assertEquals(List.of(1L),ids(ENTITY+name,Map.of("query",new DppmsCommerceEntityMapper.SourceQuery(1L,"DPPMS|D365|01|0|SO"))));
            assertTrue(ids(ENTITY+name,Map.of("query",new DppmsCommerceEntityMapper.SourceQuery(1L,"DPPMS|D365|001|0|SO-DELETED"))).isEmpty());
        }
    }

    @Test void executionEnrichmentPreservesEmptyStringsAuditAndVersionGuards() throws Exception {
        try(var sql=db.createStatement()) {
            sql.execute("INSERT INTO com_crm_execution_order(id,tenant_id,source_system,execution_no,primary_project_id,version,creator,updater,status) VALUES(50,1,'SMS','EX',100,7,'old_creator','old_updater','ACTIVE')");
        }
        var row=new CrmExecutionOrderDO();row.setId(50L);row.setSourceSystem("SMS");row.setExecutionNo("EX");
        row.setChannelName("");row.setCustomerProjectName("");row.setCompanyCode("001");row.setCompanyId(71L);row.setDepartmentId(81L);
        var at=LocalDateTime.of(2026,10,9,17,0,0,321_000_000);
        String statement=DppmsCommerceDetailMapper.class.getName()+".updateExecutionFields";
        for(var wrong:List.of(new DppmsCommerceDetailMapper.ExecutionEnrichmentUpdate(2L,100L,7L,1L,at,row),
                new DppmsCommerceDetailMapper.ExecutionEnrichmentUpdate(1L,101L,7L,1L,at,row),
                new DppmsCommerceDetailMapper.ExecutionEnrichmentUpdate(1L,100L,6L,1L,at,row)))
            assertEquals(0,update(statement,Map.of("command",wrong)));
        var correct=new DppmsCommerceDetailMapper.ExecutionEnrichmentUpdate(1L,100L,7L,1L,at,row);
        assertEquals(1,update(statement,Map.of("command",correct)));
        assertEquals(0,update(statement,Map.of("command",correct)));
        try(var sql=db.createStatement();var result=sql.executeQuery("SELECT channel_name,customer_project_name,company_id,department_id,version,creator,updater,update_time FROM com_crm_execution_order WHERE id=50")) {
            assertTrue(result.next());assertEquals("",result.getString(1));assertEquals("",result.getString(2));
            assertEquals(71L,result.getLong(3));assertEquals(81L,result.getLong(4));assertEquals(8L,result.getLong(5));
            assertEquals("old_creator",result.getString(6));assertEquals("1",result.getString(7));
            assertEquals(at,result.getTimestamp(8).toLocalDateTime());
        }
    }

    @Test void collectionPlanFinalDdlAndMapperPreserveDuplicateRowsNullsHistoryAndRollback() throws Exception {
        String migration=java.nio.file.Files.readString(java.nio.file.Path.of("../sql/migrations/V412__dppms_native_detail_field_completion.sql"));
        int start=migration.indexOf("CREATE TABLE com_contract_collection_plan (");assertTrue(start>=0);
        String ddl=migration.substring(start,migration.indexOf(';',start)+1);
        try(var sql=db.createStatement()) {
            sql.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
            sql.execute("CREATE TEMPORARY TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(64),deleted INT)");
            sql.execute("INSERT INTO com_contract VALUES(20,1,'Case-C',0),(21,2,'Case-C',0),(22,1,'CASE-C',0),(23,1,'Case-C',1)");
            try(var empty=sql.executeQuery("SELECT * FROM com_contract_collection_plan")) { assertEquals(19,empty.getMetaData().getColumnCount()); }
        }
        String prefix=ContractCollectionPlanMapper.class.getName()+".";
        assertEquals(List.of(20L),ids(prefix+"selectExactContractIds",Map.of("query",new ContractCollectionPlanMapper.ContractQuery(1L,"Case-C"))));
        var row=new ContractCollectionPlanDO();row.setTenantId(1L);row.setContractNo("Case-C");row.setBatchCode("1");
        row.setCollectionItemName("终验款");row.setReferenceEventName("终验");row.setAfterDays(30);row.setDataSource("CRM");
        var created=LocalDateTime.parse("2018-01-02T03:04:05.123");var updated=LocalDateTime.parse("2019-01-02T03:04:05.456");
        row.setCreator("11");row.setUpdater("crmapp");row.setCreateTime(created);row.setUpdateTime(updated);row.setDeleted(false);
        for(long id:List.of(501L,502L)) { row.setId(id);assertEquals(1,update(prefix+"insertPlan",Map.of("row",row))); }
        try(var sql=db.createStatement();var records=sql.executeQuery("SELECT * FROM com_contract_collection_plan ORDER BY id")) {
            int count=0;while(records.next()) {
                count++;assertNull(records.getObject("contract_id"));assertNull(records.getObject("event_planned_at"));
                assertEquals(created,records.getTimestamp("create_time").toLocalDateTime());
                assertEquals(updated,records.getTimestamp("update_time").toLocalDateTime());assertEquals("crmapp",records.getString("updater"));
                assertEquals(30,records.getInt("after_days"));assertEquals("终验款",records.getString("collection_item_name"));
            }assertEquals(2,count);
        }
        row.setId(501L);row.setTenantId(2L);assertEquals(0,update(prefix+"updatePlan",Map.of("row",row)));
        assertTrue(ids(prefix+"selectIdentityForUpdate",Map.of("query",new ContractCollectionPlanMapper.IdentityQuery(2L,501L))).isEmpty());
        row.setTenantId(1L);row.setMarketingFeedback("");assertEquals(1,update(prefix+"updatePlan",Map.of("row",row)));
        try(var sql=db.createStatement();var records=sql.executeQuery("SELECT marketing_feedback,update_time FROM com_contract_collection_plan WHERE id=501")) {
            assertTrue(records.next());assertEquals("",records.getString(1));assertEquals(updated,records.getTimestamp(2).toLocalDateTime());
        }
        db.rollback();
        try(var sql=db.createStatement();var count=sql.executeQuery("SELECT COUNT(*) FROM com_contract_collection_plan")) {
            assertTrue(count.next());assertEquals(0,count.getInt(1));
        }
    }

    private List<Long> ids(String statement,Object parameter) throws Exception {
        var mapped=configuration.getMappedStatement(statement);var bound=mapped.getBoundSql(parameter);
        try(var sql=db.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mapped,parameter,bound).setParameters(sql);
            List<Long> result=new ArrayList<>();try(var rows=sql.executeQuery()){while(rows.next())result.add(rows.getLong("id"));}return result;
        }
    }

    private int update(String statement,Object parameter) throws Exception {
        var mapped=configuration.getMappedStatement(statement);var bound=mapped.getBoundSql(parameter);
        try(var sql=db.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(mapped,parameter,bound).setParameters(sql);return sql.executeUpdate();
        }
    }
}
