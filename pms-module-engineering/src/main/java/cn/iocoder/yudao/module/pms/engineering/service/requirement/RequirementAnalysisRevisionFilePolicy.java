package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_VERSION_CONFLICT;

@Component
@RequiredArgsConstructor
public class RequirementAnalysisRevisionFilePolicy implements FileBusinessObjectPolicyProvider {
    public static final String OWNER = "SOL";
    public static final String TYPE = "REQUIREMENT_ANALYSIS_REVISION";
    private static final Set<String> READS = Set.of(FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);
    private static final Set<String> WRITES = Set.of(FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.REPLACE, FileActionCodes.DETACH);
    private final RequirementAnalysisMapper mapper;
    private final RequirementAnalysisAccess access;
    // File policies are registered before form services; resolve the callback only when inspecting a file.
    private final ObjectProvider<EntityFormApi> forms;

    @Override public String ownerContext() { return OWNER; }
    @Override public String objectType() { return TYPE; }

    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return policy(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(), query.requiredAction(), null, query.ownerExecutionContext());
    }
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        return policy(query.tenantId(), query.actorUserId(), query.key().objectId(), query.key().purposeCode(), query.requiredAction(), null, query.ownerExecutionContext());
    }
    @Override public Map<FileBusinessObjectReferenceSetQuery, FileBusinessObjectPolicyFact> inspectReferenceSets(
            List<FileBusinessObjectReferenceSetQuery> queries) {
        Map<ReadKey, Inspection> reads = new HashMap<>();
        Map<FileBusinessObjectReferenceSetQuery, FileBusinessObjectPolicyFact> result = new LinkedHashMap<>();
        for (var query : queries) result.put(query, policy(query.tenantId(), query.actorUserId(),
                query.key().objectId(), query.key().purposeCode(), query.requiredAction(), null,
                query.ownerExecutionContext(), reads));
        return Map.copyOf(result);
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return policy(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(), query.requiredAction(), query.expectedScopeVersion(), query.ownerExecutionContext());
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query) {
        return policy(query.tenantId(), query.actorUserId(), query.key().objectId(), query.key().purposeCode(), query.requiredAction(), query.expectedScopeVersion(), query.ownerExecutionContext());
    }

    private FileBusinessObjectPolicyFact policy(Long tenantId, Long userId, String objectId, String purpose,
                                                String action, Long expectedVersion, tools.jackson.databind.JsonNode execution) {
        return policy(tenantId, userId, objectId, purpose, action, expectedVersion, execution, null);
    }

    private FileBusinessObjectPolicyFact policy(Long tenantId, Long userId, String objectId, String purpose,
            String action, Long expectedVersion, tools.jackson.databind.JsonNode execution, Map<ReadKey, Inspection> reads) {
        if (!purpose.startsWith(FormAttachmentPolicy.PURPOSE_PREFIX) || purpose.length() == FormAttachmentPolicy.PURPOSE_PREFIX.length()
                || !READS.contains(action) && !WRITES.contains(action)) return FormAttachmentPolicy.fact(false, null, true);
        Long id;
        try { id = Long.valueOf(objectId); }
        catch (NumberFormatException invalid) { return FormAttachmentPolicy.fact(false, null, true); }
        var actor = new EntityActor(tenantId, userId, null);
        var key = new ReadKey(tenantId, userId, id, action, execution);
        var inspected = reads != null && READS.contains(action) ? reads.get(key) : null;
        if (inspected == null) {
            var row = access.read(id, actor);
            inspected = new Inspection(row, forms.getObject().layout(EntityDataRef.revision(row.revisionRef()), actor));
            if (reads != null && READS.contains(action)) reads.put(key, inspected);
        }
        var row = inspected.row();
        var layout = inspected.layout();
        String fieldKey = purpose.substring(FormAttachmentPolicy.PURPOSE_PREFIX.length());
        if (!RequirementAnalysisFields.attachmentKeys().contains(fieldKey)
                && (layout == null || layout.fields().stream().noneMatch(field -> field.controlledFile() && field.fieldKey().equals(fieldKey)))) {
            return FormAttachmentPolicy.fact(false, null, true);
        }
        if (expectedVersion != null) {
            if (!expectedVersion.equals(id)) throw exception(FILE_SCOPE_VERSION_CONFLICT);
            var selection = execution == null ? null : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                    execution.toString(), cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection.class);
            if (WRITES.contains(action)) row = access.lock(id, row.getVersion() == null ? null : Math.toIntExact(row.getVersion()), actor, selection, true);
            else row = mapper.lockRevision(new RequirementRevisionQuery(tenantId, id));
            if (row == null) throw exception(FILE_SCOPE_VERSION_CONFLICT);
        }
        boolean frozen = !"DRAFT".equals(row.getRevisionState());
        boolean allowed = READS.contains(action) || !frozen && access.isManager(row.getProjectId(), actor);
        // The file binding belongs to an immutable revision identity. Body edits and effective-marker
        // changes must not invalidate existing references; current permission/state is rechecked above.
        return FormAttachmentPolicy.fact(allowed, row.getId(), frozen);
    }

    private record ReadKey(Long tenantId, Long userId, Long revisionId, String action,
                           tools.jackson.databind.JsonNode execution) { }
    private record Inspection(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO row,
                              EntityFormApi.Layout layout) { }
}
