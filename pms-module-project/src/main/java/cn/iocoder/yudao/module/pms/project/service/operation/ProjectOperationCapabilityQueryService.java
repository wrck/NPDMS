package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationAccessProvider;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationRuleEvaluator.Evaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Queries three independent axes: Owner authority, project eligibility, and rule outcomes. No command is called. */
@Service
@RequiredArgsConstructor
public class ProjectOperationCapabilityQueryService {
    private final ProjectOperationContextResolver contexts;
    private final ProjectBusinessOperationRegistry operations;
    private final List<ProjectBusinessOperationAccessProvider> owners;
    private final ProjectOperationRuleEvaluator evaluator;
    private final BusinessViewQueryApi views;

    @Transactional(readOnly = true)
    public ProjectOperationCapabilities inspect(Long projectId, String kind, Long nodeId, String objectId,
            ProjectBusinessExecutionSelection expected) {
        if (objectId != null && (objectId.isBlank() || objectId.length() > 128)) throw new IllegalArgumentException("BUSINESS_OBJECT_ID_INVALID");
        var context = contexts.resolve(projectId, kind, nodeId, expected);
        var binding = context.binding();
        var presentation = presentation(binding);
        if (context.reason() != null) return new ProjectOperationCapabilities(context.node(), context.selection(),
                List.of(), presentation, null, context.reason());
        if (binding == null || binding.getOperationContract() == null)
            return new ProjectOperationCapabilities(context.node(), context.selection(), List.of(), presentation, null, "LEGACY_BINDING");
        FrozenOperationContract contract;
        try { contract = FrozenOperationContract.read(binding.getOperationContract()); }
        catch (RuntimeException invalid) { return new ProjectOperationCapabilities(context.node(), context.selection(),
                List.of(), presentation, null, "FROZEN_OPERATION_CONTRACT_INVALID"); }
        Set<String> ownerActions = Set.of();
        String ownerError = null, factVersion = null;
        var providers = owners.stream().filter(provider -> Objects.equals(binding.getTargetContextCode(), provider.ownerContext())
                && Objects.equals(binding.getTargetObjectType(), provider.objectType())).toList();
        if (providers.size() != 1) ownerError = "OWNER_OPERATION_PROVIDER_NOT_UNIQUE";
        else try {
            var access = providers.getFirst().inspect(new ProjectBusinessOperationAccessProvider.Context(
                    context.tenantId(), context.actorId(), projectId, objectId));
            if (access == null) throw new IllegalStateException("OWNER_ACCESS_UNAVAILABLE");
            ownerActions = access.permittedOperations(); factVersion = access.objectFactVersion();
        } catch (RuntimeException unavailable) { ownerError = "OWNER_OPERATION_UNAVAILABLE_OR_FORBIDDEN"; }
        var actions = new ArrayList<ProjectOperationCapabilities.Action>();
        for (var operation : contract.declaration().operations()) {
            var descriptor = operations.find(operation.operationCode(), operation.operationVersion());
            if (descriptor == null || !Objects.equals(binding.getTargetContextCode(), descriptor.ownerContext())
                    || !Objects.equals(binding.getTargetObjectType(), descriptor.objectType())) {
                actions.add(ProjectOperationCapabilities.combine(operation.operationCode(), operation.operationVersion(), operation.operationCode(),
                        false, context.permitted(), false, Evaluation.unknown("OPERATION_NOT_DEPLOYED"), Evaluation.pending(), "OPERATION_NOT_DEPLOYED"));
                continue;
            }
            String reference = "project:" + projectId + ":plan:" + context.round().getPlanVersionId()
                    + ":execution:" + context.round().getId() + ":operation:" + descriptor.operationCode() + ":v" + descriptor.operationVersion();
            var pre = evaluator.evaluate(reference, contract, operation.pre(), context.project());
            var post = "NONE".equals(operation.post().mode()) ? new Evaluation("NO_ADDITIONAL_RULE", null) : Evaluation.pending();
            actions.add(ProjectOperationCapabilities.combine(descriptor.operationCode(), descriptor.operationVersion(), descriptor.label(),
                    ownerActions.contains(descriptor.operationCode()), context.permitted(), operations.runtimeAvailable(descriptor.operationCode(), descriptor.operationVersion()),
                    pre, post, ownerError));
        }
        return new ProjectOperationCapabilities(context.node(), context.selection(), actions, presentation, factVersion, ownerError);
    }

    private ProjectOperationCapabilities.Presentation presentation(TemplateExecutionSnapshot.BindingContract binding) {
        if (binding == null || binding.getBusinessViewSnapshot() == null)
            return new ProjectOperationCapabilities.Presentation(null, "UNAVAILABLE", "VIEW_NOT_BOUND");
        try {
            var frozen = JsonUtils.convertObject(binding.getBusinessViewSnapshot(), BusinessViewRevision.class);
            if (frozen == null || frozen.id() == null) throw new IllegalArgumentException("VIEW_INVALID");
            var current = views.getRevision(new BusinessViewQueryApi.Query(frozen.id(), BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
            if (current == null || !Objects.equals(current.id(), frozen.id())
                    || !Objects.equals(current.ownerContext(), binding.getTargetContextCode())
                    || !Objects.equals(current.entityType(), binding.getTargetObjectType())
                    || !Objects.equals(current.componentKey(), binding.getComponentKey())
                    || !Objects.equals(current.componentVersion(), frozen.componentVersion())
                    || !Objects.equals(current.dynamicFormRevisionId(), frozen.dynamicFormRevisionId())
                    || !Set.of("PUBLISHED", "DISABLED").contains(current.status())) throw new IllegalArgumentException("VIEW_INVALID");
            return new ProjectOperationCapabilities.Presentation(JsonUtils.parseTree(JsonUtils.toJsonString(current)),
                    "DISABLED".equals(current.status()) ? "READ_ONLY" : "AVAILABLE", null);
        } catch (RuntimeException unavailable) {
            return new ProjectOperationCapabilities.Presentation(null, "UNAVAILABLE", "VIEW_UNAVAILABLE");
        }
    }
}
