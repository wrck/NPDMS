package cn.iocoder.yudao.module.pms.engineering.preparation;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.infra.api.config.ConfigApiImpl;
import cn.iocoder.yudao.module.infra.dal.mysql.config.ConfigMapper;
import cn.iocoder.yudao.module.infra.service.config.ConfigServiceImpl;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApiImpl;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalogProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationFilePolicyProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationInitializationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReviewService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSourceProviderRegistry;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApiImpl;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.PreparationWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeClassificationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeSourceCorrectionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.TrustedProjectServicePrincipalRegistry;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCodeAllocator;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectSiteApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateServiceImpl;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeMetrics;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = PreparationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PreparationMySqlIntegrationTest {

    @Resource ProjectManualCreationApplicationService projectCreationService;
    @Resource ProjectTemplateService projectTemplateService;
    @Resource PreparationItemApplicationService itemService;
    @Resource PreparationReviewService reviewService;
    @Resource PreparationReadinessService readinessService;
    @Resource JdbcTemplate jdbcTemplate;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        login(9_900_001L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    @Transactional
    void publicProjectCreationInitializesPreparationAndReplaysWithoutDuplicates() {
        publishPreparationTemplate();
        ManualProjectCreateCommand command = command();
        var actor = new ProjectManualCreationApplicationService.Actor(
                0L, 9_900_001L, "PRE02-PROJECT-" + UUID.randomUUID());

        var created = projectCreationService.create(command, actor);
        var replay = projectCreationService.create(command, actor);

        assertEquals(created, replay);
        assertNotNull(created.id());
        assertEquals(1L, count("proj_project", "id", created.id()));
        assertEquals(1L, count("sol_preparation", "project_id", created.id()));
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, created.id());
        assertEquals(5L, count("sol_preparation_item", "preparation_id", preparationId));
        assertEquals(5L, count("sol_dynamic_form_instance", "preparation_id", preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='POST:/pms/projects' AND actor_id=? "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class,
                actor.actorId(), command.idempotencyKey()));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='PREPARATION_INITIALIZE' AND actor_id=? "
                + "AND status='COMPLETED'", Long.class, actor.actorId()));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='PREPARATION_INITIALIZE' "
                + "AND aggregate_type='Preparation' AND aggregate_key=? AND result_code='SUCCESS'",
                Long.class, String.valueOf(preparationId)));
    }

    @Test
    @Transactional
    void initializedPreparationSupportsAssignmentFormAndRealFileFactFreeze() {
        publishPreparationTemplate();
        ManualProjectCreateCommand createCommand = command();
        var manager = new ProjectManualCreationApplicationService.Actor(
                0L, 9_900_001L, "PRE02-PROJECT-" + UUID.randomUUID());
        var created = projectCreationService.create(createCommand, manager);
        assertEquals(0, created.version());
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, created.id());
        Map<String, Object> item = jdbcTemplate.queryForMap("SELECT i.id,i.version item_version,"
                + "f.version form_version FROM sol_preparation_item i JOIN sol_dynamic_form_instance f "
                + "ON f.tenant_id=i.tenant_id AND f.preparation_id=i.preparation_id AND f.item_id=i.id "
                + "WHERE i.tenant_id=0 AND i.preparation_id=? ORDER BY i.sort_order,i.id LIMIT 1", preparationId);
        Long itemId = ((Number) item.get("id")).longValue();
        long assigneeId = 9_900_002L;

        var assignCommand = new PatchPreparationItemCommand(preparationId, itemId,
                ((Number) item.get("item_version")).intValue(), 0, 0, 0,
                ((Number) item.get("form_version")).intValue(), created.version(),
                java.util.Set.of("assignee"), null, null, assigneeId, null,
                null, null, null, null);
        var assigned = itemService.patch(assignCommand,
                new PreparationItemApplicationService.Actor(0L, manager.actorId(), manager.correlationId()));

        long artifactId = IdWorker.getId();
        insertFileFacts(itemId, artifactId, assigneeId);
        login(assigneeId);
        var filled = itemService.patch(new PatchPreparationItemCommand(preparationId, itemId,
                assigned.getItemVersion(), assigned.getPreparationVersion(), assigned.getInputVersion(), 0,
                assigned.getFormVersion(), created.version(),
                java.util.Set.of("siteResultCode", "siteResultDetail", "formValueSnapshot", "evidenceReferences"),
                null, null, null, null, "READY", "现场供电条件满足",
                "{\"siteCondition\":\"供电稳定\"}", List.of(new PatchPreparationItemCommand.EvidenceReference(
                artifactId, 1, "SITE", new FileFactVersion(1, 1, 1), 1L))),
                new PreparationItemApplicationService.Actor(0L, assigneeId, "PRE02-FILL-" + created.id()));

        assertEquals(assigneeId, jdbcTemplate.queryForObject("SELECT assignee_user_id "
                + "FROM sol_preparation_item WHERE tenant_id=0 AND id=?", Long.class, itemId));
        assertEquals("READY", jdbcTemplate.queryForObject("SELECT site_result_code "
                + "FROM sol_preparation_item WHERE tenant_id=0 AND id=?", String.class, itemId));
        assertEquals("供电稳定", jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(value_snapshot,"
                + "'$.siteCondition')) FROM sol_dynamic_form_instance WHERE tenant_id=0 AND item_id=?",
                String.class, itemId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT JSON_EXTRACT(evidence_reference_snapshot,"
                + "'$[0].fileFactVersion.referenceVersion') FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND id=?", Integer.class, itemId));
        assertEquals(2, filled.getItemVersion());
        assertEquals(2L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='PREPARATION_ITEM_PATCH' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(itemId)));
    }

    @Test
    @Transactional
    void publicPreparationChainSubmitsConfirmsAndEvaluatesReady() {
        publishPreparationTemplate();
        var manager = new ProjectManualCreationApplicationService.Actor(
                0L, 9_900_001L, "PRE02-CHAIN-" + UUID.randomUUID());
        var created = projectCreationService.create(command(), manager);
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, created.id());
        long assigneeId = 9_900_002L;
        List<String> requiredItems = List.of("POWER", "NETWORK_PORT", "CABINET", "NETWORK_CABLE");
        for (String itemCode : requiredItems) {
            patchItem(preparationId, itemCode, created.version(), manager.actorId(),
                    Set.of("assignee"), null, assigneeId, null, null, null, null);
        }
        patchItem(preparationId, "FIBER", created.version(), manager.actorId(),
                Set.of("applicabilityCode", "notApplicableReason"), "NOT_APPLICABLE_PENDING", null,
                "现场无光纤需求", null, null, null);

        login(assigneeId);
        for (String itemCode : requiredItems) {
            List<PatchPreparationItemCommand.EvidenceReference> evidence = null;
            if (Set.of("POWER", "CABINET").contains(itemCode)) {
                long artifactId = IdWorker.getId();
                Long itemId = currentItem(preparationId, itemCode).get("id") instanceof Number id
                        ? id.longValue() : null;
                insertFileFacts(itemId, artifactId, assigneeId);
                evidence = List.of(new PatchPreparationItemCommand.EvidenceReference(
                        artifactId, 1, "SITE", new FileFactVersion(1, 1, 1), 1L));
            }
            Set<String> fields = evidence == null
                    ? Set.of("siteResultCode", "siteResultDetail", "formValueSnapshot")
                    : Set.of("siteResultCode", "siteResultDetail", "formValueSnapshot", "evidenceReferences");
            patchItem(preparationId, itemCode, created.version(), assigneeId, fields, null, null,
                    null, "READY", "现场条件满足", evidence);
        }

        login(manager.actorId());
        int preparationVersion = currentPreparationVersion(preparationId);
        String submitKey = "PRE02-CHAIN-SUBMIT-" + created.id();
        var submitCommand = new PreparationReviewCommand(PreparationReviewCommand.SUBMIT,
                preparationId, null, preparationVersion, null, created.version(), null,
                submitKey);
        var submitted = reviewService.execute(submitCommand,
                reviewActor(manager.actorId(), "SUBMIT", created.id()));
        var submitReplay = reviewService.execute(submitCommand,
                reviewActor(manager.actorId(), "SUBMIT-REPLAY", created.id()));
        assertEquals(submitted, submitReplay);
        preparationVersion = submitted.preparationVersion();
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT id,item_code,version "
                + "FROM sol_preparation_item WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id",
                preparationId);
        for (Map<String, Object> item : items) {
            String itemCode = String.valueOf(item.get("item_code"));
            String action = "FIBER".equals(itemCode) ? PreparationReviewCommand.CONFIRM_NOT_APPLICABLE
                    : PreparationReviewCommand.CONFIRM;
            String reason = "FIBER".equals(itemCode) ? "确认无光纤需求" : null;
            preparationVersion = reviewService.execute(new PreparationReviewCommand(action, preparationId,
                    ((Number) item.get("id")).longValue(), preparationVersion,
                    ((Number) item.get("version")).intValue(), created.version(), reason,
                    "PRE02-CHAIN-CONFIRM-" + itemCode + "-" + created.id()),
                    reviewActor(manager.actorId(), "CONFIRM-" + itemCode, created.id())).preparationVersion();
        }
        var evaluated = readinessService.evaluate(new PreparationReadinessCommand(preparationId,
                preparationVersion, created.version(), "PRE02-CHAIN-EVALUATE-" + created.id()),
                reviewActor(manager.actorId(), "EVALUATE", created.id()));

        assertEquals("CONFIRMED", evaluated.readiness().status());
        assertEquals("READY", evaluated.readiness().readinessStatus(),
                () -> "blockers=" + evaluated.readiness().blockerCodes());
        assertEquals(true, evaluated.readiness().snapshotCurrent());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=? AND result_code='READY'", Long.class, preparationId));
        assertEquals(5L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? AND confirmation_status_code='CONFIRMED'",
                Long.class, preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_key=? AND operation_code='PREPARATION_EVALUATE_READINESS' "
                + "AND result_code='SUCCESS'", Long.class, String.valueOf(preparationId)));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='PREPARATION_REVIEW_SUBMIT' AND actor_id=? "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class, manager.actorId(), submitKey));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_key=? AND operation_code='PREPARATION_SUBMIT' "
                + "AND result_code='SUCCESS'", Long.class, String.valueOf(preparationId)));

        int finalVersion = evaluated.readiness().preparationVersion();
        assertThrows(ServiceException.class, () -> reviewService.execute(new PreparationReviewCommand(
                PreparationReviewCommand.SUBMIT, preparationId, null, submitCommand.expectedPreparationVersion(),
                null, created.version(), "不同载荷", submitKey),
                reviewActor(manager.actorId(), "SUBMIT-CONFLICT", created.id())));
        assertEquals(finalVersion, currentPreparationVersion(preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=?", Long.class, preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='PREPARATION_REVIEW_SUBMIT' AND actor_id=? "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class, manager.actorId(), submitKey));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_key=? AND operation_code='PREPARATION_SUBMIT' "
                + "AND result_code='SUCCESS'", Long.class, String.valueOf(preparationId)));
    }

    private void publishPreparationTemplate() {
        Long seedTemplateId = jdbcTemplate.queryForObject("SELECT r.template_id "
                + "FROM proj_project_template_task_definition d "
                + "JOIN proj_project_template_revision r ON r.tenant_id=d.tenant_id "
                + "AND r.id=d.template_revision_id AND r.deleted=0 "
                + "WHERE d.tenant_id=0 AND d.deleted=0 AND r.revision_no=0 "
                + "AND d.target_context_code='SOL' AND d.target_object_type='SITE_SURVEY_PREPARATION' "
                + "AND d.target_object_key='PRE_02_SITE_SURVEY' LIMIT 1", Long.class);
        TemplateDefinitionContent content = projectTemplateService.getDraftContent(seedTemplateId);
        content.setSigningMethod("DIRECT_SIGN");
        content.setProjectCategory("ENGINEERING");
        content.setImplementationMethod("DIRECT_SERVICE");
        content.setMajorProjectLevel(null);

        ProjectTemplateDO template = new ProjectTemplateDO();
        template.setCode("IT-PRE02-" + UUID.randomUUID());
        template.setName("PRE-02项目创建集成测试模板");
        template.setMatchPriority(1);
        template.setDescription("仅用于事务内验证公开发布、匹配及项目创建自动初始化");
        Long templateId = projectTemplateService.createProjectTemplate(template);
        projectTemplateService.updateProjectTemplateDraftContent(templateId, content);
        projectTemplateService.publishProjectTemplate(templateId);
    }

    private ManualProjectCreateCommand command() {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setProjectName("IT-FSOL002-" + UUID.randomUUID());
        draft.setCustomerCode("IT-CUSTOMER");
        draft.setCustomerName("工勘准备集成测试客户");
        draft.setCreationReason("F-SOL-002公开项目创建自动初始化验证");
        draft.setSigningMethod("DIRECT_SIGN");
        draft.setProjectCategory("ENGINEERING");
        draft.setImplementationMode("DIRECT_SERVICE");
        draft.setImplementationLocation("工勘准备集成测试地点");
        TemplateMatchResult match = projectTemplateService.matchPreview(
                draft.getSigningMethod(), draft.getProjectCategory(), draft.getImplementationMode(),
                draft.getMajorProjectLevel());
        if (match.getOutcome() != TemplateMatchResult.Outcome.MATCHED || match.getMatched() == null) {
            throw new IllegalStateException("公开发布的PRE-02模板必须唯一匹配：" + match.getOutcome());
        }
        String key = "it-fsol002-project-" + UUID.randomUUID();
        return new ManualProjectCreateCommand(draft, 1L, 1L, java.util.List.of(),
                match.getMatched().getTemplateRevisionId(), match.getCandidateWatermark(),
                key, sha256(key));
    }

    private void insertFileFacts(Long itemId, long artifactId, long actorId) {
        jdbcTemplate.update("INSERT INTO plt_file_artifact "
                        + "(id,name,category_code,owner_context,lifecycle_status_code,version,tenant_id) "
                        + "VALUES (?,'site.pdf','SITE_SURVEY_EVIDENCE','SOL','ACTIVE',1,0)", artifactId);
        jdbcTemplate.update("INSERT INTO plt_file_version "
                        + "(id,artifact_id,version_no,infra_file_id,availability_version,sha256,size_bytes,"
                        + "declared_media_type,detected_media_type,scan_status_code,availability_status_code,"
                        + "created_by,created_at,tenant_id) VALUES (?,?,1,?,1,?,100,'application/pdf',"
                        + "'application/pdf','PASSED','AVAILABLE',?,NOW(3),0)",
                IdWorker.getId(), artifactId, IdWorker.getId(), "a".repeat(64), actorId);
        jdbcTemplate.update("INSERT INTO plt_file_reference "
                        + "(id,owner_context,object_type,object_id,purpose_code,reference_key,artifact_id,"
                        + "file_version_no,sensitivity_code,status_code,scope_version,version,tenant_id) "
                        + "VALUES (?,'SOL','SITE_SURVEY_ITEM',?,'SITE_SURVEY_EVIDENCE','SITE',?,1,"
                        + "'INTERNAL','ACTIVE',1,1,0)", IdWorker.getId(), String.valueOf(itemId), artifactId);
    }

    private void patchItem(Long preparationId, String itemCode, Integer projectVersion, long actorId,
            Set<String> fields, String applicabilityCode, Long assigneeId, String notApplicableReason,
            String siteResultCode, String siteResultDetail,
            List<PatchPreparationItemCommand.EvidenceReference> evidence) {
        Map<String, Object> row = currentItem(preparationId, itemCode);
        itemService.patch(new PatchPreparationItemCommand(preparationId, ((Number) row.get("id")).longValue(),
                ((Number) row.get("item_version")).intValue(), ((Number) row.get("preparation_version")).intValue(),
                ((Number) row.get("input_version")).intValue(), ((Number) row.get("readiness_version")).intValue(),
                ((Number) row.get("form_version")).intValue(), projectVersion, fields, applicabilityCode, null,
                assigneeId, notApplicableReason, siteResultCode, siteResultDetail,
                fields.contains("formValueSnapshot") ? "{\"siteCondition\":\"现场条件满足\"}" : null, evidence),
                reviewActor(actorId, "PATCH-" + itemCode, preparationId));
    }

    private Map<String, Object> currentItem(Long preparationId, String itemCode) {
        return jdbcTemplate.queryForMap("SELECT i.id,i.version item_version,p.version preparation_version,"
                + "p.input_version,p.readiness_version,f.version form_version FROM sol_preparation_item i "
                + "JOIN sol_preparation p ON p.tenant_id=i.tenant_id AND p.id=i.preparation_id "
                + "JOIN sol_dynamic_form_instance f ON f.tenant_id=i.tenant_id AND f.preparation_id=i.preparation_id "
                + "AND f.item_id=i.id WHERE i.tenant_id=0 AND i.preparation_id=? AND i.item_code=?",
                preparationId, itemCode);
    }

    private int currentPreparationVersion(Long preparationId) {
        return jdbcTemplate.queryForObject("SELECT version FROM sol_preparation WHERE tenant_id=0 AND id=?",
                Integer.class, preparationId);
    }

    private PreparationItemApplicationService.Actor reviewActor(long actorId, String action, long aggregateId) {
        return new PreparationItemApplicationService.Actor(0L, actorId,
                "PRE02-CHAIN-" + action + "-" + aggregateId);
    }

    private void login(long userId) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(userId).setUserType(2),
                new MockHttpServletRequest());
    }

    private long count(String table, String column, long value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id=0 AND " + column + "=?", Long.class, value);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql",
            "cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox",
            "cn.iocoder.yudao.module.infra.dal.mysql.config"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            ProjectManualCreationApplicationService.class, ProjectManualCreationServiceImpl.class,
            ProjectTemplateServiceImpl.class, ProjectAttributeResolutionService.class,
            ProjectTemplateMatchHistoryService.class, ProjectAttributeClassificationApplicationService.class,
            ProjectAttributeSourceCorrectionService.class, ProjectTemplateMatchHistoryQueryService.class,
            ProjectTreeProjectionService.class, ProjectCodeAllocator.class,
            TaskExecutionContractFactory.class, ProjectDeliverableInitializationApplicationServiceImpl.class,
            ProjectWorkBindingFactApiImpl.class, PreparationInitializationApiImpl.class,
            PreparationInitializationService.class, PreparationItemApplicationService.class,
            PreparationReviewService.class, PreparationReadinessService.class,
            PreparationFilePolicyProvider.class, FileBusinessObjectPolicyRegistry.class,
            FileArtifactApiImpl.class, FixedSurveyFormCatalogProvider.class,
            ConfigApiImpl.class, ConfigServiceImpl.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class})
    static class TestApplication {

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean AdminUserApi adminUserApi() { return mock(AdminUserApi.class); }

        @Bean DictDataApi dictDataApi() {
            DictDataApi api = mock(DictDataApi.class);
            List<DictDataRespDTO> values = List.of("POWER", "NETWORK_PORT", "FIBER", "CABINET",
                    "NETWORK_CABLE", "OPTICAL_MODULE").stream().map(value -> {
                        DictDataRespDTO item = new DictDataRespDTO();
                        item.setLabel(value);
                        item.setValue(value);
                        item.setDictType(PreparationWorkBindingSchema.ITEM_CODE_DICT_TYPE);
                        item.setStatus(CommonStatusEnum.ENABLE.getStatus());
                        return item;
                    }).toList();
            when(api.getDictDataList(PreparationWorkBindingSchema.ITEM_CODE_DICT_TYPE)).thenReturn(values);
            return api;
        }

        @Bean DeptApi deptApi() {
            DeptApi api = mock(DeptApi.class);
            DeptRespDTO department = new DeptRespDTO();
            department.setId(1L); department.setCode("IT-DEPT"); department.setName("集成测试办事处");
            when(api.getDept(1L)).thenReturn(department);
            return api;
        }

        @Bean CompanyApi companyApi() {
            CompanyApi api = mock(CompanyApi.class);
            CompanyRespDTO company = new CompanyRespDTO();
            company.setId(1L); company.setCode("IT-COMPANY"); company.setName("集成测试公司");
            when(api.getCompany(1L)).thenReturn(company);
            return api;
        }

        @Bean OrganizationScopeApi organizationScopeApi() {
            OrganizationScopeApi api = mock(OrganizationScopeApi.class);
            when(api.hasScope(anyLong(), anyLong(), anyLong())).thenReturn(true);
            return api;
        }

        @Bean ProjectCreationAuthorizationService authorizationService() {
            return mock(ProjectCreationAuthorizationService.class);
        }

        @Bean ProjectTreeScopeService projectTreeScopeService() { return mock(ProjectTreeScopeService.class); }

        @Bean ProjectTreeMetrics projectTreeMetrics() { return mock(ProjectTreeMetrics.class); }

        @Bean ProjectParticipantFactApi participantFactApi() {
            ProjectParticipantFactApi api = mock(ProjectParticipantFactApi.class);
            when(api.lockAndRevalidate(any())).thenAnswer(invocation -> {
                var query = (cn.iocoder.yudao.module.pms.project.api.participant.dto
                        .ProjectParticipantFactRevalidationQuery) invocation.getArgument(0);
                return new ProjectParticipantFact(query.projectId(), query.userId(),
                        java.util.Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY",
                        "ACTIVE", "S0", query.expectedProjectVersion(), query.expectedProjectVersion().longValue());
            });
            return api;
        }

        @Bean ProjectScopeApi projectScopeApi() {
            ProjectScopeApi api = mock(ProjectScopeApi.class);
            when(api.resolveCurrent(any())).thenAnswer(invocation -> {
                var query = (cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery)
                        invocation.getArgument(0);
                return new ProjectScopeResult(query.anchorProjectId(), 1L,
                        java.util.Set.of(query.anchorProjectId()), java.util.Set.of());
            });
            when(api.lockAndRevalidate(any())).thenAnswer(invocation -> {
                var query = (cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery)
                        invocation.getArgument(0);
                return new ProjectScopeResult(query.anchorProjectId(), query.expectedScopeVersion(),
                        java.util.Set.of(query.anchorProjectId()), java.util.Set.of());
            });
            return api;
        }

        @Bean ProjectOrganizationFactApi organizationFactApi() {
            ProjectOrganizationFactApi api = mock(ProjectOrganizationFactApi.class);
            when(api.lockAndRevalidate(any())).thenAnswer(invocation -> {
                var query = (cn.iocoder.yudao.module.pms.project.api.organization.dto
                        .ProjectOrganizationFactRevalidationQuery) invocation.getArgument(0);
                return new ProjectOrganizationFact(query.projectId(), query.expectedProjectVersion(),
                        1L, 1L, "IT-DEPT");
            });
            return api;
        }

        @Bean PreparationSourceProviderRegistry sourceProviderRegistry() {
            return new PreparationSourceProviderRegistry(List.of());
        }

        @Bean PermissionCommonApi permissionCommonApi() {
            PermissionCommonApi api = mock(PermissionCommonApi.class);
            when(api.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
            return api;
        }

        @Bean PermissionApi permissionApi() {
            PermissionApi api = mock(PermissionApi.class);
            when(api.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
            return api;
        }

        @Bean TrustedProjectServicePrincipalRegistry trustedProjectServicePrincipalRegistry() {
            TrustedProjectServicePrincipalRegistry registry = mock(TrustedProjectServicePrincipalRegistry.class);
            when(registry.resolve("int-crm-sync")).thenReturn(9_900_002L);
            return registry;
        }

        @Bean ProjectSiteApplicationService projectSiteApplicationService() {
            ProjectSiteApplicationService service = mock(ProjectSiteApplicationService.class);
            when(service.validateLocationScope(any(), any()))
                    .thenReturn(ProjectSiteApplicationService.LOCATION_UNRESOLVED);
            return service;
        }

        @Bean AssetLocationApi assetLocationApi() { return mock(AssetLocationApi.class); }

        @Bean TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
