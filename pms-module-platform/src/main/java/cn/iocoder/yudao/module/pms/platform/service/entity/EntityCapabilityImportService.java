package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.hutool.core.bean.BeanUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** No updates: a retry compares stored content and rejects any conflicting target. */
@Service
@RequiredArgsConstructor
public class EntityCapabilityImportService implements EntityCapabilityImportApi {
    private final PlatformDynamicFormInstanceMapper instances;
    private final DynamicFormTemplateRevisionMapper formRevisions;
    private final DynamicFormSchemaService schemas;
    private final EntityCapabilityMapper mapper;
    private final EntityProviderRegistry registry;
    private final PermissionApi permissions;
    private final OperationAuditApi audit;
    private final FileReferenceMapper fileReferences;
    private final FileVersionMapper fileVersions;

    @Override
    public SourceForm inspect(Source source, EntityActor actor) {
        requireOperator(actor);
        actor.requireTenant(source.legacyOwner());
        Long revisionId = source.formRevisionId();
        if (source.instanceId() != null) {
            var instance = instances.selectByRow(new DynamicFormInstanceRowQuery(actor.tenantId(), source.instanceId()));
            if (instance == null || !source.legacyOwner().ownerModule().equals(instance.getOwnerContext())
                    || !source.legacyOwner().entityType().equals(instance.getObjectType())
                    || !source.legacyOwner().entityId().toString().equals(instance.getObjectId())) {
                throw invalid("Legacy form instance owner mismatch", source.legacyOwner());
            }
            if (revisionId != null && !revisionId.equals(instance.getTemplateRevisionId())) {
                throw invalid("Legacy form revision mismatch", source.legacyOwner());
            }
            revisionId = instance.getTemplateRevisionId();
        }
        if (revisionId == null) return new SourceForm(null, List.of());
        var revision = formRevisions.selectByRow(new DynamicFormRevisionRowQuery(actor.tenantId(), revisionId));
        if (revision == null) throw invalid("Missing original form revision", source.legacyOwner());
        var schema = schemas.parseAndValidate(revision.getFormConfJson(), revision.getFormRulesJson(),
                revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion());
        return new SourceForm(revisionId, schema.descriptors());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void importContent(Import command) {
        var entity = command.target().entity();
        var actor = command.actor();
        var source = inspect(command.source(), actor);
        actor.requireTenant(entity);
        if (!entity.ownerModule().equals(command.source().legacyOwner().ownerModule())
                || !entity.entityType().equals(command.source().legacyOwner().entityType())) {
            throw invalid("Legacy entity kind mismatch", entity);
        }
        // The owner has locked and reconciled its target inside this transaction. Online read/state
        // rules do not apply to a privileged historical import, including logically deleted records.
        var fixedCodes = registry.fields(entity).fields().stream().map(EntityField::code).collect(Collectors.toSet());
        var ordinaryCodes = source.fields().stream().filter(field -> !field.controlledFile())
                .map(DynamicFormFieldDescriptor::fieldKey).collect(Collectors.toSet());
        if (!ordinaryCodes.containsAll(command.fixedBindings().keySet())
                || !fixedCodes.containsAll(command.fixedBindings().values())) {
            throw invalid("Cannot map original fixed fields", entity);
        }
        var definitions = source.fields().stream().filter(field -> !field.controlledFile()
                        && !command.fixedBindings().containsKey(field.fieldKey()))
                .map(field -> EntityExtensionValidation.fromForm(field, field.fieldKey())).toList();
        var extraCodes = definitions.stream().map(EntityExtensionApi.Definition::code).collect(Collectors.toSet());
        if (!Collections.disjoint(extraCodes, fixedCodes) || !extraCodes.containsAll(command.extensionValues().keySet())) {
            throw invalid("Unmapped legacy extension values", entity);
        }
        // Historical completion is never re-decided with today's required-field rules.
        EntityExtensionValidation.values(definitions, command.extensionValues(), false);
        Long definitionId = definitions.isEmpty() ? null : importDefinition(entity, source.formRevisionId(), definitions, actor);
        var target = EntityValueQuery.of(command.target());
        var existingValues = mapper.lockValues(target);
        if (definitionId == null) {
            if (existingValues != null) throw invalid("Unexpected target extension values", entity);
        } else if (existingValues != null) {
            if (!definitionId.equals(existingValues.getDefinitionRevisionId())
                    || !Objects.equals(json(existingValues.getValuesJson()), command.extensionValues())) {
                throw invalid("Target extension content differs", entity);
            }
        } else {
            var values = BeanUtils.toBean(entity, EntityExtensionValueDO.class);
            values.setId(IdWorker.getId());
            values.setRevisionId(target.revisionId());
            values.setDefinitionRevisionId(definitionId);
            values.setValuesJson(JsonUtils.toJsonString(command.extensionValues()));
            values.setVersion(1);
            values.setCreator(actor.userId().toString());
            values.setUpdater(values.getCreator());
            mapper.insertValues(values);
        }
        var existingBinding = mapper.lockBinding(target);
        if (source.formRevisionId() == null) {
            if (existingBinding != null) throw invalid("Unexpected target form binding", entity);
            return;
        }
        Map<String, String> bindings = new LinkedHashMap<>(command.fixedBindings());
        extraCodes.forEach(code -> bindings.put(code, code));
        if (existingBinding != null) {
            if (!source.formRevisionId().equals(existingBinding.getFormRevisionId())
                    || !Objects.equals(definitionId, existingBinding.getExtensionDefinitionRevisionId())
                    || !bindings.equals(json(existingBinding.getFieldBindingsJson()))) {
                throw invalid("Target form binding differs", entity);
            }
        } else {
            var binding = BeanUtils.toBean(entity, EntityFormBindingDO.class);
            binding.setId(IdWorker.getId());
            binding.setRevisionId(target.revisionId());
            binding.setFormRevisionId(source.formRevisionId());
            binding.setExtensionDefinitionRevisionId(definitionId);
            binding.setFieldBindingsJson(JsonUtils.toJsonString(bindings));
            binding.setVersion(1);
            binding.setCreator(actor.userId().toString());
            binding.setUpdater(binding.getCreator());
            mapper.insertBinding(binding);
        }
        audit.record(actor.tenantId(), actor.userId(), actor.correlationId(), "ENTITY_CAPABILITY_IMPORT",
                entity.entityType(), entity.entityId().toString(), "SUCCESS",
                Map.of("target", command.target(), "source", command.source()));
    }

    private Long importDefinition(EntityRef entity, Long sourceRevisionId,
                                  List<EntityExtensionApi.Definition> fields, EntityActor actor) {
        var existing = mapper.selectDefinitionBySource(new EntityDefinitionSourceQuery(entity.tenantId(),
                entity.ownerModule(), entity.entityType(), sourceRevisionId));
        if (existing != null) {
            if (!JsonUtils.parseArray(existing.getFieldsJson(), EntityExtensionApi.Definition.class).equals(fields)) {
                throw invalid("Original field definition differs from target", entity);
            }
            return existing.getId();
        }
        var row = BeanUtils.toBean(entity, EntityExtensionDefinitionDO.class);
        row.setId(IdWorker.getId());
        row.setRevisionNo(mapper.selectMaxDefinitionRevision(new EntityDefinitionScopeQuery(
                entity.tenantId(), entity.ownerModule(), entity.entityType())) + 1);
        row.setSourceFormRevisionId(sourceRevisionId);
        row.setFieldsJson(JsonUtils.toJsonString(fields));
        row.setCreator(actor.userId().toString());
        row.setUpdater(row.getCreator());
        mapper.insertDefinition(row);
        return row.getId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void importFiles(RevisionRef target, Source source, String fileObjectType, EntityActor actor) {
        var form = inspect(source, actor);
        actor.requireTenant(target.entity());
        if (source.instanceId() == null || !target.revisionId().equals(source.legacyOwner().entityId())
                || !target.entity().ownerModule().equals(source.legacyOwner().ownerModule())
                || !target.entity().entityType().equals(source.legacyOwner().entityType())
                || fileObjectType == null || fileObjectType.isBlank()) {
            throw invalid("Invalid legacy file owner", target.entity());
        }
        for (var field : form.fields()) {
            if (!field.controlledFile()) continue;
            String purpose = cn.iocoder.yudao.module.pms.platform.api.file.FormAttachmentPolicy.PURPOSE_PREFIX + field.fieldKey();
            var original = fileReferences.selectSetForUpdate(new FileReferenceSetQuery(actor.tenantId(), "PLATFORM",
                    "DYNAMIC_FORM_INSTANCE", source.instanceId().toString(), purpose));
            var targetQuery = new FileReferenceSetQuery(actor.tenantId(), target.entity().ownerModule(),
                    fileObjectType, target.revisionId().toString(), purpose);
            var existing = fileReferences.selectSetForUpdate(targetQuery).stream()
                    .collect(Collectors.toMap(FileReferenceDO::getReferenceKey, row -> row));
            for (var reference : original) {
                if (fileVersions.selectOne(new FileVersionLockQuery(actor.tenantId(), reference.getArtifactId(),
                        reference.getFileVersionNo())) == null) throw invalid("Missing immutable file version", target.entity());
                var expected = BeanUtils.toBean(reference, FileReferenceDO.class);
                expected.setId(null);
                expected.setOwnerContext(target.entity().ownerModule());
                expected.setObjectType(fileObjectType);
                expected.setObjectId(target.revisionId().toString());
                expected.setScopeVersion(target.revisionId());
                var actual = existing.remove(reference.getReferenceKey());
                if (actual == null) {
                    fileReferences.insert(expected);
                } else {
                    var left = BeanUtil.beanToMap(expected);
                    var right = BeanUtil.beanToMap(actual);
                    left.remove("id");
                    right.remove("id");
                    if (!left.equals(right)) throw invalid("File reference differs: " + reference.getReferenceKey(), target.entity());
                }
            }
            if (!existing.isEmpty()) throw invalid("Unexpected target file references", target.entity());
        }
        audit.record(actor.tenantId(), actor.userId(), actor.correlationId(), "ENTITY_FILE_REFERENCE_IMPORT",
                target.entity().entityType(), target.revisionId().toString(), "SUCCESS", Map.of("source", source, "target", target));
    }

    private void requireOperator(EntityActor actor) {
        if (!Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(actor.userId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyRoles(actor.userId(), RoleCodeEnum.SUPER_ADMIN.getCode())) throw exception(FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String text) { return JsonUtils.parseObject(text, Map.class); }

    private IllegalStateException invalid(String reason, EntityRef entity) {
        return new IllegalStateException(reason + ": " + entity);
    }
}
