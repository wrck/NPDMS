package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressFactDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressFactMapper;
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

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectProgressSnapshotMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectProgressSnapshotMySqlTest {
    @Resource ProjectProgressFactMapper factMapper;
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
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        baseId = 884_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10;
        insertFact(baseId, baseId + 1, 1, "10.0000", "2026-01-01 00:00:00");
        insertFact(baseId + 1, baseId + 1, 2, "60.0000", "2026-01-02 00:00:00");
        insertFact(baseId + 2, baseId + 2, 1, "25.0000", "2026-01-01 00:00:00");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM proj_project_progress_fact WHERE id BETWEEN ? AND ?", baseId, baseId + 2);
    }

    @Test
    void latestFactQueryReturnsOneCurrentFactPerDirectChild() {
        List<ProjectProgressFactDO> rows = factMapper.selectLatestByProjects(0L, List.of(baseId + 1, baseId + 2));

        assertEquals(2, rows.size());
        assertEquals(2L, rows.stream().filter(row -> row.getProjectId().equals(baseId + 1))
                .findFirst().orElseThrow().getFactVersion());
    }

    private void insertFact(long id, long projectId, long factVersion, String progress, String occurredAt) {
        jdbcTemplate.update("INSERT INTO proj_project_progress_fact "
                        + "(id,project_id,fact_source_type,fact_source_id,fact_version,progress,source_watermark,"
                        + "occurred_at,version,tenant_id) VALUES (?,?,'TEST',?,?,?,'it-progress',?,0,0)",
                id, projectId, "fact-" + id, factVersion, progress, occurredAt);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    }
}
