package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.service.authorization.CompanyScopeGuard;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CommerceAuthorityCandidateMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommerceAuthorityCandidateMySqlTest {

    private static final long TENANT_ID = 990_004L;
    private static final long ACTOR_ACME = 11L;
    private static final long ACTOR_OTHER = 12L;

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private CommerceAuthorityCandidateService service;

    private String suffix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.commerce");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        suffix = Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits(), 36);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM com_authority_candidate WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_contract WHERE tenant_id=?", TENANT_ID);
        TenantContextHolder.clear();
    }

    @Test
    void createsListsAndMatchesExistingConfirmedOwnerWithoutMutation() {
        long ownerId = insertConfirmedContract("ACME", "ERP-V1");
        var created = service.create(create(ACTOR_ACME, "K-" + suffix, "IDEM-C-" + suffix));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_authority_candidate WHERE tenant_id=?", Integer.class, TENANT_ID));
        assertEquals(1, service.listVisible(new CommerceAuthorityCandidateService.ListCandidatesQuery(
                TENANT_ID, ACTOR_ACME, "CONTRACT", "PENDING_RECONCILIATION", 1, 20)).size());
        assertTrue(service.listVisible(new CommerceAuthorityCandidateService.ListCandidatesQuery(
                TENANT_ID, ACTOR_OTHER, null, null, 1, 20)).isEmpty());

        var matched = service.reconcile(new CommerceAuthorityCandidateService.DecideCandidateCommand(
                TENANT_ID, ACTOR_ACME, created.candidateId(), 0, ownerId,
                "matched to confirmed ERP owner",
                "IDEM-M-" + suffix, "CORR-M-" + suffix));

        assertEquals("MATCHED", matched.candidateStatus());
        assertEquals("ERP-V1", matched.matchedOwnerSourceVersion());
        assertEquals("ERP-V1", jdbcTemplate.queryForObject(
                "SELECT source_version FROM com_contract WHERE tenant_id=? AND id=?",
                String.class, TENANT_ID, ownerId));
    }

    @Test
    void rejectsCrossCompanyBeforePlatformReservation() {
        var error = assertThrows(CompanyScopeGuard.CompanyScopeDeniedException.class,
                () -> service.create(create(ACTOR_OTHER, "K-" + suffix, "IDEM-X-" + suffix)));

        assertNotNull(error.getMessage());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_authority_candidate WHERE tenant_id=?", Integer.class, TENANT_ID));
    }

    @Test
    void rejectsNonNormalizedCompanyBeforePlatformReservation() {
        String idempotencyKey = "IDEM-W-" + suffix;
        var command = new CommerceAuthorityCandidateService.CreateCandidateCommand(
                TENANT_ID, ACTOR_ACME, "CONTRACT", "K-" + suffix, "V1",
                "{\"companyCode\":\" ACME \",\"contractNo\":\"C-" + suffix + "\"}",
                "{\"referenceKey\":\"REF-" + suffix + "\"}",
                idempotencyKey, "CORR-" + idempotencyKey);

        var error = assertThrows(CommerceAuthorityCandidateService.CandidateException.class,
                () -> service.create(command));

        assertEquals(CommerceAuthorityCandidateService.Code.INVALID_REQUEST, error.getCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_authority_candidate WHERE tenant_id=?", Integer.class, TENANT_ID));
    }

    @Test
    void rejectsNonNormalizedCompanyBeforePlatformReservation() {
        String idempotencyKey = "IDEM-W-" + suffix;
        var command = new CommerceAuthorityCandidateService.CreateCandidateCommand(
                TENANT_ID, ACTOR_ACME, "CONTRACT", "K-" + suffix, "V1",
                "{\"companyCode\":\" ACME \",\"contractNo\":\"C-" + suffix + "\"}",
                "{\"referenceKey\":\"REF-" + suffix + "\"}",
                idempotencyKey, "CORR-" + idempotencyKey);

        var error = assertThrows(CommerceAuthorityCandidateService.CandidateException.class,
                () -> service.create(command));

        assertEquals(CommerceAuthorityCandidateService.Code.INVALID_REQUEST, error.getCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND idempotency_key=?",
                Integer.class, TENANT_ID, idempotencyKey));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_authority_candidate WHERE tenant_id=?", Integer.class, TENANT_ID));
    }

    @Test
    void concurrentMatchAndRejectPersistOneImmutableDecision() throws Exception {
        long ownerId = insertConfirmedContract("ACME", "ERP-V1");
        var created = service.create(create(ACTOR_ACME, "K-" + suffix, "IDEM-C-" + suffix));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> match = executor.submit(() -> decideConcurrently(true, created.candidateId(), ownerId, ready, start));
            Future<Boolean> reject = executor.submit(() -> decideConcurrently(false, created.candidateId(), null, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (match.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (reject.get(15, TimeUnit.SECONDS) ? 1 : 0));
            assertTrue(List.of("MATCHED", "REJECTED").contains(jdbcTemplate.queryForObject(
                    "SELECT candidate_status FROM com_authority_candidate WHERE tenant_id=? AND id=?",
                    String.class, TENANT_ID, created.candidateId())));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean decideConcurrently(boolean match, Long candidateId, Long ownerId,
                                       CountDownLatch ready, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            var command = new CommerceAuthorityCandidateService.DecideCandidateCommand(
                    TENANT_ID, ACTOR_ACME, candidateId, 0, ownerId,
                    match ? "matched concurrently" : "rejected concurrently",
                    "IDEM-" + (match ? "M-" : "R-") + suffix,
                    "CORR-" + (match ? "M-" : "R-") + suffix);
            try {
                if (match) service.reconcile(command); else service.reject(command);
                return true;
            } catch (CommerceAuthorityCandidateService.CandidateException ex) {
                if (ex.getCode() == CommerceAuthorityCandidateService.Code.STATE_CONFLICT
                        || ex.getCode() == CommerceAuthorityCandidateService.Code.VERSION_CONFLICT) return false;
                throw ex;
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CommerceAuthorityCandidateService.CreateCandidateCommand create(Long actorId, String key, String idem) {
        return new CommerceAuthorityCandidateService.CreateCandidateCommand(TENANT_ID, actorId, "CONTRACT", key,
                "V1", "{\"companyCode\":\"ACME\",\"contractNo\":\"C-" + suffix + "\"}",
                "{\"referenceKey\":\"REF-" + suffix + "\"}", idem, "CORR-" + idem);
    }

    private long insertConfirmedContract(String companyCode, String sourceVersion) {
        long id = 990_400_000_000L + Math.abs(suffix.hashCode());
        jdbcTemplate.update("INSERT INTO com_contract "
                        + "(id,company_code,contract_no,authority_status,source_lifecycle_status,source_system,source_key,"
                        + "source_version,source_updated_at,synced_at,version,creator,create_time,updater,update_time,deleted,tenant_id) "
                        + "VALUES (?,?,?,'CONFIRMED','ACTIVE','ERP',?,?,NOW(3),NOW(3),0,'0',NOW(3),'0',NOW(3),b'0',?)",
                id, companyCode, "CN-" + suffix, "OWNER-" + suffix, sourceVersion, TENANT_ID);
        return id;
    }

    private static UserCompanyDepartmentScopeRespDTO scope(String companyCode) {
        UserCompanyDepartmentScopeRespDTO result = new UserCompanyDepartmentScopeRespDTO();
        result.setCompanyCode(companyCode);
        return result;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            CommerceAuthorityCandidateService.class, CompanyScopeGuard.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformCommandExecutionApi platformCommandExecutionApi() {
            return new PlatformCommandExecutionApi() {
                @Override
                public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                                      Class<T> responseType, java.util.function.Supplier<T> operation,
                                                      java.util.function.Function<T, SuccessFacts> successFactsFactory) {
                    return new ExecutionResult<>(Decision.NEW, operation.get());
                }
            };
        }

        @Bean
        OrganizationScopeApi organizationScopeApi() {
            return new OrganizationScopeApi() {
                @Override
                public List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId) {
                    if (ACTOR_ACME == userId) return List.of(scope("ACME"));
                    if (ACTOR_OTHER == userId) return List.of(scope("OTHER"));
                    return List.of();
                }

                @Override
                public boolean hasScope(Long userId, Long companyId, Long departmentId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public PageResult<OrganizationUserCandidateRespDTO> pageActiveUsers(
                        OrganizationUserCandidatePageReqDTO request) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
