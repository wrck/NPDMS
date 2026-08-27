package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectSplitMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectSplitMySqlIntegrationTest {
    private static final String TRIGGER = "it_fproj002_outbox_failure";
    private static final String KEY_PREFIX = "it-fproj002-";

    @Resource PlatformCommandExecutionApi commandExecutionService;
    @Resource JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        clean();
        jdbcTemplate.execute("CREATE TRIGGER " + TRIGGER
                + " BEFORE INSERT ON plt_outbox_event FOR EACH ROW SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'F-PROJ-002 injected outbox failure'");
    }

    @AfterEach
    void tearDown() {
        try {
            clean();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void outboxFailureRollsBackSplitTreeAndCompletionPoint() {
        long requestId = 880_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        String key = KEY_PREFIX + UUID.randomUUID();
        String batch = KEY_PREFIX + UUID.randomUUID();

        assertThrows(RuntimeException.class, () -> commandExecutionService.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(0L, "IT:FPROJ002", 9_900_002L, key),
                "a".repeat(64), ApplyProjectSplitResult.class,
                () -> {
                    jdbcTemplate.update("INSERT INTO proj_project_split_request "
                                    + "(id,parent_project_id,status,draft_version,tree_version,version,creator,tenant_id) "
                                    + "VALUES (?,?, 'DRAFT',0,0,0,?,0)", requestId, requestId, key);
                    jdbcTemplate.update("INSERT INTO proj_project_tree_version "
                                    + "(id,root_project_id,tree_version,status,change_batch_id,node_count,path_count,version,tenant_id) "
                                    + "VALUES (?,?,1,'ACTIVE',?,0,0,0,0)", requestId, requestId, batch);
                    return new ApplyProjectSplitResult(requestId, List.of(), 1L, batch, 1L, false);
                }, result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "PROJECT_SPLIT_APPLY", "ProjectSplitRequest", String.valueOf(requestId), key,
                        "{}", "ProjectSplitApplied", "{}")));

        assertEquals(0L, count("proj_project_split_request", "id", requestId));
        assertEquals(0L, count("proj_project_tree_version", "id", requestId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE idempotency_key = ?", Long.class, key));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id = ?", Long.class, key));
    }

    private long count(String table, String column, long id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, id);
    }

    private void clean() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + TRIGGER);
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE change_batch_id LIKE ?", KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM proj_project_split_request WHERE creator LIKE ?", KEY_PREFIX + "%");
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.platform.dal.mysql.command")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, PlatformCommandExecutionApiImpl.class,
            cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    }
}
