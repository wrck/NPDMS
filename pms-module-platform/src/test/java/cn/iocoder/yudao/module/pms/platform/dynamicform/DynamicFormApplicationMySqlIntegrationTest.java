package cn.iocoder.yudao.module.pms.platform.dynamicform;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormActionProjection;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommandService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommands;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormFilePolicyProvider;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormQueryService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;
import cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_DRAFT_ALREADY_EXISTS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_REVISION_NOT_DRAFT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_TEMPLATE_CODE_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DynamicFormApplicationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DynamicFormApplicationMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long ACTOR_ID = 9_920_002L;
    private static final long OTHER_ACTOR_ID = 9_920_003L;
    private static final AtomicLong RUN_SEQUENCE = new AtomicLong(8_860_000_000L);

    @Resource DynamicFormCommandService commandService;
    @Resource DynamicFormQueryService queryService;
    @Resource DynamicFormTemplateMapper templateMapper;
    @Resource DynamicFormTemplateRevisionMapper revisionMapper;
    @Resource PlatformDynamicFormInstanceMapper instanceMapper;
    @Resource FileArtifactMapper artifactMapper;
    @Resource FileVersionMapper versionMapper;
    @Resource FileReferenceMapper referenceMapper;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource PermissionApi permissionApi;
    @MockitoSpyBean FileArtifactApiImpl fileArtifactApi;

    private final List<Long> artifactIds = new ArrayList<>();
    private String runId;
    private String keyPrefix;

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
        runId = String.valueOf(RUN_SEQUENCE.incrementAndGet());
        keyPrefix = "fplt002-it-" + runId + "-";
        TenantContextHolder.setTenantId(TENANT_ID);
        login(ACTOR_ID);
        reset(permissionApi);
        when(permissionApi.hasAnyPermissions(anyLong(), anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        List<Long> instanceIds = jdbcTemplate.queryForList(
                "SELECT i.id FROM plt_dynamic_form_instance i "
                        + "JOIN plt_dynamic_form_template t ON t.tenant_id=i.tenant_id AND t.id=i.template_id "
                        + "WHERE t.tenant_id=? AND t.template_code LIKE ?",
                Long.class, TENANT_ID, codePrefix() + "%");
        for (Long instanceId : instanceIds) {
            jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=? AND owner_context='PLATFORM' "
                            + "AND object_type='DYNAMIC_FORM_INSTANCE' AND object_id=?",
                    TENANT_ID, String.valueOf(instanceId));
        }
        for (Long artifactId : artifactIds) {
            jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE tenant_id=? AND aggregate_type='FileArtifact' "
                    + "AND aggregate_key=?", TENANT_ID, String.valueOf(artifactId));
            jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=? AND artifact_id=?",
                    TENANT_ID, artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=? AND artifact_id=?",
                    TENANT_ID, artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=? AND id=?", TENANT_ID, artifactId);
        }
        artifactIds.clear();
        jdbcTemplate.update("DELETE FROM plt_dynamic_form_instance WHERE tenant_id=? AND template_id IN "
                + "(SELECT id FROM plt_dynamic_form_template WHERE tenant_id=? AND template_code LIKE ?)",
                TENANT_ID, TENANT_ID, codePrefix() + "%");
        jdbcTemplate.update("UPDATE plt_dynamic_form_template SET current_published_revision_id=NULL "
                + "WHERE tenant_id=? AND template_code LIKE ?", TENANT_ID, codePrefix() + "%");
        jdbcTemplate.update("UPDATE plt_dynamic_form_template_revision SET source_revision_id=NULL "
                        + "WHERE tenant_id=? AND template_id IN "
                        + "(SELECT id FROM plt_dynamic_form_template WHERE tenant_id=? AND template_code LIKE ?)",
                TENANT_ID, TENANT_ID, codePrefix() + "%");
        jdbcTemplate.update("DELETE FROM plt_dynamic_form_template_revision WHERE tenant_id=? AND template_id IN "
                + "(SELECT id FROM plt_dynamic_form_template WHERE tenant_id=? AND template_code LIKE ?)",
                TENANT_ID, TENANT_ID, codePrefix() + "%");
        jdbcTemplate.update("DELETE FROM plt_dynamic_form_template WHERE tenant_id=? AND template_code LIKE ?",
                TENANT_ID, codePrefix() + "%");
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=? AND actor_id IN (?,?) "
                + "AND correlation_id LIKE ?", TENANT_ID, ACTOR_ID, OTHER_ACTOR_ID, keyPrefix + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=? AND actor_id IN (?,?) "
                + "AND idempotency_key LIKE ?", TENANT_ID, ACTOR_ID, OTHER_ACTOR_ID, keyPrefix + "%");
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void completeLifecycleUsesRealApplicationTransactionsAndFrozenFileFacts() {
        ReadyTemplate ready = createReadyTemplate("LIFECYCLE");
        DynamicFormViews.Page<DynamicFormViews.Selection> choices = queryService.pageSelection(actor(), 1, 200);
        assertTrue(choices.list().stream().anyMatch(value -> value.templateId().equals(ready.templateId())));

        DynamicFormCommands.CreateInstance create = new DynamicFormCommands.CreateInstance(actor(),
                ready.revisionId(), ready.templateVersion(), "验收实例", key("instance-create"));
        DynamicFormViews.InstanceCreated instance = commandService.createInstance(create);
        assertEquals(instance, commandService.createInstance(create));
        assertEquals(1L, count("plt_dynamic_form_instance", "id", instance.instanceId()));

        commandService.patchInstance(new DynamicFormCommands.PatchInstance(actor(), instance.instanceId(), 0,
                json("{\"subject\":\"\",\"enabled\":false,\"quantity\":0,\"nullableValue\":null,"
                        + "\"tags\":[],\"nestedValue\":\"已保存\"}"), key("instance-patch")));
        attachControlledFile(instance.instanceId(), ready.revisionId(), "controlledFiles");

        clearInvocations(fileArtifactApi);
        DynamicFormViews.Instance detail = queryService.getInstance(actor(), instance.instanceId());
        verify(fileArtifactApi, times(1)).inspectReferenceSets(any());
        assertEquals(ready.revisionId(), detail.templateRevisionId());
        assertFalse(detail.values().path("enabled").booleanValue());
        assertEquals(0, detail.values().path("quantity").intValue());
        assertTrue(detail.values().get("nullableValue").isNull());
        assertEquals("", detail.values().path("subject").textValue());
        assertTrue(detail.values().path("tags").isArray());
        assertTrue(detail.controlledFiles().containsKey("controlledFiles"));
        assertEquals(1, detail.controlledFiles().get("controlledFiles").size());

        DynamicFormViews.Revision nextDraft = commandService.createRevision(new DynamicFormCommands.CreateRevision(
                actor(), ready.templateId(), ready.templateVersion(), key("revision-2-create")));
        DynamicFormViews.Revision changed = patchRevision(nextDraft.revisionId(), 0, "v2");
        DynamicFormViews.PublishResult next = commandService.publishRevision(new DynamicFormCommands.PublishRevision(
                actor(), changed.revisionId(), changed.revisionVersion(), key("revision-2-publish")));
        assertNotEquals(ready.revisionId(), next.revision().revisionId());

        DynamicFormViews.Instance frozen = queryService.getInstance(actor(), instance.instanceId());
        assertEquals(ready.revisionId(), frozen.templateRevisionId());
        assertEquals(1, frozen.templateRevisionNo());
        assertTrue(frozen.formRulesJson().toString().contains("版本v1说明"));
        assertFalse(frozen.formRulesJson().toString().contains("版本v2说明"));

        DynamicFormViews.Template disabled = commandService.setAvailability(new DynamicFormCommands.SetAvailability(
                actor(), ready.templateId(), next.templateVersion(), "DISABLED", key("disable")));
        assertFalse(queryService.pageSelection(actor(), 1, 200).list().stream()
                .anyMatch(value -> value.templateId().equals(ready.templateId())));
        commandService.setAvailability(new DynamicFormCommands.SetAvailability(actor(), ready.templateId(),
                disabled.templateVersion(), "ENABLED", key("enable-again")));
        assertTrue(queryService.pageSelection(actor(), 1, 200).list().stream()
                .anyMatch(value -> value.templateId().equals(ready.templateId())));

        String audits = String.join("\n", jdbcTemplate.queryForList("SELECT detail_snapshot "
                + "FROM plt_operation_audit WHERE tenant_id=? AND actor_id=? AND correlation_id LIKE ?",
                String.class, TENANT_ID, ACTOR_ID, keyPrefix + "%"));
        assertTrue(audits.contains("changedFieldKeys"));
        assertFalse(audits.contains("SECRET-RICH-TEXT"));
        assertFalse(audits.contains("api.internal.example"));
        assertFalse(audits.contains("已保存"));
    }

    @Test
    void idempotencyConstraintsAndRejectedCommandsLeaveNoPartialSuccessFacts() {
        String createKey = key("template-create");
        DynamicFormCommands.CreateTemplate create = new DynamicFormCommands.CreateTemplate(actor(), createKey,
                code("GUARDS"), "约束模板", "GENERAL", "约束验证");
        DynamicFormViews.Template template = commandService.createTemplate(create);
        assertEquals(template, commandService.createTemplate(create));

        ServiceException keyConflict = assertThrows(ServiceException.class, () -> commandService.createTemplate(
                new DynamicFormCommands.CreateTemplate(actor(), createKey, code("GUARDS"),
                        "不同载荷", "GENERAL", "约束验证")));
        assertEquals(PLATFORM_COMMAND_KEY_CONFLICT.getCode(), keyConflict.getCode());

        String duplicateKey = key("duplicate-code");
        ServiceException duplicate = assertThrows(ServiceException.class, () -> commandService.createTemplate(
                new DynamicFormCommands.CreateTemplate(actor(), duplicateKey, code("GUARDS"),
                        "重复编码", "GENERAL", null)));
        assertEquals(DYNAMIC_FORM_TEMPLATE_CODE_CONFLICT.getCode(), duplicate.getCode());
        assertEquals(1L, templateCount(code("GUARDS")));
        assertEquals(1L, revisionCount(template.templateId()));
        assertEquals(0L, idempotencyCount(duplicateKey));
        assertEquals(0L, successAuditCount(duplicateKey));

        String runningKey = key("running");
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("templateCode", code("RUNNING"));
        request.put("templateName", "进行中模板");
        request.put("categoryCode", "GENERAL");
        request.put("description", null);
        jdbcTemplate.update("INSERT INTO plt_idempotency_record "
                        + "(scope_code,actor_id,idempotency_key,request_digest,status,version,creator,updater,tenant_id) "
                        + "VALUES ('PLT:DYNAMIC_FORM:TEMPLATE_CREATE',?,?,?,?,0,?,?,?)",
                ACTOR_ID, runningKey, digest(request), "IN_PROGRESS", String.valueOf(ACTOR_ID),
                String.valueOf(ACTOR_ID), TENANT_ID);
        ServiceException running = assertThrows(ServiceException.class, () -> commandService.createTemplate(
                new DynamicFormCommands.CreateTemplate(actor(), runningKey, code("RUNNING"),
                        "进行中模板", "GENERAL", null)));
        assertEquals(PLATFORM_COMMAND_IN_PROGRESS.getCode(), running.getCode());
        assertEquals(0L, templateCount(code("RUNNING")));

        DynamicFormViews.Revision patched = patchRevision(template.currentDraft().revisionId(), 0, "v1");
        DynamicFormViews.PublishResult published = commandService.publishRevision(
                new DynamicFormCommands.PublishRevision(actor(), patched.revisionId(),
                        patched.revisionVersion(), key("publish")));
        DynamicFormViews.Template enabled = commandService.setAvailability(new DynamicFormCommands.SetAvailability(
                actor(), template.templateId(), published.templateVersion(), "ENABLED", key("enable")));

        ServiceException immutable = assertThrows(ServiceException.class, () -> patchRevision(
                patched.revisionId(), published.revision().revisionVersion(), "forbidden"));
        assertEquals(DYNAMIC_FORM_REVISION_NOT_DRAFT.getCode(), immutable.getCode());
        assertEquals("PUBLISHED", revision(patched.revisionId()).getStatusCode());

        commandService.createRevision(new DynamicFormCommands.CreateRevision(actor(), template.templateId(),
                enabled.templateVersion(), key("draft-create")));
        String secondDraftKey = key("draft-duplicate");
        ServiceException secondDraft = assertThrows(ServiceException.class, () -> commandService.createRevision(
                new DynamicFormCommands.CreateRevision(actor(), template.templateId(),
                        enabled.templateVersion(), secondDraftKey)));
        assertEquals(DYNAMIC_FORM_DRAFT_ALREADY_EXISTS.getCode(), secondDraft.getCode());
        assertEquals(0L, idempotencyCount(secondDraftKey));

        String driftKey = key("instance-drift");
        Long pointerBefore = template(template.templateId()).getCurrentPublishedRevisionId();
        assertThrows(ServiceException.class, () -> commandService.createInstance(new DynamicFormCommands.CreateInstance(
                actor(), patched.revisionId(), enabled.templateVersion() + 10,
                "漂移实例", driftKey)));
        assertEquals(0L, idempotencyCount(driftKey));
        assertEquals(0L, successAuditCount(driftKey));
        assertEquals(0L, instanceCount(template.templateId()));
        assertEquals(pointerBefore, template(template.templateId()).getCurrentPublishedRevisionId());
    }

    @Test
    void concurrentPublishAndInstancePatchEachHaveExactlyOneWinner() throws Exception {
        ReadyTemplate ready = createReadyTemplate("RACE");
        DynamicFormViews.Revision draft = commandService.createRevision(new DynamicFormCommands.CreateRevision(
                actor(), ready.templateId(), ready.templateVersion(), key("race-draft")));
        DynamicFormViews.Revision patched = patchRevision(draft.revisionId(), 0, "v2");

        int publishWinners = concurrentAttempts(
                () -> commandService.publishRevision(new DynamicFormCommands.PublishRevision(actor(),
                        patched.revisionId(), patched.revisionVersion(), key("publish-race-a"))),
                () -> commandService.publishRevision(new DynamicFormCommands.PublishRevision(actor(),
                        patched.revisionId(), patched.revisionVersion(), key("publish-race-b"))));
        assertEquals(1, publishWinners);
        DynamicFormTemplateDO current = template(ready.templateId());
        assertEquals(patched.revisionId(), current.getCurrentPublishedRevisionId());
        assertEquals(3, current.getVersion());
        assertEquals(1L, successOperationCount("DYNAMIC_FORM_REVISION_PUBLISH", keyPrefix + "publish-race-%"));

        DynamicFormViews.InstanceCreated instance = commandService.createInstance(new DynamicFormCommands.CreateInstance(
                actor(), patched.revisionId(), current.getVersion(), "并发实例", key("race-instance")));
        int patchWinners = concurrentAttempts(
                () -> commandService.patchInstance(new DynamicFormCommands.PatchInstance(actor(),
                        instance.instanceId(), 0, json("{\"subject\":\"A\"}"), key("patch-race-a"))),
                () -> commandService.patchInstance(new DynamicFormCommands.PatchInstance(actor(),
                        instance.instanceId(), 0, json("{\"subject\":\"B\"}"), key("patch-race-b"))));
        assertEquals(1, patchWinners);
        PlatformDynamicFormInstanceDO stored = instance(instance.instanceId());
        assertEquals(1, stored.getVersion());
        assertTrue(stored.getValueJson().contains("\"A\"") || stored.getValueJson().contains("\"B\""));
        assertEquals(1L, successOperationCount("DYNAMIC_FORM_INSTANCE_PATCH", keyPrefix + "patch-race-%"));
    }

    @Test
    void tenantPermissionAndCreatorBoundariesFailClosed() {
        ReadyTemplate ready = createReadyTemplate("BOUNDARY");
        DynamicFormViews.InstanceCreated instance = commandService.createInstance(new DynamicFormCommands.CreateInstance(
                actor(), ready.revisionId(), ready.templateVersion(), "边界实例", key("boundary-instance")));

        ServiceException nonCreator = assertThrows(ServiceException.class, () -> commandService.patchInstance(
                new DynamicFormCommands.PatchInstance(actor(OTHER_ACTOR_ID), instance.instanceId(), 0,
                        json("{\"subject\":\"越权\"}"), key("non-creator"))));
        assertNotNull(nonCreator);
        assertEquals(0, instance(instance.instanceId()).getVersion());

        assertThrows(ServiceException.class, () -> queryService.getInstance(
                new DynamicFormCommands.Actor(991L, ACTOR_ID), instance.instanceId()));
        when(permissionApi.hasAnyPermissions(ACTOR_ID, DynamicFormActionProjection.INSTANCE_QUERY)).thenReturn(false);
        assertThrows(ServiceException.class, () -> queryService.getInstance(actor(), instance.instanceId()));
        assertEquals(0, instance(instance.instanceId()).getVersion());
    }

    private ReadyTemplate createReadyTemplate(String suffix) {
        DynamicFormViews.Template created = commandService.createTemplate(new DynamicFormCommands.CreateTemplate(
                actor(), key(suffix + "-create"), code(suffix), "动态模板" + suffix, "GENERAL", "完整闭环"));
        DynamicFormViews.Revision draft = patchRevision(created.currentDraft().revisionId(), 0, "v1");
        DynamicFormViews.PublishResult published = commandService.publishRevision(
                new DynamicFormCommands.PublishRevision(actor(), draft.revisionId(), draft.revisionVersion(),
                        key(suffix + "-publish")));
        DynamicFormViews.Template enabled = commandService.setAvailability(new DynamicFormCommands.SetAvailability(
                actor(), created.templateId(), published.templateVersion(), "ENABLED", key(suffix + "-enable")));
        return new ReadyTemplate(created.templateId(), draft.revisionId(), enabled.templateVersion());
    }

    private DynamicFormViews.Revision patchRevision(Long revisionId, int expectedVersion, String label) {
        return commandService.patchRevision(new DynamicFormCommands.PatchRevision(actor(), revisionId,
                expectedVersion, schemaConf(), schemaRules(label), DynamicFormSchemaService.ENGINE_CODE,
                DynamicFormSchemaService.DESIGNER_VERSION, DynamicFormSchemaService.RENDERER_VERSION,
                key("revision-patch-" + label)));
    }

    private JsonNode schemaConf() {
        return json("{\"form\":{\"labelWidth\":\"120px\"},\"submitBtn\":false,"
                + "\"api\":{\"url\":\"https://api.internal.example/forms\"},"
                + "\"on\":{\"mounted\":\"function(){return 'SECRET-CONFIG';}\"}}");
    }

    private JsonNode schemaRules(String label) {
        return json("[{\"type\":\"input\",\"field\":\"subject\",\"title\":\"主题\"},"
                + "{\"type\":\"Editor\",\"field\":\"description\",\"title\":\"版本" + label + "说明\","
                + "\"value\":\"SECRET-RICH-TEXT\"},"
                + "{\"type\":\"switch\",\"field\":\"enabled\",\"title\":\"启用\"},"
                + "{\"type\":\"InputNumber\",\"field\":\"quantity\",\"title\":\"数量\"},"
                + "{\"type\":\"input\",\"field\":\"nullableValue\",\"title\":\"可空值\"},"
                + "{\"type\":\"select\",\"field\":\"tags\",\"title\":\"标签\"},"
                + "{\"type\":\"row\",\"field\":\"\",\"children\":[{\"type\":\"input\","
                + "\"field\":\"nestedValue\",\"title\":\"嵌套值\"}]},"
                + "{\"type\":\"iframe\",\"props\":{\"src\":\"https://example.invalid/frame\"}},"
                + "{\"type\":\"PmsFileArtifact\",\"field\":\"controlledFiles\",\"title\":\"受控附件\"}]");
    }

    private void attachControlledFile(Long instanceId, Long scopeVersion, String fieldKey) {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setName("dynamic-form-" + runId + ".pdf");
        artifact.setCategoryCode("DYNAMIC_FORM_ATTACHMENT");
        artifact.setOwnerContext("PLATFORM");
        artifact.setLifecycleStatusCode("ACTIVE");
        artifact.setVersion(0);
        artifact.setCreator(String.valueOf(ACTOR_ID));
        artifact.setUpdater(String.valueOf(ACTOR_ID));
        artifact.setCreateTime(now);
        artifact.setUpdateTime(now);
        artifact.setTenantId(TENANT_ID);
        assertEquals(1, artifactMapper.insert(artifact));
        artifactIds.add(artifact.getId());

        FileVersionDO version = new FileVersionDO();
        version.setTenantId(TENANT_ID);
        version.setArtifactId(artifact.getId());
        version.setVersionNo(1);
        version.setInfraFileId(artifact.getId() + 6_000_000L);
        version.setAvailabilityVersion(0);
        version.setSha256("b".repeat(64));
        version.setSizeBytes(256L);
        version.setDeclaredMediaType("application/pdf");
        version.setDetectedMediaType("application/pdf");
        version.setScanStatusCode("SKIPPED");
        version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(ACTOR_ID);
        version.setCreatedAt(now);
        assertEquals(1, versionMapper.insert(version));

        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(TENANT_ID);
        reference.setOwnerContext("PLATFORM");
        reference.setObjectType("DYNAMIC_FORM_INSTANCE");
        reference.setObjectId(String.valueOf(instanceId));
        reference.setPurposeCode(DynamicFormSchemaService.FILE_PURPOSE_PREFIX + fieldKey);
        reference.setReferenceKey(UUID.randomUUID().toString());
        reference.setArtifactId(artifact.getId());
        reference.setFileVersionNo(1);
        reference.setSensitivityCode("INTERNAL");
        reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(scopeVersion);
        reference.setVersion(0);
        reference.setCreator(String.valueOf(ACTOR_ID));
        reference.setUpdater(String.valueOf(ACTOR_ID));
        reference.setCreateTime(now);
        reference.setUpdateTime(now);
        assertEquals(1, referenceMapper.insert(reference));
    }

    private int concurrentAttempts(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> left = pool.submit(() -> runAfter(ready, start, first));
            Future<Boolean> right = pool.submit(() -> runAfter(ready, start, second));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return (left.get(20, TimeUnit.SECONDS) ? 1 : 0) + (right.get(20, TimeUnit.SECONDS) ? 1 : 0);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean runAfter(CountDownLatch ready, CountDownLatch start, Runnable action) throws InterruptedException {
        TenantContextHolder.setTenantId(TENANT_ID);
        login(ACTOR_ID);
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            action.run();
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private DynamicFormTemplateDO template(Long id) {
        return templateMapper.selectByRow(new DynamicFormTemplateRowQuery(TENANT_ID, id));
    }

    private DynamicFormTemplateRevisionDO revision(Long id) {
        return revisionMapper.selectByRow(new DynamicFormRevisionRowQuery(TENANT_ID, id));
    }

    private PlatformDynamicFormInstanceDO instance(Long id) {
        return instanceMapper.selectByRow(new DynamicFormInstanceRowQuery(TENANT_ID, id));
    }

    private long templateCount(String code) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_dynamic_form_template "
                + "WHERE tenant_id=? AND template_code=?", Long.class, TENANT_ID, code);
    }

    private long revisionCount(Long templateId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_dynamic_form_template_revision "
                + "WHERE tenant_id=? AND template_id=?", Long.class, TENANT_ID, templateId);
    }

    private long instanceCount(Long templateId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_dynamic_form_instance "
                + "WHERE tenant_id=? AND template_id=?", Long.class, TENANT_ID, templateId);
    }

    private long count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND "
                + column + "=?", Long.class, TENANT_ID, value);
    }

    private long idempotencyCount(String key) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                + "AND actor_id=? AND idempotency_key=?", Long.class, TENANT_ID, ACTOR_ID, key);
    }

    private long successAuditCount(String key) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND actor_id=? AND correlation_id=? AND result_code='SUCCESS'",
                Long.class, TENANT_ID, ACTOR_ID, key);
    }

    private long successOperationCount(String operation, String correlationLike) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND actor_id=? AND operation_code=? AND result_code='SUCCESS' AND correlation_id LIKE ?",
                Long.class, TENANT_ID, ACTOR_ID, operation, correlationLike);
    }

    private DynamicFormCommands.Actor actor() {
        return actor(ACTOR_ID);
    }

    private DynamicFormCommands.Actor actor(long actorId) {
        return new DynamicFormCommands.Actor(TENANT_ID, actorId);
    }

    private String code(String suffix) {
        return codePrefix() + suffix;
    }

    private String codePrefix() {
        return "FPLT002_IT_" + runId + "_";
    }

    private String key(String suffix) {
        return keyPrefix + suffix;
    }

    private JsonNode json(String value) {
        return JsonUtils.parseTree(value);
    }

    private String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void login(long actorId) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(actorId).setUserType(2),
                new MockHttpServletRequest());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing environment variable: " + name);
        return value;
    }

    private record ReadyTemplate(Long templateId, Long revisionId, Integer templateVersion) {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            DynamicFormCommandService.class, DynamicFormQueryService.class, DynamicFormSchemaService.class,
            DynamicFormActionProjection.class, DynamicFormFilePolicyProvider.class,
            FileBusinessObjectPolicyRegistry.class, ExistingFileVersionAttachmentService.class,
            FileArtifactApiImpl.class, FileEventFactory.class, PlatformTransactionalOutboxWriter.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class})
    static class TestApplication {

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean PermissionApi permissionApi() {
            return mock(PermissionApi.class);
        }

        @Bean TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
