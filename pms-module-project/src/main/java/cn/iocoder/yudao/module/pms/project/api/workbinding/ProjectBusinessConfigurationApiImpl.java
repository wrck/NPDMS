package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Read-only configuration projection. It never chooses an active node or recompiles a publication. */
@Service
@RequiredArgsConstructor
public class ProjectBusinessConfigurationApiImpl implements ProjectBusinessConfigurationApi {
    private final ProjectMasterMapper projects;
    private final ProjectTemplateService templates;
    private final ProjectScopeApi scopes;

    @Override
    public Configuration resolve(Query query) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (query == null || !Objects.equals(tenant, query.tenantId()) || !positive(query.actorId())
                || !positive(query.projectId()) || !valid(query.target())) throw exception(PROJECT_TASK_QUERY_INVALID);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, query.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(query.projectId()))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var project = projects.selectById(query.projectId());
        if (project == null || !Objects.equals(tenant, project.getTenantId()) || !Objects.equals(query.projectId(), project.getId())
                || !positive(project.getLifecycleTemplateId()) || !positive(project.getLifecycleTemplateRevisionId())
                || project.getLifecycleTemplateRevisionNo() == null || project.getLifecycleTemplateRevisionNo() <= 0)
            throw exception(PROJECT_TASK_QUERY_INVALID);
        var publication = templates.getRevisionById(project.getLifecycleTemplateRevisionId());
        if (publication == null || !"PUBLISHED".equals(publication.getStatus()) || !Objects.equals(tenant, publication.getTenantId())
                || !Objects.equals(project.getLifecycleTemplateId(), publication.getTemplateId())
                || !Objects.equals(project.getLifecycleTemplateRevisionId(), publication.getId())
                || !Objects.equals(project.getLifecycleTemplateRevisionNo(), publication.getRevisionNo()))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        var snapshot = templates.getExecutionSnapshot(publication.getTemplateId(), publication.getRevisionNo());
        if (snapshot == null || snapshot.getStages() == null || snapshot.getTasks() == null)
            throw new IllegalStateException("BUSINESS_CONFIGURATION_UNAVAILABLE");
        List<TemplateExecutionSnapshot.BindingContract> candidates = new ArrayList<>();
        snapshot.getStages().forEach(node -> { if (node != null && matches(node.getBinding(), query.target())) candidates.add(node.getBinding()); });
        snapshot.getTasks().forEach(node -> { if (node != null && matches(node.getBinding(), query.target())) candidates.add(node.getBinding()); });
        if (candidates.isEmpty()) throw new IllegalStateException("BUSINESS_CONFIGURATION_UNAVAILABLE");
        var first = candidates.getFirst();
        // Shared nodes may have different rules/views. Only the Owner's actual configuration must agree.
        if (candidates.stream().anyMatch(candidate -> !Objects.equals(first.getDynamicFormRevisionId(), candidate.getDynamicFormRevisionId())
                || !Objects.equals(parameters(first), parameters(candidate))))
            throw new IllegalStateException("BUSINESS_CONFIGURATION_AMBIGUOUS");
        return new Configuration(tenant, project.getId(), publication.getTemplateId(), publication.getId(), publication.getRevisionNo(),
                first.getDynamicFormRevisionId(), JsonUtils.toJsonString(parameters(first)));
    }

    private JsonNode parameters(TemplateExecutionSnapshot.BindingContract binding) {
        return binding.getParameters() == null || binding.getParameters().isNull() ? null : binding.getParameters();
    }
    private boolean matches(TemplateExecutionSnapshot.BindingContract binding, ProjectWorkBindingTarget target) {
        return binding != null && Objects.equals(binding.getType(), target.workBindingTypeCode())
                && Objects.equals(binding.getTargetContextCode(), target.targetContextCode())
                && Objects.equals(binding.getTargetObjectType(), target.targetObjectType())
                && Objects.equals(binding.getTargetObjectKey(), target.targetObjectKey());
    }
    private boolean valid(ProjectWorkBindingTarget target) {
        return target != null && text(target.workBindingTypeCode()) && text(target.targetContextCode())
                && text(target.targetObjectType()) && text(target.targetObjectKey());
    }
    private boolean text(String value) { return value != null && !value.isBlank(); }
    private boolean positive(Long value) { return value != null && value > 0; }
}
