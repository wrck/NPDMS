package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** SOL owns both repositories and its single-draft/effective policy. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisEntityProvider implements EntityFieldProvider, EntityVersionProvider {
    public static final EntityFieldMapping<RequirementAnalysisDO> FIELDS = new EntityFieldMapping<>(RequirementAnalysisDO.class);
    private final RequirementAnalysisMapper mapper;
    private final RequirementAnalysisAccess access;
    private final EntityExtensionApi extensions;
    private final EntityFormApi forms;
    private final RequirementAnalysisRevisionFiles files;
    private final OperationAuditApi audit;
    private final EngineeringRuleReevaluationEvents events;

    @Override public String ownerModule() { return "SOL"; }
    @Override public String entityType() { return "REQUIREMENT_ANALYSIS"; }
    @Override public String formUsage() { return "PRE_04_REQUIREMENT_ANALYSIS"; }
    @Override public List<EntityField> fields() { return FIELDS.fields(); }

    @Override
    public Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor) {
        requireReadable(target, actor);
        RequirementAnalysisDO row = target.isRevision() ? revision(target, actor)
                : mapper.selectCurrent(new RequirementEntityQuery(actor.tenantId(), target.entity().entityId()));
        Map<String, EntityFieldValue> values = new LinkedHashMap<>();
        FIELDS.read(row).forEach((code, value) -> values.put(code, EntityFieldValue.known(value)));
        return values;
    }

    @Override
    public void requireReadable(EntityDataRef target, EntityActor actor) {
        requireType(target.entity(), actor);
        if (target.isRevision()) {
            revision(target, actor);
        } else {
            var current = mapper.selectCurrent(new RequirementEntityQuery(actor.tenantId(), target.entity().entityId()));
            if (current == null) throw exception(REQUIREMENT_STATUS_INVALID);
            access.requireRead(current.getProjectId(), actor, false);
        }
    }

    @Override
    public void lockForWrite(EntityDataRef target, EntityActor actor, Integer expectedVersion) {
        requireType(target.entity(), actor);
        if (target.isRevision()) {
            var locked = access.lock(target.revisionId(), expectedVersion, actor, null, true);
            requireEntity(locked, target.entity());
        } else {
            var query = new RequirementEntityQuery(actor.tenantId(), target.entity().entityId());
            var observed = mapper.selectCurrent(query);
            if (observed == null) throw exception(REQUIREMENT_STATUS_INVALID);
            access.lockScope(observed.getProjectId(), actor);
            var current = mapper.lockCurrent(query);
            if (!Objects.equals(current.getVersion(), expectedVersion)) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
    }

    @Override public Revision inspect(RevisionRef ref, EntityActor actor) {
        return revision(EntityDataRef.revision(ref), actor).revisionMetadata();
    }

    @Override
    public List<Revision> history(EntityRef entity, EntityActor actor, Long beforeRevisionId, int limit) {
        requireType(entity, actor);
        Integer beforeNo = null;
        if (beforeRevisionId != null) {
            var cursor = revision(new EntityDataRef(entity, beforeRevisionId), actor);
            beforeNo = cursor.getRevisionNo();
        }
        if (limit < 1 || limit > 100) throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        var rows = mapper.selectHistory(new RequirementRevisionPageQuery(actor.tenantId(), entity.entityId(), beforeNo, limit));
        if (!rows.isEmpty()) access.requireRead(rows.getFirst().getProjectId(), actor, false);
        else {
            var query = new RequirementEntityQuery(actor.tenantId(), entity.entityId());
            if (mapper.selectCurrent(query) != null) requireReadable(EntityDataRef.current(entity), actor);
            else {
                var latest = mapper.selectLatestForEntity(query);
                if (latest == null) throw exception(REQUIREMENT_STATUS_INVALID);
                access.read(latest.getId(), actor);
            }
        }
        return rows.stream().map(RequirementAnalysisRevisionDO::revisionMetadata).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Revision createInitial(Long projectId, EntityActor actor, ProjectBusinessExecutionSelection selection) {
        access.lockScope(projectId, actor);
        var project = new RequirementProjectQuery(actor.tenantId(), projectId);
        if (mapper.selectLatest(project) != null) throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        var execution = access.lockExecution(projectId, null, selection);
        var draft = newDraft(new EntityRef(actor.tenantId(), ownerModule(), entityType(), IdWorker.getId()), projectId,
                1, null, null, null, actor, execution);
        mapper.insertRevision(draft);
        bindInitialForm(draft, actor, execution);
        record("REQUIREMENT_ANALYSIS_INITIALIZE", draft, actor);
        return draft.revisionMetadata();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Revision createDraft(EntityRef entity, RevisionRef sourceRef, String reason, EntityActor actor) {
        requireType(entity, actor);
        RequirementAnalysisRevisionDO source;
        if (sourceRef != null) {
            if (!entity.equals(sourceRef.entity())) throw exception(REQUIREMENT_STATUS_INVALID);
            source = revision(EntityDataRef.revision(sourceRef), actor);
        } else {
            var current = mapper.selectCurrent(new RequirementEntityQuery(actor.tenantId(), entity.entityId()));
            if (current == null) throw exception(REQUIREMENT_STATUS_INVALID);
            access.requireRead(current.getProjectId(), actor, false);
            source = mapper.selectEffective(new RequirementProjectQuery(actor.tenantId(), current.getProjectId()));
        }
        if (source == null || !"FROZEN".equals(source.getRevisionState())) throw exception(REQUIREMENT_STATUS_INVALID);
        requireEntity(source, entity);
        access.lockScope(source.getProjectId(), actor);
        var execution = access.lockExecution(source.getProjectId(), source.getExecutionSnapshot(), null);
        var project = new RequirementProjectQuery(actor.tenantId(), source.getProjectId());
        if (mapper.selectDraft(project) != null) throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        var effective = mapper.selectEffective(project);
        var current = mapper.lockCurrent(new RequirementEntityQuery(actor.tenantId(), entity.entityId()));
        var draft = newDraft(entity, source.getProjectId(),
                mapper.maxRevisionNo(new RequirementEntityQuery(actor.tenantId(), entity.entityId())) + 1,
                source.getId(), effective == null ? null : effective.getId(), current == null ? null : current.getVersion(), actor, execution);
        FIELDS.write(draft, FIELDS.read(source));
        draft.setChangeReason(reason);
        mapper.insertRevision(draft);
        var from = EntityDataRef.revision(source.revisionRef());
        var to = EntityDataRef.revision(draft.revisionRef());
        extensions.copy(from, to, draft.getVersion(), actor);
        forms.copy(from, to, draft.getVersion(), actor);
        files.copy(source.revisionRef(), draft.revisionRef(), actor);
        record("REQUIREMENT_ANALYSIS_CREATE_DRAFT", draft, actor);
        return draft.revisionMetadata();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Revision save(RevisionRef ref, Integer expectedVersion, Map<String, Object> values, EntityActor actor) {
        requireType(ref.entity(), actor);
        var draft = access.lock(ref.revisionId(), expectedVersion, actor, null, true);
        requireEntity(draft, ref.entity());
        try { FIELDS.write(draft, values); }
        catch (IllegalArgumentException invalid) { throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID); }
        draft.setUpdater(actor.userId().toString());
        if (mapper.saveDraft(draft) != 1) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        draft.setVersion(draft.getVersion() + 1);
        record("REQUIREMENT_ANALYSIS_SAVE", draft, actor);
        return draft.revisionMetadata();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Revision freeze(RevisionRef ref, Integer expectedVersion, EntityActor actor) {
        requireType(ref.entity(), actor);
        var draft = access.lock(ref.revisionId(), expectedVersion, actor, null, true);
        requireEntity(draft, ref.entity());
        validateComplete(draft);
        files.lockForFreeze(ref, actor);
        if (mapper.freeze(new RequirementFreezeUpdate(actor.tenantId(), ref.revisionId(), expectedVersion, actor.userId(), LocalDateTime.now())) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        var frozen = mapper.selectRevision(new RequirementRevisionQuery(actor.tenantId(), ref.revisionId()));
        record("REQUIREMENT_ANALYSIS_FREEZE", frozen, actor);
        return frozen.revisionMetadata();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Revision activate(RevisionRef ref, Integer expectedVersion, EntityActor actor) {
        requireType(ref.entity(), actor);
        var revision = access.lock(ref.revisionId(), expectedVersion, actor, null, false);
        requireEntity(revision, ref.entity());
        if (!"FROZEN".equals(revision.getRevisionState()) || revision.effective()) throw exception(REQUIREMENT_STATUS_INVALID);
        var project = new RequirementProjectQuery(actor.tenantId(), revision.getProjectId());
        var effective = mapper.selectEffective(project);
        var current = mapper.lockCurrent(new RequirementEntityQuery(actor.tenantId(), ref.entity().entityId()));
        if (!Objects.equals(revision.getBaseEffectiveRevisionId(), effective == null ? null : effective.getId())
                || !Objects.equals(revision.getBaseEntityVersion(), current == null ? null : current.getVersion())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        var replacement = BeanUtils.toBean(revision, RequirementAnalysisDO.class);
        replacement.setId(revision.getEntityId());
        replacement.setUpdater(actor.userId().toString());
        if (current == null) {
            replacement.setVersion(1);
            replacement.setCreator(actor.userId().toString());
            replacement.setCreateTime(LocalDateTime.now());
            replacement.setUpdateTime(replacement.getCreateTime());
            mapper.insertCurrent(replacement);
        } else {
            replacement.setVersion(current.getVersion());
            if (mapper.updateCurrent(replacement) != 1) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
            replacement.setVersion(current.getVersion() + 1);
        }
        var from = EntityDataRef.revision(ref);
        var to = EntityDataRef.current(ref.entity());
        extensions.copy(from, to, replacement.getVersion(), actor);
        forms.copy(from, to, replacement.getVersion(), actor);
        if (effective != null && mapper.clearEffective(new RequirementActivationUpdate(actor.tenantId(), effective.getId(),
                effective.getVersion(), actor.userId().toString())) != 1) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        if (effective != null) record("REQUIREMENT_ANALYSIS_DEACTIVATE",
                mapper.selectRevision(new RequirementRevisionQuery(actor.tenantId(), effective.getId())), actor);
        if (mapper.makeEffective(new RequirementActivationUpdate(actor.tenantId(), revision.getId(),
                revision.getVersion(), actor.userId().toString())) != 1) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        var active = mapper.selectRevision(new RequirementRevisionQuery(actor.tenantId(), revision.getId()));
        record("REQUIREMENT_ANALYSIS_ACTIVATE", active, actor);
        events.changed(active.getProjectId(), "RequirementAnalysis", active.getId(), actor.userId(), actor.correlationId());
        return active.revisionMetadata();
    }

    private RequirementAnalysisRevisionDO newDraft(EntityRef entity, Long projectId, int number, Long source,
                                                     Long baseline, Integer entityVersion, EntityActor actor,
                                                     RequirementAnalysisExecutionAccess.Frozen execution) {
        var draft = new RequirementAnalysisRevisionDO();
        draft.setId(IdWorker.getId());
        draft.setEntityId(entity.entityId());
        draft.setTenantId(entity.tenantId());
        draft.setProjectId(projectId);
        draft.setRevisionNo(number);
        draft.setSourceRevisionId(source);
        draft.setBaseEffectiveRevisionId(baseline);
        draft.setBaseEntityVersion(entityVersion);
        draft.setRevisionState("DRAFT");
        draft.setDraftMarker(1);
        draft.setStatusCode("DRAFT");
        draft.setVersion(1);
        draft.setCreator(actor.userId().toString());
        draft.setUpdater(draft.getCreator());
        draft.setProjectTemplateId(execution.binding().projectTemplateId());
        draft.setProjectTemplateRevisionId(execution.binding().templateRevisionId());
        draft.setExecutionSnapshot(JsonUtils.toJsonString(execution));
        return draft;
    }

    private void bindInitialForm(RequirementAnalysisRevisionDO row, EntityActor actor,
                                 RequirementAnalysisExecutionAccess.Frozen execution) {
        var formRevision = execution.binding().dynamicFormTemplateRevisionId();
        if (formRevision == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        FIELDS.fields().forEach(field -> fields.put(cn.hutool.core.util.StrUtil.toUnderlineCase(field.code()).toUpperCase(java.util.Locale.ROOT), field.code()));
        forms.bind(new EntityFormApi.Bind(EntityDataRef.revision(row.revisionRef()), actor, row.getVersion(),
                0, formRevision, null, fields));
    }

    private RequirementAnalysisRevisionDO revision(EntityDataRef target, EntityActor actor) {
        requireType(target.entity(), actor);
        var row = access.read(target.revisionId(), actor);
        requireEntity(row, target.entity());
        return row;
    }

    private void requireType(EntityRef entity, EntityActor actor) {
        actor.requireTenant(entity);
        if (!ownerModule().equals(entity.ownerModule()) || !entityType().equals(entity.entityType())) throw exception(REQUIREMENT_STATUS_INVALID);
    }

    private void requireEntity(RequirementAnalysisRevisionDO revision, EntityRef entity) {
        if (!revision.entityRef().equals(entity)) throw exception(REQUIREMENT_STATUS_INVALID);
    }

    private void validateComplete(RequirementAnalysisDO row) {
        var values = FIELDS.read(row);
        for (var field : FIELDS.fields()) {
            if (!field.required()) continue;
            var value = (String) values.get(field.code());
            if (value == null || HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>", "")).replace('\u00a0', ' ').isBlank()) {
                throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
            }
        }
    }

    private void record(String operation, RequirementAnalysisRevisionDO revision, EntityActor actor) {
        audit.record(actor.tenantId(), actor.userId(), actor.correlationId(), operation, entityType(),
                revision.getEntityId().toString(), "SUCCESS", Map.of("revision", revision.revisionMetadata()));
    }
}
