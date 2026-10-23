package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectContractScopeApi.ScopeContext;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.ContractProjectScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ContractProjectScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses only per-test UUID sources and fresh IDs; cleanup never targets pre-existing business facts. */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectContractScopeMySqlTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectContractScopeMySqlTest {
    @Resource ProjectContractScopeService service;
    @Resource ContractProjectScopeMapper sourceMapper;
    @Resource JdbcTemplate jdbc;
    @Resource PlatformTransactionManager transactionManager;
    @MockitoBean ProjectScopeApi scopeApi;
    @MockitoBean ProjectOfficeFactApi officeApi;
    @MockitoBean ContractAccessService contractAccess;
    @MockitoBean AcceptanceScopeGuardApi guard;
    @MockitoBean AcceptanceStageBindingCoordinator binding;
    @MockitoBean OperationAuditApi audit;
    @MockitoBean PermissionApi permissions;
    private long projectId, childId, contractA, contractB, orderA, orderB, lineA, lineB;
    private String prefix;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        assertEquals("npdms_domain_test", System.getenv("NPDMS_DB_NAME"));
        assertEquals("24306", System.getenv("NPDMS_MYSQL_PORT"));
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        properties.add("spring.datasource.username", () -> System.getenv("NPDMS_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("NPDMS_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.druid.web-stat-filter.enabled", () -> false);
        properties.add("spring.datasource.druid.stat-view-servlet.enabled", () -> false);
        properties.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.commerce");
        properties.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L).setUserType(2), new MockHttpServletRequest());
        prefix = "it-cscope-" + UUID.randomUUID().toString().replace("-", "");
        projectId = IdWorker.getId(); childId = IdWorker.getId();
        contractA = IdWorker.getId(); contractB = IdWorker.getId(); orderA = IdWorker.getId(); orderB = IdWorker.getId();
        lineA = IdWorker.getId(); lineB = IdWorker.getId();
        for (long contract : List.of(contractA, contractB)) {
            jdbc.update("INSERT INTO com_contract(id,tenant_id,company_code,contract_no,master_source_system,master_source_record_key,master_source_version,status,creator,updater) VALUES (?,1,'DPTECH-DEMO',?,'ERP',?,'1','ENABLED',?,?)",
                    contract, prefix + (contract == contractA ? "-A" : "-B"), prefix + ':' + contract, prefix, prefix);
            jdbc.update("INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,source_system,status,creator,updater) VALUES (?,1,?,?,'PMS','ACTIVE',?,?)",
                    IdWorker.getId(), projectId, contract, prefix, prefix);
        }
        insertOrder(orderA, lineA, "A", prefix + "-A", 10);
        insertOrder(orderB, lineB, "B", null, 6);
        // A is associated by both its header and two explicit relations; B only by an explicit relation.
        orderContract(orderA, contractA); orderContract(orderA, contractB); orderContract(orderB, contractB);
        var tree = new ProjectScopeResult(projectId, 1L, Set.of(projectId), Set.of());
        when(scopeApi.lockAndRevalidate(any())).thenReturn(tree);
        when(scopeApi.resolveCurrent(any())).thenReturn(tree);
        when(officeApi.lockAndRevalidate(any())).thenReturn(new ProjectOfficeFact(ProjectFactOutcome.FOUND,
                projectId, 1L, prefix, 930851L, "OFFICE-HZ-DEMO", "杭州示例办事处", 0));
        when(permissions.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(true);
        when(binding.lockAndRead(any(), any(), any(), any())).thenReturn(
                new AcceptanceStageBindingCoordinator.StageContext(1L, projectId, 1L, null, false));
    }

    private void insertOrder(long orderId, long lineId, String suffix, String contractNo, int quantity) {
        jdbc.update("INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_code,order_type,order_no,contract_no,authority_status,source_lifecycle_status,status,creator,updater) VALUES (?,1,'ERP',?,'1','DPTECH-DEMO','0',? ,?,'CONFIRMED','ACTIVE','ENABLED',?,?)",
                orderId, prefix + "-ORDER-" + suffix, prefix + "-ORDER-" + suffix, contractNo, prefix, prefix);
        jdbc.update("INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,company_code,order_type,order_no,line_no,product_code,product_desc,order_qty,unit_code,unit_scale,quantity_status,source_lifecycle_status,status,creator,updater) VALUES (?,1,?,'ERP',?,'1','DPTECH-DEMO','0',?,'10','AR6280','合同同步隔离验证',?,'SET',0,'CONFIRMED','ACTIVE','ENABLED',?,?)",
                lineId, orderId, prefix + "-LINE-" + suffix, prefix + "-ORDER-" + suffix, quantity, prefix, prefix);
    }

    private void orderContract(long orderId, long contractId) {
        jdbc.update("INSERT INTO com_order_contract_relation(id,tenant_id,order_id,contract_id,relation_source,source_system,sales_order_source_key,contract_source_key,source_version,source_evidence,effective_from,creator,updater) VALUES (?,1,?,?,'SOURCE','ERP',?,?,'1',JSON_OBJECT('test',?),CURRENT_TIMESTAMP(3),?,?)",
                IdWorker.getId(), orderId, contractId, prefix + ':' + orderId, prefix + ':' + contractId, prefix, prefix, prefix);
    }

    @AfterEach void cleanup() {
        try {
            if (prefix == null) return;
            jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=1 AND correlation_id LIKE ?", prefix + "%");
            jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=1 AND scope_code='POST:project-contract-scopes:synchronize' AND idempotency_key LIKE ?", prefix + "%");
            jdbc.update("DELETE FROM com_outbox_event WHERE tenant_id=1 AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.projectId'))=?", String.valueOf(projectId));
            jdbc.update("DELETE d FROM com_delivery_scope_detail d JOIN com_delivery_scope s ON s.id=d.delivery_scope_id WHERE s.tenant_id=1 AND s.order_line_id IN (?,?)", lineA, lineB);
            jdbc.update("DELETE FROM com_delivery_scope WHERE tenant_id=1 AND order_line_id IN (?,?)", lineA, lineB);
            jdbc.update("DELETE FROM com_delivery_scope_project_version WHERE tenant_id=1 AND project_id IN (?,?)", projectId, childId);
            jdbc.update("DELETE FROM com_project_contract_relation WHERE tenant_id=1 AND creator=?", prefix);
            jdbc.update("DELETE FROM com_order_contract_relation WHERE tenant_id=1 AND creator=?", prefix);
            jdbc.update("DELETE FROM com_sales_order_line WHERE tenant_id=1 AND creator=?", prefix);
            jdbc.update("DELETE FROM com_sales_order WHERE tenant_id=1 AND creator=?", prefix);
            jdbc.update("DELETE FROM com_contract WHERE tenant_id=1 AND creator=?", prefix);
        } finally { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    }

    @Test void initializationUsesAllOrdersWithoutDuplicatingMultipleContractAssociations() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> service.initialize(context(0, "init")));
        assertEquals(2L, ownScopeCount());
        assertEquals(1L, version());
        assertEquals(2L, outboxCount());
        assertEquals(new BigDecimal("16.000000"), jdbc.queryForObject(
                "SELECT SUM(allocated_qty) FROM com_delivery_scope WHERE project_id=? AND effective_to IS NULL", BigDecimal.class, projectId));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_detail d JOIN com_delivery_scope s ON s.id=d.delivery_scope_id WHERE s.project_id=?", Long.class, projectId));
    }

    @Test void synchronizationReplaysFromRealPlatformIdempotencyWithoutDuplicatingScopesOrAudit() {
        var context = context(0, "sync"); var preview = service.preview(context);
        var result = service.synchronize(context, preview.sourceDigest());
        assertEquals(result, service.synchronize(context, preview.sourceDigest()));
        assertEquals(2L, ownScopeCount()); assertEquals(2L, outboxCount()); assertEquals(1L, version());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE correlation_id=?", Long.class, context.operationId()));
    }

    @Test void auditFailureRollsBackEveryLineDetailsEventsVersionAndIdempotencyThenRetrySucceeds() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(call -> { if (calls.incrementAndGet() == 2) throw new IllegalStateException("injected second line audit failure"); return null; })
                .when(audit).record(any(), any(), any(), any(), any(), any(), any(), any());
        var context = context(0, "retry"); var preview = service.preview(context);
        assertThrows(IllegalStateException.class, () -> service.synchronize(context, preview.sourceDigest()));
        assertEquals(0L, ownScopeCount()); assertEquals(0L, outboxCount());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_project_version WHERE project_id=?", Long.class, projectId));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=1 AND idempotency_key=?", Long.class, context.operationId()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_detail WHERE creator='7' AND source_record_key LIKE ?", Long.class, prefix + "%"));
        reset(audit);
        assertEquals(2, service.synchronize(context, preview.sourceDigest()).changedLineCount());
        assertEquals(2L, ownScopeCount());
    }

    @Test void cancelledAndRemovedAssociationStillAppearWhileForeignTenantCannotRead() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> service.initialize(context(0, "init")));
        jdbc.update("UPDATE com_sales_order_line SET source_lifecycle_status='CANCELLED' WHERE id=?", lineA);
        jdbc.update("UPDATE com_order_contract_relation SET effective_to=CURRENT_TIMESTAMP(3) WHERE tenant_id=1 AND order_id=?", orderB);
        var rows = new TransactionTemplate(transactionManager).execute(status -> sourceMapper.selectOrderLinesForUpdate(new ContractProjectScopeQuery(1L, projectId)));
        assertNotNull(rows); assertEquals(2, rows.size());
        assertEquals("CANCELLED", rows.stream().filter(line -> line.getId() == lineA).findFirst().orElseThrow().getSourceLifecycleStatus());
        assertFalse(rows.stream().filter(line -> line.getId() == lineB).findFirst().orElseThrow().getContractAssociated());
        assertEquals(List.of(), new TransactionTemplate(transactionManager).execute(status -> sourceMapper.selectOrderLinesForUpdate(new ContractProjectScopeQuery(2L, projectId))));
    }

    @Test void orderIncreaseVersionsOnlyParentRemainderAndPreservesExistingChildAndHistory() {
        jdbc.update("INSERT INTO com_delivery_scope(id,tenant_id,project_id,project_code,order_line_id,order_source_system,order_company_code,order_type,order_no,line_no,product_code,allocated_qty,scope_status,allocation_version,allocation_source,department_id,department_code,department_name,department_version,effective_from,status,creator,updater) VALUES (?,1,?,?,?,'ERP','DPTECH-DEMO','0',?,'10','AR6280',6,'ACTIVE',1,'PROJECT_SPLIT',930851,'OFFICE-HZ-DEMO','杭州示例办事处',0,CURRENT_TIMESTAMP(3),'ENABLED',?,?)",
                IdWorker.getId(), childId, prefix + "-child", lineA, prefix + "-ORDER-A", prefix, prefix);
        var beforeChild = jdbc.queryForMap("SELECT * FROM com_delivery_scope WHERE project_id=?", childId);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> service.initialize(context(0, "init")));
        assertEquals(new BigDecimal("4.000000"), jdbc.queryForObject("SELECT allocated_qty FROM com_delivery_scope WHERE project_id=? AND order_line_id=?", BigDecimal.class, projectId, lineA));
        jdbc.update("UPDATE com_sales_order_line SET source_version='2',order_qty=15 WHERE id=?", lineA);
        var context = context(1, "increase"); var preview = service.preview(context);
        assertEquals(1, service.synchronize(context, preview.sourceDigest()).changedLineCount());
        assertEquals(2L, version());
        assertEquals(beforeChild, jdbc.queryForMap("SELECT * FROM com_delivery_scope WHERE project_id=?", childId));
        assertEquals(new BigDecimal("9.000000"), jdbc.queryForObject("SELECT allocated_qty FROM com_delivery_scope WHERE project_id=? AND order_line_id=? AND effective_to IS NULL", BigDecimal.class, projectId, lineA));
        assertEquals(new BigDecimal("4.000000"), jdbc.queryForObject("SELECT allocated_qty FROM com_delivery_scope WHERE project_id=? AND order_line_id=? AND scope_status='RELEASED'", BigDecimal.class, projectId, lineA));
    }

    private ScopeContext context(long version, String operation) { return new ScopeContext(1L, projectId, 7L, 1L, 1L, version, prefix + '-' + operation); }
    private long ownScopeCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM com_delivery_scope WHERE project_id=?", Long.class, projectId); }
    private long version() { return jdbc.queryForObject("SELECT scope_version FROM com_delivery_scope_project_version WHERE project_id=?", Long.class, projectId); }
    private long outboxCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM com_outbox_event WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.projectId'))=?", Long.class, String.valueOf(projectId)); }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.commerce.dal.mysql", "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class, MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class, ProjectContractScopeService.class, DeliveryScopeProjectVersionService.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class Application {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        // Match the server's PMS @Version interceptor; upstream mini registers pagination only.
        @Bean org.springframework.beans.factory.config.BeanPostProcessor optimisticLocking() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
                        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
                    }
                    return bean;
                }
            };
        }
    }
}
