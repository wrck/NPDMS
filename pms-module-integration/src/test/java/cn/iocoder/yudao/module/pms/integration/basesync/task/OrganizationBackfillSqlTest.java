package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationBackfillSqlTest {
    @Test void frontendSizedCompletionBatchesParseWithTheRuntimeStackAndExplicitTenantPredicates() throws Exception {
        var config=config("P15TargetMapper");var rows=new ArrayList<P15DeliverySyncTask.Source>();
        for(long id=1;id<=2000;id++)rows.add(new P15DeliverySyncTask.Source().setExpectedVersion(2L)
                .setTarget(new P15DeliverySyncTask.Row().setId(id).setCompanyId(100L).setCompanyCode("001")
                        .setCompanyName("公司").setDepartmentId(200L).setDepartmentName("部门").setContractId(300L)));
        var context=context().forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
        for(var statement:List.of("updateContractOrganization","updateShipmentContractOrganization")){
            var batch=new SyncBatch<>(context,rows);var sql=config.getMappedStatement(P15DeliverySyncTask.TargetMapper.class.getName()+"."+statement).getBoundSql(batch).getSql();
            parseWithRuntimeStack(sql);assertTrue(sql.contains("tenant_id=?"));assertTrue(sql.contains("deleted=FALSE"));
        }
        var deviceConfig=config("P30TargetMapper");var devices=new ArrayList<P30DeviceBaseSyncTask.DeviceSource>();
        for(long id=1;id<=2000;id++)devices.add(new P30DeviceBaseSyncTask.DeviceSource().setTarget(new P30DeviceBaseSyncTask.DeviceRow()
                .setId(id).setCompanyId(100L).setVersion(2L).setProjectAssignmentVersion(0L)));
        var sql=deviceConfig.getMappedStatement(P30DeviceBaseSyncTask.TargetMapper.class.getName()+".updateDeviceOrganization")
                .getBoundSql(new SyncBatch<>(context().forParameters(new P30DeviceBaseSyncTask.Parameters()),devices)).getSql();
        parseWithRuntimeStack(sql);assertTrue(sql.contains("project_id IS NULL"));assertTrue(sql.contains("tenant_id=?"));
    }

    @Test void localCompletionSelectsAndNestedTargetMappingAreRegisteredWithoutCharsetConversion() throws Exception {
        var config=config("P15TargetMapper");
        for(var statement:List.of("selectContractOrganization","selectShipmentContractOrganization")){
            var mapped=config.getMappedStatement(P15DeliverySyncTask.TargetMapper.class.getName()+"."+statement);
            var sql=mapped.getBoundSql(new AbstractSyncTask.SourceQuery<>(1001,1,true,new P15DeliverySyncTask.Parameters(Map.of(),3))).getSql();
            parseWithRuntimeStack(sql);assertFalse(sql.toUpperCase(Locale.ROOT).contains("CONVERT("));assertFalse(sql.toUpperCase(Locale.ROOT).contains("COLLATE"));
            assertTrue(mapped.getResultMaps().getFirst().getPropertyResultMappings().stream().anyMatch(mapping->"target".equals(mapping.getProperty())&&mapping.getNestedResultMapId()!=null));
        }
    }

    @Test void completionCandidatesAreScopedToTheCurrentTaskConnectionBeforeLimiting() throws Exception {
        var config=config("P15TargetMapper");
        for(var statement:List.of("selectContractOrganization","selectShipmentContractOrganization")){
            var bound=config.getMappedStatement(P15DeliverySyncTask.TargetMapper.class.getName()+"."+statement)
                    .getBoundSql(new AbstractSyncTask.SourceQuery<>(2,1,true,new P15DeliverySyncTask.Parameters(Map.of(),3)));
            var sql=bound.getSql();
            assertTrue(sql.contains("int_base_sync_binding"));
            assertTrue(sql.contains("task_code='BASE_P15'"));
            assertTrue(bound.getParameterMappings().stream().anyMatch(p->p.getProperty().equals("parameters.connectionId")));
            assertTrue(sql.contains("b.target_id="));
            parseWithRuntimeStack(sql);
        }
    }

    @Test void formalLocalStreamingDoesNotEmitAnSqlLimit() throws Exception {
        var config=config("P15TargetMapper");
        for(var statement:List.of("selectContractOrganization","selectShipmentContractOrganization","selectOrderContractRelation")){
            var sql=config.getMappedStatement(P15DeliverySyncTask.TargetMapper.class.getName()+"."+statement)
                    .getBoundSql(new AbstractSyncTask.SourceQuery<>(null,1,false,new P15DeliverySyncTask.Parameters(Map.of(),3))).getSql();
            assertFalse(sql.toUpperCase(Locale.ROOT).contains("LIMIT"));
            parseWithRuntimeStack(sql);
        }
    }

    @Test void unrelatedRowsCannotConsumeTheCompletionPreviewLimit() throws Exception {
        var dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:p15_scope;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE com_contract(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,master_source_system VARCHAR,master_source_record_key VARCHAR)");
        jdbc.execute("CREATE TABLE int_base_sync_task(id BIGINT,tenant_id BIGINT,task_code VARCHAR,connection_id BIGINT,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE int_base_sync_binding(tenant_id BIGINT,task_id BIGINT,object_key VARCHAR,source_key VARCHAR,target_id BIGINT,deleted BOOLEAN)");
        jdbc.update("INSERT INTO com_contract VALUES(1,1,FALSE,'DPPMS_ERP','other'),(2,1,FALSE,'DPPMS_ERP','mine'),(3,2,FALSE,'DPPMS_ERP','tenant')");
        jdbc.update("INSERT INTO int_base_sync_task VALUES(10,1,'BASE_P15',3,FALSE),(11,1,'BASE_P15',4,FALSE),(12,1,'BASE_P14',3,FALSE)");
        jdbc.update("INSERT INTO int_base_sync_binding VALUES(1,11,'CONTRACT','other',1,FALSE),(1,12,'CONTRACT','other',1,FALSE),(1,10,'CONTRACT','mine',2,FALSE)");
        var config=config("P15TargetMapper");
        config.setEnvironment(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),dataSource));
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession()){
            List<P15DeliverySyncTask.Source> rows=session.selectList(P15DeliverySyncTask.TargetMapper.class.getName()+".selectContractOrganization",
                    new AbstractSyncTask.SourceQuery<>(1,1,true,new P15DeliverySyncTask.Parameters(Map.of(),3)));
            assertEquals(List.of("mine"),rows.stream().map(P15DeliverySyncTask.Source::getKey).toList());
            List<P15DeliverySyncTask.Source> empty=session.selectList(P15DeliverySyncTask.TargetMapper.class.getName()+".selectContractOrganization",
                    new AbstractSyncTask.SourceQuery<>(1,1,true,new P15DeliverySyncTask.Parameters(Map.of(),99)));
            assertTrue(empty.isEmpty(),"an unbound connection must not fall back to every source row");
        }
    }

    static Configuration config(String name)throws Exception{
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);String resource="mapper/basesync/"+name+".xml";
        try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}return config;
    }
    static SyncContext<Void> context(){return new SyncContext<>(1,2,3,true,2000,5000,LocalDateTime.of(2026,10,23,12,0),mock(SyncSource.class),
            mock(SyncIdentityStore.class),mock(SyncReferences.class),"Test",mock(SqlSession.class),false,true);}
    static void parseWithRuntimeStack(String sql)throws Exception{
        var failure=new AtomicReference<Throwable>();
        var thread=new Thread(null,()->{try{assertNotNull(JsqlParserGlobal.parse(sql));}catch(Throwable error){failure.set(error);}},"organization-sql-parser",512*1024);
        thread.setDaemon(true);thread.start();thread.join(10000);assertFalse(thread.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get());
    }
}
