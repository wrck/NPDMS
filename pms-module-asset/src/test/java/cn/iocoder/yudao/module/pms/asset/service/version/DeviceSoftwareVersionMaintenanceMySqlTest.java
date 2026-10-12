package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceLogParsedFactMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParsedFactDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareChangeBatchDO;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi.ParsedLog;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceLogParsedFactService;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Every mutation uses connection-local temporary tables; no existing business row is changed. */
@EnabledIfSystemProperty(named="npdms.software-maintenance.mysql",matches="true")
class DeviceSoftwareVersionMaintenanceMySqlTest {
    Connection db; SingleConnectionDataSource source; DeviceSoftwareVersionMaintenanceService service;
    DeviceSoftwareVersionRecordMapper records; SoftwareVersionWriteMapper writes;
    DeviceLogParsedFactMapper facts; DeviceLogParsedFactService logs; DeviceSoftwareChangeBatchMapper batches;
    final SoftwareVersionValues next=new SoftwareVersionValues("NEW",null,"SERIES",null,"",null,"PCB",false,null,"说明");
    @BeforeEach void setup() throws Exception {
        var authenticated=new cn.iocoder.yudao.framework.security.core.LoginUser();authenticated.setId(7L);authenticated.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(authenticated,null));
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        source=new SingleConnectionDataSource(db,true);
        String sql=Files.readString(Path.of("../sql/migrations/V414__dppms_native_source_business_records.sql"));
        int begin=sql.indexOf("CREATE TABLE ast_device_software_version_record (");int end=sql.indexOf(';',begin);
        try(var statement=db.createStatement()) {
            statement.execute(sql.substring(begin,end).replace("CREATE TABLE","CREATE TEMPORARY TABLE"));
            int batchStart=sql.indexOf("CREATE TABLE ast_device_software_change_batch (");
            statement.execute(sql.substring(batchStart,sql.indexOf(';',batchStart)).replace("CREATE TABLE","CREATE TEMPORARY TABLE"));
            statement.execute("CREATE TEMPORARY TABLE software_fact_fixture LIKE ast_device_log_parsed_fact");
            statement.execute("ALTER TABLE software_fact_fixture RENAME TO ast_device_log_parsed_fact");
            // LIKE preserves real columns/defaults/indexes. Temporary tables cannot carry foreign keys;
            // these tests update an existing current row and do not test current/SN FK creation.
            statement.execute("CREATE TEMPORARY TABLE software_current_fixture LIKE ast_device_network_version");
            statement.execute("ALTER TABLE software_current_fixture RENAME TO ast_device_network_version");
            statement.execute("CREATE TEMPORARY TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,sn VARCHAR(100),project_id BIGINT,version BIGINT NOT NULL,conp_version VARCHAR(255),conp_type VARCHAR(100),conp_series VARCHAR(100),conp_mark VARCHAR(255),product_code VARCHAR(100),updater VARCHAR(64),update_time DATETIME(3),deleted BIT NOT NULL DEFAULT 0) ENGINE=InnoDB");
            statement.execute("INSERT INTO ast_device(id,tenant_id,sn,project_id,version,conp_version,product_code) VALUES(8,1,'SN',10,4,'OLD','PROTECTED')");
            statement.execute("INSERT INTO ast_device_network_version(id,tenant_id,device_sn,conp_version,boot_version,cpld_version,pcb_version,version,effective_from,source_system,source_key,source_version,synced_at,sync_status,creator,create_time,updater,update_time) VALUES(3,1,'SN','OLD','',NULL,'PCB',2,'2024-01-02 03:04:05.123','DEVICE_LOG','old-log','old-parser','2024-01-02 03:04:05.123','SYNCED','ORIGINAL','2023-01-02 03:04:05.123','old-updater','2024-01-02 03:04:05.123')");
        }
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(source);
        var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
        factory.setGlobalConfig(global);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setDefaultScriptingLanguage(com.baomidou.mybatisplus.core.MybatisXMLLanguageDriver.class);
        global.setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig());
        global.getDbConfig().setIdType(com.baomidou.mybatisplus.annotation.IdType.AUTO);
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config,global);
        config.addMapper(DeviceNetworkVersionMapper.class);config.addMapper(DeviceLogParsedFactMapper.class);factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/version/DeviceSoftwareObservationMapper.xml"),new ClassPathResource("mapper/version/DeviceSoftwareVersionRecordMapper.xml"),new ClassPathResource("mapper/version/SoftwareVersionWriteMapper.xml"),new ClassPathResource("mapper/version/DeviceSoftwareChangeBatchMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());records=session.getMapper(DeviceSoftwareVersionRecordMapper.class);
        writes=spy(session.getMapper(SoftwareVersionWriteMapper.class));
        facts=spy(session.getMapper(DeviceLogParsedFactMapper.class));batches=session.getMapper(DeviceSoftwareChangeBatchMapper.class);
        var projects=mock(ProjectScopeApi.class);var scope=new ProjectScopeResult(10L,3L,Set.of(10L),Set.of());
        when(projects.resolveCurrent(any())).thenReturn(scope);when(projects.lockAndRevalidate(any())).thenReturn(scope);
        var permission=mock(PermissionApi.class);when(permission.hasAnyPermissions(7L,"pms:device:update")).thenReturn(true);
        var target=new DeviceSoftwareVersionMaintenanceService(mock(DeviceMapper.class),session.getMapper(DeviceNetworkVersionMapper.class),records,writes,
                projects,mock(DeviceAccessScopeService.class),permission,mock(EntityExtensionApi.class));
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        service=(DeviceSoftwareVersionMaintenanceService)proxy.getProxy();TenantContextHolder.setTenantId(1L);
        var logDevices=mock(DeviceMapper.class);
        when(logDevices.selectByTenantAndSn(1L,"SN")).thenAnswer(i -> writes.selectDeviceForUpdate(new SoftwareVersionDeviceLockQuery(1L,8L)));
        when(logDevices.selectLogDeviceForUpdate(new DeviceLogDeviceLockQuery(1L,8L))).thenAnswer(i -> writes.selectDeviceForUpdate(new SoftwareVersionDeviceLockQuery(1L,8L)));
        var logProxy=new ProxyFactory(new DeviceLogParsedFactService(logDevices,facts,service,projects,
                mock(cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper.class)));
        logProxy.setProxyTargetClass(true);
        logProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        logs=(DeviceLogParsedFactService)logProxy.getProxy();
    }
    @AfterEach void close() throws Exception {TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();if(source!=null)source.destroy();else if(db!=null)db.close();}
    Object scalar(String sql) throws Exception {try(var statement=db.createStatement();var rs=statement.executeQuery(sql)){assertTrue(rs.next());return rs.getObject(1);}}
    ParsedLog log(String key,java.time.LocalDateTime received,String conp,String boot) {
        return new ParsedLog(1L,7L,10L,key,"archive:"+key,received,"parse:"+key,"parser",42L,"SN",conp,boot,null,null,1L);
    }
    @Test void nativeLogSavesNamedHistoryAndFactTogetherThenReplaysWithoutWritingAgain() throws Exception {
        var command=log("native",java.time.LocalDateTime.of(2024,2,1,3,4,5,123000000),null,"BASIC-2");
        var result=logs.record(command);assertEquals("UPDATED",result.versionStatus());assertEquals(result,logs.record(command));
        assertEquals(1,((Number)scalar("SELECT COUNT(*) FROM ast_device_log_parsed_fact")).intValue());
        assertEquals(2,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
        assertEquals("BASIC-2",scalar("SELECT boot_version FROM ast_device_network_version WHERE id=3"));
        assertEquals("PARSED_FACT:"+facts.byRequest(1L,"native").getId(),scalar("SELECT source_version FROM ast_device_network_version WHERE id=3"));
        assertEquals(123000000,facts.byRequest(1L,"native").getLogReceivedAt().getNano());
        assertEquals(1,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record r JOIN ast_device_log_parsed_fact f ON r.parsed_fact_id=f.id WHERE r.data_source='DEVICE_LOG' AND r.previous_record_id IS NOT NULL")).intValue());
    }
    @Test void factInsertFailureRollsBackBothCurrentProjectionsAndNamedHistory() throws Exception {
        doReturn(0).when(facts).insert(any(DeviceLogParsedFactDO.class));
        assertThrows(IllegalStateException.class,()->logs.record(log("failed",java.time.LocalDateTime.of(2024,2,1,0,0),"LOG",null)));
        assertEquals(0,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
        assertEquals(0,((Number)scalar("SELECT COUNT(*) FROM ast_device_log_parsed_fact")).intValue());
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
        assertEquals(2,((Number)scalar("SELECT version FROM ast_device_network_version WHERE id=3")).intValue());
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device WHERE id=8"));
    }
    @Test void lateOldLogKeepsManualCurrentButItsFactAndNamedHistoryRemainQueryableOnlyInTheirProject() throws Exception {
        var manual=service.register(1L,7L,8L,10L,2,"manual",next);
        assertEquals("HISTORICAL",logs.record(log("late",java.time.LocalDateTime.of(2024,2,1,0,0),"LOG-OLD",null)).versionStatus());
        assertEquals("NEW",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
        assertEquals("SOFTWARE_RECORD:"+manual.recordId(),scalar("SELECT source_key FROM ast_device_network_version WHERE id=3"));
        assertEquals(3,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
        assertEquals(1,facts.visibleHistory(new DeviceParsedLogVisibleHistoryQuery(1L,8L,Set.of(10L))).size());
        assertTrue(facts.visibleHistory(new DeviceParsedLogVisibleHistoryQuery(1L,8L,Set.of(20L))).isEmpty());
        assertTrue(facts.visibleHistory(new DeviceParsedLogVisibleHistoryQuery(2L,8L,Set.of(10L))).isEmpty());
        assertTrue(facts.visibleHistory(new DeviceParsedLogVisibleHistoryQuery(1L,8L,Set.of())).isEmpty());
    }
    @Test void approvedCarryPreservesIdsSourceAuditAndEightPropertiesWithoutChangingCurrents() throws Exception {
        carry(false);
    }
    @Test void extensionFailureRollsBackFormalCarryAndBatchAuditInTheRealTransaction() throws Exception {
        carry(true);
    }
    @Test void approvedEventWithdrawalPreservesCustomInfoColumnAndReplaysAfterMirrorRemoval() throws Exception {
        withdrawEvents(false);
    }
    @Test void secondEventDeletionFailureRollsBackTheFirstInTheRealTransaction() throws Exception {
        withdrawEvents(true);
    }
    void withdrawEvents(boolean failure) throws Exception {
        try(var sql=db.createStatement()) {
            sql.execute(Files.readString(Path.of("src/test/resources/fixtures/software-network-events-v4.sql")));
            for(int i=0;i<2;i++) {
                sql.execute("INSERT INTO ast_device_network_version_event(id,tenant_id,device_sn,source_device_key,source_event_key,source_system,mapping_status,sync_status,event_time,received_time,source_version) VALUES("+(80+i)+",1,'SN','SN','pm_project_soft_version:"+(20+i)+"','DPPMS','MAPPED','SUCCESS','2020-01-02 03:04:05.123','2026-01-01','V3')");
                sql.execute("INSERT INTO ast_device_software_version_record(id,tenant_id,device_id,project_id,device_sn,recorded_at,data_source,create_time,update_time) VALUES("+(70+i)+",1,8,10,'SN','2020-01-02 03:04:05.123','DPPMS','2020-01-02 03:04:05.123','2020-01-02 03:04:05.123')");
            }
        }
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(source);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/version/DeviceNetworkVersionEventMapper.xml"));
        var events=spy(new SqlSessionTemplate(factory.getObject()).getMapper(DeviceNetworkVersionEventMapper.class));
        var history=mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.class);
        var guard=mock(cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi.class);
        when(guard.validate(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult(10L,1L,null,10L,1L,true,null));
        var owner=new cn.iocoder.yudao.module.pms.asset.service.migration.DppmsSoftwareLegacyEventWithdrawalService(events,records,batches,history,guard);
        var proxy=new ProxyFactory(owner);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        var withdrawal=(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi)proxy.getProxy();
        var rows=new java.util.ArrayList<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.SoftwareObservation>();
        var ids=new java.util.ArrayList<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.Identity>();
        for(int i=0;i<2;i++) {
            rows.add(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject("{\"sourceId\":"+(20+i)+",\"barCode\":\"SN\",\"mappedRecordId\":"+(70+i)+"}",cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.SoftwareObservation.class));
            ids.add(new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.Identity(20L+i,80L+i,70L+i));
        }
        var command=new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.Command(1L,7L,10L,"100","capture",java.time.LocalDateTime.of(2026,1,1,0,0),rows,List.of(),List.of());
        var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source));
        var baseline=tx.execute(status->withdrawal.preview(command,ids));
        if(failure) doReturn(0).when(events).withdrawLegacyEvent(argThat(query->query.eventId()==81L));
        var input=new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.Withdrawal(command,baseline);
        if(failure) {
            assertThrows(IllegalArgumentException.class,()->tx.execute(status->{withdrawal.withdraw(input);return null;}));
            assertEquals(2,((Number)scalar("SELECT COUNT(*) FROM ast_device_network_version_event")).intValue());
        } else {
            tx.execute(status->{withdrawal.withdraw(input);return null;});
            assertEquals(0,((Number)scalar("SELECT COUNT(*) FROM ast_device_network_version_event")).intValue());
            try(var sql=db.createStatement()){sql.execute("ALTER TABLE ast_device_network_version_event DROP source_log_key,DROP source_data_state");}
            tx.execute(status->{withdrawal.verifyWithdrawn(command,ids);return null;});
            try(var sql=db.createStatement();var columns=sql.executeQuery("SHOW COLUMNS FROM ast_device_network_version_event")) {
                int count=0;boolean customInfo=false;while(columns.next()){count++;customInfo|="custom_info".equals(columns.getString("Field"));}
                assertEquals(30,count);assertTrue(customInfo);
            }
        }
        assertEquals(2,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
        assertEquals(2,((Number)scalar("SELECT version FROM ast_device_network_version WHERE id=3")).intValue());
    }
    void carry(boolean extensionFailure) throws Exception {
        try(var sql=db.createStatement()) {
            sql.execute(Files.readString(Path.of("src/test/resources/fixtures/software-observation-v4.sql")));
            sql.execute("ALTER TABLE ast_device_software_change_batch ADD source_project_key VARCHAR(100),ADD source_system VARCHAR(32),ADD source_key VARCHAR(180),ADD source_version VARCHAR(128),ADD source_checksum VARCHAR(64),ADD source_read_at DATETIME(3),ADD source_id BIGINT,ADD source_project_id BIGINT,ADD source_created_at DATETIME(3),ADD source_created_by VARCHAR(64),ADD source_updated_at DATETIME(3),ADD source_updated_by VARCHAR(64)");
            sql.execute("INSERT INTO ast_device_software_observation(id,tenant_id,project_id,source_project_key,source_system,source_key,source_version,source_checksum,source_read_at,source_id,source_project_id,log_id,bar_code,conp,conp_bak,conp_change,boot_change,cpld_change,pcb_change,source_created_at,creator,create_time,updater,update_time) VALUES(70,1,10,'100','DPPMS','20','capture','row-checksum','2026-01-01',20,100,30,'SN','HISTORY','',0,0,0,0,'2020-01-02 03:04:05.123','1','2026-01-01','1','2026-01-01')");
            sql.execute("INSERT INTO ast_device_software_change_batch(id,tenant_id,project_id,change_version,latest,source_project_key,source_system,source_key,source_id,source_project_id,source_checksum,source_read_at,source_version,source_created_at,creator,create_time,updater,update_time) VALUES(81,1,10,'',0,'100','DPPMS','30',30,100,'batch-checksum','2026-01-01','capture','2020-01-02 03:04:05.123','1','2026-01-01','1','2026-01-01')");
        }
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(source);
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setMapperLocations(new ClassPathResource("mapper/version/DeviceSoftwareObservationMapper.xml"));
        var legacy=new SqlSessionTemplate(factory.getObject()).getMapper(DeviceSoftwareObservationMapper.class);
        var guard=mock(cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi.class);
        when(guard.validate(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult(10L,1L,null,10L,1L,true,null));
        var attrs=mock(SoftwareVersionLegacyExtensionService.class);
        if (extensionFailure) doThrow(new IllegalStateException("extension failure")).when(attrs).initialize(anyLong(),anyLong(),anyLong(),anyLong(),anyMap());
        var importer=new cn.iocoder.yudao.module.pms.asset.service.migration.DppmsAssetSourceHistoryService(records,batches,mock(DeviceFactoryVersionRecordMapper.class),
                mock(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMigrationIdentityMapper.class),guard,mock(DeviceAccessScopeService.class),
                mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class),attrs,mock(EntityExtensionApi.class));
        var target=new cn.iocoder.yudao.module.pms.asset.service.migration.DppmsSoftwareHistoryCorrectionService(legacy,batches,records,importer,guard);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        var correction=(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi)proxy.getProxy();
        var time=java.time.LocalDateTime.of(2020,1,2,3,4,5,123000000);
        var observation=new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.SoftwareObservation(20L,100L,30L,null,null,"SN","HISTORY",null,null,null,"",0,null,null,0,null,null,0,null,null,0,null,null,time,null,null,null,null).withMapping(70L,null,99L);
        var batch=new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.SoftwareBatch(30L,100L,"",null,0,null,time,null,null,81L);
        var command=new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.Command(1L,7L,10L,"100","capture",java.time.LocalDateTime.of(2026,1,1,0,0),List.of(observation),List.of(batch));
        List<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi.Baseline> baseline;
        // Production caller opens the migration transaction; MANDATORY cannot be bypassed by these tests.
        var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source));
        baseline=tx.execute(status->correction.preview(command));
        var request=new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi.Correction(command,baseline);
        if(extensionFailure) {
            assertThrows(IllegalStateException.class,()->tx.execute(status->correction.correct(request)));
            assertEquals(0,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
            assertEquals("1",scalar("SELECT updater FROM ast_device_software_change_batch WHERE id=81"));
        } else {
            assertEquals(2,tx.execute(status->correction.correct(request)).size());
            var read=records.selectById(70L);assertEquals("HISTORY",read.getConpVersion());assertEquals(81L,read.getChangeBatchId());
            assertNull(read.getCreator());assertNull(read.getUpdater());assertEquals(time,read.getCreateTime());assertEquals(time,read.getUpdateTime());
            verify(attrs).initialize(eq(1L),eq(7L),eq(70L),eq(99L),argThat(v->v.size()==8 && "".equals(v.get("conp_bak")) && v.containsKey("boot_bak") && v.get("boot_bak")==null && Integer.valueOf(0).equals(v.get("boot_change"))));
            assertNull(scalar("SELECT updater FROM ast_device_software_change_batch WHERE id=81"));
        }
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device WHERE id=8"));
        assertEquals("1",scalar("SELECT updater FROM ast_device_software_observation WHERE id=70"));
    }
    @Test void formalBatchPreservesNullActorsEmptyTextAndHistoricalDatesWithTenantProjectBoundLock() {
        var batch=new DeviceSoftwareChangeBatchDO();batch.setId(81L);batch.setTenantId(1L);batch.setProjectId(10L);
        batch.setChangeVersion("");batch.setLatest(0);batch.setCreateTime(java.time.LocalDateTime.of(2020,1,2,3,4,5,123000000));
        batch.setUpdateTime(batch.getCreateTime());batch.setDeleted(false);assertEquals(1,batches.insertHistorical(new DeviceSoftwareBatchHistoricalWrite(batch)));
        var read=batches.selectRecordForUpdate(new DeviceSoftwareBatchIdentityQuery(1L,10L,81L));
        assertEquals(batch,read);assertNull(read.getCreator());assertNull(read.getUpdater());assertNull(read.getChangeRemark());
        assertNull(batches.selectRecordForUpdate(new DeviceSoftwareBatchIdentityQuery(2L,10L,81L)));
        assertNull(batches.selectRecordForUpdate(new DeviceSoftwareBatchIdentityQuery(1L,20L,81L)));
    }
    @Test void atomicNamedBeforeAndAfterPreserveNullEmptyAuditAndOtherDeviceFields() throws Exception {
        var receipt=service.register(1L,7L,8L,10L,2,"request",next);
        var after=records.selectById(receipt.recordId());var before=records.selectById(after.getPreviousRecordId());
        assertEquals("CURRENT_BASELINE",before.getDataSource());assertEquals("OLD",before.getConpVersion());assertEquals("",before.getBootVersion());assertNull(before.getCpldVersion());
        assertEquals(123000000,before.getRecordedAt().getNano());assertEquals(next,SoftwareVersionValues.of(after));
        assertEquals("ORIGINAL",scalar("SELECT creator FROM ast_device_network_version WHERE id=3"));
        assertEquals("2023-01-02 03:04:05.123",scalar("SELECT CAST(create_time AS CHAR) FROM ast_device_network_version WHERE id=3"));
        assertEquals("PROTECTED",scalar("SELECT product_code FROM ast_device WHERE id=8"));
        assertEquals("NEW",scalar("SELECT conp_version FROM ast_device WHERE id=8"));
        assertEquals(3,((Number)scalar("SELECT version FROM ast_device_network_version WHERE id=3")).intValue());
    }
    @Test void projectionConflictRollsBackBaselineNewHistoryAndCurrentInTheRealSpringTransaction() throws Exception {
        doReturn(0).when(writes).updateDeviceProjection(any());
        assertThrows(RuntimeException.class,()->service.register(1L,7L,8L,10L,2,"request",next));
        assertEquals(0,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
        assertEquals(2,((Number)scalar("SELECT version FROM ast_device_network_version WHERE id=3")).intValue());
        assertEquals("OLD",scalar("SELECT conp_version FROM ast_device WHERE id=8"));
    }
    @Test void replayIsStableAndSameKeyDifferentContentNeverReplacesCurrent() throws Exception {
        var first=service.register(1L,7L,8L,10L,2,"request",next);
        var replay=service.register(1L,7L,8L,10L,2,"request",next);assertTrue(replay.replayed());assertEquals(first.recordId(),replay.recordId());
        assertThrows(RuntimeException.class,()->service.register(1L,7L,8L,10L,3,"request",new SoftwareVersionValues("OTHER",null,null,null,null,null,null,null,null,null)));
        assertEquals(2,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());assertEquals("NEW",scalar("SELECT conp_version FROM ast_device_network_version WHERE id=3"));
    }
    @Test void currentAndRecordLocksAreTenantBoundAndEmptyPermissionsNeverReturnHistory() {
        assertNull(writes.selectCurrentForUpdate(2L,"SN"));assertNull(writes.selectDeviceForUpdate(new SoftwareVersionDeviceLockQuery(2L,8L)));
        var receipt=service.register(1L,7L,8L,10L,2,"request",next);
        assertNull(records.selectForUpdate(new DeviceSoftwareVersionRecordLockQuery(2L,receipt.recordId())));
        assertTrue(records.selectHistoryPage(new DeviceSoftwareVersionHistoryPageQuery(1L,8L,Set.of(),1,20)).getList().isEmpty());
    }
    @Test void duplicateNativeRequestOrParsedFactIsRejectedAndLegacyNullKeysAllowDistinctRows() throws Exception {
        try(var statement=db.createStatement()) {
            String columns="(id,tenant_id,data_source,parsed_fact_id,request_key,create_time,update_time)";
            statement.execute("INSERT INTO ast_device_software_version_record"+columns+" VALUES(100,1,'DEVICE_LOG',42,'r','2024-01-01','2024-01-01')");
            assertThrows(SQLException.class,()->statement.execute("INSERT INTO ast_device_software_version_record"+columns+" VALUES(101,1,'DEVICE_LOG',42,'r2','2024-01-01','2024-01-01')"));
            assertThrows(SQLException.class,()->statement.execute("INSERT INTO ast_device_software_version_record"+columns+" VALUES(102,1,'PLATFORM_MANUAL',NULL,'r','2024-01-01','2024-01-01')"));
            statement.execute("INSERT INTO ast_device_software_version_record"+columns+" VALUES(103,1,'DPPMS',NULL,NULL,'2024-01-01','2024-01-01'),(104,1,'DPPMS',NULL,NULL,'2024-01-01','2024-01-01'),(105,2,'DEVICE_LOG',42,'r','2024-01-01','2024-01-01')");
        }
        assertEquals(4,((Number)scalar("SELECT COUNT(*) FROM ast_device_software_version_record")).intValue());
    }
}
