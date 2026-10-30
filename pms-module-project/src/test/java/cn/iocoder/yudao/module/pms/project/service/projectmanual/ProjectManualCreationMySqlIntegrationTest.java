package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.AcceptanceActivityInitializationApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.application.ProjectDeliverableInitializationApplicationServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeMetrics;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeClassificationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeSourceCorrectionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.TrustedProjectServicePrincipalRegistry;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.outbox.PlatformOutboxDeliveryApiImpl;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateServiceImpl;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class ProjectManualCreationMySqlIntegrationTest extends ProjectManualCreationMySqlTestSupport {

    @Test
    void successfulRootCreationPublishesInitialTreeProjection() {
        ManualProjectCreateCommand command = newCommand();
        // 三层任务树已烘进V2模板包的冻结执行快照（@BeforeAll真实发布管道产出），实例化按快照落树。
        ManualProjectCreateResult created = applicationService.create(command, newActor());

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_tree_version WHERE root_project_id=? "
                        + "AND tree_version=1 AND status='ACTIVE' AND node_count=1 AND path_count=1",
                Long.class, created.id()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project_tree_path WHERE root_project_id=? "
                        + "AND tree_version=1 AND ancestor_project_id=? AND descendant_project_id=? AND distance=0",
                Long.class, created.id(), created.id(), created.id()));
        assertEquals(3L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM proj_project_task task
                JOIN proj_task_state_machine_revision revision
                  ON revision.tenant_id = task.tenant_id
                 AND revision.id = task.state_machine_revision_id
                WHERE task.project_id=?
                  AND task.task_code IN (?, ?, ?)
                  AND task.root_task_id IS NOT NULL
                  AND task.tree_depth IN (0, 1, 2)
                  AND revision.status='PUBLISHED'
                """, Long.class, created.id(), packRootTaskCode, packChildTaskCode, packGrandTaskCode));
        assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM proj_project_task root
                JOIN proj_project_task child
                  ON child.tenant_id=root.tenant_id AND child.project_id=root.project_id
                 AND child.parent_task_id=root.id AND child.root_task_id=root.id AND child.tree_depth=1
                JOIN proj_project_task grandchild
                  ON grandchild.tenant_id=child.tenant_id AND grandchild.project_id=child.project_id
                 AND grandchild.parent_task_id=child.id AND grandchild.root_task_id=root.id
                 AND grandchild.tree_depth=2
                WHERE root.project_id=? AND root.task_code=?
                  AND root.parent_task_id IS NULL AND root.root_task_id=root.id AND root.tree_depth=0
                  AND child.task_code=? AND grandchild.task_code=?
                """, Long.class, created.id(), packRootTaskCode, packChildTaskCode, packGrandTaskCode));
        assertEquals(6L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM proj_task_tree_path path
                JOIN proj_project_task ancestor ON ancestor.id=path.ancestor_task_id
                JOIN proj_project_task descendant ON descendant.id=path.descendant_task_id
                WHERE path.project_id=?
                  AND ancestor.task_code IN (?, ?, ?)
                  AND descendant.task_code IN (?, ?, ?)
                """, Long.class, created.id(), packRootTaskCode, packChildTaskCode, packGrandTaskCode,
                packRootTaskCode, packChildTaskCode, packGrandTaskCode));
        assertEquals(0L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT task.id
                    FROM proj_project_task task
                    LEFT JOIN proj_project_task_execution_contract contract
                      ON contract.tenant_id=task.tenant_id AND contract.project_task_id=task.id
                     AND contract.current_marker=1 AND contract.deleted=b'0'
                    WHERE task.project_id=?
                    GROUP BY task.id
                    HAVING COUNT(contract.id) <> 1
                ) invalid_contracts
                """, Long.class, created.id()));
    }

    @ParameterizedTest(name = "{0} failure rolls back every fact")
    @EnumSource(value = FailurePoint.class, mode = EnumSource.Mode.EXCLUDE, names = "COM_RELATION")
    void everyFailurePointRollsBackAllFacts(FailurePoint point) {
        Map<String, Long> before = factCounts();
        installFailureTrigger(point);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> applicationService.create(newCommand(), newActor()));

        dropFailureTrigger();
        assertTrue(hasCauseMessage(failure, "F-PROJ-001 injected failure"),
                () -> point + "未到达指定MySQL Trigger，实际异常：" + failure);
        assertEquals(before, factCounts());
    }

    @Test
    void creationWithContractChainBindsRelationAndAppliesCrmAuthoritativeFields() {
        long contractId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long orderId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long executionId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        String contractNo = DATA_PREFIX + "CT-" + UUID.randomUUID();
        String orderNo = DATA_PREFIX + "SO-" + UUID.randomUUID();
        String executionNo = DATA_PREFIX + "EX-" + UUID.randomUUID();
        seedCommerceChain(contractId, contractNo, orderId, orderNo, executionId, executionNo);
        ManualProjectCreateCommand base = newCommand();
        base.draft().setContractNo(contractNo);
        ManualProjectCreateCommand command = new ManualProjectCreateCommand(base.draft(),
                base.orderOfficeCompanyId(), base.orderOfficeDepartmentId(), base.sites(),
                base.templateRevisionId(), base.candidateWatermark(), null, contractId,
                base.idempotencyKey(), base.requestDigest());

        ManualProjectCreateResult created = applicationService.create(command, newActor());

        assertEquals(1L, jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM com_project_contract_relation
                        WHERE tenant_id=0 AND project_id=? AND contract_id=? AND relation_role='RELATED'
                          AND source_system='PMS' AND status='ACTIVE' AND source_record_key=? AND deleted=0
                        """, Long.class, created.id(), contractId, command.idempotencyKey()),
                "创建事务内必须写入项目-合同关系");
        assertEquals(created.id(), jdbcTemplate.queryForObject(
                "SELECT primary_project_id FROM com_crm_execution_order WHERE execution_no=? AND tenant_id=0",
                Long.class, executionNo));
        assertEquals("客户项目名称X", jdbcTemplate.queryForObject(
                "SELECT customer_project_name FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("OFFICE_LEVEL", jdbcTemplate.queryForObject(
                "SELECT major_project_level FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("MKT-1", jdbcTemplate.queryForObject(
                "SELECT market_code FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("市场一部", jdbcTemplate.queryForObject(
                "SELECT market_name FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("系统一部", jdbcTemplate.queryForObject(
                "SELECT system_name FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("拓展一部", jdbcTemplate.queryForObject(
                "SELECT expend_name FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("IND-1", jdbcTemplate.queryForObject(
                "SELECT industry_code FROM proj_project WHERE id=?", String.class, created.id()));
        assertEquals("集成测试合同", jdbcTemplate.queryForObject(
                "SELECT contract_name FROM com_contract WHERE id=?", String.class, contractId));
    }

    @Test
    void contractAlreadyBoundToAnotherProjectIsRejectedAndCreationRollsBack() {
        long contractId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        seedCommerceChain(contractId, DATA_PREFIX + "CT-" + UUID.randomUUID(),
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), DATA_PREFIX + "SO-" + UUID.randomUUID(),
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), DATA_PREFIX + "EX-" + UUID.randomUUID());
        ManualProjectCreateCommand first = newCommand();
        ManualProjectCreateCommand firstWithContract = new ManualProjectCreateCommand(first.draft(),
                first.orderOfficeCompanyId(), first.orderOfficeDepartmentId(), first.sites(),
                first.templateRevisionId(), first.candidateWatermark(), null, contractId,
                first.idempotencyKey(), first.requestDigest());
        ManualProjectCreateResult owner = applicationService.create(firstWithContract, newActor());

        String secondProjectName = DATA_PREFIX + UUID.randomUUID();
        ManualProjectCreateCommand second = newCommand(KEY_PREFIX + UUID.randomUUID(),
                sha256(UUID.randomUUID().toString()), secondProjectName);
        ManualProjectCreateCommand secondWithContract = new ManualProjectCreateCommand(second.draft(),
                second.orderOfficeCompanyId(), second.orderOfficeDepartmentId(), second.sites(),
                second.templateRevisionId(), second.candidateWatermark(), null, contractId,
                second.idempotencyKey(), second.requestDigest());

        cn.iocoder.yudao.framework.common.exception.ServiceException failure =
                assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                        () -> applicationService.create(secondWithContract, newActor()));

        assertEquals(cn.iocoder.yudao.module.pms.commerce.enums.ErrorCodeConstants
                .COMMERCE_CONTRACT_ALREADY_BOUND.getCode(), failure.getCode());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proj_project WHERE project_name=?", Long.class, secondProjectName),
                "绑定拒绝必须整体回滚第二个项目创建");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_project_contract_relation WHERE contract_id=? AND deleted=0",
                Long.class, contractId));
        assertEquals(owner.id(), jdbcTemplate.queryForObject(
                "SELECT project_id FROM com_project_contract_relation WHERE contract_id=? AND deleted=0",
                Long.class, contractId));
    }

    @Test
    void contractChainCreationReplayDoesNotDuplicateBind() {
        long contractId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        seedCommerceChain(contractId, DATA_PREFIX + "CT-" + UUID.randomUUID(),
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), DATA_PREFIX + "SO-" + UUID.randomUUID(),
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), DATA_PREFIX + "EX-" + UUID.randomUUID());
        ManualProjectCreateCommand base = newCommand();
        ManualProjectCreateCommand command = new ManualProjectCreateCommand(base.draft(),
                base.orderOfficeCompanyId(), base.orderOfficeDepartmentId(), base.sites(),
                base.templateRevisionId(), base.candidateWatermark(), null, contractId,
                base.idempotencyKey(), base.requestDigest());

        ManualProjectCreateResult first = applicationService.create(command, newActor());
        // 生产重放=客户端按原payload重发：全新draft（服务端按CRM回填的字段不回流）、同幂等键与摘要。
        ManualProjectCreateCommand replayCommand = new ManualProjectCreateCommand(
                replayDraft(command.draft()), command.orderOfficeCompanyId(), command.orderOfficeDepartmentId(),
                command.sites(), command.templateRevisionId(), command.candidateWatermark(), null, contractId,
                command.idempotencyKey(), command.requestDigest());
        ManualProjectCreateResult replay = applicationService.create(replayCommand, newActor());

        assertEquals(first.id(), replay.id());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM com_project_contract_relation WHERE contract_id=? AND deleted=0",
                Long.class, contractId));
    }

    @Test
    void commerceRelationInsertFailureRollsBackWholeCreation() {
        Map<String, Long> before = factCounts();
        long contractId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        seedCommerceChain(contractId, DATA_PREFIX + "CT-" + UUID.randomUUID(),
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), DATA_PREFIX + "SO-" + UUID.randomUUID(),
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), DATA_PREFIX + "EX-" + UUID.randomUUID());
        ManualProjectCreateCommand base = newCommand();
        ManualProjectCreateCommand command = new ManualProjectCreateCommand(base.draft(),
                base.orderOfficeCompanyId(), base.orderOfficeDepartmentId(), base.sites(),
                base.templateRevisionId(), base.candidateWatermark(), null, contractId,
                base.idempotencyKey(), base.requestDigest());
        installFailureTrigger(FailurePoint.COM_RELATION);

        RuntimeException failure;
        try {
            failure = assertThrows(RuntimeException.class,
                    () -> applicationService.create(command, newActor()));
        } finally {
            dropFailureTrigger();
        }
        assertTrue(hasCauseMessage(failure, "F-PROJ-001 injected failure"),
                () -> "绑定阶段必须到达com_project_contract_relation触发器，实际异常：" + failure);
        assertEquals(before, factCounts(), "ADR-0032：绑定写入失败必须整体回滚项目创建");
    }

    void seedCommerceChain(long contractId, String contractNo, long orderId, String orderNo,
                           long executionId, String executionNo) {
        jdbcTemplate.update("""
                INSERT INTO com_contract (id, tenant_id, company_code, company_name, contract_no,
                    master_source_system, contract_name, customer_code, customer_name, contract_amount,
                    currency_code, status, version, creator, updater)
                VALUES (?, 0, 'IT-COMPANY', '集成测试公司', ?, 'IT-SEED', '集成测试合同', 'IT-CUSTOMER',
                    '集成测试客户', 100000.00, 'CNY', 'ENABLED', 0, 'it-fproj001', 'it-fproj001')
                """, contractId, contractNo);
        jdbcTemplate.update("""
                INSERT INTO com_sales_order (id, tenant_id, source_system, company_code, company_name,
                    order_type, order_no, sales_type, customer_code, customer_name, source_project_name,
                    order_amount, currency_code, order_create_time, customer_required_time,
                    contract_no, execution_no, status, version, creator, updater)
                VALUES (?, 0, 'IT-SEED', 'IT-COMPANY', '集成测试公司', '0', ?, '01', 'IT-CUSTOMER',
                    '集成测试客户', '集成测试源项目名', 80000.00, 'CNY', '2026-09-01 10:00:00',
                    '2026-10-01 10:00:00', ?, ?, 'ENABLED', 0, 'it-fproj001', 'it-fproj001')
                """, orderId, orderNo, contractNo, executionNo);
        jdbcTemplate.update("""
                INSERT INTO com_crm_execution_order (id, tenant_id, source_system, execution_no, project_code,
                    project_name, market_code, market_name, system_code, system_name, expend_code,
                    expend_name, industry_code, industry_name, department_code, department_name,
                    company_code, company_name, sales_rep_code, sales_rep_name, customer_project_name,
                    major_project_level, project_type, project_amount, submit_time, status, version,
                    creator, updater)
                VALUES (?, 0, 'IT-SEED', ?, 'IT-PC-001', 'CRM项目名称', 'MKT-1', '市场一部', 'SYS-1',
                    '系统一部', 'EXP-1', '拓展一部', 'IND-1', '行业一类', 'IT-DEPT', '集成测试办事处',
                    'IT-COMPANY', '集成测试公司', 'SALER-1', '销售代表', '客户项目名称X', 'OFFICE_LEVEL',
                    'SYSTEM_INTEGRATION', 60000.0000, '2026-09-05 10:00:00', 'ACTIVE', 0,
                    'it-fproj001', 'it-fproj001')
                """, executionId, executionNo);
    }
}

@SpringBootTest(
        classes = ProjectManualCreationMySqlTestSupport.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "liteflow.enable=true",
                "liteflow.parse-mode=PARSE_ALL_ON_START",
                "liteflow.print-banner=false",
                "liteflow.print-execution-log=false",
                "liteflow.monitor.enable-log=false",
                "liteflow.metrics.enabled=false"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ProjectManualCreationMySqlTestSupport {

    static final String DATA_PREFIX = "IT-FPROJ001-";
    static final String KEY_PREFIX = "it-fproj001-";
    static final String TEMPLATE_PACK_CODE = "IT-FPROJ001-V2";
    private static final String FAILURE_TRIGGER = "it_fproj001_failure";

    @Resource
    ProjectManualCreationApplicationService applicationService;
    @Resource
    JdbcTemplate jdbcTemplate;

    /** @BeforeAll 在冻结快照内构造的三层任务链编码，供树形断言动态取用。 */
    String packRootTaskCode;
    String packChildTaskCode;
    String packGrandTaskCode;

    @BeforeAll
    void seedV2TemplatePack() {
        TenantContextHolder.setTenantId(0L);
        // V2匹配与实例化只读冻结执行快照，快照自足（binding/permission/completionRule内嵌）。
        // 租户0没有交付定义，走真实发布管道需先补建整套交付定义；改为复制租户1已发布的
        // V2快照为租户0模板（同为共享库domain_test既有事实），并在快照内把同阶段三个
        // 根任务重挂成三层链供树形断言使用。snapshot_hash无运行时校验，改写安全。
        jdbcTemplate.update("DELETE FROM proj_project_template_revision WHERE template_id IN "
                + "(SELECT id FROM (SELECT id FROM proj_project_template WHERE code=? AND tenant_id=0) p)",
                TEMPLATE_PACK_CODE);
        jdbcTemplate.update("DELETE FROM proj_project_template WHERE code=? AND tenant_id=0", TEMPLATE_PACK_CODE);

        Long sourceRevisionId;
        try {
            // 共享库已发布修订snapshot_hash为NULL，匹配按TemplateVersionPublication.applies(schema=3)接纳，
            // 与V2匹配循环的候选条件保持一致。回滚矩阵覆盖里程碑落库，源快照必须带里程碑，否则MILESTONE
            // 触发点不可达；优先取带里程碑的最新直签-工程类，保证快照形状确定。
            sourceRevisionId = jdbcTemplate.queryForObject("""
                    SELECT r.id FROM proj_project_template_revision r
                    JOIN proj_project_template t ON t.id=r.template_id AND t.tenant_id=r.tenant_id
                    WHERE t.tenant_id=1 AND t.status='ACTIVE' AND r.status='PUBLISHED' AND r.revision_no=1
                      AND r.execution_schema_version=3 AND r.execution_snapshot IS NOT NULL
                      AND r.signing_method='DIRECT_SIGN' AND r.project_category='ENGINEERING'
                      AND JSON_LENGTH(JSON_EXTRACT(r.execution_snapshot,'$.milestones')) > 0
                      AND t.deleted=0 AND r.deleted=0
                    ORDER BY r.id DESC LIMIT 1
                    """, Long.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalStateException(
                    "IT需要共享库npdms_domain_test存在租户1已发布V2模板（直签-工程类，含里程碑）；"
                            + "请用 NPDMS_MYSQL_PORT=24306 NPDMS_DB_NAME=npdms_domain_test 运行", ex);
        }
        Long sourceTemplateId = jdbcTemplate.queryForObject(
                "SELECT template_id FROM proj_project_template_revision WHERE id=?", Long.class, sourceRevisionId);
        String snapshotJson = jdbcTemplate.queryForObject(
                "SELECT execution_snapshot FROM proj_project_template_revision WHERE id=?", String.class, sourceRevisionId);

        tools.jackson.databind.JsonNode root = new tools.jackson.databind.ObjectMapper().readTree(snapshotJson);
        Map<String, List<tools.jackson.databind.node.ObjectNode>> rootsByStage = new LinkedHashMap<>();
        for (tools.jackson.databind.JsonNode task : root.get("tasks")) {
            if (task.hasNonNull("parentTaskCode")) continue;
            rootsByStage.computeIfAbsent(task.get("stageCode").asText(), ignored -> new ArrayList<>())
                    .add((tools.jackson.databind.node.ObjectNode) task);
        }
        List<tools.jackson.databind.node.ObjectNode> chain = rootsByStage.values().stream()
                .filter(tasks -> tasks.size() >= 3)
                .max(Comparator.comparingInt(List::size))
                .orElseThrow(() -> new IllegalStateException("源V2快照没有含3个根任务的阶段，无法构造三层树"));
        chain.sort(Comparator.comparingInt(task -> task.path("sortOrder").asInt(0)));
        packRootTaskCode = chain.get(0).get("code").asText();
        packChildTaskCode = chain.get(1).get("code").asText();
        packGrandTaskCode = chain.get(2).get("code").asText();
        chain.get(1).put("parentTaskCode", packRootTaskCode);
        chain.get(2).put("parentTaskCode", packChildTaskCode);
        String frozenSnapshot = new tools.jackson.databind.ObjectMapper().writeValueAsString(root);

        Map<String, Object> templateRow = new LinkedHashMap<>(
                jdbcTemplate.queryForMap("SELECT * FROM proj_project_template WHERE id=?", sourceTemplateId));
        templateRow.remove("id");
        templateRow.put("tenant_id", 0);
        templateRow.put("code", TEMPLATE_PACK_CODE);
        templateRow.put("name", "F-PROJ-001集成测试模板");
        templateRow.put("description", "复制租户1已发布V2冻结快照，快照内含三层任务树");
        templateRow.put("status", "ACTIVE");
        templateRow.put("creator", "it-fproj001");
        templateRow.put("updater", "it-fproj001");
        insertRow("proj_project_template", templateRow);
        Long packTemplateId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_project_template WHERE code=? AND tenant_id=0", Long.class, TEMPLATE_PACK_CODE);

        Map<String, Object> revisionRow = new LinkedHashMap<>(
                jdbcTemplate.queryForMap("SELECT * FROM proj_project_template_revision WHERE id=?", sourceRevisionId));
        revisionRow.remove("id");
        revisionRow.put("tenant_id", 0);
        revisionRow.put("template_id", packTemplateId);
        revisionRow.put("revision_no", 1);
        revisionRow.put("status", "PUBLISHED");
        revisionRow.put("execution_snapshot", frozenSnapshot);
        revisionRow.put("published_by", "it-fproj001");
        revisionRow.put("creator", "it-fproj001");
        revisionRow.put("updater", "it-fproj001");
        insertRow("proj_project_template_revision", revisionRow);
    }

    private void insertRow(String table, Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(ignored -> "?").toList());
        jdbcTemplate.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")",
                row.values().toArray());
    }

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
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void cleanBefore() {
        TenantContextHolder.setTenantId(0L);
        // V2冻结链(阶段执行契约等)的creator/updater列NOT NULL，与生产一致经登录上下文由元处理器填充。
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser();
        login.setId(9_900_001L);
        login.setTenantId(0L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        login, null, java.util.List.of()));
        dropFailureTrigger();
        cleanOwnedFacts();
    }

    @AfterEach
    void cleanAfter() {
        try {
            dropFailureTrigger();
            cleanOwnedFacts();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    ManualProjectCreateCommand newCommand() {
        return newCommand(KEY_PREFIX + UUID.randomUUID(), sha256(UUID.randomUUID().toString()));
    }

    ManualProjectCreateCommand newCommand(String idempotencyKey, String requestDigest) {
        return newCommand(idempotencyKey, requestDigest, DATA_PREFIX + UUID.randomUUID());
    }

    ManualProjectCreateCommand newCommand(String idempotencyKey, String requestDigest, String projectName) {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setProjectName(projectName);
        draft.setCustomerCode("IT-CUSTOMER");
        draft.setCustomerName("集成测试客户");
        draft.setCreationReason("F-PROJ-001真实MySQL原子性验证");
        draft.setSigningMethod("DIRECT_SIGN");
        draft.setProjectCategory("ENGINEERING");
        draft.setImplementationMode("DIRECT_SERVICE");
        draft.setImplementationLocation("集成测试兼容地点");
        TemplateMatchResult match = applicationService.previewMatching(draft, 1L, 1L, newActor());
        if (match.getOutcome() != TemplateMatchResult.Outcome.MATCHED || match.getMatched() == null) {
            throw new IllegalStateException("真实MySQL集成测试需要V54/V55提供唯一生效模板：outcome="
                    + match.getOutcome() + ", conflicts=" + match.getConflicts()
                    + ", candidates=" + match.getCandidates());
        }
        return new ManualProjectCreateCommand(draft, 1L, 1L, java.util.List.of(),
                match.getMatched().getTemplateRevisionId(), match.getCandidateWatermark(),
                null, null, idempotencyKey, requestDigest);
    }

    ProjectManualCreationApplicationService.Actor newActor() {
        return new ProjectManualCreationApplicationService.Actor(
                0L, 9_900_001L, KEY_PREFIX + "correlation-" + UUID.randomUUID());
    }

    /** 重放请求的draft镜像：只带客户端可控字段，服务端按CRM回填的字段不回流。 */
    ProjectMasterDO replayDraft(ProjectMasterDO original) {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setProjectName(original.getProjectName());
        draft.setCustomerCode(original.getCustomerCode());
        draft.setCustomerName(original.getCustomerName());
        draft.setCreationReason(original.getCreationReason());
        draft.setSigningMethod(original.getSigningMethod());
        draft.setProjectCategory(original.getProjectCategory());
        draft.setImplementationMode(original.getImplementationMode());
        draft.setImplementationLocation(original.getImplementationLocation());
        draft.setContractNo(original.getContractNo());
        return draft;
    }

    Map<String, Long> factCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of(
                "proj_project", "proj_project_stage", "proj_project_task",
                "proj_project_milestone", "proj_project_gate", "proj_project_gate_reference",
                "proj_project_task_execution_contract", "proj_task_tree_path", "acc_project_deliverable",
                "proj_project_template_match_history", "proj_project_tree_version", "proj_project_tree_path",
                "com_project_contract_relation",
                "plt_idempotency_record", "plt_operation_audit", "plt_outbox_event")) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }

    void installFailureTrigger(FailurePoint point) {
        dropFailureTrigger();
        jdbcTemplate.execute("CREATE TRIGGER " + FAILURE_TRIGGER + " BEFORE " + point.operation
                + " ON " + point.table + " FOR EACH ROW SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'F-PROJ-001 injected failure'");
    }

    void dropFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILURE_TRIGGER);
    }

    void cleanOwnedFacts() {
        // 商务链种子与绑定事实先于项目清理：com_project_contract_relation 对 com_contract 有外键。
        jdbcTemplate.update("DELETE FROM com_project_contract_relation WHERE source_record_key LIKE ?",
                KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM com_crm_execution_order WHERE execution_no LIKE ?",
                DATA_PREFIX + "EX-%");
        jdbcTemplate.update("DELETE FROM com_sales_order WHERE order_no LIKE ?", DATA_PREFIX + "SO-%");
        jdbcTemplate.update("DELETE FROM com_contract WHERE contract_no LIKE ?", DATA_PREFIX + "CT-%");
        List<Long> projectIds = jdbcTemplate.queryForList(
                "SELECT id FROM proj_project WHERE project_name LIKE ?", Long.class, DATA_PREFIX + "%");
        if (!projectIds.isEmpty()) {
            String placeholders = String.join(",", projectIds.stream().map(ignored -> "?").toList());
            Object[] ids = projectIds.toArray();
            // PM-07历史通过专用append-only Mapper封闭写入口；测试也不删除已落历史。
            jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE aggregate_type = 'Project' "
                    + "AND aggregate_key IN (" + placeholders + ")", Arrays.stream(ids)
                    .map(String::valueOf).toArray());
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE aggregate_type = 'Project' "
                    + "AND aggregate_key IN (" + placeholders + ")", Arrays.stream(ids)
                    .map(String::valueOf).toArray());
            jdbcTemplate.update("DELETE FROM proj_project_gate_reference WHERE gate_id IN "
                    + "(SELECT id FROM proj_project_gate WHERE project_id IN (" + placeholders + "))", ids);
            jdbcTemplate.update("DELETE FROM proj_project_task_execution_contract WHERE project_task_id IN "
                    + "(SELECT id FROM proj_project_task WHERE project_id IN (" + placeholders + "))", ids);
            jdbcTemplate.update("DELETE FROM proj_task_tree_path WHERE project_id IN (" + placeholders + ")", ids);
            for (String table : List.of("acc_project_deliverable", "proj_project_company_department_relation",
                    "proj_project_member_assignment", "proj_project_gate", "proj_project_milestone",
                    "proj_project_stage")) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE project_id IN (" + placeholders + ")", ids);
            }
            deleteProjectTasksForTest(projectIds, placeholders);
            jdbcTemplate.update("DELETE FROM proj_project_tree_path WHERE root_project_id IN ("
                    + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE root_project_id IN ("
                    + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM proj_project WHERE id IN (" + placeholders + ")", ids);
        }
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?", KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?", KEY_PREFIX + "%");
    }

    private void deleteProjectTasksForTest(List<Long> projectIds, String placeholders) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
            }
            try (var delete = connection.prepareStatement(
                    "DELETE FROM proj_project_task WHERE project_id IN (" + placeholders + ")")) {
                for (int index = 0; index < projectIds.size(); index++) {
                    delete.setLong(index + 1, projectIds.get(index));
                }
                delete.executeUpdate();
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static boolean hasCauseMessage(Throwable failure, String expected) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> currentEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(System.getenv());
        // 命令行-D叠加（经surefire argLine注入fork JVM）：显式指定集成库时不依赖env传播链。
        for (String key : List.of("NPDMS_MYSQL_PORT", "NPDMS_DB_NAME", "NPDMS_DB_USER", "NPDMS_DB_PASSWORD")) {
            String override = System.getProperty(key);
            if (override != null && !override.isBlank()) {
                values.put(key, override);
            }
        }
        Path dotenv = findRepositoryDotenv();
        if (dotenv == null) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
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

    enum FailurePoint {
        STAGE("INSERT", "proj_project_stage"),
        TASK("INSERT", "proj_project_task"),
        TASK_TREE_PATH("INSERT", "proj_task_tree_path"),
        MILESTONE("INSERT", "proj_project_milestone"),
        GATE("INSERT", "proj_project_gate"),
        CONTRACT("INSERT", "proj_project_task_execution_contract"),
        ACC_DELIVERABLE("INSERT", "acc_project_deliverable"),
        MATCH_HISTORY("INSERT", "proj_project_template_match_history"),
        TREE_VERSION("INSERT", "proj_project_tree_version"),
        TREE_PATH("INSERT", "proj_project_tree_path"),
        COM_RELATION("INSERT", "com_project_contract_relation"),
        IDEMPOTENCY_SUCCESS("UPDATE", "plt_idempotency_record"),
        AUDIT("INSERT", "plt_operation_audit"),
        OUTBOX("INSERT", "plt_outbox_event");

        private final String operation;
        private final String table;

        FailurePoint(String operation, String table) {
            this.operation = operation;
            this.table = table;
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox",
            "cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract",
            "cn.iocoder.yudao.module.pms.commerce.dal.mysql.order",
            "cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder",
            "cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance"})
    @Import({
            YudaoDataSourceAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class,
            ProjectManualCreationApplicationService.class,
            ProjectServiceManagerCandidateValidator.class,
            PlatformCommandExecutionApiImpl.class,
            cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter.class,
            PlatformOutboxDeliveryApiImpl.class,
            cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl.class,
            cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService.class,
            cn.iocoder.yudao.module.pms.commerce.service.contract.ProjectCommerceSourceServiceImpl.class,
            ProjectManualCreationServiceImpl.class,
            cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateV2ServiceImpl.class,
            cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateCompiler.class,
            cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateDesignerDependencyValidator.class,
            cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateMatchRuleEvaluator.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.Prepare.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.Decisions.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.DecisionValue.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.Predicate.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.Field.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.Matched.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleComponents.NotMatched.class,
            com.yomahub.liteflow.springboot4.config.LiteflowPropertyAutoConfiguration.class,
            com.yomahub.liteflow.springboot4.config.LiteflowMainAutoConfiguration.class,
            ProjectTemplateServiceImpl.class,
            ProjectAttributeResolutionService.class,
            ProjectTemplateMatchHistoryService.class,
            ProjectAttributeClassificationApplicationService.class,
            ProjectAttributeSourceCorrectionService.class,
            ProjectTemplateMatchHistoryQueryService.class,
            ProjectTreeProjectionService.class,
            cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer.class,
            ProjectCodeAllocator.class,
            TaskExecutionContractFactory.class,
            ProjectDeliverableInitializationApplicationServiceImpl.class,
            cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService.class,
            cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateDefinitionReferenceAssembler.class,
            cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionResolver.class,
            cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands.class,
            cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry.class,
            cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRulePublicationValidator.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService.class,
            cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionEngineConfiguration.class
    })
    static class TestApplication {

        /**
         * 施工计划初始化与阶段准入依赖规则引擎(LiteFlow/DMN)与结果订阅链，
         * 本类不断言计划行与准入结果；两服务各有专属测试类，这里以桩替换。
         */
        @Bean
        cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanInitializationService
        planInitializationService() {
            return mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanInitializationService.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService
        stageAdmissionService() {
            return mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService.class);
        }

        @Bean
        AcceptanceActivityInitializationApi acceptanceActivityInitializationApi() {
            return mock(AcceptanceActivityInitializationApi.class);
        }

        @Bean
        SatisfactionQuestionnaireTemplateApi satisfactionQuestionnaireTemplateApi() {
            // 满意度解析属pms-module-acceptance自治逻辑（有专属测试），本IT只断言创建链冻结了Owner快照；
            // V2快照任务带satisfactionTiming时冻结必须拿到FOUND事实，桩按链路维度返回唯一已发布模板。
            SatisfactionQuestionnaireTemplateApi api = mock(SatisfactionQuestionnaireTemplateApi.class);
            when(api.resolvePublished(any())).thenReturn(new SatisfactionTemplateFact(
                    "FOUND", 993009909901L, 993009909902L, 1, "it-fproj001-v1", new java.math.BigDecimal("90.00")));
            return api;
        }

        @Bean
        cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi deviceQueryApi() {
            return mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi.class);
        }

        // 两个桩服务的 @Resource 字段仍会被注入，这三个构造注入的下游以空桩终结依赖链。
        @Bean
        cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService
        currentStageService() {
            return mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler
        ruleTimerScheduler() {
            return mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultSubscriptionInstaller
        resultSubscriptionInstaller() {
            return mock(cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultSubscriptionInstaller.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields projectRuleFields() {
            return new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null);
        }

        @Bean
        cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi bpmNormalClosureApi() {
            return mock(cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.infra.api.config.ConfigApi configApi() {
            return mock(cn.iocoder.yudao.module.infra.api.config.ConfigApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.system.api.dict.DictDataApi dictDataApi() {
            return mock(cn.iocoder.yudao.module.system.api.dict.DictDataApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi dynamicFormBusinessInstanceApi() {
            return mock(cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi explicitPermissionApi() {
            return mock(cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi stageGateProcessOwnerApi() {
            return mock(cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi businessViewQueryApi() {
            return mock(cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi
                projectWorkBindingFactApi() {
            return mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi.class);
        }

        @Bean
        cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi
                preparationInitializationApi() {
            return mock(cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        AdminUserApi adminUserApi() {
            return mock(AdminUserApi.class);
        }

        @Bean
        CustomerQueryApi customerQueryApi() {
            CustomerQueryApi api = mock(CustomerQueryApi.class);
            when(api.getCustomer(anyLong())).thenAnswer(invocation -> {
                Long customerId = invocation.getArgument(0);
                return new CustomerSummaryDTO(customerId, 0L, "IT-CUSTOMER", "集成测试客户",
                        "集成测试客户", "ENABLED", "PLATFORM", 1L, null);
            });
            return api;
        }

        @Bean
        DeptApi deptApi() {
            DeptApi api = mock(DeptApi.class);
            DeptRespDTO department = new DeptRespDTO();
            department.setId(1L);
            department.setCode("IT-DEPT");
            department.setName("集成测试办事处");
            when(api.getDept(1L)).thenReturn(department);
            return api;
        }

        @Bean
        CompanyApi companyApi() {
            CompanyApi api = mock(CompanyApi.class);
            CompanyRespDTO company = new CompanyRespDTO();
            company.setId(1L);
            company.setCode("IT-COMPANY");
            company.setName("集成测试公司");
            when(api.getCompany(1L)).thenReturn(company);
            return api;
        }

        @Bean
        OrganizationScopeApi organizationScopeApi() {
            OrganizationScopeApi api = mock(OrganizationScopeApi.class);
            when(api.hasScope(anyLong(), anyLong(), anyLong())).thenReturn(true);
            when(api.getActiveScopes(anyLong())).thenAnswer(invocation -> {
                cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO scope =
                        new cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO();
                scope.setId(1L);
                scope.setCompanyCode("IT-COMPANY");
                scope.setVersion(1);
                return List.of(scope);
            });
            return api;
        }

        @Bean
        cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScopeApi() {
            cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi api =
                    mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
            when(api.resolveAllCurrent(any())).thenReturn(java.util.Set.of());
            return api;
        }

        @Bean
        ProjectCreationAuthorizationService authorizationService() {
            return mock(ProjectCreationAuthorizationService.class);
        }

        @Bean
        ProjectTreeScopeService projectTreeScopeService() {
            return mock(ProjectTreeScopeService.class);
        }

        @Bean
        ProjectTreeMetrics projectTreeMetrics() {
            return mock(ProjectTreeMetrics.class);
        }

        @Bean
        PermissionCommonApi permissionCommonApi() {
            PermissionCommonApi api = mock(PermissionCommonApi.class);
            when(api.hasAnyPermissions(anyLong(), any())).thenReturn(true);
            return api;
        }

        @Bean
        PermissionApi permissionApi() {
            PermissionApi api = mock(PermissionApi.class);
            when(api.hasAnyPermissions(anyLong(), any())).thenReturn(true);
            return api;
        }

        @Bean
        TrustedProjectServicePrincipalRegistry trustedProjectServicePrincipalRegistry() {
            TrustedProjectServicePrincipalRegistry registry = mock(TrustedProjectServicePrincipalRegistry.class);
            when(registry.resolve("int-crm-sync")).thenReturn(9_900_002L);
            return registry;
        }

        @Bean
        ProjectSiteApplicationService projectSiteApplicationService() {
            ProjectSiteApplicationService service = mock(ProjectSiteApplicationService.class);
            when(service.validateLocationScope(any(), any()))
                    .thenReturn(ProjectSiteApplicationService.LOCATION_UNRESOLVED);
            return service;
        }

        @Bean
        AssetLocationApi assetLocationApi() {
            return mock(AssetLocationApi.class);
        }

        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
