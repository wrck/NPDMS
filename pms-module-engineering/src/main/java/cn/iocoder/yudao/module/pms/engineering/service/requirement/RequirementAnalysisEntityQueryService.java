package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;

/** Current content is read from the business table; revision content is read from the revision table. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisEntityQueryService {
    private final RequirementAnalysisMapper mapper;
    private final RequirementAnalysisEntityProvider provider;
    private final RequirementAnalysisAccess access;
    private final EntityExtensionApi extensions;
    private final EntityFormApi forms;
    private final RequirementAnalysisRevisionFiles files;
    private final RequirementAnalysisExecutionAccess executions;
    private final ProjectWorkBindingFactApi bindings;

    public Workspace workspace(Long projectId, EntityActor actor) {
        return workspace(projectId, actor, null, null);
    }

    public Workspace workspace(Long projectId, EntityActor actor, Long stageId, Long taskId) {
        if (stageId != null && taskId != null) throw new IllegalArgumentException("Select one execution node");
        access.requireRead(projectId, actor, false);
        var project = new RequirementProjectQuery(actor.tenantId(), projectId);
        var effective = mapper.selectEffective(project);
        var draft = mapper.selectDraft(project);
        boolean manager = access.isManager(projectId, actor);
        ProjectBusinessExecutionSelection selected = null;
        boolean explicitEntry = stageId != null || taskId != null;
        boolean existing = effective != null || draft != null;
        boolean canCreate = !explicitEntry && existing;
        if (explicitEntry || !existing) try {
            var binding = stageId != null ? bindings.inspectStage(new ProjectWorkBindingStageFactQuery(projectId, stageId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS))
                    : taskId != null ? bindings.inspectTask(new ProjectWorkBindingTaskFactQuery(projectId, taskId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS))
                    : bindings.inspect(new ProjectWorkBindingFactQuery(projectId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
            if (binding != null) {
                selected = executions.observeCurrent(binding);
                canCreate = executions.canCreate(binding);
            }
        } catch (RuntimeException unavailable) {
            // Existing content remains readable when its execution node is unavailable.
        }
        boolean nodeWritable = !explicitEntry && existing || selected != null;
        return new Workspace(projectId,
                effective == null ? null : view(effective, EntityDataRef.current(effective.entityRef()), actor, selected, nodeWritable),
                draft == null || !manager ? null : view(draft, EntityDataRef.revision(draft.revisionRef()), actor, selected, nodeWritable),
                manager && canCreate && draft == null ? mapper.selectLatest(project) == null
                        ? List.of("CREATE_INITIAL_DRAFT") : effective != null && canWrite(effective, selected)
                        ? List.of("CREATE_DRAFT") : List.of() : List.of());
    }

    public View revision(Long revisionId, EntityActor actor) {
        var row = access.read(revisionId, actor);
        return view(row, EntityDataRef.revision(row.revisionRef()), actor, null, true);
    }

    private View view(RequirementAnalysisRevisionDO row, EntityDataRef target, EntityActor actor,
                      ProjectBusinessExecutionSelection selected, boolean nodeWritable) {
        Map<String, Object> values = new LinkedHashMap<>();
        provider.read(target, actor).forEach((code, fact) -> { if (fact.readable()) values.put(code, fact.value()); });
        var extra = extensions.read(target, actor);
        values.putAll(extra.fields());
        var form = forms.layout(target, actor);
        var entity = mapper.selectCurrent(new RequirementEntityQuery(actor.tenantId(), row.getEntityId()));
        boolean manager = access.isManager(row.getProjectId(), actor);
        boolean draft = "DRAFT".equals(row.getRevisionState());
        List<String> actions = manager && nodeWritable && canWrite(row, selected) ? draft ? List.of("PATCH_FORM", "COMPLETE")
                : mapper.selectDraft(new RequirementProjectQuery(actor.tenantId(), row.getProjectId())) == null ? List.of("CREATE_DRAFT") : List.of()
                : List.of();
        return new View(row.getProjectId(), row.revisionMetadata(), entity == null ? null : entity.getVersion(), form,
                extra.definitionRevisionId(), extra.version(), values, files.inspect(row.revisionRef(), actor), actions,
                row.getProjectTemplateId(), row.getProjectTemplateRevisionId(), RequirementAnalysisEntityProvider.FIELDS.fields());
    }

    private boolean canWrite(RequirementAnalysisRevisionDO row, ProjectBusinessExecutionSelection selected) {
        return selected == null ? executions.canUseFrozenConfiguration(row.getProjectId(), row.getExecutionSnapshot())
                : executions.canWrite(row.getProjectId(), row.getExecutionSnapshot(), selected);
    }

    public List<EntityVersionApi.FieldDifference> attachmentDifferences(RevisionRef left, RevisionRef right, EntityActor actor) {
        var before = attachmentValues(left, actor);
        var after = attachmentValues(right, actor);
        var fields = new TreeSet<String>(); fields.addAll(before.keySet()); fields.addAll(after.keySet());
        return fields.stream().filter(field -> !Objects.equals(before.get(field), after.get(field)))
                .map(field -> new EntityVersionApi.FieldDifference(field,
                        EntityFieldValue.known(before.getOrDefault(field, List.of())),
                        EntityFieldValue.known(after.getOrDefault(field, List.of())))).toList();
    }

    private Map<String, List<AttachmentValue>> attachmentValues(RevisionRef revision, EntityActor actor) {
        Map<String, List<AttachmentValue>> result = new LinkedHashMap<>();
        files.inspect(revision, actor).forEach(set -> result.put(set.key().purposeCode().substring(
                cn.iocoder.yudao.module.pms.platform.api.file.FormAttachmentPolicy.PURPOSE_PREFIX.length()),
                set.activeFacts().stream().map(file -> new AttachmentValue(file.artifactId(), file.versionNo()))
                        .sorted(Comparator.comparing(AttachmentValue::artifactId).thenComparing(AttachmentValue::versionNo)).toList()));
        return result;
    }
    public record AttachmentValue(Long artifactId, Integer versionNo) {}

    public record Workspace(Long projectId, View currentEffective, View draft, List<String> allowedActions) {}
    public record View(Long projectId, EntityVersionProvider.Revision revision, Integer entityVersion,
                       EntityFormApi.Layout form, Long extensionDefinitionRevisionId, int extensionValueVersion,
                       Map<String, Object> values, List<FileReferenceSetFact> attachments, List<String> allowedActions,
                       Long projectTemplateId, Long projectTemplateRevisionId, List<EntityField> fieldCatalog) {}
}
