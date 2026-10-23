package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsLogSplitApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL locks/transactions. DAC and PLT are test substitutes; runtime acceptance covers their actual services. */
@EnabledIfSystemProperty(named="skipITs", matches="false")
@SpringBootTest(classes=DeviceLogArchiveMySqlTest.Application.class, webEnvironment=SpringBootTest.WebEnvironment.NONE)
class DeviceLogArchiveMySqlTest {
    private static final long PROJECT = 999_861_000_000L + new java.security.SecureRandom().nextInt(1_000_000);
    @Resource DeviceLogArchiveService service;
    @Resource JdbcTemplate jdbc;
    @Resource DeviceOpsLogSplitApi dac;
    @Resource DeviceLogFileApi storage;
    @Resource FileEvidenceApi evidence;
    private long deviceId;
    private Long extraDeviceId;
    private String serial;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (!"npdms_test".equals(System.getenv("NPDMS_DB_NAME"))) throw new IllegalStateException("日志MySQL验证仅使用指定npdms_test库");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> System.getenv("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.asset");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }
    @BeforeEach void setup() {
        login(); reset(dac,storage,evidence); extraDeviceId=null;
        deviceId=999_862_000_000L+Math.abs(UUID.randomUUID().getLeastSignificantBits()%1_000_000);
        serial="IT-DEVICE-LOG-"+deviceId;
        jdbc.update("INSERT INTO ast_device(id,sn,name,project_id,project_assignment_version,status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) "
                + "VALUES(?,?,?, ?,0,'ACTIVE','PMS',?,'FRESH',0,'device-log-it','device-log-it',0,1)",deviceId,serial,"日志MySQL验证设备",PROJECT,"device-log-it-"+deviceId);
        when(dac.split(any())).thenAnswer(inv -> new DeviceOpsLogSplitApi.Result("SN: "+serial+"\nfull log\n",0,
                List.of(new DeviceOpsLogSplitApi.DeviceLog(serial,"SN: "+serial+"\nfull log\n"))));
        var next=new AtomicLong(900_000_000L);
        when(storage.store(any())).thenAnswer(inv -> {long id=next.incrementAndGet();return new NativeGeneratedFileApi.RegisteredFile(id,id,id,1,"digest","device.txt");});
        when(evidence.inspectDocument(anyLong(),anyLong())).thenAnswer(inv -> new FileEvidenceApi.Document(inv.getArgument(1),"AST","DeviceConfigurationLogFile","1",
                "DEVICE_CONFIGURATION_LOG","device-log",1L,1,"digest","device.txt",true));
    }
    @AfterEach void cleanup() {
        // Only rows created by this test's isolated virtual project and explicit device ID are removed.
        jdbc.update("DELETE FROM ast_device_log_file WHERE tenant_id=1 AND project_id=?",PROJECT);
        jdbc.update("DELETE FROM ast_device_log_import WHERE tenant_id=1 AND project_id=?",PROJECT);
        jdbc.update("DELETE FROM ast_device_log_version WHERE tenant_id=1 AND device_id=?",deviceId);
        jdbc.update("DELETE FROM ast_device WHERE id=? AND tenant_id=1 AND creator='device-log-it'",deviceId);
        if (extraDeviceId!=null) {
            jdbc.update("DELETE FROM ast_device_log_file WHERE tenant_id=1 AND project_id=?",PROJECT+1);
            jdbc.update("DELETE FROM ast_device_log_import WHERE tenant_id=1 AND project_id=?",PROJECT+1);
            jdbc.update("DELETE FROM ast_device_log_version WHERE tenant_id=1 AND device_id=?",extraDeviceId);
            jdbc.update("DELETE FROM ast_device WHERE id=? AND tenant_id=1 AND creator='device-log-it'",extraDeviceId);
        }
        TenantContextHolder.clear();SecurityContextHolder.clearContext();
    }
    @Test void concurrentIngressAllocatesDistinctVersionsAndKeepsBothHistories() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<DeviceLogArchiveService.ImportView> upload=()->{login();try {start.await();return service.upload(new DeviceLogArchiveService.Upload(PROJECT,UUID.randomUUID().toString(),"input.log","content".getBytes(StandardCharsets.UTF_8)));}
                finally {TenantContextHolder.clear();SecurityContextHolder.clearContext();}};
            var first=pool.submit(upload);var second=pool.submit(upload);start.countDown();
            assertEquals("ARCHIVED",first.get(30,TimeUnit.SECONDS).outcome());assertEquals("ARCHIVED",second.get(30,TimeUnit.SECONDS).outcome());
        }
        assertEquals(List.of(1L,2L),jdbc.queryForList("SELECT device_version FROM ast_device_log_file WHERE tenant_id=1 AND device_id=? ORDER BY device_version",Long.class,deviceId));
        assertEquals(2L,service.latest(PROJECT).getFirst().latest().deviceVersion());
    }
    @Test void storageFailureRollsBackBatchAndNeverAdvancesLatest() {
        service.upload(new DeviceLogArchiveService.Upload(PROJECT,UUID.randomUUID().toString(),"first.log","first".getBytes(StandardCharsets.UTF_8)));
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_log_file WHERE tenant_id=1 AND project_id=?",Long.class,PROJECT);
        var attempted=new java.util.concurrent.atomic.AtomicInteger();
        when(storage.store(any())).thenAnswer(inv -> {
            if (attempted.incrementAndGet()>1) throw new IllegalStateException("storage unavailable");
            return new NativeGeneratedFileApi.RegisteredFile(999L,999L,999L,1,"digest","source.txt");
        });
        assertThrows(RuntimeException.class,()->service.upload(new DeviceLogArchiveService.Upload(PROJECT,UUID.randomUUID().toString(),"second.log","second".getBytes(StandardCharsets.UTF_8))));
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_log_file WHERE tenant_id=1 AND project_id=?",Long.class,PROJECT));
        assertEquals(1L,service.latest(PROJECT).getFirst().latest().deviceVersion());
        assertEquals(1L,jdbc.queryForObject("SELECT current_version FROM ast_device_log_version WHERE tenant_id=1 AND device_id=?",Long.class,deviceId));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_log_import WHERE tenant_id=1 AND project_id=?",Long.class,PROJECT));
    }
    @Test void concurrentSameRequestReturnsOneImportAndCompleteFileIdentities() throws Exception {
        String operation=UUID.randomUUID().toString();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<DeviceLogArchiveService.ImportView> upload=()->{login();try {start.await();return service.upload(new DeviceLogArchiveService.Upload(PROJECT,operation,"input.log","same".getBytes(StandardCharsets.UTF_8)));}
                finally {TenantContextHolder.clear();SecurityContextHolder.clearContext();}};
            var first=pool.submit(upload);var second=pool.submit(upload);start.countDown();
            var a=first.get(30,TimeUnit.SECONDS);var b=second.get(30,TimeUnit.SECONDS);
            assertEquals(a.id(),b.id());assertEquals(2,a.files().size());assertEquals(2,b.files().size());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT current_version FROM ast_device_log_version WHERE tenant_id=1 AND device_id=?",Long.class,deviceId));
    }
    @Test void firstImportsForDifferentProjectsAndDevicesDoNotLockAnEmptyHistoryRange() throws Exception {
        extraDeviceId=deviceId+1_000_001L;
        String secondSerial="IT-DEVICE-LOG-"+extraDeviceId;
        jdbc.update("INSERT INTO ast_device(id,sn,name,project_id,project_assignment_version,status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) "
                + "VALUES(?,?,?, ?,0,'ACTIVE','PMS',?,'FRESH',0,'device-log-it','device-log-it',0,1)",extraDeviceId,secondSerial,"另一项目验证设备",PROJECT+1,"device-log-it-"+extraDeviceId);
        when(dac.split(any())).thenAnswer(inv -> {
            String sn=inv.getArgument(0,DeviceOpsLogSplitApi.Command.class).content().contains(secondSerial)?secondSerial:serial;
            return new DeviceOpsLogSplitApi.Result("SN: "+sn+"\ncomplete\n",0,List.of(new DeviceOpsLogSplitApi.DeviceLog(sn,"SN: "+sn+"\ncomplete\n")));
        });
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<DeviceLogArchiveService.ImportView> first=()->{login();try {start.await();return service.upload(new DeviceLogArchiveService.Upload(PROJECT,UUID.randomUUID().toString(),"first.log",serial.getBytes(StandardCharsets.UTF_8)));}
                finally {TenantContextHolder.clear();SecurityContextHolder.clearContext();}};
            Callable<DeviceLogArchiveService.ImportView> second=()->{login();try {start.await();return service.upload(new DeviceLogArchiveService.Upload(PROJECT+1,UUID.randomUUID().toString(),"second.log",secondSerial.getBytes(StandardCharsets.UTF_8)));}
                finally {TenantContextHolder.clear();SecurityContextHolder.clearContext();}};
            var a=pool.submit(first);var b=pool.submit(second);start.countDown();
            assertEquals("ARCHIVED",a.get(30,TimeUnit.SECONDS).outcome());assertEquals("ARCHIVED",b.get(30,TimeUnit.SECONDS).outcome());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT current_version FROM ast_device_log_version WHERE tenant_id=1 AND device_id=?",Long.class,deviceId));
        assertEquals(1L,jdbc.queryForObject("SELECT current_version FROM ast_device_log_version WHERE tenant_id=1 AND device_id=?",Long.class,extraDeviceId));
    }
    static void login() {TenantContextHolder.setTenantId(1L);var user=new LoginUser();user.setId(7L);user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));}
    @SpringBootConfiguration(proxyBeanMethods=false)
    @MapperScan({"cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog","cn.iocoder.yudao.module.pms.asset.dal.mysql.device"})
    @Import({YudaoDataSourceAutoConfiguration.class,DataSourceAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,YudaoMybatisAutoConfiguration.class,MybatisPlusAutoConfiguration.class,MybatisPlusJoinAutoConfiguration.class,SpringUtil.class})
    static class Application {
        @Bean JdbcTemplate jdbc(DataSource dataSource) {return new JdbcTemplate(dataSource);}
        @Bean PermissionApi permissions() {var p=mock(PermissionApi.class);when(p.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);return p;}
        @Bean ProjectScopeApi scopes() {var s=mock(ProjectScopeApi.class);var scope=new ProjectScopeResult(PROJECT,1L,Set.of(PROJECT,PROJECT+1),Set.of());
            when(s.lockAndRevalidate(any())).thenReturn(scope);when(s.resolveCurrent(any())).thenReturn(scope);return s;}
        @Bean DeviceOpsLogSplitApi dac() {return mock(DeviceOpsLogSplitApi.class);}
        @Bean DeviceLogFileApi storage() {return mock(DeviceLogFileApi.class);}
        @Bean FileEvidenceApi evidence() {return mock(FileEvidenceApi.class);}
        @Bean NativeGeneratedFileApi downloads() {return mock(NativeGeneratedFileApi.class);}
        @Bean DeviceLogArchiveService service(DeviceLogImportMapper imports,DeviceLogFileMapper files,DeviceLogVersionMapper versions,DeviceLogDeviceMapper queries,DeviceMapper devices,
                PermissionApi permissions,ObjectProvider<ProjectScopeApi> scopes,ObjectProvider<DeviceOpsLogSplitApi> dac,ObjectProvider<DeviceLogFileApi> storage,
                ObjectProvider<FileEvidenceApi> evidence,ObjectProvider<NativeGeneratedFileApi> downloads) {
            return new DeviceLogArchiveService(imports,files,versions,queries,devices,permissions,scopes,dac,storage,evidence,downloads,
                    new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceArchivedLogParsingService.class),
                    new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi.class));
        }
    }
}
