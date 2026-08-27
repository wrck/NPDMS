package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormDraftCreateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceValueUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionPublishUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplateVersionUpdate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_CURRENT_REVISION_CHANGED;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_DRAFT_ALREADY_EXISTS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_FILE_FIELD_REQUIRES_FILE_API;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_INSTANCE_FIELD_UNKNOWN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_INSTANCE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_REVISION_NOT_DRAFT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_SCHEMA_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_TEMPLATE_CODE_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_TEMPLATE_DISABLED;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_TEMPLATE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;

@Service
@RequiredArgsConstructor
public class DynamicFormCommandService {

    private static final String TEMPLATE_AGGREGATE = "DynamicFormTemplate";
    private static final String REVISION_AGGREGATE = "DynamicFormTemplateRevision";
    private static final String INSTANCE_AGGREGATE = "DynamicFormInstance";

    private final DynamicFormTemplateMapper templateMapper;
    private final DynamicFormTemplateRevisionMapper revisionMapper;
    private final PlatformDynamicFormInstanceMapper instanceMapper;
    private final DynamicFormSchemaService schemaService;
    private final DynamicFormActionProjection actionProjection;
    private final DynamicFormQueryService queryService;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public DynamicFormViews.Template createTemplate(DynamicFormCommands.CreateTemplate command) {
        String operation = "DYNAMIC_FORM_TEMPLATE_CREATE";
        validateActor(command == null ? null : command.actor());
        validateKey(command.idempotencyKey());
        String code = required(command.templateCode(), 64);
        String name = required(command.templateName(), 128);
        String category = required(command.categoryCode(), 64);
        String description = optional(command.description(), 512);
        actionProjection.require(command.actor().userId(), DynamicFormActionProjection.TEMPLATE_MANAGE);
        Map<String, Object> digest = linked("templateCode", code, "templateName", name,
                "categoryCode", category, "description", description);
        return idempotent(command.actor(), "PLT:DYNAMIC_FORM:TEMPLATE_CREATE", command.idempotencyKey(), digest,
                DynamicFormViews.Template.class, operation, TEMPLATE_AGGREGATE, "NEW",
                () -> createTemplateTx(command.actor(), code, name, category, description),
                value -> linked("operationId", command.idempotencyKey(), "actorId", command.actor().userId(),
                        "templateId", value.templateId(), "availabilityBefore", null,
                        "availabilityAfter", value.availability(), "templateVersionBefore", null,
                        "templateVersionAfter", value.templateVersion(), "draftRevisionId",
                        value.currentDraft() == null ? null : value.currentDraft().revisionId()));
    }

    public DynamicFormViews.Template patchTemplate(DynamicFormCommands.PatchTemplate command) {
        String operation = "DYNAMIC_FORM_TEMPLATE_PATCH";
        validatePatchTemplate(command);
        try {
            return transactionTemplate.execute(status -> {
                DynamicFormTemplateDO row = lockTemplate(command.actor(), command.templateId(),
                        DynamicFormActionProjection.TEMPLATE_MANAGE);
                requireVersion(row.getVersion(), command.expectedVersion());
                String name = command.templateName().present()
                        ? required(command.templateName().value(), 128) : row.getTemplateName();
                String category = command.categoryCode().present()
                        ? required(command.categoryCode().value(), 64) : row.getCategoryCode();
                String description = command.description().present()
                        ? optional(command.description().value(), 512) : row.getDescription();
                if (templateMapper.updateIfMatch(new DynamicFormTemplateVersionUpdate(command.actor().tenantId(),
                        row.getId(), command.expectedVersion(), command.templateName().present(), name,
                        command.categoryCode().present(), category, command.description().present(), description,
                        false, null, false, null, actorText(command.actor()))) != 1) {
                    throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
                }
                auditSuccess(command.actor(), command.correlationId(), operation, TEMPLATE_AGGREGATE,
                        row.getId(), linked("operationId", command.correlationId(), "actorId", command.actor().userId(),
                                "templateId", row.getId(), "templateVersionBefore", row.getVersion(),
                                "templateVersionAfter", row.getVersion() + 1,
                                "changedFieldKeys", changedTemplateFields(command)));
                return queryService.toTemplate(command.actor().userId(), requireTemplate(command.actor().tenantId(),
                        row.getId()));
            });
        } catch (RuntimeException failure) {
            auditRejected(command.actor(), command.correlationId(), operation, TEMPLATE_AGGREGATE,
                    command.templateId(), failure);
            throw failure;
        }
    }

    public DynamicFormViews.Revision createRevision(DynamicFormCommands.CreateRevision command) {
        String operation = "DYNAMIC_FORM_REVISION_CREATE";
        validateActor(command == null ? null : command.actor());
        validateId(command.templateId());
        validateVersion(command.expectedTemplateVersion());
        validateKey(command.idempotencyKey());
        Map<String, Object> digest = linked("templateId", command.templateId(),
                "expectedTemplateVersion", command.expectedTemplateVersion());
        return idempotent(command.actor(), "PLT:DYNAMIC_FORM:REVISION_CREATE", command.idempotencyKey(), digest,
                DynamicFormViews.Revision.class, operation, REVISION_AGGREGATE,
                "NEW", () -> createRevisionTx(command),
                value -> linked("operationId", command.idempotencyKey(), "actorId", command.actor().userId(),
                        "templateId", command.templateId(), "revisionId", value.revisionId(),
                        "sourceRevisionId", value.sourceRevisionId(), "statusBefore", null,
                        "statusAfter", value.status(), "revisionVersionBefore", null,
                        "revisionVersionAfter", value.revisionVersion()));
    }

    public DynamicFormViews.Revision patchRevision(DynamicFormCommands.PatchRevision command) {
        String operation = "DYNAMIC_FORM_REVISION_PATCH";
        validatePatchRevision(command);
        String conf = JsonUtils.toJsonString(command.formConfJson());
        String rules = JsonUtils.toJsonString(command.formRulesJson());
        DynamicFormSchemaService.SchemaFields fields = schemaService.parseAndValidate(conf, rules,
                command.engineCode(), command.designerVersion(), command.rendererVersion());
        try {
            return transactionTemplate.execute(status -> {
                DynamicFormTemplateRevisionDO inspected = requireRevision(command.actor().tenantId(),
                        command.revisionId());
                lockTemplate(command.actor(), inspected.getTemplateId(), DynamicFormActionProjection.TEMPLATE_MANAGE);
                DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(
                        new DynamicFormRevisionLockQuery(command.actor().tenantId(), inspected.getTemplateId(),
                                command.revisionId()));
                requireDraft(revision, command.expectedVersion());
                revision.setFormConfJson(conf);
                revision.setFormRulesJson(rules);
                revision.setEngineCode(command.engineCode());
                revision.setDesignerVersion(command.designerVersion());
                revision.setRendererVersion(command.rendererVersion());
                revision.setUpdater(actorText(command.actor()));
                if (revisionMapper.updateDraftIfMatch(revision) != 1) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
                auditSuccess(command.actor(), command.correlationId(), operation, REVISION_AGGREGATE,
                        revision.getId(), linked("operationId", command.correlationId(), "actorId",
                                command.actor().userId(), "templateId", revision.getTemplateId(),
                                "revisionId", revision.getId(), "revisionVersionBefore", revision.getVersion(),
                                "revisionVersionAfter", revision.getVersion() + 1, "engineCode", command.engineCode(),
                                "designerVersion", command.designerVersion(), "rendererVersion",
                                command.rendererVersion(), "ordinaryFieldKeys", fields.ordinaryFieldKeys(),
                                "fileFieldKeys", fields.fileFieldKeys()));
                return queryService.toRevision(command.actor().userId(), requireRevision(command.actor().tenantId(),
                        revision.getId()));
            });
        } catch (RuntimeException failure) {
            auditRejected(command.actor(), command.correlationId(), operation, REVISION_AGGREGATE,
                    command.revisionId(), failure);
            throw failure;
        }
    }

    public DynamicFormViews.PublishResult publishRevision(DynamicFormCommands.PublishRevision command) {
        String operation = "DYNAMIC_FORM_REVISION_PUBLISH";
        validateActor(command == null ? null : command.actor());
        validateId(command.revisionId());
        validateVersion(command.expectedVersion());
        validateKey(command.idempotencyKey());
        Map<String, Object> digest = linked("revisionId", command.revisionId(),
                "expectedVersion", command.expectedVersion());
        return idempotent(command.actor(), "PLT:DYNAMIC_FORM:REVISION_PUBLISH", command.idempotencyKey(), digest,
                DynamicFormViews.PublishResult.class, operation, REVISION_AGGREGATE,
                String.valueOf(command.revisionId()), () -> publishRevisionTx(command),
                value -> linked("operationId", command.idempotencyKey(), "actorId", command.actor().userId(),
                        "templateId", value.templateId(), "revisionId", value.revision().revisionId(),
                        "statusBefore", "DRAFT", "statusAfter", "PUBLISHED",
                        "revisionVersionBefore", command.expectedVersion(), "revisionVersionAfter",
                        value.revision().revisionVersion(), "templateVersionAfter", value.templateVersion(),
                        "engineCode", value.revision().engineCode(), "designerVersion",
                        value.revision().designerVersion(), "rendererVersion", value.revision().rendererVersion()));
    }

    public DynamicFormViews.Template setAvailability(DynamicFormCommands.SetAvailability command) {
        validateActor(command == null ? null : command.actor());
        String target = command.targetAvailability();
        if (!List.of("ENABLED", "DISABLED").contains(target)) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        String operation = "DYNAMIC_FORM_TEMPLATE_" + ("ENABLED".equals(target) ? "ENABLE" : "DISABLE");
        validateId(command.templateId());
        validateVersion(command.expectedVersion());
        validateKey(command.idempotencyKey());
        Map<String, Object> digest = linked("templateId", command.templateId(), "expectedVersion",
                command.expectedVersion(), "targetAvailability", target);
        return idempotent(command.actor(), "PLT:DYNAMIC_FORM:TEMPLATE_" + target, command.idempotencyKey(), digest,
                DynamicFormViews.Template.class, operation, TEMPLATE_AGGREGATE,
                String.valueOf(command.templateId()), () -> setAvailabilityTx(command),
                value -> linked("operationId", command.idempotencyKey(), "actorId", command.actor().userId(),
                        "templateId", value.templateId(), "availabilityAfter", value.availability(),
                        "templateVersionBefore", command.expectedVersion(), "templateVersionAfter",
                        value.templateVersion(), "currentPublishedRevisionId", value.currentPublishedRevisionId()));
    }

    public DynamicFormViews.InstanceCreated createInstance(DynamicFormCommands.CreateInstance command) {
        String operation = "DYNAMIC_FORM_INSTANCE_CREATE";
        validateActor(command == null ? null : command.actor());
        validateId(command.templateRevisionId());
        validateVersion(command.expectedTemplateVersion());
        validateKey(command.idempotencyKey());
        String name = required(command.instanceName(), 128);
        Map<String, Object> digest = linked("templateRevisionId", command.templateRevisionId(),
                "expectedTemplateVersion", command.expectedTemplateVersion(), "instanceName", name);
        return idempotent(command.actor(), "PLT:DYNAMIC_FORM:INSTANCE_CREATE", command.idempotencyKey(), digest,
                DynamicFormViews.InstanceCreated.class, operation, INSTANCE_AGGREGATE, "NEW",
                () -> createInstanceTx(command, name),
                value -> linked("operationId", command.idempotencyKey(), "actorId", command.actor().userId(),
                        "instanceId", value.instanceId(), "templateId", value.templateId(),
                        "templateRevisionId", value.templateRevisionId(), "instanceVersionBefore", null,
                        "instanceVersionAfter", value.instanceVersion()));
    }

    public DynamicFormViews.InstancePatchResult patchInstance(DynamicFormCommands.PatchInstance command) {
        String operation = "DYNAMIC_FORM_INSTANCE_PATCH";
        validatePatchInstance(command);
        try {
            return transactionTemplate.execute(status -> patchInstanceTx(command, operation));
        } catch (RuntimeException failure) {
            auditRejected(command.actor(), command.correlationId(), operation, INSTANCE_AGGREGATE,
                    command.instanceId(), failure);
            throw failure;
        }
    }

    private DynamicFormViews.Template createTemplateTx(DynamicFormCommands.Actor actor, String code,
                                                        String name, String category, String description) {
        actionProjection.require(actor.userId(), DynamicFormActionProjection.TEMPLATE_MANAGE);
        LocalDateTime now = LocalDateTime.now();
        DynamicFormTemplateDO template = new DynamicFormTemplateDO();
        template.setId(IdWorker.getId());
        base(template, actor, now);
        template.setTemplateCode(code);
        template.setTemplateName(name);
        template.setCategoryCode(category);
        template.setDescription(description);
        template.setAvailabilityCode("DISABLED");
        template.setVersion(0);
        try {
            if (templateMapper.insert(template) != 1) throw new IllegalStateException("DYNAMIC_FORM_TEMPLATE_INSERT");
        } catch (DuplicateKeyException conflict) {
            throw exception(DYNAMIC_FORM_TEMPLATE_CODE_CONFLICT);
        }
        DynamicFormTemplateRevisionDO draft = new DynamicFormTemplateRevisionDO();
        draft.setId(IdWorker.getId());
        base(draft, actor, now);
        draft.setTemplateId(template.getId());
        draft.setRevisionNo(1);
        draft.setStatusCode("DRAFT");
        draft.setDraftMarker(1);
        draft.setFormConfJson("{}");
        draft.setFormRulesJson("[]");
        draft.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        draft.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        draft.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        draft.setVersion(0);
        if (revisionMapper.insert(draft) != 1) throw new IllegalStateException("DYNAMIC_FORM_REVISION_INSERT");
        return queryService.toTemplate(actor.userId(), requireTemplate(actor.tenantId(), template.getId()));
    }

    private DynamicFormViews.Revision createRevisionTx(DynamicFormCommands.CreateRevision command) {
        DynamicFormTemplateDO template = lockTemplate(command.actor(), command.templateId(),
                DynamicFormActionProjection.TEMPLATE_MANAGE);
        requireVersion(template.getVersion(), command.expectedTemplateVersion());
        DynamicFormDraftCreateQuery key = new DynamicFormDraftCreateQuery(command.actor().tenantId(),
                template.getId());
        if (revisionMapper.selectDraftForUpdate(key) != null) throw exception(DYNAMIC_FORM_DRAFT_ALREADY_EXISTS);
        DynamicFormTemplateRevisionDO source = revisionMapper.selectCurrentPublishedForUpdate(key);
        if (source == null || !Objects.equals(source.getId(), template.getCurrentPublishedRevisionId())
                || !"PUBLISHED".equals(source.getStatusCode())) {
            throw exception(DYNAMIC_FORM_CURRENT_REVISION_CHANGED);
        }
        DynamicFormTemplateRevisionDO draft = new DynamicFormTemplateRevisionDO();
        draft.setId(IdWorker.getId());
        base(draft, command.actor(), LocalDateTime.now());
        draft.setTemplateId(template.getId());
        draft.setRevisionNo(source.getRevisionNo() + 1);
        draft.setStatusCode("DRAFT");
        draft.setDraftMarker(1);
        draft.setSourceRevisionId(source.getId());
        draft.setFormConfJson(source.getFormConfJson());
        draft.setFormRulesJson(source.getFormRulesJson());
        draft.setEngineCode(source.getEngineCode());
        draft.setDesignerVersion(source.getDesignerVersion());
        draft.setRendererVersion(source.getRendererVersion());
        draft.setVersion(0);
        try {
            if (revisionMapper.insert(draft) != 1) throw new IllegalStateException("DYNAMIC_FORM_REVISION_INSERT");
        } catch (DuplicateKeyException conflict) {
            throw exception(DYNAMIC_FORM_DRAFT_ALREADY_EXISTS);
        }
        return queryService.toRevision(command.actor().userId(), requireRevision(command.actor().tenantId(), draft.getId()));
    }

    private DynamicFormViews.PublishResult publishRevisionTx(DynamicFormCommands.PublishRevision command) {
        DynamicFormTemplateRevisionDO inspected = requireRevision(command.actor().tenantId(), command.revisionId());
        DynamicFormTemplateDO template = lockTemplate(command.actor(), inspected.getTemplateId(),
                DynamicFormActionProjection.TEMPLATE_PUBLISH);
        DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(
                command.actor().tenantId(), template.getId(), command.revisionId()));
        requireDraft(revision, command.expectedVersion());
        schemaService.parseAndValidate(revision.getFormConfJson(), revision.getFormRulesJson(),
                revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion());
        LocalDateTime now = LocalDateTime.now();
        if (revisionMapper.publishIfMatch(new DynamicFormRevisionPublishUpdate(command.actor().tenantId(),
                template.getId(), revision.getId(), command.expectedVersion(), command.actor().userId(), now,
                actorText(command.actor()))) != 1) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        if (templateMapper.updateIfMatch(new DynamicFormTemplateVersionUpdate(command.actor().tenantId(),
                template.getId(), template.getVersion(), false, null, false, null, false, null,
                false, null, true, revision.getId(), actorText(command.actor()))) != 1) {
            throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        }
        DynamicFormTemplateRevisionDO published = requireRevision(command.actor().tenantId(), revision.getId());
        DynamicFormTemplateDO updated = requireTemplate(command.actor().tenantId(), template.getId());
        return new DynamicFormViews.PublishResult(updated.getId(), updated.getVersion(),
                updated.getAvailabilityCode(), queryService.toRevision(command.actor().userId(), published),
                actionProjection.templateActions(command.actor().userId(), updated.getAvailabilityCode(),
                        updated.getCurrentDraftRevisionId() != null, updated.getCurrentPublishedRevisionId() != null));
    }

    private DynamicFormViews.Template setAvailabilityTx(DynamicFormCommands.SetAvailability command) {
        DynamicFormTemplateDO template = lockTemplate(command.actor(), command.templateId(),
                DynamicFormActionProjection.TEMPLATE_PUBLISH);
        requireVersion(template.getVersion(), command.expectedVersion());
        if (Objects.equals(template.getAvailabilityCode(), command.targetAvailability())) {
            throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        }
        if ("ENABLED".equals(command.targetAvailability()) && template.getCurrentPublishedRevisionId() == null) {
            throw exception(DYNAMIC_FORM_CURRENT_REVISION_CHANGED);
        }
        if (templateMapper.updateIfMatch(new DynamicFormTemplateVersionUpdate(command.actor().tenantId(),
                template.getId(), template.getVersion(), false, null, false, null, false, null,
                true, command.targetAvailability(), false, null, actorText(command.actor()))) != 1) {
            throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        }
        return queryService.toTemplate(command.actor().userId(), requireTemplate(command.actor().tenantId(),
                template.getId()));
    }

    private DynamicFormViews.InstanceCreated createInstanceTx(DynamicFormCommands.CreateInstance command,
                                                               String name) {
        actionProjection.require(command.actor().userId(), DynamicFormActionProjection.INSTANCE_CREATE);
        DynamicFormTemplateRevisionDO inspected = requireRevision(command.actor().tenantId(),
                command.templateRevisionId());
        DynamicFormTemplateDO template = templateMapper.selectForUpdate(new DynamicFormTemplateLockQuery(
                command.actor().tenantId(), inspected.getTemplateId()));
        if (template == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        requireVersion(template.getVersion(), command.expectedTemplateVersion());
        DynamicFormTemplateRevisionDO revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(
                command.actor().tenantId(), template.getId(), command.templateRevisionId()));
        if (revision == null || !"PUBLISHED".equals(revision.getStatusCode())
                || !Objects.equals(template.getCurrentPublishedRevisionId(), revision.getId())) {
            throw exception(DYNAMIC_FORM_CURRENT_REVISION_CHANGED);
        }
        if (!"ENABLED".equals(template.getAvailabilityCode())) throw exception(DYNAMIC_FORM_TEMPLATE_DISABLED);
        actionProjection.require(command.actor().userId(), DynamicFormActionProjection.INSTANCE_CREATE);
        Long id = IdWorker.getId();
        PlatformDynamicFormInstanceDO instance = new PlatformDynamicFormInstanceDO();
        instance.setId(id);
        base(instance, command.actor(), LocalDateTime.now());
        instance.setInstanceCode("DFI-" + id);
        instance.setInstanceName(name);
        instance.setOwnerContext(DynamicFormQueryService.OWNER_CONTEXT);
        instance.setObjectType(DynamicFormFilePolicyProvider.INSTANCE_OBJECT_TYPE);
        instance.setObjectId(String.valueOf(id));
        instance.setTemplateId(template.getId());
        instance.setTemplateRevisionId(revision.getId());
        instance.setTemplateRevisionNo(revision.getRevisionNo());
        instance.setEngineCode(revision.getEngineCode());
        instance.setDesignerVersion(revision.getDesignerVersion());
        instance.setRendererVersion(revision.getRendererVersion());
        instance.setValueJson("{}");
        instance.setCreatedBy(command.actor().userId());
        instance.setVersion(0);
        if (instanceMapper.insert(instance) != 1) throw new IllegalStateException("DYNAMIC_FORM_INSTANCE_INSERT");
        return new DynamicFormViews.InstanceCreated(id, instance.getInstanceCode(), template.getId(), revision.getId(),
                revision.getRevisionNo(), 0, actionProjection.instanceActions(command.actor().userId(),
                instance.getCreatedBy()));
    }

    private DynamicFormViews.InstancePatchResult patchInstanceTx(DynamicFormCommands.PatchInstance command,
                                                                  String operation) {
        PlatformDynamicFormInstanceDO instance = instanceMapper.selectForUpdate(new DynamicFormInstanceLockQuery(
                command.actor().tenantId(), command.instanceId()));
        if (instance == null) throw exception(DYNAMIC_FORM_INSTANCE_NOT_FOUND);
        actionProjection.require(command.actor().userId(), DynamicFormActionProjection.INSTANCE_UPDATE);
        if (!Objects.equals(instance.getCreatedBy(), command.actor().userId())) throw exception(FORBIDDEN);
        requireVersion(instance.getVersion(), command.expectedVersion());
        DynamicFormTemplateRevisionDO revision = requireRevision(command.actor().tenantId(),
                instance.getTemplateRevisionId());
        DynamicFormSchemaService.SchemaFields schema = schemaService.parseAndValidate(revision.getFormConfJson(),
                revision.getFormRulesJson(), revision.getEngineCode(), revision.getDesignerVersion(),
                revision.getRendererVersion());
        if (command.values() == null || !command.values().isObject()) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        List<String> submittedKeys = command.values().properties().stream().map(Map.Entry::getKey).toList();
        if (submittedKeys.isEmpty()) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        for (String key : submittedKeys) {
            if (schema.fileFieldKeys().contains(key)) throw exception(DYNAMIC_FORM_FILE_FIELD_REQUIRES_FILE_API);
            if (!schema.ordinaryFieldKeys().contains(key)) throw exception(DYNAMIC_FORM_INSTANCE_FIELD_UNKNOWN);
        }
        tools.jackson.databind.JsonNode current = JsonUtils.parseTree(instance.getValueJson());
        ObjectNode merged = current != null && current.isObject()
                ? (ObjectNode) current.deepCopy() : JsonUtils.getObjectMapper().createObjectNode();
        command.values().properties().forEach(entry -> merged.set(entry.getKey(), entry.getValue()));
        if (instanceMapper.updateValueIfMatch(new DynamicFormInstanceValueUpdate(command.actor().tenantId(),
                instance.getId(), command.expectedVersion(), JsonUtils.toJsonString(merged),
                actorText(command.actor()))) != 1) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
        List<String> changed = submittedKeys.stream().sorted().toList();
        auditSuccess(command.actor(), command.correlationId(), operation, INSTANCE_AGGREGATE, instance.getId(),
                linked("operationId", command.correlationId(), "actorId", command.actor().userId(),
                        "instanceId", instance.getId(), "instanceVersionBefore", instance.getVersion(),
                        "instanceVersionAfter", instance.getVersion() + 1, "changedFieldKeys", changed));
        return new DynamicFormViews.InstancePatchResult(instance.getId(), instance.getVersion() + 1, changed,
                actionProjection.instanceActions(command.actor().userId(), instance.getCreatedBy()));
    }

    private DynamicFormTemplateDO lockTemplate(DynamicFormCommands.Actor actor, Long templateId, String permission) {
        actionProjection.require(actor.userId(), permission);
        DynamicFormTemplateDO row = templateMapper.selectForUpdate(
                new DynamicFormTemplateLockQuery(actor.tenantId(), templateId));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        actionProjection.require(actor.userId(), permission);
        return row;
    }

    private DynamicFormTemplateDO requireTemplate(Long tenantId, Long id) {
        DynamicFormTemplateDO row = templateMapper.selectByRow(new DynamicFormTemplateRowQuery(tenantId, id));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        return row;
    }

    private DynamicFormTemplateRevisionDO requireRevision(Long tenantId, Long id) {
        DynamicFormTemplateRevisionDO row = revisionMapper.selectByRow(new DynamicFormRevisionRowQuery(tenantId, id));
        if (row == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        return row;
    }

    private void requireDraft(DynamicFormTemplateRevisionDO revision, Integer expectedVersion) {
        if (revision == null || !"DRAFT".equals(revision.getStatusCode())
                || !Integer.valueOf(1).equals(revision.getDraftMarker())) {
            throw exception(DYNAMIC_FORM_REVISION_NOT_DRAFT);
        }
        requireVersion(revision.getVersion(), expectedVersion);
    }

    private <T> T idempotent(DynamicFormCommands.Actor actor, String scope, String key,
                             Map<String, Object> request, Class<T> responseType, String operation,
                             String aggregateType, String aggregateKey, Supplier<T> action,
                             Function<T, Map<String, Object>> detailFactory) {
        try {
            return transactionTemplate.execute(status -> {
                PlatformCommandExecutionApi.ExecutionResult<T> result = commandExecutionApi.execute(
                        new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), scope, actor.userId(), key),
                        digest(request), responseType, action,
                        response -> new PlatformCommandExecutionApi.SuccessFacts(operation, aggregateType,
                                resolveAggregateKey(aggregateKey, response), key,
                                JsonUtils.toJsonString(detailFactory.apply(response)), null, null));
                if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                    throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
                }
                if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null) {
                    throw exception(PLATFORM_COMMAND_IN_PROGRESS);
                }
                return result.response();
            });
        } catch (RuntimeException failure) {
            auditRejected(actor, key, operation, aggregateType, parseKey(aggregateKey), failure);
            throw failure;
        }
    }

    private String resolveAggregateKey(String fallback, Object response) {
        if (!"NEW".equals(fallback)) return fallback;
        if (response instanceof DynamicFormViews.Template value) return String.valueOf(value.templateId());
        if (response instanceof DynamicFormViews.Revision value) return String.valueOf(value.revisionId());
        if (response instanceof DynamicFormViews.InstanceCreated value) return String.valueOf(value.instanceId());
        return fallback;
    }

    private void auditSuccess(DynamicFormCommands.Actor actor, String correlationId, String operation,
                              String aggregateType, Long aggregateId, Map<String, Object> detail) {
        operationAuditApi.record(actor.tenantId(), actor.userId(), correlationId, operation,
                aggregateType, String.valueOf(aggregateId), "SUCCESS", detail);
    }

    private void auditRejected(DynamicFormCommands.Actor actor, String correlationId, String operation,
                               String aggregateType, Long aggregateId, RuntimeException failure) {
        if (actor == null || actor.tenantId() == null || actor.userId() == null
                || correlationId == null || correlationId.isBlank()) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operationId", correlationId);
        if (aggregateId != null) detail.put("aggregateId", aggregateId);
        detail.put("errorCode", failure instanceof ServiceException service ? String.valueOf(service.getCode())
                : "DYNAMIC_FORM_COMMAND_FAILED");
        operationAuditApi.record(actor.tenantId(), actor.userId(), correlationId, operation, aggregateType,
                aggregateId == null ? "UNKNOWN" : String.valueOf(aggregateId), "REJECTED", detail);
    }

    private void base(cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO row,
                      DynamicFormCommands.Actor actor, LocalDateTime now) {
        row.setTenantId(actor.tenantId());
        row.setCreator(actorText(actor));
        row.setCreateTime(now);
        row.setUpdater(actorText(actor));
        row.setUpdateTime(now);
        row.setDeleted(false);
    }

    private void validatePatchTemplate(DynamicFormCommands.PatchTemplate command) {
        validateActor(command == null ? null : command.actor());
        validateId(command.templateId());
        validateVersion(command.expectedVersion());
        validateCorrelation(command.correlationId());
        if (command.templateName() == null || command.categoryCode() == null || command.description() == null
                || !command.templateName().present() && !command.categoryCode().present()
                && !command.description().present()) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
    }

    private void validatePatchRevision(DynamicFormCommands.PatchRevision command) {
        validateActor(command == null ? null : command.actor());
        validateId(command.revisionId());
        validateVersion(command.expectedVersion());
        validateCorrelation(command.correlationId());
        if (command.formConfJson() == null || command.formRulesJson() == null) {
            throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        }
    }

    private void validatePatchInstance(DynamicFormCommands.PatchInstance command) {
        validateActor(command == null ? null : command.actor());
        validateId(command.instanceId());
        validateVersion(command.expectedVersion());
        validateCorrelation(command.correlationId());
        if (command.values() == null) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
    }

    private void validateActor(DynamicFormCommands.Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.userId() == null || actor.userId() <= 0) throw exception(FORBIDDEN);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
    }

    private void validateVersion(Integer version) {
        if (version == null || version < 0) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
    }

    private void requireVersion(Integer actual, Integer expected) {
        validateVersion(expected);
        if (!Objects.equals(actual, expected)) throw exception(DYNAMIC_FORM_VERSION_CONFLICT);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
    }

    private void validateCorrelation(String value) {
        if (value == null || value.isBlank() || value.length() > 128) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
    }

    private String required(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        }
        return value.trim();
    }

    private String optional(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        return normalized;
    }

    private String actorText(DynamicFormCommands.Actor actor) {
        return String.valueOf(actor.userId());
    }

    private List<String> changedTemplateFields(DynamicFormCommands.PatchTemplate command) {
        List<String> fields = new ArrayList<>();
        if (command.templateName().present()) fields.add("templateName");
        if (command.categoryCode().present()) fields.add("categoryCode");
        if (command.description().present()) fields.add("description");
        return fields.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private String digest(Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Long parseKey(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Object> linked(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}
