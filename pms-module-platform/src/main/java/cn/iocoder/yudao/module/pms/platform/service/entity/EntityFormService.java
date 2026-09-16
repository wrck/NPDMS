package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionUsageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.EntityFormBindingDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.EntityValueQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class EntityFormService implements EntityFormApi {
    private final EntityCapabilityMapper mapper;
    private final EntityProviderRegistry registry;
    private final EntityExtensionApi extensions;
    private final DynamicFormTemplateRevisionMapper formRevisions;
    private final DynamicFormSchemaService schemas;
    private final DynamicFormBusinessInstanceApi businessForms;
    private final OperationAuditApi audit;

    @Override
    public Binding read(EntityDataRef target, EntityActor actor) {
        registry.requireReadable(target, actor);
        return binding(mapper.selectBinding(EntityValueQuery.of(target)));
    }

    @Override
    public Layout layout(EntityDataRef target, EntityActor actor) {
        var binding = read(target, actor);
        if (binding == null) return null;
        var revision = formRevisions.selectByRow(new DynamicFormRevisionRowQuery(actor.tenantId(), binding.formRevisionId()));
        if (revision == null) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        var schema = schemas.parseAndValidate(revision.getFormConfJson(), revision.getFormRulesJson(),
                revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion());
        return new Layout(binding, revision.getTemplateId(), revision.getRevisionNo(), revision.getVersion(),
                revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion(),
                revision.getFormConfJson(), revision.getFormRulesJson(), schema.descriptors());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Binding bind(Bind command) {
        registry.lockForWrite(command.target(), command.actor(), command.expectedEntityVersion());
        var entity = command.target().entity();
        var revision = formRevisions.selectByRow(new DynamicFormRevisionRowQuery(entity.tenantId(), command.formRevisionId()));
        if (revision == null || !entity.tenantId().equals(revision.getTenantId())
                || !"PUBLISHED".equals(revision.getStatusCode())) throw exception(DYNAMIC_FORM_TEMPLATE_NOT_FOUND);
        businessForms.inspectRevisionForUsage(new DynamicFormRevisionUsageQuery(entity.tenantId(), command.actor().userId(),
                new DynamicFormProviderKey(entity.ownerModule(), entity.entityType()), revision.getId(),
                registry.fields(entity).formUsage(), DynamicFormBusinessAction.REVISION_FROZEN_USE, revision.getVersion()));
        var schema = schemas.parseAndValidate(revision.getFormConfJson(), revision.getFormRulesJson(),
                revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion());
        Set<String> fields = new HashSet<>();
        registry.fields(entity).fields().forEach(field -> fields.add(field.code()));
        if (command.fieldBindings() == null || !schema.ordinaryFieldKeys().containsAll(command.fieldBindings().keySet())
                || new HashSet<>(command.fieldBindings().values()).size() != command.fieldBindings().size()) {
            throw exception(ENTITY_VALUE_INVALID);
        }
        Long definitionId = command.extensionDefinitionRevisionId();
        if (definitionId == null) {
            // A published layout is already authorized above. Materialize its extension definition
            // before the first business save; operators need no template-management permission.
            var definitions = schema.descriptors().stream()
                    .filter(field -> command.fieldBindings().containsKey(field.fieldKey())
                            && !fields.contains(command.fieldBindings().get(field.fieldKey())))
                    .map(field -> EntityExtensionValidation.fromForm(field, command.fieldBindings().get(field.fieldKey())))
                    .toList();
            if (!definitions.isEmpty()) definitionId = definitionForForm(command, definitions);
        }
        if (definitionId != null) {
            extensions.definition(definitionId, entity, command.actor()).fields()
                    .forEach(field -> fields.add(field.code()));
        }
        if (!fields.containsAll(command.fieldBindings().values())) throw exception(ENTITY_VALUE_INVALID);
        var old = mapper.lockBinding(EntityValueQuery.of(command.target()));
        if ((old == null ? 0 : old.getVersion()) != command.expectedBindingVersion()) throw exception(ENTITY_VALUE_VERSION_CONFLICT);
        var row = old == null ? new EntityFormBindingDO() : old;
        row.setTenantId(entity.tenantId());
        row.setOwnerModule(entity.ownerModule());
        row.setEntityType(entity.entityType());
        row.setEntityId(entity.entityId());
        row.setRevisionId(command.target().revisionId() == null ? 0L : command.target().revisionId());
        row.setFormRevisionId(command.formRevisionId());
        row.setExtensionDefinitionRevisionId(definitionId);
        row.setFieldBindingsJson(JsonUtils.toJsonString(command.fieldBindings()));
        row.setUpdater(command.actor().userId().toString());
        if (old == null) {
            row.setId(IdWorker.getId());
            row.setCreator(row.getUpdater());
            row.setVersion(1);
            try { mapper.insertBinding(row); }
            catch (DuplicateKeyException conflict) { throw exception(ENTITY_VALUE_VERSION_CONFLICT); }
        } else {
            if (mapper.updateBinding(row) != 1) throw exception(ENTITY_VALUE_VERSION_CONFLICT);
            row.setVersion(row.getVersion() + 1);
        }
        audit.record(entity.tenantId(), command.actor().userId(), command.actor().correlationId(),
                "ENTITY_FORM_BIND", entity.entityType(), entity.entityId().toString(), "SUCCESS",
                Map.of("target", command.target(), "formRevisionId", command.formRevisionId(), "bindingVersion", row.getVersion()));
        return binding(row);
    }

    private Long definitionForForm(Bind command, List<EntityExtensionApi.Definition> definitions) {
        var entity = command.target().entity();
        EntityExtensionValidation.definitions(definitions, registry.fields(entity).fields());
        var source = new cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.EntityDefinitionSourceQuery(
                entity.tenantId(), entity.ownerModule(), entity.entityType(), command.formRevisionId());
        var existing = mapper.selectDefinitionBySource(source);
        if (existing != null) {
            // Preserve the immutable definition, including definitions brought over by migration.
            var saved = JsonUtils.parseArray(existing.getFieldsJson(), EntityExtensionApi.Definition.class);
            if (!new HashSet<>(saved).equals(new HashSet<>(definitions))) throw exception(ENTITY_VALUE_INVALID);
            return existing.getId();
        }
        var row = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.EntityExtensionDefinitionDO();
        row.setId(IdWorker.getId()); row.setTenantId(entity.tenantId());
        row.setOwnerModule(entity.ownerModule()); row.setEntityType(entity.entityType());
        row.setSourceFormRevisionId(command.formRevisionId());
        row.setRevisionNo(mapper.selectMaxDefinitionRevision(
                new cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.EntityDefinitionScopeQuery(
                        entity.tenantId(), entity.ownerModule(), entity.entityType())) + 1);
        row.setFieldsJson(JsonUtils.toJsonString(definitions));
        row.setCreator(command.actor().userId().toString()); row.setUpdater(row.getCreator());
        try { mapper.insertDefinition(row); }
        catch (DuplicateKeyException conflict) { throw exception(ENTITY_VALUE_VERSION_CONFLICT); }
        return row.getId();
    }

    @SuppressWarnings("unchecked")
    private Binding binding(EntityFormBindingDO row) {
        if (row == null) return null;
        Map<String, String> fields = JsonUtils.parseObject(row.getFieldBindingsJson(), Map.class);
        return new Binding(row.getFormRevisionId(), row.getExtensionDefinitionRevisionId(), Map.copyOf(fields), row.getVersion());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void copy(EntityDataRef source, EntityDataRef target, Integer expectedTargetVersion, EntityActor actor) {
        if (!source.entity().equals(target.entity()) || source.equals(target)) throw exception(ENTITY_REVISION_MISMATCH);
        var original = read(source, actor);
        registry.lockForWrite(target, actor, expectedTargetVersion);
        var current = mapper.lockBinding(EntityValueQuery.of(target));
        if (original != null) {
            bind(new Bind(target, actor, expectedTargetVersion, current == null ? 0 : current.getVersion(),
                    original.formRevisionId(), original.extensionDefinitionRevisionId(), original.fieldBindings()));
        } else if (current != null) {
            if (mapper.deleteBinding(current) != 1) throw exception(ENTITY_VALUE_VERSION_CONFLICT);
            audit.record(actor.tenantId(), actor.userId(), actor.correlationId(), "ENTITY_FORM_BINDING_CLEAR",
                    target.entity().entityType(), target.entity().entityId().toString(), "SUCCESS", Map.of("target", target, "source", source));
        }
    }
}
