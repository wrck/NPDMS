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
