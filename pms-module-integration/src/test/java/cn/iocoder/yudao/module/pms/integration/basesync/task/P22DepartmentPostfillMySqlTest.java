package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Native tenant/code matching and postfill; all DML stays in connection-local temporary tables. */
class P22DepartmentPostfillMySqlTest extends RemainingTaskMySqlSupport {
    P22ProductScopeSyncTask.TargetMapper mapper;
    @BeforeEach void scopeFixture() throws Exception {
        jdbc.execute("CREATE TEMPORARY TABLE proj_project(id BIGINT,tenant_id BIGINT,version BIGINT,project_code VARCHAR(64),project_name VARCHAR(100),company_code VARCHAR(64),company_name VARCHAR(100),customer_code VARCHAR(64),customer_name VARCHAR(100),manager_employee_no VARCHAR(64),manager_name VARCHAR(100),department_id BIGINT,department_code VARCHAR(64),lifecycle_status VARCHAR(32) DEFAULT 'ACTIVE',deleted BOOLEAN,PRIMARY KEY(tenant_id,id))");
        jdbc.execute("CREATE TEMPORARY TABLE com_delivery_scope_project_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,scope_version BIGINT,payload_version INT,last_change_type VARCHAR(32),version INT,creator VARCHAR(64),create_time DATETIME(3),updater VARCHAR(64),update_time DATETIME(3),deleted BOOLEAN,UNIQUE KEY uk_scope_project_version(tenant_id,project_id))");
        try(var in=new org.springframework.core.io.ClassPathResource("basesync/p22-pending-department-scope-mysql.sql").getInputStream()){
            for(var sql:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split(";\\s*(?:\\r?\\n|$)"))if(!sql.isBlank()){
                if(!sql.startsWith("CREATE TEMPORARY TABLE "))throw new IllegalStateException("Only connection-local temporary DDL permitted");
                jdbc.execute(sql);
            }
        }
        jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,status,version) VALUES(7,1,'OFFICE','office',0,3),(8,2,'OTHER','other tenant',0,3)");
        jdbc.update("INSERT INTO int_base_sync_task(id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows,active_run_id) VALUES(1,1,'scope','BASE_P22','DPPMS',3,1,100,10),(2,1,'other connection','BASE_P22','DPPMS',4,1,100,10),(3,1,'other task','BASE_P20','DPPMS',3,1,100,10),(4,2,'other tenant','BASE_P22','DPPMS',3,1,100,10)");
        // MySQL temporary tables cannot be reopened through two aliases in one UPDATE.
        // Substitute only the second metadata table names; copy current fixture data before each statement.
        jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_binding_order_test LIKE int_base_sync_binding");
        jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_task_order_test LIKE int_base_sync_task");
        jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_run(id BIGINT,tenant_id BIGINT,task_id BIGINT,connection_id BIGINT,task_code VARCHAR(16),status VARCHAR(32),preview BOOLEAN,deleted BOOLEAN)");
        jdbc.update("INSERT INTO t_company(id,compCode) VALUES(1,'01')");
        var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(session.getConfiguration().getEnvironment());
        for(var resource:List.of("mapper/basesync/P22TargetMapper.xml","base-sync-source/P22SourceMapper.xml"))try(var input=new org.springframework.core.io.ClassPathResource(resource).getInputStream()){
            String xml=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("int_base_sync_binding ob","int_base_sync_binding_order_test ob").replace("int_base_sync_task ot","int_base_sync_task_order_test ot");
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(new java.io.StringReader(xml),config,resource,config.getSqlFragments()).parse();
        }
        session=new org.mybatis.spring.SqlSessionTemplate(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config));
        mapper=mock(P22ProductScopeSyncTask.TargetMapper.class,delegatesTo(session.getMapper(P22ProductScopeSyncTask.TargetMapper.class)));
        // This compatible pending-reference TEMPORARY schema has no formal schema effect.
        doReturn(1).when(mapper).pendingOrderSchemaReady();
        doReturn(1).when(mapper).pendingDepartmentSchemaReady();
        doAnswer(call->{copyOrderMetadata();return session.getMapper(P22ProductScopeSyncTask.TargetMapper.class).backfillOrderLineIds(call.getArgument(0));}).when(mapper).backfillOrderLineIds(any());
        var ids=new AtomicLong(100);
        doAnswer(call->{for(var v:call.<List<SyncIdentityStore.Save>>getArgument(0)){
            var old=saved.get(v.object()+":"+v.sourceKey());
            if(old==null)jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(?,1,1,?,?,?,?,10)",ids.incrementAndGet(),v.object(),v.sourceKey(),v.targetId(),v.fingerprint());
            else jdbc.update("UPDATE int_base_sync_binding SET fingerprint=?,last_run_id=10 WHERE task_id=1 AND object_key=? AND source_key=?",v.fingerprint(),v.object(),v.sourceKey());
            saved.put(v.object()+":"+v.sourceKey(),new SyncIdentityStore.Identity(v.sourceKey(),v.targetId(),v.fingerprint()));
        }return null;}).when(identities).saveAll(any());
    }
    P22ProductScopeSyncTask.Source row(long id,String code){return new P22ProductScopeSyncTask.Source().setSourceId(id).setOriginalProjectId(1L).setCanonicalProjectId(1L).setOriginalCompanyId(1L).setOriginalProjectCode("P-1").setOriginalProjectName("original project").setOriginalDepartmentCode(code).setProjectType("10").setOrderNumber("O").setLineNum("1").setItemCode("ITEM").setItemName("item").setProjectQuantity(BigDecimal.ONE).setOrderQuantity(BigDecimal.ONE);}
    P22ProductScopeSyncTask task(List<P22ProductScopeSyncTask.Source> rows,Runnable afterRead){return new P22ProductScopeSyncTask(mapper,projects){
        @Override protected String targetMapperNamespace(){return P22ProductScopeSyncTask.TargetMapper.class.getName();}
        @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> c,ResultHandler<Source> handler){for(var row:rows){var r=new DefaultResultContext<Source>();r.nextResultObject(row);handler.handleResult(r);}afterRead.run();}
    };}
    void copyOrderMetadata(){
        jdbc.update("DELETE FROM int_base_sync_binding_order_test");jdbc.update("INSERT INTO int_base_sync_binding_order_test SELECT * FROM int_base_sync_binding");
        jdbc.update("DELETE FROM int_base_sync_task_order_test");jdbc.update("INSERT INTO int_base_sync_task_order_test SELECT * FROM int_base_sync_task");
    }
    @Test void previewUsesOneTenantCodeCatalogueWithoutAFormalProjectOfficeAndKeepsMinusOne() {
        var rows=List.of(row(1,"OFFICE"),row(2,"OFFICE"));var c=context(true);
        assertEquals(2,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        for(var r:rows){assertEquals(-1L,r.getTarget().getDepartmentId());assertEquals("OFFICE",r.getTarget().getDepartmentCode());assertEquals(3,r.getTarget().getDepartmentVersion());}
        assertTrue(c.runState().warnings().contains("P22_DEPARTMENT_ID_POSTFILL_PENDING:-1"));
        verify(mapper,times(1)).selectDepartments(new P22ProductScopeSyncTask.DepartmentCatalogueQuery(1));
        verify(mapper,never()).backfillDepartmentIds(any());verify(mapper,never()).countUnresolvedCodedDepartmentsWithSnapshot(any());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));assertTrue(saved.isEmpty());
    }
    @Test void rawScopePreviewDoesNotRequireOrReadFormalOrderLines() {
        var source=row(1,"OFFICE").setProjectQuantity(null).setOrderQuantity(new BigDecimal("3.25"));
        assertEquals(1,run(task(List.of(source),()->{}),true).inserted());
        assertEquals(-1L,source.getTarget().getOrderLineId());
        assertEquals(0,new BigDecimal("3.25").compareTo(source.getTarget().getAllocatedQty()));
        verify(mapper,never()).selectOwnedOrderCatalogue(any());
        verify(mapper,never()).backfillOrderLineIds(any());
        assertTrue(saved.isEmpty());
    }
    @Test void exactCaseAndTenantRemainRequiredButUnmatchedCodesStayPending() {
        var rows=List.of(row(1,"office"),row(2,"OTHER"));var c=context(true);
        assertEquals(2,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        for(var source:rows){assertEquals(source.getOriginalDepartmentCode(),source.getTarget().getDepartmentCode());assertEquals(-1L,source.getTarget().getDepartmentId());assertNull(source.getTarget().getDepartmentName());assertNull(source.getTarget().getDepartmentVersion());}
        assertTrue(c.runState().warnings().contains("P22_DEPARTMENT_CODE_UNRESOLVED_AT_READ:2"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));assertTrue(saved.isEmpty());
    }
    @Test void previewPreservesNullAndEmptyDepartmentCodesAsPendingWithoutDroppingRows() {
        var rows=List.of(row(1,null),row(2,""));var c=context(true);
        var counts=transaction.execute(status->task(rows,()->{}).execute(c));
        assertEquals(2,counts.read());assertEquals(2,counts.inserted());
        assertNull(rows.getFirst().getTarget().getDepartmentCode());
        assertEquals("",rows.getLast().getTarget().getDepartmentCode());
        for(var row:rows){
            assertEquals(-1L,row.getTarget().getDepartmentId());
            assertNull(row.getTarget().getDepartmentName());assertNull(row.getTarget().getDepartmentVersion());
            assertEquals(row.getOriginalDepartmentCode(),row.getTarget().getProjectDepartmentCode());
            assertNull(row.getTarget().getProjectDepartmentName());assertEquals(-1L,row.getTarget().getOrderLineId());
        }
        assertTrue(c.runState().warnings().contains("P22_DEPARTMENT_SOURCE_MISSING_PENDING:2"));
        verify(mapper,times(1)).selectDepartments(new P22ProductScopeSyncTask.DepartmentCatalogueQuery(1));
        verify(mapper,never()).selectOwnedOrderCatalogue(any());verify(mapper,never()).backfillDepartmentIds(any());
        assertTrue(saved.isEmpty());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));
    }
    @Test void onePostfillKeepsOriginalAuditAndReplayBaselineButRejectsAChangedDerivedId() {
        var task=task(List.of(row(1,"OFFICE"),row(2,"OFFICE")),()->{});
        assertEquals(2,run(task,false).inserted());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE department_id=7 AND update_time=?",Integer.class,at));
        assertEquals(2,run(task,true).unchanged());
        verify(mapper,times(1)).backfillDepartmentIds(any());
        jdbc.update("UPDATE com_delivery_scope SET department_id=88,update_time=update_time");
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("DEPARTMENT_IDENTITY_CHANGED"));
    }
    @Test void nullableFixtureKeepsMissingDepartmentsPendingWhileKnownCodesPostfillAndReplay() {
        var rows=List.of(row(1,"OFFICE"),row(2,null),row(3,""));var c=context(false);
        assertEquals(3,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE department_id=7 AND department_code='OFFICE'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE department_id=-1 AND (department_code IS NULL OR department_code='') AND department_name IS NULL AND department_version IS NULL",Integer.class));
        assertEquals(0,mapper.countUnresolvedCodedDepartmentsWithSnapshot(new P22ProductScopeSyncTask.CurrentRunQuery(1,3)));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE update_time=?",Integer.class,at));
        for(var table:List.of("com_delivery_scope","com_delivery_scope_detail","int_base_sync_binding"))assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
        assertTrue(c.runState().warnings().contains("P22_DEPARTMENT_SOURCE_MISSING_PENDING:2"));
        verify(mapper,times(1)).backfillDepartmentIds(any());
        assertEquals(3,run(task(rows,()->{}),true).unchanged());
        jdbc.update("UPDATE com_delivery_scope SET department_id=88,update_time=update_time WHERE department_code IS NULL");
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(rows,()->{}),true)).getMessage().contains("DEPARTMENT_IDENTITY_CHANGED"));
    }
    @Test void actualNonnullableDepartmentsWarnPreviewAndRejectMissingSourceBeforeWriting() {
        assertEquals(0,session.getMapper(P22ProductScopeSyncTask.TargetMapper.class).pendingDepartmentSchemaReady());
        doReturn(0).when(mapper).pendingDepartmentSchemaReady();var c=context(true);
        assertEquals(1,transaction.execute(status->task(List.of(row(1,null)),()->{}).execute(c)).inserted());
        assertTrue(c.runState().warnings().contains("P22_EXECUTION_SCHEMA_REQUIRED:department_pending_reference"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(List.of(row(1,null)),()->{}),false)).getMessage().contains("EXECUTION_SCHEMA_REQUIRED:department_pending_reference"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));assertTrue(saved.isEmpty());
    }
    @Test void unresolvedCodeIsPreservedAndLaterUniqueDepartmentCanBeLinkedWithoutChangingItsSnapshot() {
        var rows=List.of(row(1,"LATER"));var c=context(false);
        assertEquals(1,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        assertTrue(c.runState().warnings().contains("P22_DEPARTMENT_CODE_UNRESOLVED_AT_READ:1"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE department_id=-1 AND department_code='LATER' AND department_name IS NULL AND department_version IS NULL",Integer.class));
        jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,status,version) VALUES(9,1,'LATER','later',0,4)");
        assertEquals(1,mapper.backfillDepartmentIds(new P22ProductScopeSyncTask.CurrentRunQuery(1,3)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE department_id=9 AND department_code='LATER' AND department_name IS NULL AND department_version IS NULL AND update_time=?",Integer.class,at));
        assertEquals(1,run(task(rows,()->{}),true).unchanged());
    }
    void insertScope(long id,long tenant,long task,long run,long departmentId,String code) {
        var s=row(id,code).setFormalProjectId(1000L);var p=new P22ProductScopeSyncTask.Snapshot().setProjectCode("P-1").setCompanyCode("001").setDepartmentCode(code).setDepartmentName("office").setDepartmentVersion(3).setScopeVersion(0L);
        var c=context(false).forParameters(new P22ProductScopeSyncTask.Parameters()).forData("ProductScope",false,true);
        s.setTarget(P22ProductScopeSyncTask.build(new P22ProductScopeSyncTask.Plan(s,p,BigDecimal.ONE,c)).setId(id).setTenantId(tenant).setDepartmentId(departmentId));
        session.getMapper(P22ProductScopeSyncTask.TargetMapper.class).insertProductScope(new SyncBatch<>(c,List.of(s)));
        jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(?,?,?,'PRODUCT_SCOPE',?,?,'a',?)",id,tenant,task,Long.toString(id),id,run);
    }
    @Test void nativePostfillUpdatesOnlyMinusOneInsideTheCurrentNewTaskRunTenantAndConnection() throws Exception {
        insertScope(201,1,1,10,-1,"OFFICE");insertScope(202,1,1,9,-1,"OFFICE");insertScope(203,1,2,10,-1,"OFFICE");
        insertScope(204,1,1,10,88,"OFFICE");insertScope(205,2,4,10,-1,"OFFICE");insertScope(206,1,3,10,-1,"OFFICE");
        insertScope(207,1,1,10,-1,"office");
        var q=new P22ProductScopeSyncTask.CurrentRunQuery(1,3);
        assertEquals(1,mapper.backfillDepartmentIds(q));assertEquals(7L,jdbc.queryForObject("SELECT department_id FROM com_delivery_scope WHERE id=201",Long.class));
        assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE department_id=-1",Integer.class));
        assertEquals(88L,jdbc.queryForObject("SELECT department_id FROM com_delivery_scope WHERE id=204",Long.class));assertEquals(1,mapper.countUnresolvedCodedDepartmentsWithSnapshot(q));
        var config=session.getConfiguration();for(var name:List.of("backfillDepartmentIds","countUnresolvedCodedDepartmentsWithSnapshot"))assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(config.getMappedStatement(P22ProductScopeSyncTask.TargetMapper.class.getName()+"."+name).getBoundSql(Map.of("query",q)).getSql()));
    }
    @Test void catalogueChangeDuringTheRunRollsBackScopeDetailsAndNewBindings() {
        var task=task(List.of(row(1,"OFFICE")),()->jdbc.update("UPDATE system_dept SET version=4 WHERE tenant_id=1 AND id=7"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("DEPARTMENT_POSTFILL_UNRESOLVED"));
        for(var table:List.of("com_delivery_scope","com_delivery_scope_detail","int_base_sync_binding"))assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT version FROM system_dept WHERE tenant_id=1 AND id=7",Integer.class));
    }
    void orderLine(long id,String system,String type,boolean ownBinding){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_task WHERE id=5",Integer.class)==0){
            jdbc.update("INSERT INTO int_base_sync_task(id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows) VALUES(5,1,'orders','BASE_P14','DPPMS',3,1,100)");
            jdbc.update("INSERT INTO int_base_sync_run VALUES(20,1,5,3,'BASE_P14','COMPLETED',FALSE,FALSE)");
        }
        jdbc.update("INSERT INTO com_sales_order(id,tenant_id,source_system,company_code,order_type,order_no,status,creator,updater) VALUES(?,1,?,'001',?,'O','ENABLED','original','original')",50000+id,system,type);
        jdbc.update("INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,company_code,order_type,line_type,order_no,line_no,product_code,order_qty,unit_scale,quantity_status,status,creator,updater) VALUES(?,1,?,?,?,'v1','001',?,?,'O','1','ITEM',1,6,'PENDING_AUTHORITY','ENABLED','original','original')",id,50000+id,system,"DPPMS|"+system+"|001|"+type+"|O|1",type,type);
        if(ownBinding)jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(?,1,5,'SALES_LINE',SHA2(CAST(JSON_ARRAY(?,'001',CAST(? AS UNSIGNED),'O','1') AS CHAR),256),?,'a',20)",id,system,type,id);
        copyOrderMetadata();
    }
    void formalProject(){
        jdbc.update("INSERT INTO proj_project(id,tenant_id,version,project_code,project_name,company_code,deleted) VALUES(1000,1,0,'P-1','project','001',0)");
    }
    void secondFormalProject(){
        jdbc.update("INSERT INTO proj_project(id,tenant_id,version,project_code,company_code,deleted) VALUES(1001,1,0,'P-2','001',0)");
        doAnswer(call->{
            var q=call.<BaseSyncProjectReferences.Query>getArgument(0);var result=new HashMap<String,BaseSyncProjectReferences.Identity>();
            for(var key:q.canonicalKeys()){boolean second="2".equals(key);result.put(key,new BaseSyncProjectReferences.Identity(key,second?1001L:1000L,1L,0L,second?"P-2":"P-1","10",null,null,null,"ACTIVE",false));}
            return result;
        }).when(projects).resolve(any());
    }
    @Test void newUniqueScopeOccupiesQuantityAndAdvancesProjectVersionWithoutConfirmingTheOrder(){
        formalProject();orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE proj_project SET lifecycle_status='NORMAL_CLOSED' WHERE id=1000");
        var rows=List.of(row(1,"OFFICE"));var c=context(false);
        var task=task(rows,()->{});assertEquals(Set.of("pms:commerce:scope:assign"),task.requiredPermissions());
        assertEquals(1,transaction.execute(status->task.execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND current_order_line_id=50 AND allocation_confirmed=1",Integer.class));
        assertEquals(1L,jdbc.queryForObject("SELECT scope_version FROM com_delivery_scope_project_version WHERE tenant_id=1 AND project_id=1000",Long.class));
        assertEquals("PENDING_AUTHORITY",jdbc.queryForObject("SELECT quantity_status FROM com_sales_order_line WHERE id=50",String.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_CONFIRMED:1"));
        assertEquals(1,run(task(rows,()->{}),true).unchanged());
    }
    @Test void quantityOverOrderStaysUnconfirmed(){
        formalProject();orderLine(50,"SAP","0",true);
        var rows=List.of(row(1,"OFFICE").setProjectQuantity(new BigDecimal("2")));var c=context(false);
        assertEquals(1,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=0",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_EXCESS:1"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version",Integer.class));
    }
    @Test void concurrentVersionConflictRollsBackNewScopeAndBinding(){
        formalProject();orderLine(50,"SAP","0",true);
        doReturn(0).when(mapper).advanceAllocationVersions(any());
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(List.of(row(1,"OFFICE")),()->{}),false)).getMessage().contains("ALLOCATION_PROJECT_VERSION_CHANGED"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding WHERE object_key='PRODUCT_SCOPE'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version",Integer.class));
    }
    @Test void missingOrdersAndDuplicateNewScopesRemainUnconfirmedWithoutRollingBack(){
        formalProject();orderLine(50,"SAP","0",true);
        var rows=List.of(row(1,"OFFICE"),row(2,"OFFICE"),row(3,"OFFICE").setOrderNumber("NO"));var c=context(false);
        assertEquals(3,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE allocation_confirmed=0",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_DUPLICATE:2"));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_PENDING:1"));
    }
    @Test void validNewScopeIsConfirmedWhileMissingOrderContinuesUnconfirmed(){
        formalProject();orderLine(50,"SAP","0",true);
        var rows=List.of(row(1,"OFFICE"),row(2,"OFFICE").setOrderNumber("NO"));var c=context(false);
        assertEquals(2,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=-1 AND allocation_confirmed=0",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_CONFIRMED:1"));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_PENDING:1"));
    }
    @Test void distinctProjectsOnOneOrderAreConfirmedWhenTheirTotalFits(){
        formalProject();jdbc.update("INSERT INTO proj_project(id,tenant_id,version,project_code,company_code,deleted) VALUES(1001,1,0,'P-2','001',0)");
        doAnswer(call->{
            var q=call.<BaseSyncProjectReferences.Query>getArgument(0);var result=new HashMap<String,BaseSyncProjectReferences.Identity>();
            for(var key:q.canonicalKeys()){boolean second="2".equals(key);result.put(key,new BaseSyncProjectReferences.Identity(key,second?1001L:1000L,1L,0L,second?"P-2":"P-1","10",null,null,null,"ACTIVE",false));}
            return result;
        }).when(projects).resolve(any());
        orderLine(50,"SAP","0",true);jdbc.update("UPDATE com_sales_order_line SET order_qty=5 WHERE id=50");
        var first=row(1,"OFFICE").setProjectQuantity(new BigDecimal("2"));
        var second=row(2,"OFFICE").setOriginalProjectId(2L).setCanonicalProjectId(2L).setOriginalProjectCode("P-2").setProjectQuantity(new BigDecimal("3"));
        var c=context(false);assertEquals(2,transaction.execute(status->task(List.of(first,second),()->{}).execute(c)).inserted());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=1",Integer.class));
        assertEquals(0,new BigDecimal("5").compareTo(jdbc.queryForObject("SELECT SUM(allocated_qty) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=1",BigDecimal.class)));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version WHERE scope_version=1",Integer.class));
    }
    @Test void duplicateCandidatesDoNotConsumeCapacityOfAnOtherwiseValidProject(){
        formalProject();secondFormalProject();orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE com_sales_order_line SET order_qty=10 WHERE id=50");
        var duplicate1=row(1,"OFFICE").setProjectQuantity(new BigDecimal("3"));
        var duplicate2=row(2,"OFFICE").setProjectQuantity(new BigDecimal("3"));
        var valid=row(3,"OFFICE").setOriginalProjectId(2L).setCanonicalProjectId(2L).setOriginalProjectCode("P-2").setProjectQuantity(new BigDecimal("5"));
        var c=context(false);assertEquals(3,transaction.execute(status->task(List.of(duplicate1,duplicate2,valid),()->{}).execute(c)).inserted());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE project_id=1000 AND allocation_confirmed=0",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE project_id=1001 AND allocation_confirmed=1",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_DUPLICATE:2"));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_CONFIRMED:1"));
    }
    @Test void invalidPrecisionDoesNotConsumeCapacityOfAnOtherwiseValidProject(){
        formalProject();secondFormalProject();orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE com_sales_order_line SET order_qty=10,unit_scale=0 WHERE id=50");
        var invalid=row(1,"OFFICE").setProjectQuantity(new BigDecimal("6.1"));
        var valid=row(2,"OFFICE").setOriginalProjectId(2L).setCanonicalProjectId(2L).setOriginalProjectCode("P-2").setProjectQuantity(new BigDecimal("5"));
        var c=context(false);assertEquals(2,transaction.execute(status->task(List.of(invalid,valid),()->{}).execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE project_id=1000 AND allocation_confirmed=0",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE project_id=1001 AND allocation_confirmed=1",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_EXCESS:1"));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_CONFIRMED:1"));
    }
    @Test void eligibleProjectsThatCollectivelyExceedCapacityRemainUnconfirmed(){
        formalProject();secondFormalProject();orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE com_sales_order_line SET order_qty=10 WHERE id=50");
        var first=row(1,"OFFICE").setProjectQuantity(new BigDecimal("6"));
        var second=row(2,"OFFICE").setOriginalProjectId(2L).setCanonicalProjectId(2L).setOriginalProjectCode("P-2").setProjectQuantity(new BigDecimal("5"));
        var c=context(false);assertEquals(2,transaction.execute(status->task(List.of(first,second),()->{}).execute(c)).inserted());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=0",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_EXCESS:2"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version",Integer.class));
    }
    @Test void existingConfirmedQuantityPreventsExcessWithoutChangingThatScope(){
        formalProject();orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE com_sales_order_line SET order_qty=2 WHERE id=50");
        insertScope(201,1,1,9,-1,"OFFICE");
        jdbc.update("UPDATE com_delivery_scope SET project_id=1001,order_line_id=50,allocation_confirmed=1,update_time=update_time WHERE id=201");
        var c=context(false);var rows=List.of(row(1,"OFFICE").setProjectQuantity(new BigDecimal("2")));
        assertEquals(1,transaction.execute(status->task(rows,()->{}).execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE id=201 AND project_id=1001 AND order_line_id=50 AND allocation_confirmed=1 AND allocated_qty=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE id<>201 AND order_line_id=50 AND allocation_confirmed=0",Integer.class));
        assertEquals(9L,jdbc.queryForObject("SELECT last_run_id FROM int_base_sync_binding WHERE id=201",Long.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_EXCESS:1"));
    }
    @Test void laterSameProjectOrderIsUnconfirmedAndCurrentSlotRejectsDuplicate(){
        formalProject();orderLine(50,"SAP","0",true);
        assertEquals(1,run(task(List.of(row(1,"OFFICE")),()->{}),false).inserted());
        jdbc.update("UPDATE int_base_sync_binding SET last_run_id=9 WHERE object_key='PRODUCT_SCOPE' AND source_key='1'");
        var c=context(false);assertEquals(1,transaction.execute(status->task(List.of(row(2,"OFFICE")),()->{}).execute(c)).inserted());
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_DUPLICATE:1"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=0",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE com_delivery_scope SET allocation_confirmed=1 WHERE order_line_id=50 AND allocation_confirmed=0"));
    }
    @Test void changedOrderIdentityAfterPostfillRemainsUnconfirmed(){
        formalProject();orderLine(50,"SAP","0",true);
        doAnswer(call->{
            copyOrderMetadata();int affected=session.getMapper(P22ProductScopeSyncTask.TargetMapper.class).backfillOrderLineIds(call.getArgument(0));
            jdbc.update("UPDATE com_sales_order_line SET product_code='CHANGED' WHERE id=50");return affected;
        }).when(mapper).backfillOrderLineIds(any());
        var c=context(false);assertEquals(1,transaction.execute(status->task(List.of(row(1,"OFFICE")),()->{}).execute(c)).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=0",Integer.class));
        assertTrue(c.runState().warnings().contains("P22_ALLOCATION_PENDING:1"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version",Integer.class));
    }
    @Test void allocationRunsWithRuntimeTenantAndDataPermissionInterceptors(){
        formalProject();orderLine(50,"SAP","0",true);insertScope(205,2,4,10,-1,"OFFICE");
        var type=P22ProductScopeSyncTask.TargetMapper.class;
        var strategy=com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper.initSqlParserInfoCache(type);
        for(var method:type.getDeclaredMethods())com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper.initSqlParserInfoCache(strategy,type.getName(),method);
        var interceptor=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor(
                (com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler)(table,where,id)->null));
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler(){public net.sf.jsqlparser.expression.Expression getTenantId(){return new net.sf.jsqlparser.expression.LongValue(1);}public boolean ignoreTable(String table){return "t_company".equalsIgnoreCase(table);}}));
        session.getConfiguration().addInterceptor(interceptor);
        assertEquals(1,run(task(List.of(row(1,"OFFICE")),()->{}),false).inserted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE tenant_id=1 AND allocation_confirmed=1",Integer.class));
        var foreign=new P22ProductScopeSyncTask.AllocationRow().setScopeId(205L).setOrderLineId(-1L);
        assertEquals(0,mapper.confirmAllocations(new P22ProductScopeSyncTask.AllocationUpdates(1,List.of(foreign))));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE tenant_id=2 AND id=205 AND allocation_confirmed=0",Integer.class));
    }
    @Test void replayDoesNotChangeOlderUnconfirmedScopeOrItsBinding(){
        var old=row(1,"OFFICE");assertEquals(1,run(task(List.of(old),()->{}),false).inserted());
        var before=saved.get("PRODUCT_SCOPE:1");jdbc.update("UPDATE int_base_sync_binding SET last_run_id=9 WHERE object_key='PRODUCT_SCOPE' AND source_key='1'");
        formalProject();orderLine(50,"SAP","0",true);
        var fresh=row(2,"OFFICE");var counts=run(task(List.of(old,fresh),()->{}),false);
        assertEquals(1,counts.inserted());assertEquals(1,counts.unchanged());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE id=? AND order_line_id=-1 AND allocation_confirmed=0",Integer.class,before.targetId()));
        assertEquals(9L,jdbc.queryForObject("SELECT last_run_id FROM int_base_sync_binding WHERE object_key='PRODUCT_SCOPE' AND source_key='1'",Long.class));
        assertEquals(before.fingerprint(),saved.get("PRODUCT_SCOPE:1").fingerprint());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND allocation_confirmed=1",Integer.class));
    }
    @Test void rawInsertPrecedesOneOwnedOrderPostfillAndReplayProtectsDerivedIdentity(){
        orderLine(50,"SAP","0",true);
        var rows=List.of(row(1,"OFFICE"),row(2,"OFFICE"));var task=task(rows,()->assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=-1",Integer.class)));
        assertEquals(2,run(task,false).inserted());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=50 AND order_source_system='SAP' AND order_type='0' AND update_time=?",Integer.class,at));
        verify(mapper,times(1)).backfillOrderLineIds(any());
        assertEquals(2,run(task(rows,()->{}),true).unchanged());
        jdbc.update("UPDATE com_delivery_scope SET order_line_id=88,update_time=update_time");
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(rows,()->{}),true)).getMessage().contains("ORDER_LINE_IDENTITY_CHANGED"));
    }
    @Test void orderPostfillUsesTheApprovedLineTypeBindingWhileKeepingTheActualParentType(){
        orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE com_sales_order_line SET line_type='1' WHERE id=50");
        jdbc.update("UPDATE int_base_sync_binding SET source_key=SHA2(CAST(JSON_ARRAY('SAP','001',1,'O','1') AS CHAR),256) WHERE id=50");
        copyOrderMetadata();
        var rows=List.of(row(1,"OFFICE"));
        assertEquals(1,run(task(rows,()->{}),false).inserted());
        assertEquals(50L,jdbc.queryForObject("SELECT order_line_id FROM com_delivery_scope",Long.class));
        assertEquals("0",jdbc.queryForObject("SELECT order_type FROM com_delivery_scope",String.class));
        assertEquals(1,run(task(rows,()->{}),true).unchanged());
        verify(mapper,times(1)).backfillOrderLineIds(any());
    }
    @Test void aParentTypeHashCannotStandInForTheIndependentLineTypeBinding(){
        orderLine(50,"SAP","0",true);
        jdbc.update("UPDATE com_sales_order_line SET line_type='1' WHERE id=50");
        copyOrderMetadata();
        assertTrue(mapper.selectOwnedOrderCatalogue(new P22ProductScopeSyncTask.OwnedOrderCatalogueQuery(1,3)).isEmpty());
        insertScope(201,1,1,10,-1,"OFFICE");
        assertEquals(0,mapper.backfillOrderLineIds(new P22ProductScopeSyncTask.CurrentRunQuery(1,3)));
        assertEquals(-1L,jdbc.queryForObject("SELECT order_line_id FROM com_delivery_scope",Long.class));
    }
    @Test void orderPostfillCannotTouchOtherTenantsTasksRunsConnectionsOrPositiveIds() throws Exception {
        orderLine(50,"SAP","0",true);
        insertScope(201,1,1,10,-1,"OFFICE");insertScope(202,1,1,9,-1,"OFFICE");insertScope(203,1,2,10,-1,"OFFICE");
        insertScope(204,1,1,10,-1,"OFFICE");insertScope(205,2,4,10,-1,"OFFICE");insertScope(206,1,3,10,-1,"OFFICE");insertScope(207,1,1,10,-1,"OFFICE");
        jdbc.update("UPDATE com_delivery_scope SET order_line_id=88,update_time=update_time WHERE id=204");
        jdbc.update("UPDATE com_delivery_scope SET order_no='o',update_time=update_time WHERE id=207");
        var q=new P22ProductScopeSyncTask.CurrentRunQuery(1,3);
        assertEquals(1,mapper.backfillOrderLineIds(q));assertEquals(50L,jdbc.queryForObject("SELECT order_line_id FROM com_delivery_scope WHERE id=201",Long.class));
        assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE order_line_id=-1",Integer.class));assertEquals(88L,jdbc.queryForObject("SELECT order_line_id FROM com_delivery_scope WHERE id=204",Long.class));
        assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(session.getConfiguration().getMappedStatement(P22ProductScopeSyncTask.TargetMapper.class.getName()+".backfillOrderLineIds").getBoundSql(Map.of("query",q)).getSql()));
    }
    @Test void missingOrAmbiguousOrderReferencesStayMinusOneWithoutLosingRawRows(){
        orderLine(50,"SAP","0",true);orderLine(51,"D365","0",true);
        var c=context(false);assertEquals(1,transaction.execute(status->task(List.of(row(1,"OFFICE")),()->{}).execute(c)).inserted());
        assertEquals(-1L,jdbc.queryForObject("SELECT order_line_id FROM com_delivery_scope",Long.class));assertTrue(c.runState().warnings().contains("P22_ORDER_LINE_POSTFILL_UNRESOLVED:1"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_detail",Integer.class));
        assertTrue(mapper.selectOwnedOrderCatalogue(new P22ProductScopeSyncTask.OwnedOrderCatalogueQuery(1,3)).isEmpty());
    }
    @Test void orderPostfillRequiresAnOwnCompletedNonpreviewP14Binding(){
        orderLine(50,"SAP","0",false);insertScope(201,1,1,10,-1,"OFFICE");var q=new P22ProductScopeSyncTask.CurrentRunQuery(1,3);
        assertEquals(0,mapper.backfillOrderLineIds(q));
        jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(50,1,5,'SALES_LINE',SHA2(CAST(JSON_ARRAY('SAP','001',0,'O','1') AS CHAR),256),50,'a',20)");
        jdbc.update("UPDATE int_base_sync_binding SET source_key='wrong-business-key' WHERE id=50");assertEquals(0,mapper.backfillOrderLineIds(q));
        jdbc.update("UPDATE int_base_sync_binding SET source_key=SHA2(CAST(JSON_ARRAY('SAP','001',0,'O','1') AS CHAR),256) WHERE id=50");
        jdbc.update("UPDATE int_base_sync_run SET status='PREVIEW_READY',preview=TRUE WHERE id=20");assertEquals(0,mapper.backfillOrderLineIds(q));
        jdbc.update("UPDATE int_base_sync_run SET status='COMPLETED',preview=FALSE WHERE id=20");jdbc.update("UPDATE int_base_sync_task SET connection_id=4 WHERE id=5");assertEquals(0,mapper.backfillOrderLineIds(q));
        jdbc.update("UPDATE int_base_sync_task SET connection_id=3 WHERE id=5");assertEquals(1,mapper.backfillOrderLineIds(q));
        assertEquals(50L,jdbc.queryForObject("SELECT order_line_id FROM com_delivery_scope",Long.class));
    }
    @Test void actualSchemaRestrictionWarnsPreviewAndStopsExecutionBeforeAnyWrite(){
        assertEquals(0,session.getMapper(P22ProductScopeSyncTask.TargetMapper.class).pendingOrderSchemaReady());
        doReturn(0).when(mapper).pendingOrderSchemaReady();var c=context(true);
        assertEquals(1,transaction.execute(status->task(List.of(row(1,"OFFICE")),()->{}).execute(c)).inserted());
        assertTrue(c.runState().warnings().contains("P22_EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(List.of(row(1,"OFFICE")),()->{}),false)).getMessage().contains("EXECUTION_SCHEMA_REQUIRED"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));assertTrue(saved.isEmpty());
    }
    @Test void lateOrderPostfillFailureRollsBackScopeDetailsAndOwnBindings(){
        doThrow(new IllegalStateException("postfill failed")).when(mapper).backfillOrderLineIds(any());
        assertThrows(IllegalStateException.class,()->run(task(List.of(row(1,"OFFICE")),()->{}),false));
        for(var table:List.of("com_delivery_scope","com_delivery_scope_detail","int_base_sync_binding"))assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
    }
    @Test void reviewedZeroQuantityRowsRemainCountedAndPendingWithoutForeignReferenceOrTargetWrites(){
        var zero=row(2,null).setCanonicalProjectId(999L).setProjectQuantity(BigDecimal.ZERO).setOrderQuantity(BigDecimal.TEN).setOrderNumber(null);
        var c=context(true);var counts=transaction.execute(status->task(List.of(row(1,"OFFICE"),zero),()->{}).execute(c));
        assertEquals(2,counts.read());assertEquals(1,counts.inserted());assertEquals(0,counts.unchanged());
        assertTrue(c.runState().warnings().contains("P22_ZERO_QUANTITY_PENDING:1"));
        verify(projects,times(1)).resolve(argThat(q->q.canonicalKeys().equals(List.of("1"))));
        assertTrue(saved.isEmpty());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));
    }
    @Test void zeroPendingDoesNotRelaxNegativeQuantityProtection(){
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(task(List.of(row(1,"OFFICE").setProjectQuantity(BigDecimal.ONE.negate())),()->{}),true)).getMessage().contains("POSITIVE_QUANTITY_REQUIRED"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope",Integer.class));assertTrue(saved.isEmpty());
    }
}
