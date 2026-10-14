package cn.iocoder.yudao.module.pms.asset.service.cutover;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetCutoverResultApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover.DeviceCutoverResultMapper;
import cn.iocoder.yudao.module.pms.cutover.api.result.*;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = AssetCutoverResultMySqlTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AssetCutoverResultMySqlTest {
    private static final long TENANT = 980_000_000L + new java.security.SecureRandom().nextInt(1_000_000);
    private static final long PROJECT = 980_100_000_000L;
    @Resource AssetCutoverResultService service;
    @Resource CutoverResultApi results;
    @Resource JdbcTemplate jdbc;
    private long deviceId;
    private CutoverCompleted event;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (!"npdms_cutover_verify_20261012_v2".equals(System.getenv("NPDMS_DB_NAME"))
                || !"24306".equals(System.getenv("NPDMS_MYSQL_PORT")))
            throw new IllegalStateException("Only dedicated CUT verification database on 24306 is permitted");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:24306/npdms_cutover_verify_20261012_v2?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> System.getenv("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.asset");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(TENANT);
        reset(results);
        deviceId = 980_200_000_000L + new java.security.SecureRandom().nextInt(1_000_000);
        String sn = "CUT-RESULT-IT-" + deviceId;
        jdbc.update("INSERT INTO ast_device(id,sn,name,project_id,project_assignment_version,status,source_system,source_key,sync_status,version,creator,updater,deleted,tenant_id) VALUES(?,?,?,?,0,'ACTIVE','PMS',?,'FRESH',0,'cutover-result-it','cutover-result-it',0,?)",
                deviceId, sn, "CUT result test", PROJECT, "cutover-result-it-" + deviceId, TENANT);
        event = new CutoverCompleted("CUTOVER_COMPLETED:" + deviceId, TENANT, PROJECT, deviceId + 1,
                deviceId + 2, 1L, "SUCCESS", "CUTOVER_CLOSURE:" + deviceId, 8L, "test");
        var result = new CutoverResultApi.Result(TENANT, PROJECT, event.taskId(), "CUT-IT", "test", event.closureId(),
                1L, event.resultRef(), 5L, LocalDateTime.of(2026, 10, 13, 12, 0),
                List.of(new CutoverResultApi.Device(deviceId, sn, 0L)), List.of(), List.of());
        when(results.lockAndInspect(any())).thenReturn(result);
    }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void matchingZeroAssignmentVersionRefreshesAndReplayDoesNotDuplicate() {
        var before = assetFields();
        service.accept(event);
        assertEquals("REFRESHED", service.byTask(query()).getFirst().refreshStatus());
        service.accept(event);
        assertEquals(1, service.byTask(query()).size());
        assertEquals(before, assetFields());
    }

    @Test
    void changedAssignmentVersionWithinSameProjectRemainsHistoricalWithoutAssetMutation() {
        jdbc.update("UPDATE ast_device SET project_assignment_version=1 WHERE id=? AND tenant_id=?", deviceId, TENANT);
        var before = assetFields();
        service.accept(event);
        assertEquals("HISTORICAL_SCOPE_CHANGED", service.byTask(query()).getFirst().refreshStatus());
        assertEquals(before, assetFields());
    }

    @Test
    void foreignTenantDeviceCannotBeRefreshed() {
        jdbc.update("UPDATE ast_device SET tenant_id=? WHERE id=? AND tenant_id=?", TENANT + 1, deviceId, TENANT);
        var before = assetFields();
        service.accept(event);
        assertEquals("HISTORICAL_SCOPE_CHANGED", service.byTask(query()).getFirst().refreshStatus());
        assertEquals(before, assetFields());
    }

    @Test
    void deletedDeviceCannotBeRefreshed() {
        jdbc.update("UPDATE ast_device SET deleted=1 WHERE id=? AND tenant_id=?", deviceId, TENANT);
        var before = assetFields();
        service.accept(event);
        assertEquals("HISTORICAL_SCOPE_CHANGED", service.byTask(query()).getFirst().refreshStatus());
        assertEquals(before, assetFields());
    }

    private AssetCutoverResultApi.Query query() { return new AssetCutoverResultApi.Query(TENANT, PROJECT, event.taskId()); }
    private Map<String, Object> assetFields() {
        return jdbc.queryForMap("SELECT sn,project_id,project_assignment_version,status,version,deleted,tenant_id FROM ast_device WHERE id=?", deviceId);
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.asset.dal.mysql.device", "cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class, YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, AssetCutoverResultService.class})
    static class Application {
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean CutoverResultApi results() { return mock(CutoverResultApi.class); }
    }
}
