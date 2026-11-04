package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDeviceProjects.*;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All writes are connection-local temporary tables; no persistent schema or business rows are modified. */
@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class DeviceProjectSyncMySqlTest {
    java.sql.Connection connection;
    java.net.URLClassLoader loader;
    JdbcTemplate jdbc;
    SqlSessionTemplate session;
    TransactionTemplate transaction;
    TargetMapper mapper;
    BaseSyncDeviceProjects sync;
    PermissionApi permissions;
    ProjectScopeApi projects;
    LocalDateTime at = LocalDateTime.of(2026, 11, 2, 12, 0);
    Map<String,String> copies = new LinkedHashMap<>();
    int batchSize = 1;

    @BeforeEach void setup() throws Exception {
        loader = new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver = (java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties = new Properties();
        properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));
        properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
        connection = driver.connect(System.getenv("BASE_PRODUCT_DB_URL"),properties);
        var data = new SingleConnectionDataSource(connection,true);
        jdbc = new JdbcTemplate(data);
        jdbc.execute("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
        for (var table : List.of("ast_device","ast_device_project_relationship","ast_device_shipment","imp_eng_installation","proj_project","proj_project_tree_version","com_project_contract_relation","com_delivery_scope","com_contract","com_shipment_contract_reference","plt_outbox_event","ast_device_project_ancestor","ast_device_ancestor_projection_operation","proj_project_tree_path"))
            { jdbc.execute("CREATE TEMPORARY TABLE " + table + "_schema_seed LIKE " + table);
              jdbc.execute("CREATE TEMPORARY TABLE " + table + " LIKE " + table + "_schema_seed"); }
        // The local test schema predates the approved raw-contract column; change only its temporary copy.
        if(jdbc.queryForList("SHOW COLUMNS FROM com_project_contract_relation LIKE 'contract_no'").isEmpty())
            jdbc.execute("ALTER TABLE com_project_contract_relation ADD COLUMN contract_no VARCHAR(64) COLLATE utf8mb4_unicode_ci NULL");
        jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_task(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_code VARCHAR(64),connection_id BIGINT,deleted BOOLEAN DEFAULT FALSE) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_binding(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,object_key VARCHAR(64),source_key VARCHAR(128) COLLATE utf8mb4_bin,target_id BIGINT,fingerprint VARCHAR(64),updater VARCHAR(64),deleted BOOLEAN DEFAULT FALSE) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        jdbc.execute("CREATE TEMPORARY TABLE pm_project_shipment(id BIGINT PRIMARY KEY,projectId BIGINT,barcode VARCHAR(100) COLLATE utf8mb4_unicode_ci,transferFlag VARCHAR(8),chProjectId VARCHAR(64),chContractNo VARCHAR(64),transferProjectId VARCHAR(64),transferContractNo VARCHAR(64)) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        jdbc.update("INSERT INTO int_base_sync_task(id,tenant_id,task_code,connection_id) VALUES(30,1,'BASE_P30',3),(16,1,'BASE_P16',3)");
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("temporary",new SpringManagedTransactionFactory(),data));
        for (var resource : List.of("mapper/basesync/DeviceProjectsTargetMapper.xml","base-sync-source/DeviceProjectsSourceMapper.xml")) {
            String xml;
            try (var input = new ClassPathResource(resource).getInputStream()) { xml = new String(input.readAllBytes(),StandardCharsets.UTF_8); }
            // MySQL cannot reopen one TEMPORARY table twice. Identical connection-local copies retain the production SQL's joins.
            for (var entry : Map.of("ast_device_project_relationship latest","ast_device_project_relationship", "imp_eng_installation newest","imp_eng_installation", "ast_device_shipment latest","ast_device_shipment", "proj_project child","proj_project", "pm_project_shipment latest","pm_project_shipment", "proj_project_tree_version active","proj_project_tree_version").entrySet()) {
                String alias = entry.getKey().substring(entry.getKey().lastIndexOf(' ') + 1);
                String copy = entry.getValue() + "_read_copy";
                if (xml.contains(entry.getKey())) { copies.put(copy,entry.getValue()); xml=xml.replace(entry.getKey(),copy+" "+alias); }
            }
            for (var fragment : List.of("activeContract","activeOrder")) {
                String marker = "<sql id=\""+fragment+"\">";
                int start=xml.indexOf(marker);
                if(start<0)continue;
                int end=xml.indexOf("</sql>",start)+6;
                String table=fragment.equals("activeContract")?"com_project_contract_relation":"com_delivery_scope";
                String copy=table+"_read_copy";
                copies.put(copy,table);
                String duplicate=xml.substring(start,end).replace(marker,"<sql id=\""+fragment+"Count\">").replace("FROM "+table+" ","FROM "+copy+" ");
                xml=xml.substring(0,end)+duplicate+xml.substring(end);
                String ref="<include refid=\""+fragment+"\"/>";
                int first=xml.indexOf(ref),second=xml.indexOf(ref,first+ref.length());
                xml=xml.substring(0,second)+ref.replace(fragment,fragment+"Count")+xml.substring(second+ref.length());
            }
            try(var input=new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) { new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse(); }
        }
        for (var entry : copies.entrySet()) jdbc.execute("CREATE TEMPORARY TABLE "+entry.getKey()+" LIKE "+entry.getValue());
        session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
        mapper=session.getMapper(TargetMapper.class);
        permissions=mock(PermissionApi.class);when(permissions.hasAnyPermissions(anyLong(),any())).thenReturn(true);
        projects=mock(ProjectScopeApi.class);when(projects.resolveAllCurrent(any())).thenReturn(Set.of(101L,102L,103L));
        sync=new BaseSyncDeviceProjects(mapper,projects,permissions);
        project(101);project(102);project(103);
        jdbc.update("UPDATE proj_project SET company_id=10,company_name='Company',department_id=20,department_code='D',department_name='Dept' WHERE id=102");
    }
    @AfterEach void close() throws Exception { try { if(connection!=null)connection.close(); } finally { if(loader!=null)loader.close(); } }
    void project(long id) {
        jdbc.update("INSERT INTO proj_project(id,tenant_id,project_code,root_id,code_root_id,tree_path,status) VALUES(?,1,?,?,?,'/','ACTIVE')",id,"P-"+id,id,id);
        jdbc.update("INSERT INTO proj_project_tree_version(id,tenant_id,root_project_id,tree_version,status,change_batch_id) VALUES(?,1,?,1,'ACTIVE',?)",id,id,"T-"+id);
    }
    void device(long id) {
        jdbc.update("INSERT INTO ast_device(id,tenant_id,sn,source_system,source_key,status) VALUES(?,1,?,'DPPMS',?,'IN_STOCK')",id,"SN-"+id,"SN:"+id);
        var row=mapper.selectDevicesByIds(new DeviceQuery(1,List.of(id))).getFirst();
        jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint) VALUES(?,1,30,'DEVICE_BASE',?,?,?)",10000+id,row.getSn(),id,P30DeviceBaseSyncTask.fingerprint(row));
    }
    void installation(long id,long device,long project) {
        jdbc.update("INSERT INTO imp_eng_installation(id,tenant_id,project_id,equipment_id,code,status,create_time,effective_to) VALUES(?,1,?,?,?,2,?,?)",id,project,device,"I-"+id,at.minusDays(id),at.minusMinutes(1));
    }
    void shipment(long id,long device,String contract,Long line) {
        jdbc.update("INSERT INTO ast_device_shipment(id,tenant_id,device_sn,event_type,source_key,source_system,sync_status,contract_no,order_line_id,shipment_time) VALUES(?,1,?,'SHIPMENT',?,'DPPMS','SUCCESS',?,?,?)",id,"SN-"+device,"S-"+id,contract,line,at);
    }
    void contract(long id,String number,long project) {
        jdbc.update("INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,contract_no,source_system,status) VALUES(?,1,?,1,?,'DPPMS','ACTIVE')",id,project,number);
    }
    void raw(long id,long device,long project,String transfer) {
        jdbc.update("INSERT INTO pm_project_shipment(id,projectId,barcode,transferFlag) VALUES(?,?,?,?)",id,project,"sn-"+device+" ",transfer);
        jdbc.update("INSERT IGNORE INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint) VALUES(?,1,16,'PROJECT_ALIAS',?,?,?)",20000+project,""+project,project,"0".repeat(64));
    }
    void copyReads() {
        for(var entry:copies.entrySet()) {
            String columns=jdbc.queryForList("SHOW COLUMNS FROM "+entry.getKey()).stream()
                    .filter(column -> !column.get("Extra").toString().matches(".*(STORED|VIRTUAL) GENERATED.*"))
                    .map(column -> "`"+column.get("Field")+"`").collect(java.util.stream.Collectors.joining(","));
            jdbc.update("DELETE FROM "+entry.getKey());
            jdbc.update("INSERT INTO "+entry.getKey()+" ("+columns+") SELECT "+columns+" FROM "+entry.getValue());
        }
        session.clearCache();
    }
    SyncContext<Void> context(boolean preview) {
        SyncSource source=id->new SyncSource.Session(){public SqlSession sqlSession(){return session;}public <M>M mapper(Class<M> type){return session.getMapper(type);}public void close(){}};
        return new SyncContext<>(1,2,3,preview,batchSize,100,at,source,mock(SyncIdentityStore.class),mock(SyncReferences.class),"BASE_P30",session,false,true);
    }
    void run(boolean preview) { copyReads();transaction.executeWithoutResult(status->sync.refresh(context(preview)));at=at.plusMinutes(1); }
    Long owner(long id) { return jdbc.queryForObject("SELECT project_id FROM ast_device WHERE id=?",Long.class,id); }
    long version(long id) { return jdbc.queryForObject("SELECT project_assignment_version FROM ast_device WHERE id=?",Long.class,id); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    void fingerprintMatches(long id) {
        session.clearCache();var row=mapper.selectDevicesByIds(new DeviceQuery(1,List.of(id))).getFirst();
        assertEquals(P30DeviceBaseSyncTask.fingerprint(row),jdbc.queryForObject("SELECT fingerprint FROM int_base_sync_binding WHERE task_id=30 AND target_id=?",String.class,id));
    }

    @Test void maximumInstallationIdWinsAndRepeatedIncrementalInvalidationKeepsHistory() {
        device(1);shipment(1,1,"CON",null);contract(1,"CON",101);
        installation(10,1,101);installation(20,1,102); // Higher ID has the older original time.
        run(false);assertEquals(102L,owner(1));assertEquals(1,version(1));fingerprintMatches(1);
        assertEquals(10L,jdbc.queryForObject("SELECT company_id FROM ast_device WHERE id=1",Long.class));
        assertEquals("PROJECT",jdbc.queryForObject("SELECT organization_source FROM ast_device WHERE id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plt_outbox_event WHERE event_id != JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventId')) OR tenant_id != 1 OR status != 'DELIVERED'",Integer.class));
        run(false);assertEquals(1,count("ast_device_project_relationship"));assertEquals(1,count("plt_outbox_event"));
        installation(30,1,103);run(false);assertEquals(103L,owner(1));assertEquals(2,version(1));
        jdbc.update("UPDATE imp_eng_installation SET status=0");jdbc.update("UPDATE com_project_contract_relation SET effective_to=?",at.minusSeconds(1));
        run(false);assertNull(owner(1));assertEquals(3,version(1));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_project_relationship WHERE effective_to IS NULL",Integer.class));
        run(false);assertEquals(3,version(1));
        installation(40,1,102);run(false);assertEquals(102L,owner(1));assertEquals(4,version(1));assertEquals(3,count("ast_device_project_relationship"));assertEquals(4,count("plt_outbox_event"));fingerprintMatches(1);
    }
    @Test void contractConflictClearsOnlySyncOwnedAssignmentAndNeverOverwritesManualOrUnboundData() {
        device(1);shipment(1,1,"CON",null);contract(1,"CON",101);run(false);
        contract(2,"CON",102);raw(50,1,103,"-1");
        device(2);jdbc.update("UPDATE ast_device SET project_id=102,project_assignment_version=1 WHERE id=2");
        jdbc.update("INSERT INTO ast_device_project_relationship(device_sn,tenant_id,project_id,relationship_type,effective_from,assignment_version,reason,operation_id,source_system,source_key) VALUES('SN-2',1,102,'DIRECT',?,1,'manual','manual-2','PMS','manual-2')",at.minusDays(1));
        device(3);jdbc.update("DELETE FROM int_base_sync_binding WHERE target_id=3 AND task_id=30");jdbc.update("UPDATE ast_device SET project_id=103 WHERE id=3");
        run(false);assertNull(owner(1));assertEquals(102L,owner(2));assertEquals(1,version(2));assertEquals(103L,owner(3));
        assertEquals(Set.of(2L),sync.manualDeviceIds(new DeviceQuery(1,List.of(1L,2L,3L))));
    }
    @Test void rawFallbackUsesMaximumSourceIdAndRejectsTransferOrMissingAlias() {
        device(1);raw(1,1,101,"-1");raw(2,1,102,"-1");run(false);assertEquals(102L,owner(1));
        raw(3,1,103,"1");run(false);assertNull(owner(1));
        raw(4,1,103,"-1");jdbc.update("DELETE FROM int_base_sync_binding WHERE object_key='PROJECT_ALIAS' AND source_key='103'");
        run(false);assertNull(owner(1));assertEquals(2,version(1));
    }
    @Test void latestFormalShipmentReplacesOldContractSnapshotWithoutRewritingTheBase() {
        device(1);shipment(1,1,"OLD",null);contract(1,"OLD",101);run(false);assertEquals(101L,owner(1));
        shipment(2,1,"NEW",null);contract(2,"NEW",102);run(false);assertEquals(102L,owner(1));
        assertNull(jdbc.queryForObject("SELECT contract_no FROM ast_device WHERE id=1",String.class));fingerprintMatches(1);
    }
    @Test void uniqueOrderScopeIsUsedOnlyWithoutContractConflictAndStopsOnScopeAmbiguity() {
        device(1);shipment(1,1,"NONE",500L);
        jdbc.update("INSERT INTO com_delivery_scope(id,tenant_id,project_id,project_code,order_line_id,order_source_system,order_company_code,order_type,order_no,line_no,allocated_qty,scope_status,status,allocation_version,allocation_source,department_id,department_code,department_name,department_version,effective_from,creator,updater) VALUES(1,1,101,'P-101',500,'SAP','001','NORMAL','ORDER','10',1,'ACTIVE','ENABLED',1,'DPPMS',1,'D','Dept',1,?,'test','test')",at.minusDays(1));
        run(false);assertEquals(101L,owner(1));
        jdbc.update("INSERT INTO com_delivery_scope(id,tenant_id,project_id,project_code,order_line_id,order_source_system,order_company_code,order_type,order_no,line_no,allocated_qty,scope_status,status,allocation_version,allocation_source,department_id,department_code,department_name,department_version,effective_from,creator,updater) VALUES(2,1,102,'P-102',500,'SAP','001','NORMAL','ORDER','10',1,'ACTIVE','ENABLED',1,'DPPMS',1,'D','Dept',1,?,'test','test')",at.minusDays(1));
        raw(1,1,103,"-1");run(false);assertNull(owner(1));assertEquals(2,version(1));
    }
    @Test void unicodeContractsAndMixedAssignmentChangesWorkInOneNativeBatch() {
        batchSize=50;
        device(1);device(2);device(3);
        String number="\u5408\u540c-\uD83D\uDE80";
        shipment(1,1,number,null);contract(1,number,101);
        installation(2,2,102);installation(3,3,103);
        run(false);assertEquals(101L,owner(1));assertEquals(102L,owner(2));assertEquals(103L,owner(3));
        jdbc.update("UPDATE com_project_contract_relation SET effective_to=?",at.minusSeconds(1));
        installation(4,2,103);
        run(false);assertNull(owner(1));assertEquals(103L,owner(2));assertEquals(103L,owner(3));
        assertEquals(2,version(1));assertEquals(2,version(2));assertEquals(1,version(3));
        assertEquals(5,count("plt_outbox_event"));
        fingerprintMatches(1);fingerprintMatches(2);fingerprintMatches(3);
    }
    @Test void ancestorProjectionIsCommittedWithEventAndClearedWithVersionedReceipt() {
        project(201);
        jdbc.update("UPDATE proj_project SET parent_id=201,root_id=201 WHERE id=101");
        jdbc.update("INSERT INTO proj_project_tree_path(id,tenant_id,root_project_id,ancestor_project_id,descendant_project_id,distance,tree_version) VALUES(1,1,201,201,101,1,1)");
        device(1);installation(1,1,101);run(false);
        assertEquals(201L,jdbc.queryForObject("SELECT ancestor_project_id FROM ast_device_project_ancestor",Long.class));
        assertEquals(1,count("ast_device_ancestor_projection_operation"));
        assertEquals("DELIVERED",jdbc.queryForObject("SELECT status FROM plt_outbox_event",String.class));
        run(false);assertEquals(1,count("ast_device_ancestor_projection_operation"));
        jdbc.update("UPDATE imp_eng_installation SET status=0");run(false);
        assertEquals(0,count("ast_device_project_ancestor"));
        assertEquals(2,count("ast_device_ancestor_projection_operation"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_ancestor_projection_operation WHERE project_id=101 AND assignment_version=2 AND tree_version=0",Integer.class));
    }
    @Test void newerProjectionWatermarkAndEventWriteFailureRollbackAllSyncWrites() {
        device(1);installation(1,1,101);
        jdbc.update("INSERT INTO ast_device_ancestor_projection_operation(event_id,operation_id,device_sn,project_id,tree_version,assignment_version,tenant_id) VALUES('newer','newer','SN-1',101,1,5,1)");
        assertThrows(IllegalStateException.class,()->run(false));assertNull(owner(1));fingerprintMatches(1);
        assertEquals(0,count("ast_device_project_relationship"));assertEquals(0,count("plt_outbox_event"));
        jdbc.update("DELETE FROM ast_device_ancestor_projection_operation");
        var delegate=mock(TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(mapper));
        doReturn(0).when(delegate).insertEvents(any());sync=new BaseSyncDeviceProjects(delegate,projects,permissions);
        assertThrows(IllegalStateException.class,()->run(false));assertNull(owner(1));fingerprintMatches(1);
        assertEquals(0,count("ast_device_ancestor_projection_operation"));assertEquals(0,count("ast_device_project_ancestor"));
        assertEquals(0,count("ast_device_project_relationship"));assertEquals(0,count("plt_outbox_event"));
    }
    @Test void previewDoesNotWriteAnyProjectionBindingHistoryOrEvent() {
        device(1);installation(1,1,101);String before=jdbc.queryForObject("SELECT fingerprint FROM int_base_sync_binding WHERE task_id=30",String.class);
        run(true);assertNull(owner(1));assertEquals(0,version(1));assertEquals(0,count("ast_device_project_relationship"));assertEquals(0,count("plt_outbox_event"));
        assertEquals(before,jdbc.queryForObject("SELECT fingerprint FROM int_base_sync_binding WHERE task_id=30",String.class));
    }
    @Test void targetTamperingRollsBackEarlierPages() {
        device(1);device(2);installation(1,1,101);installation(2,2,102);jdbc.update("UPDATE ast_device SET remark='changed outside sync' WHERE id=2");
        assertThrows(IllegalStateException.class,()->run(false));assertNull(owner(1));assertNull(owner(2));assertEquals(0,count("ast_device_project_relationship"));assertEquals(0,count("plt_outbox_event"));fingerprintMatches(1);
    }
    @Test void emptyManageScopeAndMissingPermissionBothFailClosed() {
        device(1);installation(1,1,101);when(projects.resolveAllCurrent(any())).thenReturn(Set.of());
        assertThrows(AccessDeniedException.class,()->run(false));assertNull(owner(1));
        when(projects.resolveAllCurrent(any())).thenReturn(Set.of(101L));when(permissions.hasAnyPermissions(anyLong(),any())).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->run(false));assertEquals(0,count("ast_device_project_relationship"));assertEquals(0,count("plt_outbox_event"));
    }
    @Test void conditionalWriteFailureRollsBackHistoryBindingsAndEventsAcrossPages() {
        device(1);device(2);installation(1,1,101);installation(2,2,102);
        var delegate=mock(TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(mapper));
        doAnswer(call->{var batch=call.<WriteBatch>getArgument(0);return batch.changes().getFirst().device().getId()==2?0:mapper.updateDevices(batch);}).when(delegate).updateDevices(any());
        sync=new BaseSyncDeviceProjects(delegate,projects,permissions);
        assertThrows(IllegalStateException.class,()->run(false));assertNull(owner(1));assertEquals(0,version(1));assertEquals(0,count("ast_device_project_relationship"));assertEquals(0,count("plt_outbox_event"));fingerprintMatches(1);
    }
    @Test void tenantAndEmptyCollectionsCannotWidenQueries() {
        device(1);installation(1,1,101);copyReads();
        assertTrue(mapper.selectDevices(new PageQuery(2,0,10,true)).isEmpty());
        assertTrue(mapper.selectDevicesByIds(new DeviceQuery(1,List.of())).isEmpty());
        assertTrue(mapper.selectLatestRelationships(new DeviceQuery(1,List.of())).isEmpty());
        assertTrue(mapper.selectFacts(new BatchQuery(1,3,List.of(),at)).isEmpty());
        assertTrue(mapper.selectAssignableProjects(new ProjectQuery(1,List.of())).isEmpty());
        assertTrue(mapper.selectAliases(new AliasQuery(1,3,List.of())).isEmpty());
        assertTrue(session.getMapper(SourceMapper.class).selectLatestRaw(new RawQuery(List.of())).isEmpty());
    }
}
