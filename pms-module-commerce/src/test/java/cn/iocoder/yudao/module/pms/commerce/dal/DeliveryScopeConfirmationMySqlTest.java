package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local temporary tables exercise the real MySQL schema; persistent rows are never written. */
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql", matches="true")
class DeliveryScopeConfirmationMySqlTest {
    Connection db; Configuration config; org.apache.ibatis.session.SqlSession session;int temporaryQuery;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));
        assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var mybatisConfig=new com.baomidou.mybatisplus.core.MybatisConfiguration();mybatisConfig.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(mybatisConfig);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/scope/DeliveryScopeMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mapper/contract/ProjectCommerceMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mapper/authority/AuthorityScopeImpactMapper.xml"));
        var built=factory.getObject();config=built.getConfiguration();session=built.openSession(db);
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE scope_confirmation_test LIKE com_delivery_scope");
            sql.execute("ALTER TABLE scope_confirmation_test RENAME TO com_delivery_scope");
        }
        insert(1,1,10,20,true,1,null,0);
        insert(2,1,10,20,false,2,null,0);
        insert(3,1,10,20,false,3,null,0);
        insert(4,1,10,20,true,4,LocalDateTime.of(2026,10,8,0,0),0);
        insert(5,2,10,20,true,1,null,0);
        insert(6,1,11,20,true,1,null,1);
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void equipmentListSelectionRejectsUnconfirmedHistoryAndMixedReferences() {
        var mapper=session.getMapper(DeliveryScopeMapper.class);
        assertEquals(List.of(1L),mapper.selectConfirmedSelection(new DeliveryScopeSelectionQuery(1L,10L,List.of(1L,2L,3L,4L,5L,6L))).stream().map(x->x.getId()).toList());
        assertTrue(mapper.selectConfirmedSelection(new DeliveryScopeSelectionQuery(1L,10L,List.of())).isEmpty());
        var service=new cn.iocoder.yudao.module.pms.commerce.service.scope.DeliveryScopeLineFactService();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"scopeMapper",mapper);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            var ref=cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineRef.ofScope(1L);
            var old=cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineRef.ofScope(2L);
            assertEquals(1L,service.validateSelection(10L,List.of(ref)).getFirst().scopeId());
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.validateSelection(10L,List.of(old)));
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.validateSelection(10L,List.of(ref,old)));
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
    private void insert(long id,long tenant,long project,long line,boolean confirmed,long version,LocalDateTime end,int deleted) throws Exception {
        try(var sql=db.prepareStatement("INSERT INTO com_delivery_scope(id,tenant_id,project_id,project_code,order_line_id,order_source_system,order_company_code,order_type,order_no,line_no,allocated_qty,allocation_confirmed,scope_status,allocation_version,allocation_source,department_id,department_code,department_name,department_version,effective_from,effective_to,deleted,creator,create_time,updater,update_time) VALUES(?,?,?,'P',?,'SAP','001','NORMAL','ORDER','10',2,?,'ACTIVE',?,'DPPMS',1,'D','Dept',1,'2026-10-01',?,?,'scope-test','2026-10-01','scope-test','2026-10-01')")) {
            sql.setLong(1,id);sql.setLong(2,tenant);sql.setLong(3,project);sql.setLong(4,line);
            sql.setBoolean(5,confirmed);sql.setLong(6,version);sql.setObject(7,end);sql.setInt(8,deleted);sql.executeUpdate();
        }
    }
    private PreparedStatement statement(String mapper,String method,Object query) throws Exception {
        Object parameters=mapper.startsWith("authority.")?query:Map.of("query",query);
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.commerce.dal.mysql."+mapper+"."+method).getBoundSql(parameters);
        var sql=db.prepareStatement(bound.getSql());int i=1;
        for(var mapping:bound.getParameterMappings()) {
            String name=mapping.getProperty();Object value=bound.hasAdditionalParameter(name)?bound.getAdditionalParameter(name):config.newMetaObject(parameters).getValue(name);
            sql.setObject(i++,value);
        }
        return sql;
    }
    private Set<Long> ids(String method,Object query) throws Exception {
        try(var sql=statement("scope.DeliveryScopeMapper",method,query);var result=sql.executeQuery()) {
            Set<Long> ids=new HashSet<>();while(result.next())ids.add(result.getLong(1));return ids;
        }
    }
    @Test void unconfirmedRecordsDoNotOccupyCurrentSlotButRemainVisibleWithTheirVersions() throws Exception {
        try(var sql=db.createStatement();var result=sql.executeQuery("SELECT id,current_order_line_id FROM com_delivery_scope WHERE tenant_id=1 AND project_id=10 ORDER BY id")) {
            Map<Long,Long> slots=new HashMap<>();while(result.next())slots.put(result.getLong(1),(Long)result.getObject(2));
            assertEquals(20L,slots.get(1L));assertNull(slots.get(2L));assertNull(slots.get(3L));assertNull(slots.get(4L));
        }
        assertThrows(SQLException.class,()->insert(7,1,10,20,true,7,null,0));
        assertEquals(Set.of(1L,2L,3L),ids("selectPageByProjectScope",new DeliveryScopePageQuery(1L,List.of(10L),null,null,false,0,20)));
        assertEquals(Set.of(1L,2L,3L,4L),ids("selectPageByProjectScope",new DeliveryScopePageQuery(1L,List.of(10L),null,null,true,0,20)));
        assertTrue(ids("selectPageByProjectScope",new DeliveryScopePageQuery(1L,List.of(),null,null,true,0,20)).isEmpty());
    }
    @Test void capacityAdjustmentSplitAndAcceptanceLocksConsumeConfirmedRecordsOnly() throws Exception {
        var mapper=session.getMapper(DeliveryScopeMapper.class);
        assertEquals(List.of(1L),mapper.selectActiveByProjectId(new DeliveryScopeProjectQuery(1L,10L)).stream().map(x->x.getId()).toList());
        assertEquals(List.of(1L),mapper.selectActiveByOrderLineIds(new DeliveryScopeOrderLineQuery(1L,List.of(20L))).stream().map(x->x.getId()).toList());
        assertTrue(mapper.selectActiveByOrderLineIds(new DeliveryScopeOrderLineQuery(1L,List.of())).isEmpty());
        assertNull(mapper.selectCurrentById(new DeliveryScopeIdQuery(1L,2L)));
        for(String method:List.of("selectActiveByProjectIdForUpdate","selectCurrentByProjectId","selectCurrentByProjectIdForUpdate"))
            assertEquals(Set.of(1L),ids(method,new DeliveryScopeProjectQuery(1L,10L)),method);
        for(String method:List.of("selectActiveByOrderLineIdsForUpdate","selectCurrentByOrderLineIdsForUpdate")) {
            assertEquals(Set.of(1L),ids(method,new DeliveryScopeOrderLineQuery(1L,List.of(20L))),method);
            assertTrue(ids(method,new DeliveryScopeOrderLineQuery(1L,List.of())).isEmpty(),method);
        }
        assertEquals(Set.of(1L),ids("selectCurrentVersionsForAcceptanceLock",new DeliveryScopeAcceptanceLockQuery(1L,10L)));
        assertTrue(ids("selectCurrentByIdForUpdate",new DeliveryScopeIdQuery(1L,2L)).isEmpty());
        assertTrue(ids("selectCurrentByIdForUpdate",new DeliveryScopeIdQuery(2L,1L)).isEmpty());
        assertEquals(Set.of(1L),ids("selectCurrentByIdForUpdate",new DeliveryScopeIdQuery(1L,1L)));
    }
    @Test void authorityRefreshCannotFreezeOrReleaseUnconfirmedHistoricalScope() throws Exception {
        try(var sql=statement("authority.AuthorityScopeImpactMapper","selectActiveScopesForUpdate",new AuthorityScopeImpactQuery(1L,20L));var rows=sql.executeQuery()) {
            assertTrue(rows.next());assertEquals(1L,rows.getLong("id"));assertFalse(rows.next());
        }
        try(var sql=statement("authority.AuthorityScopeImpactMapper","releaseActiveScopeByVersion",
                new AuthorityScopeReleaseUpdate(1L,2L,0L,LocalDateTime.of(2026,10,9,0,0),LocalDateTime.of(2026,10,9,0,0)))) {
            assertEquals(0,sql.executeUpdate());
        }
    }
    @Test void historicalScopeCannotCreateCurrentContractOrOrderAssociation() throws Exception {
        try(var sql=db.createStatement()) {
            for(String ddl:List.of(
                    "CREATE TEMPORARY TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(64),company_code VARCHAR(64),deleted BIT)",
                    "CREATE TEMPORARY TABLE com_sales_order(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(64),company_code VARCHAR(64),order_create_time DATETIME,deleted BIT)",
                    "CREATE TEMPORARY TABLE com_sales_order_line(id BIGINT,tenant_id BIGINT,order_id BIGINT,deleted BIT)",
                    "CREATE TEMPORARY TABLE com_project_contract_relation(project_id BIGINT,contract_id BIGINT,tenant_id BIGINT,status VARCHAR(32),effective_from DATETIME,effective_to DATETIME,deleted BIT)",
                    "CREATE TEMPORARY TABLE com_order_contract_relation(order_id BIGINT,contract_id BIGINT,tenant_id BIGINT,effective_from DATETIME,effective_to DATETIME,deleted BIT)")) sql.execute(ddl);
            sql.execute("INSERT INTO com_contract VALUES(100,1,'CONFIRMED','001',0),(101,1,'HISTORICAL','001',0),(102,1,'LINKED_HISTORY','001',0),(103,1,'EXPLICIT','001',0)");
            sql.execute("INSERT INTO com_sales_order VALUES(200,1,'CONFIRMED','001','2026-10-01',0),(201,1,'HISTORICAL','001','2026-10-01',0),(202,1,NULL,'001','2026-10-01',0),(203,1,'EXPLICIT','001','2026-10-01',0)");
            sql.execute("INSERT INTO com_sales_order_line VALUES(20,1,200,0),(21,1,201,0),(22,1,202,0)");
            sql.execute("INSERT INTO com_order_contract_relation VALUES(202,102,1,'2026-10-01',NULL,0)");
            sql.execute("INSERT INTO com_project_contract_relation VALUES(10,103,1,'ACTIVE','2026-10-01',NULL,0)");
        }
        insert(7,1,10,21,false,1,null,0);insert(8,1,10,22,false,1,null,0);
        var query=new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery(1L,10L);
        assertEquals(Set.of(100L,103L),commerceIds("selectContracts",query));
        assertEquals(Set.of(200L,203L),commerceIds("selectOrders",query));
        assertTrue(commerceIds("selectContracts",new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery(2L,10L)).isEmpty());
    }
    private Set<Long> commerceIds(String method,Object query) throws Exception {
        var parameters=Map.of("query",query);
        var bound=config.getMappedStatement("cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper."+method).getBoundSql(parameters);
        // MySQL cannot reopen a temporary table across aliases. Each reference uses an identical
        // connection-local fixture copy; the production predicates and parameter bindings stay intact.
        var references=java.util.regex.Pattern.compile("(?i)\\b(FROM|JOIN)\\s+(com_[a-z_]+)\\b").matcher(bound.getSql());
        var text=new StringBuffer();int index=0;int sequence=++temporaryQuery;
        try(var ddl=db.createStatement()) {
            while(references.find()) {
                String alias="scope_commerce_test_"+sequence+"_"+(index++);
                ddl.execute("CREATE TEMPORARY TABLE "+alias+" LIKE "+references.group(2));
                var columns=new ArrayList<String>();
                try(var fields=ddl.executeQuery("SHOW COLUMNS FROM "+references.group(2))) {
                    while(fields.next()) if(!fields.getString("Extra").contains("GENERATED")) columns.add("`"+fields.getString("Field")+"`");
                }
                String names=String.join(",",columns);
                ddl.execute("INSERT INTO "+alias+" ("+names+") SELECT "+names+" FROM "+references.group(2));
                references.appendReplacement(text,references.group(1)+" "+alias);
            }
        }
        references.appendTail(text);
        try(var sql=db.prepareStatement(text.toString())) {
            int position=1;
            for(var mapping:bound.getParameterMappings()) sql.setObject(position++,config.newMetaObject(parameters).getValue(mapping.getProperty()));
            try(var result=sql.executeQuery()) {
            Set<Long> ids=new HashSet<>();while(result.next())ids.add(result.getLong("id"));return ids;
            }
        }
    }
}
