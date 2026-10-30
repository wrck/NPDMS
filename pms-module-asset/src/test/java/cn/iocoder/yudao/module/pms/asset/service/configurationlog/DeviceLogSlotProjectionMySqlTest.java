package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.assembly.command.ApplyDeviceAssemblyCommand;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.iocoder.yudao.module.pms.asset.api.device.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.asset.service.assembly.DeviceAssemblyService;
@EnabledIfSystemProperty(named="skipITs",matches="false")
@SpringBootTest(classes=DeviceLogSlotProjectionMySqlTest.TestApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class DeviceLogSlotProjectionMySqlTest {
    @Resource DeviceLogSlotProjectionService slots;
    @Resource DeviceOrganizationProjectionApi organizations;
    @Resource JdbcTemplate jdbc;
    @Resource DeviceAssemblyService assembly;
    String prefix;long base;String root;LocalDateTime time=LocalDateTime.of(2026,10,9,12,0);
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> values = environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(values, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(values, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.asset");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);org.mockito.Mockito.reset(organizations);
        base=983000000000L+Math.abs(UUID.randomUUID().getLeastSignificantBits()%1000000L)*10;
        prefix="IT-SLOT-"+base;root=prefix+"-ROOT";
        jdbc.update("INSERT INTO ast_device (id,sn,name,project_id,status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) VALUES (?,?,?,992203060198,'IN_STOCK','DEVICE_LOG',?,'PENDING_RECONCILIATION',0,'slot-it','slot-it',b'0',1)",base,root,root,prefix);
    }
    @AfterEach void cleanup() {
        try {
            jdbc.update("DELETE FROM ast_device_log_parsed_fact WHERE tenant_id=1 AND request_key LIKE ?",prefix+"%" );
            jdbc.update("DELETE FROM ast_device_network_version WHERE tenant_id=1 AND device_sn LIKE ?",prefix+"%" );
            jdbc.update("DELETE FROM ast_device_assembly WHERE tenant_id=1 AND (parent_device_sn LIKE ? OR child_device_sn LIKE ?)",prefix+"%",prefix+"%");
            jdbc.update("DELETE FROM ast_device WHERE tenant_id=1 AND sn LIKE ?",prefix+"%" );
        } finally {TenantContextHolder.clear();}
    }
    DeviceLogParsedFactApi.ParsedLog parent(String request,LocalDateTime received) {
        return new DeviceLogParsedFactApi.ParsedLog(1L,7L,992203060198L,prefix+request,"ast-file:"+(base+Math.abs(request.hashCode())),received,prefix+request,"1.3.2",base+Math.abs(request.hashCode()),root,"SOFT-P","BOOT-P","C-P","PCB-P",1L);
    }
    DeviceLogSlotFacts observed(String... suffixes) {
        var values=new java.util.ArrayList<DeviceLogSlotFacts.SlotDevice>();int pos=4;
        for(var suffix:suffixes) values.add(new DeviceLogSlotFacts.SlotDevice(pos++,prefix+suffix,"BOARD","FW","A","1.00",null,null));
        return new DeviceLogSlotFacts(values);
    }
    void apply(String request,LocalDateTime received,String... children) {
        slots.record(parent(request,received),observed(children),new DeviceLogParsedFactApi.Outcome("LINKED",base,root,"UPDATED",null));
    }
    @Test void savesAssemblyAndAllChildVersionsAndIdempotentReplay() {
        apply("-a",time,"-C");apply("-a",time,"-C");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND parent_device_sn=? AND effective_to IS NULL",Integer.class,root));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_log_parsed_fact WHERE tenant_id=1 AND request_key LIKE ?",Integer.class,prefix+"%"));
        var row=jdbc.queryForMap("SELECT conp_version,cpld_version,pcb_version,boot_version FROM ast_device_network_version WHERE tenant_id=1 AND device_sn=?",prefix+"-C");
        assertEquals("SOFT-P",row.get("conp_version"));assertEquals("1.00",row.get("cpld_version"));assertEquals("A",row.get("pcb_version"));assertEquals("BOOT-P",row.get("boot_version"));
        assertEquals(null,jdbc.queryForObject("SELECT project_id FROM ast_device WHERE tenant_id=1 AND sn=?",Long.class,prefix+"-C"));
    }
    @Test void secondSlotFailureRollsBackFirstSlotMasterRelationAndVersions() {
        org.mockito.Mockito.doAnswer(i->{DeviceOrganizationProjectionApi.Refresh command=i.getArgument(0);String sn=jdbc.queryForObject("SELECT sn FROM ast_device WHERE id=?",String.class,command.deviceIds().iterator().next());if(sn.endsWith("-Z"))throw new IllegalStateException("injected second slot failure");return null;}).when(organizations).refresh(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->apply("-rollback",time,"-A","-Z"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=1 AND sn LIKE ?",Integer.class,prefix+"%"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND parent_device_sn=?",Integer.class,root));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_network_version WHERE tenant_id=1 AND device_sn LIKE ?",Integer.class,prefix+"%"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_log_parsed_fact WHERE tenant_id=1 AND request_key LIKE ?",Integer.class,prefix+"%"));
    }
    @Test void replacingConflictPreservesOldIntervalAndAbsentSlots() {
        apply("-first",time,"-A","-B");apply("-replace",time.plusDays(1),"-C");
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND parent_device_sn=?",Integer.class,root));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND parent_device_sn=? AND effective_to IS NULL",Integer.class,root));
        assertEquals(time.plusDays(1),jdbc.queryForObject("SELECT effective_to FROM ast_device_assembly WHERE tenant_id=1 AND child_device_sn=?",LocalDateTime.class,prefix+"-A"));
        assertEquals(null,jdbc.queryForObject("SELECT effective_to FROM ast_device_assembly WHERE tenant_id=1 AND child_device_sn=?",LocalDateTime.class,prefix+"-B"));
    }
    @Test void olderConflictRollsBackNewMasterWithoutChangingHistory() {
        apply("-new",time.plusDays(1),"-A");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->apply("-old",time,"-B"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=1 AND sn=?",Integer.class,prefix+"-B"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND parent_device_sn=? AND effective_to IS NULL",Integer.class,root));
    }
    @Test void concurrentDisjointEndpointsCannotJointlyCreateCycle() throws Exception {
        for (int i=1;i<=3;i++) {
            String sn=prefix+"-N"+i;
            jdbc.update("INSERT INTO ast_device (id,sn,name,status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) VALUES (?,?,?,'IN_STOCK','DEVICE_LOG',?,'PENDING_RECONCILIATION',0,'slot-it','slot-it',b'0',1)",base+i,sn,sn,prefix);
        }
        String b=prefix+"-N1",c=prefix+"-N2",d=prefix+"-N3";
        assembly.apply(edge(b,c,"-bc"));assembly.apply(edge(d,root,"-da"));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<Future<Boolean>>();
            for (var command:List.of(edge(root,b,"-ab"),edge(c,d,"-cd"))) {
                tasks.add(pool.submit(()->{TenantContextHolder.setTenantId(1L);try {start.await();assembly.apply(command);return true;}catch(cn.iocoder.yudao.framework.common.exception.ServiceException rejected){return false;}finally{TenantContextHolder.clear();}}));
            }
            start.countDown();int succeeded=0;for(var task:tasks) if(task.get(20,java.util.concurrent.TimeUnit.SECONDS)) succeeded++;
            assertEquals(1,succeeded);
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_assembly WHERE tenant_id=1 AND parent_device_sn LIKE ? AND effective_to IS NULL",Integer.class,prefix+"%"));
        } finally {pool.shutdownNow();}
    }
    ApplyDeviceAssemblyCommand edge(String parent,String child,String key) {
        return new ApplyDeviceAssemblyCommand(1L,parent,child,"SLOT_4","SLOT",time,prefix,"DEVICE_LOG",prefix+key,"1.3.2");
    }
    private static Map<String, String> environment() {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path dotenv = findDotenv();
        if (dotenv == null) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) {
                    continue;
                }
                int separator = value.indexOf('=');
                values.putIfAbsent(value.substring(0, separator).trim(),
                        unquote(value.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("读取真实MySQL集成测试环境失败", ex);
        }
    }

    private static Path findDotenv() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("compose.yaml"))) {
                return Files.isRegularFile(path.resolve(".env")) ? path.resolve(".env") : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.asset.dal.mysql.device",
            "cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly",
            "cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog", "cn.iocoder.yudao.module.pms.asset.dal.mysql.version"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            DeviceAssemblyService.class, DeviceLogParsedFactService.class, DeviceLogSlotProjectionService.class})
    static class TestApplication {

        @Bean ProjectScopeApi scopes() {
            var scopes=org.mockito.Mockito.mock(ProjectScopeApi.class);
            var result=new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(992203060198L,1L,java.util.Set.of(992203060198L),java.util.Set.of());
            org.mockito.Mockito.when(scopes.resolveCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(result);
            org.mockito.Mockito.when(scopes.lockAndRevalidate(org.mockito.ArgumentMatchers.any())).thenReturn(result);
            return scopes;
        }
        @Bean DeviceOrganizationProjectionApi organizations() { return org.mockito.Mockito.mock(DeviceOrganizationProjectionApi.class); }
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
