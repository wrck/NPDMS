package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApiImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectParticipantFactMapperTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectParticipantFactMapperTest {

    @Resource
    private ProjectMemberAssignmentMapper memberMapper;
    @Resource
    private ProjectParticipantFactApi participantFactApi;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;

    private long projectId;

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
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 976_000_000_000L + seed;
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,manager_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S1','ASSIGNED',0,0,3,0)",
                projectId, "FSOL001-T2-" + projectId, projectId, 0,
                "F-SOL-001 Task2 " + projectId, 8_000_001L, projectId, "/", 0, 0);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM proj_project_member_assignment WHERE tenant_id=0 AND project_id=?",
                projectId);
        jdbcTemplate.update("DELETE FROM proj_project WHERE tenant_id=0 AND id=?", projectId);
        TenantContextHolder.clear();
    }

    @Test
    void shouldSelectOnlyPrimaryFactsEffectiveAtSnapshotAndFailClosedForEmptyRoles() {
        LocalDateTime checkedAt = LocalDateTime.now().withNano(0);
        insertAssignment(8_100_001L, ROLE_SERVICE_MANAGER_L1, null,
                checkedAt.minusDays(1), null);
        insertAssignment(8_100_002L, ROLE_SERVICE_MANAGER_L1, "COLLABORATOR",
                checkedAt.minusDays(1), null);
        insertAssignment(8_100_003L, ROLE_SERVICE_MANAGER_L2, "PRIMARY",
                checkedAt.minusDays(2), checkedAt.minusDays(1));

        var result = memberMapper.selectParticipantFacts(new ProjectParticipantFactLookupQuery(
                0L, projectId, null, Set.of(ROLE_SERVICE_MANAGER_L1, ROLE_SERVICE_MANAGER_L2), checkedAt));

        assertEquals(1, result.size());
        assertEquals(8_100_001L, result.getFirst().getUserId());
        assertEquals(ROLE_SERVICE_MANAGER_L1, result.getFirst().getMemberRole());
        assertTrue(memberMapper.selectParticipantFacts(new ProjectParticipantFactLookupQuery(
                0L, projectId, null, Set.of(), checkedAt)).isEmpty());
        assertTrue(memberMapper.selectParticipantFacts(new ProjectParticipantFactLookupQuery(
                1L, projectId, null, Set.of(ROLE_SERVICE_MANAGER_L1), checkedAt)).isEmpty());
    }

    @Test
    void shouldLockCurrentPrimaryFactAndObserveLatestProjectVersionAndRole() {
        insertAssignment(8_100_001L, ROLE_SERVICE_MANAGER_L1, null,
                LocalDateTime.now().minusDays(1), null);

        var first = participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                projectId, 8_100_001L, 3, "ACTIVE", "S1", Set.of(ROLE_SERVICE_MANAGER_L1)));
        assertEquals(Set.of(ROLE_SERVICE_MANAGER_L1), first.effectiveRoleCodes());

        jdbcTemplate.update("UPDATE proj_project_member_assignment SET effective_to=NOW(3) "
                + "WHERE tenant_id=0 AND project_id=? AND user_id=? AND effective_to IS NULL",
                projectId, 8_100_001L);
        insertAssignment(8_100_002L, ROLE_SERVICE_MANAGER_L2, "PRIMARY",
                LocalDateTime.now().minusSeconds(1), null);
        jdbcTemplate.update("UPDATE proj_project SET version=4 WHERE tenant_id=0 AND id=?", projectId);

        assertThrows(ServiceException.class, () -> participantFactApi.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(
                        projectId, 8_100_001L, 3, "ACTIVE", null, Set.of(ROLE_SERVICE_MANAGER_L1))));
        var latest = participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                projectId, 8_100_002L, 4, "ACTIVE", null, Set.of(ROLE_SERVICE_MANAGER_L2)));
        assertEquals(Set.of(ROLE_SERVICE_MANAGER_L2), latest.effectiveRoleCodes());
        assertEquals(4L, latest.factVersion());
    }

    @Test
    void shouldUseCurrentServerTimeForLockQuery() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertAssignment(8_100_001L, ROLE_SERVICE_MANAGER_L1, "PRIMARY", now.minusHours(1), now.minusMinutes(1));

        var result = transactionTemplate.execute(status -> memberMapper.selectParticipantFactsForUpdate(
                new ProjectParticipantFactLockQuery(
                        0L, projectId, 8_100_001L, Set.of(ROLE_SERVICE_MANAGER_L1))));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHoldProjectLockUntilFactTransactionCompletes() throws Exception {
        insertAssignment(8_100_001L, ROLE_SERVICE_MANAGER_L1, "PRIMARY",
                LocalDateTime.now().minusDays(1), null);
        CountDownLatch factRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var lockedRead = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                try {
                    return transactionTemplate.execute(status -> {
                        var fact = participantFactApi.lockAndRevalidate(
                                new ProjectParticipantFactRevalidationQuery(
                                        projectId, 8_100_001L, 3, "ACTIVE", null,
                                        Set.of(ROLE_SERVICE_MANAGER_L1)));
                        factRead.countDown();
                        await(release);
                        return fact;
                    });
                } finally {
                    TenantContextHolder.clear();
                }
            });
            factRead.await();

            assertThrows(DataAccessException.class, () -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout=1");
                jdbcTemplate.update("UPDATE proj_project SET version=4 WHERE tenant_id=0 AND id=?", projectId);
            }));
            release.countDown();

            assertEquals(3L, lockedRead.get().factVersion());
        }
    }

    private void insertAssignment(long userId, String role, String assignmentType,
                                  LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(project_id,user_id,member_role,assignment_type,responsibility,effective_from,effective_to,"
                        + "status,version,tenant_id) VALUES (?,?,?,?,?,?,?,'ACTIVE',0,0)",
                projectId, userId, role, assignmentType, role, effectiveFrom, effectiveTo);
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
    @MapperScan("cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual")
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
        ProjectParticipantFactApi participantFactApi(ProjectMasterMapper projectMapper,
                                                     ProjectMemberAssignmentMapper memberMapper) {
            return new ProjectParticipantFactApiImpl(projectMapper, memberMapper);
        }
    }

}
