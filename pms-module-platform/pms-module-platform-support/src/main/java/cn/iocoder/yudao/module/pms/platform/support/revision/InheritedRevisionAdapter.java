package cn.iocoder.yudao.module.pms.platform.support.revision;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldValue;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.MutableEntityRevision;
import cn.iocoder.yudao.module.pms.platform.api.entity.RevisionRef;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ResolvableType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 继承式内容历史的通用实现：修订实体继承业务实体字段并实现 MutableEntityRevision，
 * 修订元数据与通用保存/冻结/生效由本适配器统一承载，业务 Owner 无须手写版本 Provider。
 * 仅描述符启用 CONTENT_HISTORY 且声明携带修订 Mapper 的实体获得本适配；
 * 专业 Owner 注册的 EntityVersionProvider 优先，适配器不改变业务状态机语义：
 * 生效只把冻结内容按乐观锁回写当前业务行，状态类字段仍走声明目录的合法命令。
 */
public class InheritedRevisionAdapter implements EntityVersionProvider, EntityFieldProvider {

    private final BusinessModelDeclaration declaration;
    private final BusinessAccessGuard guard;
    private final EntityExtensionApi extensionApi;
    private final OperationAuditApi audit;
    private final Class<? extends MutableEntityRevision> revisionClass;

    @SuppressWarnings("unchecked")
    public InheritedRevisionAdapter(BusinessModelDeclaration declaration, BusinessAccessGuard guard,
                                    EntityExtensionApi extensionApi, OperationAuditApi audit) {
        if (!(declaration.revisionMapper() instanceof BaseMapper)) {
            throw new BusinessContractException("DECLARATION_MISMATCH", "修订 Mapper 不是 BaseMapper");
        }
        Class<?> resolved = ResolvableType.forInstance(declaration.revisionMapper())
                .as(BaseMapper.class).getGeneric(0).resolve();
        if (resolved == null || !MutableEntityRevision.class.isAssignableFrom(resolved)
                || !declaration.entityClass().isAssignableFrom(resolved)) {
            throw new BusinessContractException("DECLARATION_MISMATCH",
                    "修订实体必须继承业务实体并实现 MutableEntityRevision: " + resolved);
        }
        this.declaration = declaration;
        this.guard = guard;
        this.extensionApi = extensionApi;
        this.audit = audit;
        this.revisionClass = (Class<? extends MutableEntityRevision>) resolved;
    }

    /** 通用适配的准入条件：描述符启用 CONTENT_HISTORY，且声明携带满足契约的修订 Mapper。 */
    public static boolean supports(BusinessModelDeclaration declaration) {
        boolean historyEnabled = declaration.descriptor().capabilities().stream()
                .anyMatch(binding -> binding.type() == BusinessCapabilityType.CONTENT_HISTORY && binding.enabled());
        if (!historyEnabled || !(declaration.revisionMapper() instanceof BaseMapper)) {
            return false;
        }
        Class<?> resolved = ResolvableType.forInstance(declaration.revisionMapper())
                .as(BaseMapper.class).getGeneric(0).resolve();
        return resolved != null && MutableEntityRevision.class.isAssignableFrom(resolved)
                && declaration.entityClass().isAssignableFrom(resolved);
    }

    private BusinessModelDescriptor descriptor() {
        return declaration.descriptor();
    }

    @SuppressWarnings("unchecked")
    private BaseMapper<MutableEntityRevision> revisionMapper() {
        return (BaseMapper<MutableEntityRevision>) declaration.revisionMapper();
    }

    @SuppressWarnings("unchecked")
    private BaseMapper<BaseBusinessEntity> businessMapper() {
        return (BaseMapper<BaseBusinessEntity>) declaration.mapper();
    }

    private List<BusinessModelIntrospector.IntrospectedField> businessFields() {
        return BusinessModelIntrospector.businessFields(declaration.entityClass());
    }

    // ---------- EntityVersionProvider ----------

    @Override
    public String ownerModule() {
        return descriptor().ownerModule();
    }

    @Override
    public String entityType() {
        return descriptor().entityType();
    }

    @Override
    public Revision inspect(RevisionRef ref, EntityActor actor) {
        MutableEntityRevision row = selectRevision(ref.revisionId());
        if (row == null || !ref.entity().equals(row.entityRef())
                || !actor.tenantId().equals(row.getTenantId())) {
            return null;
        }
        return row.revisionMetadata();
    }

    @Override
    public List<Revision> history(EntityRef entity, EntityActor actor, Long beforeRevisionId, int limit) {
        QueryWrapper<MutableEntityRevision> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", entity.tenantId()).eq("entity_id", entity.entityId());
        if (beforeRevisionId != null) {
            wrapper.lt("id", beforeRevisionId);
        }
        wrapper.orderByDesc("id");
        List<MutableEntityRevision> rows = revisionMapper().selectList(wrapper);
        return rows.stream().limit(limit).map(MutableEntityRevision::revisionMetadata).toList();
    }

    @Override
    public Revision createDraft(EntityRef entity, RevisionRef source, String reason, EntityActor actor) {
        actor.requireTenant(entity);
        requireGuard();
        guard.requireWritable(descriptor(), actor, "revision:create");
        BaseBusinessEntity current = requireBusinessRow(entity);
        MutableEntityRevision sourceRow;
        Long sourceRevisionId;
        if (source != null) {
            sourceRow = requireFrozenSource(source);
            sourceRevisionId = sourceRow.getId();
        } else {
            MutableEntityRevision effective = selectEffective(entity);
            sourceRow = effective != null ? effective : null;
            sourceRevisionId = effective == null ? null : effective.getId();
        }
        requireNoOpenDraft(entity);
        MutableEntityRevision draft = newDraftInstance();
        copyContent(sourceRow != null ? sourceRow : current, draft);
        // 租户列由统一业务基类承载；修订实体继承业务实体，直接经基类写入。
        ((BaseBusinessEntity) draft).setTenantId(entity.tenantId());
        draft.setEntityId(entity.entityId());
        draft.setRevisionNo(nextRevisionNo(entity));
        draft.setSourceRevisionId(sourceRevisionId);
        MutableEntityRevision effectiveNow = selectEffective(entity);
        draft.setBaseEffectiveRevisionId(effectiveNow == null ? null : effectiveNow.getId());
        draft.setBaseEntityVersion(current.getVersion() == null ? null : current.getVersion().intValue());
        draft.setRevisionState(Revision.State.DRAFT);
        draft.setEffective(false);
        draft.setChangeReason(reason);
        draft.setVersion(0L);
        setUpdater(draft, actor);
        if (revisionMapper().insert(draft) != 1) {
            throw new BusinessContractException("REVISION_NOT_PERSISTED", "修订草稿写入失败: " + entity);
        }
        copyExtensions(sourceRow, entity, draft, actor);
        audit(actor, "ENTITY_REVISION_CREATE", entity, draft);
        return draft.revisionMetadata();
    }

    @Override
    public Revision save(RevisionRef ref, Integer expectedVersion, Map<String, Object> fields, EntityActor actor) {
        MutableEntityRevision draft = requireRevision(ref);
        if (draft.revisionState() != Revision.State.DRAFT) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "修订已冻结，不能保存: " + ref);
        }
        requireGuard();
        guard.requireWritable(descriptor(), actor, "revision:save");
        requireWritableFields(fields);
        applyFields(draft, fields);
        setUpdater(draft, actor);
        updateRevision(draft, expectedVersion, ref);
        audit(actor, "ENTITY_REVISION_SAVE", ref.entity(), draft);
        return draft.revisionMetadata();
    }

    @Override
    public Revision freeze(RevisionRef ref, Integer expectedVersion, EntityActor actor) {
        MutableEntityRevision draft = requireRevision(ref);
        if (draft.revisionState() != Revision.State.DRAFT) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "修订已冻结，不能重复冻结: " + ref);
        }
        requireGuard();
        guard.requireWritable(descriptor(), actor, "revision:freeze");
        draft.setRevisionState(Revision.State.FROZEN);
        draft.setFrozenBy(actor.userId());
        draft.setFrozenAt(LocalDateTime.now());
        setUpdater(draft, actor);
        updateRevision(draft, expectedVersion, ref);
        audit(actor, "ENTITY_REVISION_FREEZE", ref.entity(), draft);
        return draft.revisionMetadata();
    }

    @Override
    public Revision activate(RevisionRef ref, Integer expectedVersion, EntityActor actor) {
        MutableEntityRevision revision = requireRevision(ref);
        if (revision.revisionState() != Revision.State.FROZEN || revision.effective()) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "修订未冻结或已生效: " + ref);
        }
        requireGuard();
        guard.requireWritable(descriptor(), actor, "revision:activate");
        BaseBusinessEntity current = requireBusinessRow(ref.entity());
        MutableEntityRevision effectiveNow = selectEffective(ref.entity());
        if (!Objects.equals(revision.getBaseEffectiveRevisionId(), effectiveNow == null ? null : effectiveNow.getId())
                || !Objects.equals(revision.getBaseEntityVersion(),
                        current.getVersion() == null ? null : current.getVersion().intValue())) {
            throw new BusinessContractException("REVISION_BASE_CHANGED",
                    "修订基线已变化，内容不能直接生效，须发起新修订: " + ref);
        }
        BaseBusinessEntity replacement = newCurrentInstance(revision);
        replacement.setId(ref.entity().entityId());
        replacement.setVersion(current.getVersion());
        replacement.setCreator(null);
        replacement.setCreateTime(null);
        replacement.setUpdateTime(null);
        replacement.setDeleted(null);
        replacement.setUpdater(actor.userId().toString());
        QueryWrapper<BaseBusinessEntity> condition = new QueryWrapper<>();
        // 乐观锁插件负责 SET version+1 与 WHERE version=旧值；这里只保留 id 与租户条件。
        condition.eq("id", replacement.getId()).eq("tenant_id", actor.tenantId());
        if (businessMapper().update(replacement, condition) != 1) {
            throw new BusinessContractException("CONCURRENCY_CONFLICT", "生效回写并发冲突: " + ref);
        }
        revisionMapper().update(null, Wrappers.<MutableEntityRevision>update()
                .eq("tenant_id", actor.tenantId()).eq("entity_id", ref.entity().entityId())
                .eq("effective", true).set("effective", false));
        revision.setEffective(true);
        setUpdater(revision, actor);
        updateRevision(revision, expectedVersion, ref);
        audit(actor, "ENTITY_REVISION_ACTIVATE", ref.entity(), revision);
        return revision.revisionMetadata();
    }

    @Override
    public void discard(RevisionRef ref, EntityActor actor) {
        MutableEntityRevision draft = requireRevision(ref);
        // 已冻结修订是不可变历史；只有未冻结的草稿工作区可以放弃。
        if (draft.revisionState() != Revision.State.DRAFT) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "已冻结修订不能放弃: " + ref);
        }
        requireGuard();
        guard.requireWritable(descriptor(), actor, "revision:discard");
        // 逻辑删除前先把修订号改写为行内唯一的负值占位，释放 (tenant,entity,revision_no) 唯一键，
        // 让后续新修订可复用该修订号；占位行对历史与草稿查询均不可见（逻辑删除过滤）。
        revisionMapper().update(null, Wrappers.<MutableEntityRevision>update()
                .eq("id", draft.getId()).eq("tenant_id", actor.tenantId())
                .set("revision_no", -draft.getId()));
        QueryWrapper<MutableEntityRevision> condition = new QueryWrapper<>();
        condition.eq("id", draft.getId()).eq("tenant_id", actor.tenantId());
        if (revisionMapper().delete(condition) != 1) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "草稿放弃失败: " + ref);
        }
        audit(actor, "ENTITY_REVISION_DISCARD", ref.entity(), draft);
    }

    // ---------- EntityFieldProvider ----------
    @Override
    public List<EntityField> fields() {
        return descriptor().fields().stream()
                .map(field -> new EntityField(field.code(), field.type(), field.required())).toList();
    }

    @Override
    public Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor) {
        Object row = target.isRevision() ? selectRevision(target.revisionId())
                : businessMapper().selectById(target.entity().entityId());
        if (row == null) {
            return Map.of();
        }
        Map<String, EntityFieldValue> values = new java.util.LinkedHashMap<>();
        BusinessModelIntrospector.readValues(row, businessFields())
                .forEach((code, value) -> values.put(code, EntityFieldValue.known(value)));
        return values;
    }

    @Override
    public void lockForWrite(EntityDataRef target, EntityActor actor, Long expectedVersion) {
        actor.requireTenant(target.entity());
        requireGuard();
        guard.requireWritable(descriptor(), actor, target.isRevision() ? "revision:write" : "entity:write");
        if (target.isRevision()) {
            MutableEntityRevision row = requireRevision(new RevisionRef(target.entity(), target.revisionId()));
            if (row.revisionState() != Revision.State.DRAFT) {
                throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "扩展字段只能写入修订草稿: " + target);
            }
        } else {
            requireBusinessRow(target.entity());
        }
    }

    @Override
    public void requireReadable(EntityDataRef target, EntityActor actor) {
        actor.requireTenant(target.entity());
        requireGuard();
        guard.requireReadable(descriptor(), actor, target.isRevision() ? "revision:read" : "entity:read");
    }

    // ---------- 内部实现 ----------

    private MutableEntityRevision selectRevision(Long revisionId) {
        return revisionMapper().selectById(revisionId);
    }

    private MutableEntityRevision requireRevision(RevisionRef ref) {
        MutableEntityRevision row = selectRevision(ref.revisionId());
        if (row == null || !ref.entity().equals(row.entityRef())) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "修订不存在或不属于该实体: " + ref);
        }
        return row;
    }

    private BaseBusinessEntity requireBusinessRow(EntityRef entity) {
        BaseBusinessEntity row = businessMapper().selectById(entity.entityId());
        if (row == null || !entity.tenantId().equals(row.getTenantId())) {
            throw new BusinessContractException("ENTITY_NOT_FOUND", "业务实体不存在: " + entity);
        }
        return row;
    }

    private MutableEntityRevision requireFrozenSource(RevisionRef source) {
        MutableEntityRevision row = requireRevision(source);
        if (row.revisionState() != Revision.State.FROZEN) {
            throw new BusinessContractException("ENTITY_REVISION_MISMATCH", "复制来源修订必须已冻结: " + source);
        }
        return row;
    }

    private MutableEntityRevision selectEffective(EntityRef entity) {
        QueryWrapper<MutableEntityRevision> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", entity.tenantId()).eq("entity_id", entity.entityId())
                .eq("effective", true);
        return revisionMapper().selectList(wrapper).stream().findFirst().orElse(null);
    }

    private void requireNoOpenDraft(EntityRef entity) {
        QueryWrapper<MutableEntityRevision> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", entity.tenantId()).eq("entity_id", entity.entityId())
                .eq("revision_state", Revision.State.DRAFT.name());
        if (!revisionMapper().selectList(wrapper).isEmpty()) {
            throw new BusinessContractException("REVISION_DRAFT_EXISTS",
                    "已有未完成的修订草稿，须先冻结或由 Owner 另行处理: " + entity);
        }
    }

    private int nextRevisionNo(EntityRef entity) {
        QueryWrapper<MutableEntityRevision> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", entity.tenantId()).eq("entity_id", entity.entityId());
        int max = revisionMapper().selectList(wrapper).stream()
                .map(MutableEntityRevision::getRevisionNo)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        return max + 1;
    }

    private MutableEntityRevision newDraftInstance() {
        try {
            return revisionClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new BusinessContractException("ENTITY_NOT_CREATABLE",
                    "修订实体缺少无参构造: " + revisionClass.getName());
        }
    }

    private BaseBusinessEntity newCurrentInstance(MutableEntityRevision revision) {
        BaseBusinessEntity replacement;
        try {
            replacement = (BaseBusinessEntity) declaration.entityClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new BusinessContractException("ENTITY_NOT_CREATABLE",
                    "业务实体缺少无参构造: " + declaration.entityClass().getName());
        }
        Map<String, Object> content = BusinessModelIntrospector.readValues(revision, businessFields());
        for (Map.Entry<String, Object> entry : content.entrySet()) {
            BusinessModelIntrospector.IntrospectedField field = businessFields().stream()
                    .filter(f -> f.code().equals(entry.getKey())).findFirst().orElseThrow();
            try {
                field.property().set(replacement, entry.getValue());
            } catch (IllegalAccessException ex) {
                throw new BusinessContractException("FIELD_INACCESSIBLE", "字段不可写: " + entry.getKey());
            }
        }
        return replacement;
    }

    /** 修订继承业务字段：直接从来源行读取目录开放字段并写入新修订。 */
    private void copyContent(Object sourceRow, MutableEntityRevision draft) {
        Map<String, Object> content = BusinessModelIntrospector.readValues(sourceRow, businessFields());
        for (Map.Entry<String, Object> entry : content.entrySet()) {
            BusinessModelIntrospector.IntrospectedField field = businessFields().stream()
                    .filter(f -> f.code().equals(entry.getKey())).findFirst().orElseThrow();
            try {
                field.property().set(draft, entry.getValue());
            } catch (IllegalAccessException ex) {
                throw new BusinessContractException("FIELD_INACCESSIBLE", "字段不可写: " + entry.getKey());
            }
        }
    }

    /** 扩展字段随修订快照：按来源身份复制，解释依据（定义版本）随值一起进入修订。 */
    private void copyExtensions(MutableEntityRevision sourceRow, EntityRef entity,
                                MutableEntityRevision draft, EntityActor actor) {
        boolean formEnabled = descriptor().capabilities().stream()
                .anyMatch(binding -> binding.type() == BusinessCapabilityType.DYNAMIC_FORM && binding.enabled());
        if (!formEnabled || extensionApi == null) {
            return;
        }
        EntityDataRef sourceRef = sourceRow == null
                ? EntityDataRef.current(entity)
                : EntityDataRef.revision(sourceRow.revisionRef());
        extensionApi.copy(sourceRef, EntityDataRef.revision(draft.revisionRef()), 0L, actor);
    }

    private void requireWritableFields(Map<String, Object> fields) {
        List<String> writableCodes = descriptor().fields().stream()
                .filter(BusinessFieldDescriptor::writable).map(BusinessFieldDescriptor::code).toList();
        for (String key : fields == null ? List.<String>of() : fields.keySet()) {
            if (!writableCodes.contains(key)) {
                throw new BusinessContractException("FIELD_NOT_WRITABLE", "字段未开放写入: " + key);
            }
        }
    }

    private void applyFields(MutableEntityRevision target, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values == null ? Map.<String, Object>of().entrySet() : values.entrySet()) {
            BusinessModelIntrospector.IntrospectedField field = businessFields().stream()
                    .filter(f -> f.code().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new BusinessContractException("FIELD_NOT_OPEN", "字段未在目录开放: " + entry.getKey()));
            try {
                field.property().set(target, convert(entry.getValue(), field.type()));
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw new BusinessContractException("FIELD_VALUE_INVALID",
                        "字段值非法: " + entry.getKey() + " " + ex.getMessage());
            }
        }
    }

    private Object convert(Object value, EntityField.Type type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case NUMBER -> value instanceof Number ? value : new BigDecimal(value.toString());
            case TEXT, TEXT_LIST, OBJECT_LIST -> value;
            case BOOLEAN -> value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
            case DATE -> value instanceof LocalDate ? value : LocalDate.parse(value.toString());
            case DATETIME -> value instanceof LocalDateTime ? value : LocalDateTime.parse(value.toString());
        };
    }

    /** 乐观锁插件负责 version 自增与条件追加；预期不匹配在更新行数上暴露。 */
    private void updateRevision(MutableEntityRevision row, Integer expectedVersion, RevisionRef ref) {
        Long currentVersion = row.getVersion();
        if (expectedVersion != null && (currentVersion == null || currentVersion.intValue() != expectedVersion)) {
            throw new BusinessContractException("CONCURRENCY_CONFLICT",
                    "并发依据过期: 期望 " + expectedVersion + " 实际 " + currentVersion);
        }
        if (revisionMapper().updateById(row) != 1) {
            throw new BusinessContractException("CONCURRENCY_CONFLICT", "修订更新失败: " + ref);
        }
        row.setVersion(currentVersion == null ? 1L : currentVersion + 1);
    }

    private void setUpdater(MutableEntityRevision row, EntityActor actor) {
        try {
            row.getClass().getMethod("setUpdater", String.class).invoke(row, actor.userId().toString());
        } catch (ReflectiveOperationException ignored) {
            // 无 updater 字段的修订表省略审计列。
        }
    }

    private void requireGuard() {
        if (guard == null) {
            throw new BusinessContractException("ACCESS_GUARD_UNAVAILABLE", "访问守卫未装配，修订能力不可用");
        }
    }

    private void audit(EntityActor actor, String action, EntityRef entity, MutableEntityRevision row) {
        if (audit == null) {
            return;
        }
        // 审计列 correlation_id 非空：入口未携带关联标识时以动作为关联，保证可追溯。
        String correlationId = actor.correlationId() != null ? actor.correlationId()
                : action + ":" + entity.entityId();
        audit.record(entity.tenantId(), actor.userId(), correlationId, action,
                entity.entityType(), String.valueOf(row.getId()), "SUCCESS",
                Map.of("revisionNo", row.getRevisionNo() == null ? 0 : row.getRevisionNo(),
                        "state", row.revisionState().name()));
    }
}
