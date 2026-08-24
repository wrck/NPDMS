package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTreeAuthorizationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTreeAuthorizationMySqlTest {
    @Resource ProjectTreeScopeService service;
    @Resource JdbcTemplate jdbcTemplate;
    private long baseId;

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
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        baseId = 882_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10;
        insertProject(baseId, null, 0);
        insertProject(baseId + 1, baseId, 1);
        insertProject(baseId + 2, baseId + 1, 2);
        insertProject(baseId + 3, baseId, 3);
        long id = baseId;
        insertPath(id++, baseId, baseId, 0);
        insertPath(id++, baseId, baseId + 1, 1);
        insertPath(id++, baseId, baseId + 2, 2);
        insertPath(id++, baseId, baseId + 3, 1);
        insertPath(id++, baseId + 1, baseId + 1, 0);
        insertPath(id++, baseId + 1, baseId + 2, 1);
        insertPath(id++, baseId + 2, baseId + 2, 0);
        insertPath(id, baseId + 3, baseId + 3, 0);
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(id,project_id,user_id,member_role,status,version,tenant_id) "
                        + "VALUES (?,?,9,'PROJECT_MANAGER','ACTIVE',0,0)", baseId, baseId + 1);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM proj_project_member_assignment WHERE project_id BETWEEN ? AND ?",
                    baseId, baseId + 3);
            jdbcTemplate.update("DELETE FROM proj_project_tree_path WHERE root_project_id = ?", baseId);
            jdbcTemplate.update("DELETE FROM proj_project WHERE id BETWEEN ? AND ?", baseId, baseId + 3);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void managerScopeUsesPinnedProjectionAndLimitsSiblingToSummary() {
        var scope = service.resolve(9L, baseId + 2, 7L);
        assertEquals(ProjectTreeScopeService.Visibility.ROOT_SUMMARY, scope.visibility(baseId));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(baseId + 1));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(baseId + 2));
        assertEquals(ProjectTreeScopeService.Visibility.ROOT_SUMMARY, scope.visibility(baseId + 3));
    }

    private void insertProject(long id, Long parentId, int sequence) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,parent_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,version,tenant_id) VALUES (?,?,?,?,?,?,?,?,?,?, 'S0',0,0)",
                id, "it-scope-" + id, baseId, sequence, "it-scope-" + id, parentId, baseId,
                parentId == null ? "/" : "/" + baseId + "/", parentId == null ? 0 : 1, sequence);
    }

    private void insertPath(long id, long ancestorId, long descendantId, int distance) {
        jdbcTemplate.update("INSERT INTO proj_project_tree_path "
                        + "(id,tree_version,root_project_id,ancestor_project_id,descendant_project_id,distance,version,tenant_id) "
                        + "VALUES (?,7,?,?,?,?,0,0)", id, baseId, ancestorId, descendantId, distance);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, ProjectTreeScopeService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    }
}
