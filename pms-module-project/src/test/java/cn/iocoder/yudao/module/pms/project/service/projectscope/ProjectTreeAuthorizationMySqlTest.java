package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.service.authorization.AuthorizationGrantService;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTreeAuthorizationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTreeAuthorizationMySqlTest {

    @Resource ProjectTreeScopeService service;
    @Resource JdbcTemplate jdbcTemplate;
    private long baseId;
    private long actorId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = currentEnvironment();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000L);
        baseId = 882_000_000_000L + suffix * 100;
        actorId = 9_940_000L + suffix;
        cleanFacts();
        insertProject(baseId, null, 0, 0);
        insertProject(baseId + 1, baseId, 1, 1);
        insertProject(baseId + 2, baseId + 1, 2, 2);
        insertProject(baseId + 3, baseId, 1, 3);
        insertVersion(7L);
        insertVersion7Paths();
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(id,project_id,user_id,member_role,status,version,tenant_id) "
                        + "VALUES (?,?,?,'PROJECT_MANAGER','ACTIVE',0,0)",
                baseId, baseId + 1, actorId);
    }

    @AfterEach
    void tearDown() {
        try {
            cleanFacts();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void managerRoleDoesNotAutomaticallyGrantDescendantsOrSiblingSummary() {
        var scope = resolve(actorId, baseId + 2, "PROJECT_VIEW", 7L);

        assertEquals(ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER, scope.visibility(baseId));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(baseId + 1));
        assertEquals(ProjectTreeScopeService.Visibility.NONE, scope.visibility(baseId + 2));
        assertEquals(ProjectTreeScopeService.Visibility.NONE, scope.visibility(baseId + 3));
    }

    @Test
    void descendantGrantExpandsThenRevocationImmediatelyShrinksScope() {
        long grantId = insertGrant(baseId + 1, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS",
                "ACTIVE", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), 1);

        var granted = resolve(actorId, baseId + 2, "PROJECT_VIEW", 7L);
        jdbcTemplate.update("UPDATE plt_authorization_grant SET status_code='REVOKED', "
                        + "current_marker=NULL, revoked_by=?, revoked_at=NOW(), revoke_reason='集成测试撤权', "
                        + "version=version+1 WHERE id=?", actorId, grantId);
        var revoked = resolve(actorId, baseId + 2, "PROJECT_VIEW", 7L);

        assertEquals(java.util.Set.of(baseId + 1, baseId + 2), granted.fullProjectIds());
        assertEquals(java.util.Set.of(baseId + 1), revoked.fullProjectIds());
    }

    @Test
    void expiredGrantAndEmptyActorNeverExpandToTenantData() {
        insertGrant(baseId + 3, "PROJECT_VIEW", "CURRENT_PROJECT",
                "ACTIVE", LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), 1);

        var member = resolve(actorId, baseId + 2, "PROJECT_VIEW", 7L);
        var empty = resolve(actorId + 1, baseId + 2, "PROJECT_VIEW", 7L);

        assertEquals(ProjectTreeScopeService.Visibility.NONE, member.visibility(baseId + 3));
        assertEquals(java.util.Set.of(), empty.fullProjectIds());
        assertEquals(java.util.Set.of(), empty.placeholderProjectIds());
    }

    @Test
    void moveUsesLatestCompleteTreeAndDoesNotCopyAnchorGrant() {
        insertGrant(baseId + 1, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS",
                "ACTIVE", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), 1);
        assertEquals(java.util.Set.of(baseId + 1, baseId + 2),
                resolve(actorId, baseId + 2, "PROJECT_VIEW", 7L).fullProjectIds());

        insertVersion(8L);
        insertVersion8MovedPaths();
        var moved = resolve(actorId, baseId + 2, "PROJECT_VIEW", 8L);
        ServiceException stale = assertThrows(ServiceException.class,
                () -> resolve(actorId, baseId + 2, "PROJECT_VIEW", 7L));

        assertEquals(java.util.Set.of(baseId + 1), moved.fullProjectIds());
        assertEquals(PROJECT_TREE_VERSION_CONFLICT.getCode(), stale.getCode());
    }

    @Test
    void viewGrantDoesNotProvideManageAction() {
        insertGrant(baseId + 1, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS",
                "ACTIVE", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(1), 1);

        var manage = resolve(actorId, baseId + 2, "PROJECT_MANAGE", 7L);

        assertEquals(java.util.Set.of(baseId + 1), manage.fullProjectIds());
        assertEquals(ProjectTreeScopeService.Visibility.NONE, manage.visibility(baseId + 2));
    }

    private ProjectTreeScopeService.ProjectTreeScope resolve(
            long subjectId, long anchorId, String actionCode, long treeVersion) {
        return service.resolve(new ProjectScopeQuery(0L, subjectId, anchorId, actionCode, treeVersion));
    }

    private long insertGrant(long resourceId, String actionCode, String scopeCode, String statusCode,
                             LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Integer currentMarker) {
        long grantId = baseId + 50 + Math.abs((resourceId + actionCode.hashCode() + scopeCode.hashCode()) % 40);
        jdbcTemplate.update("INSERT INTO plt_authorization_grant "
                        + "(id,subject_type_code,subject_id,resource_context_code,resource_type_code,resource_id,"
                        + "action_code,scope_code,effective_from,effective_to,status_code,source_context_code,"
                        + "granted_by,granted_at,version,current_marker,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,0)",
                grantId, "USER", actorId, "PROJ", "PROJECT", resourceId, actionCode, scopeCode,
                effectiveFrom, effectiveTo, statusCode, "PROJ", actorId, LocalDateTime.now(), currentMarker);
        return grantId;
    }

    private void insertProject(long id, Long parentId, int depth, int sequence) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,parent_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,version,tenant_id) VALUES (?,?,?,?,?,?,?,?,?,?, 'S0',0,0)",
                id, "it-scope-" + id, baseId, sequence, "it-scope-" + id, parentId, baseId,
                parentId == null ? "/" : "/" + baseId + "/", depth, sequence);
    }

    private void insertVersion(long version) {
        jdbcTemplate.update("INSERT INTO proj_project_tree_version "
                        + "(id,root_project_id,tree_version,status,change_batch_id,node_count,path_count,"
                        + "activated_at,version,tenant_id) VALUES (?,?,?,'ACTIVE',?,4,8,NOW(),0,0)",
                baseId + 70 + version, baseId, version, "it-scope-" + baseId + "-v" + version);
    }

    private void insertVersion7Paths() {
        long id = baseId + 10;
        insertPath(id++, 7L, baseId, baseId, 0);
        insertPath(id++, 7L, baseId, baseId + 1, 1);
        insertPath(id++, 7L, baseId, baseId + 2, 2);
        insertPath(id++, 7L, baseId, baseId + 3, 1);
        insertPath(id++, 7L, baseId + 1, baseId + 1, 0);
        insertPath(id++, 7L, baseId + 1, baseId + 2, 1);
        insertPath(id++, 7L, baseId + 2, baseId + 2, 0);
        insertPath(id, 7L, baseId + 3, baseId + 3, 0);
    }

    private void insertVersion8MovedPaths() {
        long id = baseId + 30;
        insertPath(id++, 8L, baseId, baseId, 0);
        insertPath(id++, 8L, baseId, baseId + 1, 1);
        insertPath(id++, 8L, baseId, baseId + 2, 2);
        insertPath(id++, 8L, baseId, baseId + 3, 1);
        insertPath(id++, 8L, baseId + 1, baseId + 1, 0);
        insertPath(id++, 8L, baseId + 2, baseId + 2, 0);
        insertPath(id++, 8L, baseId + 3, baseId + 3, 0);
        insertPath(id, 8L, baseId + 3, baseId + 2, 1);
    }

    private void insertPath(long id, long treeVersion, long ancestorId, long descendantId, int distance) {
        jdbcTemplate.update("INSERT INTO proj_project_tree_path "
                        + "(id,tree_version,root_project_id,ancestor_project_id,descendant_project_id,distance,"
                        + "version,tenant_id) VALUES (?,?,?,?,?,?,0,0)",
                id, treeVersion, baseId, ancestorId, descendantId, distance);
    }

    private void cleanFacts() {
        if (actorId != 0) {
            jdbcTemplate.update("DELETE FROM plt_authorization_grant WHERE subject_id = ?", actorId);
        }
        if (baseId != 0) {
            jdbcTemplate.update("DELETE FROM proj_project_member_assignment WHERE project_id BETWEEN ? AND ?",
                    baseId, baseId + 3);
            jdbcTemplate.update("DELETE FROM proj_project_tree_path WHERE root_project_id = ?", baseId);
            jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE root_project_id = ?", baseId);
            jdbcTemplate.update("DELETE FROM proj_project WHERE id BETWEEN ? AND ?", baseId, baseId + 3);
        }
    }

    private static Map<String, String> currentEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(System.getenv());
        Path dotenv = findRepositoryDotenv();
        if (dotenv == null) return values;
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                int separator = trimmed.indexOf('=');
                values.putIfAbsent(trimmed.substring(0, separator).trim(),
                        unquote(trimmed.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取当前仓库.env", ex);
        }
    }

    private static Path findRepositoryDotenv() {
        for (Path directory = Path.of("").toAbsolutePath().normalize();
                directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("compose.yaml"))) {
                Path dotenv = directory.resolve(".env");
                return Files.isRegularFile(dotenv) ? dotenv : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, AuthorizationGrantService.class,
            ProjectTreeScopeService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
