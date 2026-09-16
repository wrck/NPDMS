package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class EntityExtensionService implements EntityExtensionApi {
    private final EntityCapabilityMapper mapper;
    private final EntityProviderRegistry registry;
    private final PermissionApi permissionApi;
    private final OperationAuditApi audit;

    @Override
    public Values read(EntityDataRef target, EntityActor actor) {
        registry.requireReadable(target, actor);
        return values(mapper.selectValues(EntityValueQuery.of(target)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Values save(Save command) {
        registry.lockForWrite(command.target(), command.actor(), command.expectedEntityVersion());
        var definition = definition(command.definitionRevisionId(), command.target().entity(), command.actor());
        EntityExtensionValidation.values(definition.fields(), command.fields(), false);
        var old = mapper.lockValues(EntityValueQuery.of(command.target()));
        if ((old == null ? 0 : old.getVersion()) != command.expectedValueVersion()) {
            throw exception(ENTITY_VALUE_VERSION_CONFLICT);
        }
        var row = old == null ? new EntityExtensionValueDO() : old;
        var entity = command.target().entity();
        row.setTenantId(entity.tenantId());
        row.setOwnerModule(entity.ownerModule());
        row.setEntityType(entity.entityType());
        row.setEntityId(entity.entityId());
        row.setRevisionId(command.target().revisionId() == null ? 0L : command.target().revisionId());
        row.setDefinitionRevisionId(definition.id());
        row.setValuesJson(JsonUtils.toJsonString(command.fields()));
        row.setUpdater(command.actor().userId().toString());
        if (old == null) {
            row.setId(IdWorker.getId());
            row.setCreator(row.getUpdater());
            row.setVersion(1);
            try { mapper.insertValues(row); }
            catch (DuplicateKeyException conflict) { throw exception(ENTITY_VALUE_VERSION_CONFLICT); }
        } else {
            if (mapper.updateValues(row) != 1) throw exception(ENTITY_VALUE_VERSION_CONFLICT);
            row.setVersion(row.getVersion() + 1);
        }
        audit.record(entity.tenantId(), command.actor().userId(), command.actor().correlationId(),
                "ENTITY_EXTENSION_SAVE", entity.entityType(), entity.entityId().toString(), "SUCCESS",
                Map.of("target", command.target(), "definitionRevisionId", definition.id(),
                        "valueVersion", row.getVersion(), "fields", command.fields().keySet()));
        return values(row);
    }

    @Override
    public void validateComplete(EntityDataRef target, EntityActor actor) {
        Values values = read(target, actor);
        var binding = mapper.selectBinding(EntityValueQuery.of(target));
        Long definitionId = values.definitionRevisionId();
        if (binding != null && binding.getExtensionDefinitionRevisionId() != null) {
            if (definitionId != null && !definitionId.equals(binding.getExtensionDefinitionRevisionId())) throw exception(ENTITY_VALUE_INVALID);
            definitionId = binding.getExtensionDefinitionRevisionId();
        }
        if (definitionId != null) {
            EntityExtensionValidation.values(definition(definitionId, target.entity(), actor).fields(),
                    values.fields(), true);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void copy(EntityDataRef source, EntityDataRef target, Integer expectedTargetVersion, EntityActor actor) {
        if (!source.entity().equals(target.entity()) || source.equals(target)) throw exception(ENTITY_REVISION_MISMATCH);
        Values sourceValues = read(source, actor);
        // Even an empty source must not bypass the target's write protection.
        registry.lockForWrite(target, actor, expectedTargetVersion);
        var currentRow = mapper.lockValues(EntityValueQuery.of(target));
        Values current = values(currentRow);
        if (sourceValues.definitionRevisionId() != null) {
            save(new Save(target, actor, expectedTargetVersion, current.version(),
                    sourceValues.definitionRevisionId(), sourceValues.fields()));
        } else if (current.definitionRevisionId() != null) {
            if (mapper.deleteValues(currentRow) != 1) throw exception(ENTITY_VALUE_VERSION_CONFLICT);
            audit.record(actor.tenantId(), actor.userId(), actor.correlationId(), "ENTITY_EXTENSION_CLEAR",
                    target.entity().entityType(), target.entity().entityId().toString(), "SUCCESS", Map.of("target", target, "source", source));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionRevision publishDefinition(Long tenantId, String ownerModule, String entityType,
                                                List<Definition> fields, EntityActor actor) {
        var scope = new EntityRef(tenantId, ownerModule, entityType, 1L);
        actor.requireTenant(scope);
        if (!permissionApi.hasAnyPermissions(actor.userId(), "pms:dynamic-form-template:manage")) throw exception(FORBIDDEN);
        EntityExtensionValidation.definitions(fields, registry.fields(scope).fields());
        var row = new EntityExtensionDefinitionDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setOwnerModule(ownerModule);
        row.setEntityType(entityType);
        row.setRevisionNo(mapper.selectMaxDefinitionRevision(new EntityDefinitionScopeQuery(tenantId, ownerModule, entityType)) + 1);
        row.setFieldsJson(JsonUtils.toJsonString(fields));
        row.setCreator(actor.userId().toString());
        try { mapper.insertDefinition(row); }
        catch (DuplicateKeyException concurrentPublish) { throw exception(ENTITY_VALUE_VERSION_CONFLICT); }
        audit.record(tenantId, actor.userId(), actor.correlationId(), "ENTITY_EXTENSION_DEFINITION_PUBLISH",
                entityType, row.getId().toString(), "SUCCESS", Map.of("ownerModule", ownerModule, "revisionNo", row.getRevisionNo()));
        return definition(row);
    }

    @Override
    public DefinitionRevision definition(Long id, EntityRef entity, EntityActor actor) {
        actor.requireTenant(entity);
        var row = id == null ? null : mapper.selectDefinition(id);
        if (row == null || !entity.tenantId().equals(row.getTenantId()) || !entity.ownerModule().equals(row.getOwnerModule())
                || !entity.entityType().equals(row.getEntityType())) throw exception(ENTITY_DEFINITION_NOT_FOUND);
        return definition(row);
    }

    private DefinitionRevision definition(EntityExtensionDefinitionDO row) {
        return new DefinitionRevision(row.getId(), row.getTenantId(), row.getOwnerModule(), row.getEntityType(),
                row.getRevisionNo(), List.copyOf(JsonUtils.parseArray(row.getFieldsJson(), Definition.class)));
    }

    @SuppressWarnings("unchecked")
    private Values values(EntityExtensionValueDO row) {
        if (row == null) return new Values(null, Map.of(), 0);
        Map<String, Object> decoded = JsonUtils.parseObject(row.getValuesJson(), Map.class);
        return new Values(row.getDefinitionRevisionId(), Collections.unmodifiableMap(new LinkedHashMap<>(decoded)), row.getVersion());
    }
}
