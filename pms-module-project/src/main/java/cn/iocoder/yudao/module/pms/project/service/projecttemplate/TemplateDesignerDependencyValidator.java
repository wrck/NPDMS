package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PM-03 V2 publication dependency guard.
 *
 * <p>The Designer owns frozen WorkBinding semantics, but a new publication must still prove that
 * each BusinessView revision or approval definition is the exact currently-publishable selection. This guard
 * never grants object permissions and never queries Owner business instances.</p>
 */
@Component
@RequiredArgsConstructor
public class TemplateDesignerDependencyValidator {

    private final BusinessViewQueryApi businessViewQueryApi;
    private final ProjectStageGateProcessOwnerApi processes;

    /** Existing project bindings retain their frozen reference. Only additions/changes create new references. */
    public List<Issue> validateProjectChanges(TemplateDesignerDocument effective, TemplateDesignerDocument submitted, boolean lockForPublish) {
        var changes = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(submitted), TemplateDesignerDocument.class);
        if (changes.getStages() != null && effective.getStages() != null)
            for (var node : changes.getStages())
                if (node != null && effective.getStages().stream().anyMatch(old -> old != null && Objects.equals(old.getNodeKey(),node.getNodeKey())
                        && Objects.equals(old.getWorkBinding(),node.getWorkBinding()))) node.setWorkBinding(null);
        if (changes.getTasks() != null && effective.getTasks() != null)
            for (var node : changes.getTasks())
                if (node != null && effective.getTasks().stream().anyMatch(old -> old != null && Objects.equals(old.getNodeKey(),node.getNodeKey())
                        && Objects.equals(old.getWorkBinding(),node.getWorkBinding()))) node.setWorkBinding(null);
        if (changes.getGates() != null && effective.getGates() != null)
            for (var node : changes.getGates()) {
                if (node == null || node.getReferences() == null) continue;
                var previous = effective.getGates().stream().filter(old -> old != null
                        && Objects.equals(old.getNodeKey(), node.getNodeKey())
                        && Objects.equals(old.getStageCode(), node.getStageCode())
                        && Objects.equals(old.getGateType(), node.getGateType())).findFirst().orElse(null);
                if (previous != null && previous.getReferences() != null)
                    for (int i = 0; i < node.getReferences().size(); i++)
                        if (previous.getReferences().contains(node.getReferences().get(i))) node.getReferences().set(i, null);
            }
        return validate(changes, lockForPublish);
    }

    public List<Issue> validate(TemplateDesignerDocument designer, boolean lockForPublish) {
        List<Issue> issues = new ArrayList<>();
        if (designer != null && designer.getGates() != null)
            for (int i = 0; i < designer.getGates().size(); i++) {
                var gate = designer.getGates().get(i);
                if (gate == null || gate.getReferences() == null) continue;
                for (int j = 0; j < gate.getReferences().size(); j++) {
                    var ref = gate.getReferences().get(j);
                    if (ref == null || !("APPROVAL".equals(ref.getRefType()) || "PROCESS".equals(ref.getRefType()))) continue;
                    String path = "gates[" + i + "].references[" + j + "]";
                    if (ref.getRefVersion() == null || ref.getRefVersion().isBlank())
                        issues.add(new Issue(path, "GATE_PROCESS_DEFINITION_REQUIRED", "流程引用缺少冻结的精确定义ID"));
                    else validateProcessDefinition(ref.getRefCode(), ref.getRefVersion(), path, issues);
                }
            }
        if (designer != null && designer.getStages() != null)
            for (int i = 0; i < designer.getStages().size(); i++) {
                var node = designer.getStages().get(i);
                if (node != null) validateApproval(node.getWorkBinding(), "stages[" + i + "].workBinding", issues);
            }
        if (designer != null && designer.getTasks() != null)
            for (int i = 0; i < designer.getTasks().size(); i++) {
                var node = designer.getTasks().get(i);
                if (node != null) validateApproval(node.getWorkBinding(), "tasks[" + i + "].workBinding", issues);
            }
        List<BindingRef> refs = collect(designer);
        if (refs.isEmpty()) return List.copyOf(issues);

        Map<Long, BusinessViewRevision> revisions = new LinkedHashMap<>();
        if (lockForPublish) {
            List<BusinessViewQueryApi.Query> queries = refs.stream()
                    .filter(ref -> ref.query() != null && ref.revisionId() > 0)
                    .collect(java.util.stream.Collectors.toMap(BindingRef::revisionId, BindingRef::query,
                            (left, right) -> left, LinkedHashMap::new))
                    .values().stream().toList();
            if (!queries.isEmpty()) {
                try {
                    for (BusinessViewRevision revision : businessViewQueryApi.lockAndRevalidateAll(queries)) {
                        if (revision != null) revisions.put(revision.id(), revision);
                    }
                } catch (RuntimeException ex) {
                    issues.add(new Issue("businessViews", "BUSINESS_VIEW_REVALIDATION_FAILED",
                            "办理视图发布重验失败：" + safeMessage(ex)));
                    return List.copyOf(issues);
                }
            }
        } else {
            for (BindingRef ref : refs) {
                if (ref.query() == null || ref.revisionId() <= 0 || revisions.containsKey(ref.revisionId())) continue;
                try {
                    BusinessViewRevision revision = businessViewQueryApi.getRevision(ref.query());
                    if (revision != null) revisions.put(revision.id(), revision);
                } catch (RuntimeException ex) {
                    // Absence is reported at the exact binding below.
                }
            }
        }

        for (BindingRef ref : refs) {
            if (ref.query() == null || ref.revisionId() <= 0) {
                issues.add(new Issue(ref.path() + ".businessViewSnapshot.id", "BUSINESS_VIEW_ID_REQUIRED",
                        "办理视图冻结快照缺少有效精确修订ID"));
                continue;
            }
            BusinessViewRevision actual = revisions.get(ref.revisionId());
            if (actual == null) {
                issues.add(new Issue(ref.path(), "BUSINESS_VIEW_UNAVAILABLE",
                        "办理视图修订不可用或已变化：" + ref.revisionId()));
                continue;
            }
            compare(ref, actual, issues);
        }
        return issues;
    }

    private void validateApproval(TemplateDesignerDocument.WorkBindingSpec binding, String path, List<Issue> issues) {
        if (binding == null || !"APPROVAL".equals(binding.getType())) return;
        ApprovalWorkBindingSchema.Definition definition;
        try {
            definition = ApprovalWorkBindingSchema.read(binding.getApprovalDefinitionKey(), binding.getParameters());
        } catch (IllegalArgumentException invalid) {
            issues.add(new Issue(path, "APPROVAL_DEFINITION_REQUIRED", invalid.getMessage()));
            return;
        }
        validateProcessDefinition(definition.key(), definition.id(), path, issues);
    }

    private void validateProcessDefinition(String key, String id, String path, List<Issue> issues) {
        try {
            var actual = processes.inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(
                    TenantContextHolder.getRequiredTenantId(), key, id));
            if (actual == null || !actual.selectable() || !id.equals(actual.processDefinitionId())
                    || !Objects.equals(key, actual.processDefinitionKey())) throw new IllegalStateException("APPROVAL_DEFINITION_UNAVAILABLE");
        } catch (RuntimeException unavailable) {
            issues.add(new Issue(path, "APPROVAL_DEFINITION_UNAVAILABLE", "所选精确审批定义不可用或不匹配，请重新选择；不会改用最新版本"));
        }
    }

    private List<BindingRef> collect(TemplateDesignerDocument designer) {
        List<BindingRef> refs = new ArrayList<>();
        if (designer == null) return refs;
        if (designer.getStages() != null) {
            for (int i = 0; i < designer.getStages().size(); i++) {
                TemplateDesignerDocument.StageNode node = designer.getStages().get(i);
                if (node != null) add(refs, node.getWorkBinding(), "stages[" + i + "].workBinding");
            }
        }
        if (designer.getTasks() != null) {
            for (int i = 0; i < designer.getTasks().size(); i++) {
                TemplateDesignerDocument.TaskNode node = designer.getTasks().get(i);
                if (node != null) add(refs, node.getWorkBinding(), "tasks[" + i + "].workBinding");
            }
        }
        return refs;
    }

    private void add(List<BindingRef> refs, TemplateDesignerDocument.WorkBindingSpec binding, String path) {
        if (binding == null || binding.getBusinessViewSnapshot() == null
                || binding.getBusinessViewSnapshot().isNull()) return;
        JsonNode snapshot = binding.getBusinessViewSnapshot();
        Long revisionId = positiveLong(snapshot.path("id"));
        if (revisionId == null) {
            refs.add(new BindingRef(path, -1L, null, binding, snapshot));
            return;
        }
        Integer expectedVersion = snapshot.path("version").canConvertToInt()
                ? snapshot.path("version").intValue() : null;
        refs.add(new BindingRef(path, revisionId,
                new BusinessViewQueryApi.Query(revisionId,
                        BusinessViewQueryApi.Purpose.NEW_REFERENCE, expectedVersion), binding, snapshot));
    }

    private void compare(BindingRef ref, BusinessViewRevision actual, List<Issue> issues) {
        if (!"PUBLISHED".equals(actual.status()) || actual.disabledAt() != null) {
            issues.add(new Issue(ref.path(), "BUSINESS_VIEW_NOT_PUBLISHED",
                    "办理视图不是当前可用于新模板的已发布修订"));
        }
        same(ref, "ownerContext", ref.binding().getTargetContextCode(), actual.ownerContext(), issues);
        same(ref, "entityType", ref.binding().getTargetObjectType(), actual.entityType(), issues);
        same(ref, "componentKey", ref.binding().getComponentKey(), actual.componentKey(), issues);
        same(ref, "componentVersion", text(ref.snapshot(), "componentVersion"), actual.componentVersion(), issues);
        same(ref, "dynamicFormRevisionId", longText(ref.binding().getDynamicFormRevisionId()),
                actual.dynamicFormRevisionId() == null ? null : String.valueOf(actual.dynamicFormRevisionId()), issues);
        same(ref, "snapshot.ownerContext", text(ref.snapshot(), "ownerContext"), actual.ownerContext(), issues);
        same(ref, "snapshot.entityType", text(ref.snapshot(), "entityType"), actual.entityType(), issues);
        same(ref, "snapshot.componentKey", text(ref.snapshot(), "componentKey"), actual.componentKey(), issues);
        same(ref, "snapshot.componentVersion", text(ref.snapshot(), "componentVersion"), actual.componentVersion(), issues);
        same(ref, "snapshot.version", text(ref.snapshot(), "version"), String.valueOf(actual.version()), issues);
        same(ref, "snapshot.revisionNo", text(ref.snapshot(), "revisionNo"), String.valueOf(actual.revisionNo()), issues);
        String parameterViewId = ref.binding().getParameters() == null ? null
                : ref.binding().getParameters().path("businessViewRevisionId").asText(null);
        if (parameterViewId != null && !parameterViewId.equals(String.valueOf(actual.id()))) {
            issues.add(new Issue(ref.path() + ".parameters.businessViewRevisionId", "BUSINESS_VIEW_ID_MISMATCH",
                    "WorkBinding参数中的办理视图修订与冻结快照不一致"));
        }
    }

    private void same(BindingRef ref, String field, String expected, String actual, List<Issue> issues) {
        if (!Objects.equals(expected, actual)) {
            issues.add(new Issue(ref.path() + "." + field, "BUSINESS_VIEW_SNAPSHOT_MISMATCH",
                    "办理视图冻结字段已变化，期望=" + expected + "，实际=" + actual));
        }
    }

    private Long positiveLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            long value = Long.parseLong(node.asText());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String longText(Long value) { return value == null ? null : String.valueOf(value); }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private record BindingRef(String path, Long revisionId, BusinessViewQueryApi.Query query,
                              TemplateDesignerDocument.WorkBindingSpec binding, JsonNode snapshot) { }
}
