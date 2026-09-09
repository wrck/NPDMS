package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.*;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationErrors.*;
import static cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;

/** PM-03 / F-PROJ-009: eight typed revisions, one transactional configuration service. */
@Service
@RequiredArgsConstructor
public class DeliveryDefinitionService {
    private final DeliveryDefinitionRevisionMapper revisions;
    private final DeliveryDefinitionReferenceMapper references;
    private final DeliveryConfigurationCommands commands;
    private final DeliveryDefinitionResolver resolver;

    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public PageResult<Revision> page(DeliveryDefinitionPageQuery query) {
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        if (query.getPageSize() == null || query.getPageSize() < 1 || query.getPageSize() > 100
                || query.getPageNo() == null || query.getPageNo() < 1) throw exception(INVALID, "pagination");
        if (query.getDefinitionKind() != null) kind(query.getDefinitionKind());
        if (query.getRevisionState() != null && !Set.of("DRAFT", "PUBLISHED").contains(query.getRevisionState()))
            throw exception(INVALID, "revisionState");
        var rows = revisions.selectPage(query);
        return new PageResult<>(rows.getList().stream().map(this::toRevision).toList(), rows.getTotal());
    }

    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public Revision get(Long id) { return toRevision(required(id)); }

    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public Long create(Save body, String key) {
        validateBody(body);
        return commands.execute("DELIVERY_DEFINITION_CREATE", key, body, Long.class, () -> {
            if (!revisions.lockIdentity(identity(body.definitionKind().name(), body.definitionCode())).isEmpty())
                throw exception(DUPLICATE);
            resolver.resolve(body.references(), null, true);
            DeliveryDefinitionRevisionDO row = newDraft(body, 1L);
            insert(row); insertReferences(row.getId(), body.references());
            return row.getId();
        });
    }

    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public Long update(Long id, int version, Save body, String key) {
        validateBody(body);
        return commands.execute("DELIVERY_DEFINITION_UPDATE", key, new Intent(id, version, body), Long.class, () -> {
            DeliveryDefinitionRevisionDO row = lockIdentityAndRevision(id, version);
            requireDraft(row);
            if (!row.getDefinitionKind().equals(body.definitionKind().name())
                    || !row.getDefinitionCode().equals(body.definitionCode())) throw exception(INVALID, "identity is immutable");
            resolver.resolve(body.references(), id, true);
            row.setPayload(JsonUtils.toJsonString(body.payload())); row.setSchemaVersion(body.schemaVersion());
            row.setUpdater(actor());
            if (revisions.replaceDraft(row) != 1) throw exception(VERSION_CONFLICT);
            references.deleteDraftReferences(new DeliveryDefinitionReferencesQuery(tenant(), id));
            insertReferences(id, body.references());
            return id;
        });
    }

    @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public Long copy(Long id, int version, String key) {
        return commands.execute("DELIVERY_DEFINITION_COPY", key, new Intent(id, version, null), Long.class, () -> {
            DeliveryDefinitionRevisionDO source = required(id);
            List<DeliveryDefinitionRevisionDO> history = revisions.lockIdentity(identity(source.getDefinitionKind(), source.getDefinitionCode()));
            source = lockedFrom(history, id, version);
            if (history.stream().anyMatch(row -> "DRAFT".equals(row.getRevisionState()))) throw exception(DUPLICATE);
            Revision from = toRevision(source);
            resolver.resolve(from.references(), null, true);
            Save body = new Save(from.definitionKind(), from.definitionCode(), from.schemaVersion(), from.payload(), from.references());
            validateBody(body);
            long next = Math.addExact(history.stream().mapToLong(DeliveryDefinitionRevisionDO::getRevisionNo).max().orElse(0), 1);
            DeliveryDefinitionRevisionDO draft = newDraft(body, next);
            insert(draft); insertReferences(draft.getId(), body.references()); return draft.getId();
        });
    }

    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public Validation validate(Long id) {
        Revision revision = toRevision(required(id));
        try {
            validateBody(new Save(revision.definitionKind(), revision.definitionCode(), revision.schemaVersion(), revision.payload(), revision.references()));
            resolver.resolveDefinition(revision, false);
            return Validation.of(List.of());
        } catch (RuntimeException ex) {
            return Validation.of(List.of(new Issue("definition", "INVALID_CONFIGURATION", ex.getMessage())));
        }
    }

    @PreAuthorize("@ss.hasPermission('pms:project-template:publish')")
    public Long publish(Long id, int version, String key) {
        return commands.execute("DELIVERY_DEFINITION_PUBLISH", key, new Intent(id, version, null), Long.class, () -> {
            DeliveryDefinitionRevisionDO row = lockIdentityAndRevision(id, version); requireDraft(row);
            Revision definition = toRevision(row);
            resolver.resolveDefinition(definition, true);
            row.setPublishedAt(LocalDateTime.now()); row.setUpdater(actor());
            if (revisions.publish(row) != 1) throw exception(VERSION_CONFLICT); return id;
        });
    }

    @PreAuthorize("@ss.hasPermission('pms:project-template:disable')")
    public Long disable(Long id, int version, String key) {
        return commands.execute("DELIVERY_DEFINITION_DISABLE", key, new Intent(id, version, null), Long.class, () -> {
            DeliveryDefinitionRevisionDO row = lockIdentityAndRevision(id, version);
            if (!"PUBLISHED".equals(row.getRevisionState()) || row.getPublishedAt() == null || row.getDisabledAt() != null)
                throw exception(STATE_INVALID);
            row.setDisabledAt(LocalDateTime.now()); row.setUpdater(actor());
            if (revisions.disable(row) != 1) throw exception(VERSION_CONFLICT); return id;
        });
    }

    private DeliveryDefinitionRevisionDO required(Long id) {
        if (id == null || id <= 0) throw exception(NOT_FOUND);
        DeliveryDefinitionRevisionDO row = revisions.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenant()) || Boolean.TRUE.equals(row.getDeleted())) throw exception(NOT_FOUND);
        return row;
    }
    private DeliveryDefinitionRevisionDO lockIdentityAndRevision(Long id, int version) {
        DeliveryDefinitionRevisionDO row = required(id);
        return lockedFrom(revisions.lockIdentity(identity(row.getDefinitionKind(), row.getDefinitionCode())), id, version);
    }
    private DeliveryDefinitionRevisionDO lockedFrom(List<DeliveryDefinitionRevisionDO> rows, Long id, int version) {
        DeliveryDefinitionRevisionDO row = rows.stream().filter(item -> Objects.equals(item.getId(), id)
                && Objects.equals(item.getTenantId(), tenant())).findFirst().orElseThrow(() -> exception(NOT_FOUND));
        if (!Objects.equals(row.getVersion(), version)) throw exception(VERSION_CONFLICT); return row;
    }
    private Revision toRevision(DeliveryDefinitionRevisionDO row) {
        return new Revision(row.getId(), kind(row.getDefinitionKind()), row.getDefinitionCode(), row.getRevisionNo(),
                row.getRevisionState(), row.getSchemaVersion(), JsonUtils.parseObject(row.getPayload(), JsonNode.class),
                references.selectReferences(new DeliveryDefinitionReferencesQuery(tenant(), row.getId())).stream()
                        .map(ref -> new DeliveryDefinitionReference(ref.getReferenceKey(), ref.getTargetRevisionId())).toList(),
                row.getPublishedAt(), row.getDisabledAt(), row.getVersion());
    }
    private void validateBody(Save body) {
        try {
            if (body == null || !DeliveryDefinitionPayloadValidator.code(body.definitionCode()) || body.definitionCode().length() > 64)
                throw new IllegalArgumentException("definitionCode");
            DeliveryDefinitionPayloadValidator.validate(body.definitionKind(), body.schemaVersion(), body.payload(), body.references());
        } catch (IllegalArgumentException ex) { throw exception(INVALID, ex.getMessage()); }
    }
    private DeliveryDefinitionRevisionDO newDraft(Save body, Long revisionNo) {
        DeliveryDefinitionRevisionDO row = new DeliveryDefinitionRevisionDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenant()); row.setDefinitionKind(body.definitionKind().name()); row.setDefinitionCode(body.definitionCode());
        row.setRevisionNo(revisionNo); row.setRevisionState("DRAFT"); row.setSchemaVersion(body.schemaVersion());
        row.setPayload(JsonUtils.toJsonString(body.payload())); row.setVersion(0); return row;
    }
    private void insert(DeliveryDefinitionRevisionDO row) {
        try { revisions.insert(row); } catch (DuplicateKeyException ex) { throw exception(DUPLICATE); }
    }
    private void insertReferences(Long id, List<DeliveryDefinitionReference> refs) {
        for (DeliveryDefinitionReference ref : refs) {
            DeliveryDefinitionReferenceDO row = new DeliveryDefinitionReferenceDO();
            row.setId(IdWorker.getId());
            row.setTenantId(tenant()); row.setOwnerRevisionId(id); row.setReferenceKey(ref.referenceKey());
            row.setTargetRevisionId(ref.targetRevisionId()); references.insert(row);
        }
    }
    private void requireDraft(DeliveryDefinitionRevisionDO row) {
        if (!"DRAFT".equals(row.getRevisionState()) || row.getPublishedAt() != null || row.getDisabledAt() != null) throw exception(STATE_INVALID);
    }
    private DeliveryDefinitionKind kind(String value) {
        try { return DeliveryDefinitionKind.valueOf(value); } catch (RuntimeException ex) { throw exception(INVALID, "definitionKind"); }
    }
    private DeliveryDefinitionIdentityQuery identity(String kind, String code) { return new DeliveryDefinitionIdentityQuery(tenant(), kind, code); }
    private Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private String actor() { return String.valueOf(SecurityFrameworkUtils.getLoginUserId()); }
    private record Intent(Long id, int expectedVersion, Save body) { }
}
