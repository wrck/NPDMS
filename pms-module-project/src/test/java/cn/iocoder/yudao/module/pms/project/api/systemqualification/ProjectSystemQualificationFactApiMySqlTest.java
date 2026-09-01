package cn.iocoder.yudao.module.pms.project.api.systemqualification;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectSystemQualificationFactApiMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectSystemQualificationFactApiMySqlTest {

    @Resource private ProjectSystemQualificationFactApi api;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private TransactionTemplate transactionTemplate;

    private long rootId;
    private long projectId;
    private long treeVersionId;

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
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000L);
        rootId = 978_000_000_000L + suffix * 10;
        projectId = rootId + 1;
        treeVersionId = rootId + 2;
        insertProject(rootId, rootId, null, 0, 8_000_001L, 2);
        insertProject(projectId, rootId, rootId, 1, 8_000_002L, 3);
        insertTreeVersion(treeVersionId, 7L);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE root_project_id=?", rootId);
            jdbcTemplate.update("DELETE FROM proj_project WHERE id IN (?,?)", projectId, rootId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void readsCurrentManagerProjectAndTreeVersionsWithoutFrozenExpectations() {
        var first = api.lockCurrentForSystem(query());
        assertEquals(8_000_002L, first.currentManagerUserId());
        assertEquals(3, first.currentProjectVersion());
        assertEquals(3L, first.currentParticipantFactVersion());
        assertEquals(7L, first.currentTreeVersion());

        jdbcTemplate.update("UPDATE proj_project SET manager_id=?, version=4 WHERE id=?",
                8_000_003L, projectId);
        insertTreeVersion(treeVersionId + 1, 8L);

        var current = api.lockCurrentForSystem(query());
        assertEquals(8_000_003L, current.currentManagerUserId());
        assertEquals(4, current.currentProjectVersion());
        assertEquals(4L, current.currentParticipantFactVersion());
        assertEquals(8L, current.currentTreeVersion());
    }

    @Test
    void holdsRootProjectAndTreeLocksUntilOuterTransactionCompletes() throws Exception {
        CountDownLatch factRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var locked = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                try {
                    return transactionTemplate.execute(status -> {
                        var fact = api.lockCurrentForSystem(query());
                        factRead.countDown();
                        await(release);
                        return fact;
                    });
                } finally {
                    TenantContextHolder.clear();
                }
            });
            factRead.await();

            assertLockTimeout("UPDATE proj_project SET version=version+1 WHERE id=" + rootId);
            assertLockTimeout("UPDATE proj_project SET manager_id=8000003,version=version+1 WHERE id=" + projectId);
            assertLockTimeout("UPDATE proj_project_tree_version "
                    + "SET change_batch_id=CONCAT(change_batch_id,'-blocked') WHERE id=" + treeVersionId);
            release.countDown();

            assertEquals(8_000_002L, locked.get().currentManagerUserId());
            assertEquals(7L, locked.get().currentTreeVersion());
        } finally {
            release.countDown();
        }
    }

    private void assertLockTimeout(String sql) {
        assertThrows(DataAccessException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout=1");
            jdbcTemplate.update(sql);
        }));
    }

    private ProjectSystemQualificationLockQuery query() {
        return new ProjectSystemQualificationLockQuery(projectId, "ACTIVE", "S4");
    }

    private void insertProject(long id, long codeRootId, Long parentId, int sequence,
                               long managerId, int version) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,parent_id,manager_id,root_id,"
                        + "tree_path,tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'S4','ACTIVE','S4','ASSIGNED',0,0,?,0)",
                id, "SYSTEM-QUAL-" + id, codeRootId, sequence, "System qualification " + id,
                parentId, managerId, rootId, parentId == null ? "/" : "/" + rootId + "/",
                parentId == null ? 0 : 1, sequence, version);
    }

    private void insertTreeVersion(long id, long version) {
        jdbcTemplate.update("INSERT INTO proj_project_tree_version "
                        + "(id,root_project_id,tree_version,status,change_batch_id,node_count,path_count,"
                        + "activated_at,version,tenant_id) VALUES (?,?,?,'ACTIVE',?,2,3,NOW(),0,0)",
                id, rootId, version, "SYSTEM-QUAL-" + rootId + "-" + version);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @SpringBootConfiguration
    @EnableTransactionManagement
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        ProjectSystemQualificationFactApi projectSystemQualificationFactApi(
                ProjectMasterMapper projectMapper, ProjectTreeVersionMapper treeVersionMapper) {
            return new ProjectSystemQualificationFactApiImpl(projectMapper, treeVersionMapper);
        }
    }

}
