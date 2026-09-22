package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RequirementAnalysisPresentations {
    private final RequirementAnalysisAccess access;
    private final EntityPresentationApi presentations;
    private final EntityExtensionApi extensions;
    private final ConfigApi configs;
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi bindings;

    public Selection list(Long revisionId, EntityActor actor) { return list(revisionId, actor, null, null); }

    public Selection list(Long revisionId, EntityActor actor, Long stageId, Long taskId) {
        if (stageId != null && taskId != null) throw new IllegalArgumentException("Select one presentation context");
        var row = access.read(revisionId, actor);
        var target = EntityDataRef.revision(row.revisionRef());
        var extra = extensions.read(target, actor);
        Set<String> extensionCodes = new HashSet<>(extra.fields().keySet());
        if (extra.definitionRevisionId() != null) extensions.definition(extra.definitionRevisionId(), row.entityRef(), actor)
                .fields().forEach(field -> extensionCodes.add(field.code()));
        List<Option> options = new ArrayList<>();
        for (var candidate : presentations.list(new EntityPresentationApi.Query(target, actor, "REQUIREMENT_ANALYSIS"))) {
            Map<String, String> mapping = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            boolean compatible = true;
            for (var field : candidate.fields()) {
                if (!seen.add(field.fieldKey())) { compatible = false; break; }
                if (field.controlledFile()) {
                    if (!RequirementAnalysisFields.attachmentKeys().contains(field.fieldKey())) { compatible = false; break; }
                    continue;
                }
                String property = RequirementAnalysisFields.property(field.fieldKey());
                if (property == null && extensionCodes.contains(field.fieldKey())) property = field.fieldKey();
                if (property == null || mapping.containsValue(property)) { compatible = false; break; }
                final String code = property;
                var entityField = RequirementAnalysisEntityProvider.FIELDS.fields().stream().filter(value -> value.code().equals(code)).findFirst();
                if (entityField.isPresent() && (entityField.get().type() == EntityField.Type.OBJECT_LIST && !"group".equals(field.componentType())
                        || entityField.get().type() == EntityField.Type.TEXT_LIST && !"checkbox".equals(field.componentType()))) {
                    compatible = false; break;
                }
                mapping.put(field.fieldKey(), property);
            }
            if (!compatible || mapping.isEmpty()) continue;
            var binding = new EntityFormApi.Binding(candidate.revisionId(), extra.definitionRevisionId(), Map.copyOf(mapping), 0);
            options.add(new Option(candidate.name(), new EntityFormApi.Layout(binding, candidate.templateId(), candidate.revisionNo(),
                    candidate.version(), candidate.engineCode(), candidate.designerVersion(), candidate.rendererVersion(),
                    candidate.formConfJson(), candidate.formRulesJson(), candidate.fields())));
        }
        Long preferred = null;
        String configured = configs.getConfigValueByKey("pms.requirement-analysis.form-template-id." + actor.tenantId());
        if (configured != null) try { preferred = Long.valueOf(configured); } catch (NumberFormatException ignored) { }
        Long configuredRevision = null;
        if (stageId != null || taskId != null) try {
            var selected = stageId != null ? bindings.inspectStage(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactQuery(
                    row.getProjectId(), stageId, cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS))
                    : bindings.inspectTask(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTaskFactQuery(
                    row.getProjectId(), taskId, cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
            if (selected != null && Objects.equals(selected.projectId(), row.getProjectId())) configuredRevision = selected.dynamicFormTemplateRevisionId();
        } catch (RuntimeException unavailable) {
            // An unavailable display default cannot revoke business capabilities.
        }
        return new Selection(preferred, configuredRevision, List.copyOf(options));
    }

    public record Option(String name, EntityFormApi.Layout layout) { }
    public record Selection(Long defaultTemplateId, Long defaultRevisionId, List<Option> options) { }
}
