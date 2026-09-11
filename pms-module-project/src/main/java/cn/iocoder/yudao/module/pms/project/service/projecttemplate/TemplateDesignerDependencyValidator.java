package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
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
 * every selected BusinessView revision is the exact currently-publishable registration. This guard
 * never grants object permissions and never queries Owner business instances.</p>
 */
@Component
@RequiredArgsConstructor
public class TemplateDesignerDependencyValidator {

    private final BusinessViewQueryApi businessViewQueryApi;

    public List<Issue> validate(TemplateDesignerDocument designer, boolean lockForPublish) {
        List<BindingRef> refs = collect(designer);
        if (refs.isEmpty()) return List.of();

        Map<Long, BusinessViewRevision> revisions = new LinkedHashMap<>();
        if (lockForPublish) {
            List<BusinessViewQueryApi.Query> queries = refs.stream()
                    .collect(java.util.stream.Collectors.toMap(BindingRef::revisionId, BindingRef::query,
                            (left, right) -> left, LinkedHashMap::new))
                    .values().stream().toList();
            try {
                for (BusinessViewRevision revision : businessViewQueryApi.lockAndRevalidateAll(queries)) {
                    if (revision != null) revisions.put(revision.id(), revision);
                }
            } catch (RuntimeException ex) {
                return List.of(new Issue("businessViews", "BUSINESS_VIEW_REVALIDATION_FAILED",
                        "办理视图发布重验失败：" + safeMessage(ex)));
            }
        } else {
            for (BindingRef ref : refs) {
                if (revisions.containsKey(ref.revisionId())) continue;
                try {
                    BusinessViewRevision revision = businessViewQueryApi.getRevision(ref.query());
                    if (revision != null) revisions.put(revision.id(), revision);
                } catch (RuntimeException ex) {
                    revisions.put(ref.revisionId(), null);
                }
            }
        }

        List<Issue> issues = new ArrayList<>();
        for (BindingRef ref : refs) {
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
            // Keep a synthetic invalid ref so validation returns a stable issue at the exact node.
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
        if (ref.query() == null || ref.revisionId() <= 0) {
            issues.add(new Issue(ref.path() + ".businessViewSnapshot.id", "BUSINESS_VIEW_ID_REQUIRED",
                    "办理视图冻结快照缺少有效精确修订ID"));
            return;
        }
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
