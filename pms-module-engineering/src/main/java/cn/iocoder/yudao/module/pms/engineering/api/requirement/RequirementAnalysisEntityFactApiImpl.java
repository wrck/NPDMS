package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisRevisionFiles;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class RequirementAnalysisEntityFactApiImpl implements RequirementAnalysisEntityFactApi {
    private static final ProjectWorkBindingTarget WORK_BINDING_TARGET = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
    private final RequirementAnalysisMapper mapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectOrganizationFactApi organizationFactApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final RequirementAnalysisEntityQueryService queries;
    private final RequirementAnalysisRevisionFiles files;

    @Override
    @Transactional(readOnly = true)
    public Fact inspect(Query query) {
        requireQuery(query);
        var actor = trustedActor();
        requireQueryPermission(actor);
        requireScope(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.actorId(),
                query.projectId(), ProjectScopeApi.ACTION_VIEW)), query.projectId());
        var project = organizationFactApi.inspect(new ProjectOrganizationFactQuery(query.projectId()));
        requireProject(project, query.projectId());
        var selected = query.revisionId() == null
                ? mapper.selectEffective(new RequirementProjectQuery(actor.tenantId(), query.projectId()))
                : mapper.selectRevision(new RequirementRevisionQuery(actor.tenantId(), query.revisionId()));
        if (selected == null && query.revisionId() == null) return null;
        requireCompleted(selected, query);
        var origin = originBinding(selected);
        var binding = requireBinding(origin.projectStageId() != null
                ? workBindingFactApi.inspectStage(new ProjectWorkBindingStageFactQuery(query.projectId(), origin.projectStageId(), WORK_BINDING_TARGET))
                : workBindingFactApi.inspectTask(new ProjectWorkBindingTaskFactQuery(query.projectId(), origin.projectTaskId(), WORK_BINDING_TARGET)), project);
        requireOriginNode(selected, binding);
        return fact(selected, project, binding, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Fact lockAndRevalidate(Fact expected) {
        requireQuery(expected);
        var actor = trustedActor();
        requireQueryPermission(actor);
        var scope = requireScope(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.actorId(),
                expected.projectId(), ProjectScopeApi.ACTION_VIEW)), expected.projectId());
        requireScope(projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.actorId(),
                expected.projectId(), ProjectScopeApi.ACTION_VIEW, scope.treeVersion())), expected.projectId());
        var project = organizationFactApi.lockAndRevalidate(new ProjectOrganizationFactRevalidationQuery(
                expected.projectId(), expected.projectVersion()));
        requireProject(project, expected.projectId());
        var prior = expected.workBinding();
        if (prior == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        var binding = requireBinding(prior.projectStageId() != null
                ? workBindingFactApi.lockAndRevalidateStage(new ProjectWorkBindingStageFactRevalidationQuery(expected.projectId(),
                    prior.projectStageId(), prior.executionContractId(), prior.projectStageVersion(), prior.executionContractVersion(),
                    expected.projectVersion(), WORK_BINDING_TARGET))
                : workBindingFactApi.lockAndRevalidate(new ProjectWorkBindingFactRevalidationQuery(expected.projectId(),
                    prior.projectTaskId(), prior.executionContractId(), prior.projectTaskVersion(), prior.executionContractVersion(),
                    expected.projectVersion(), WORK_BINDING_TARGET)), project);
        mapper.lockCurrent(new RequirementEntityQuery(actor.tenantId(), expected.entityId()));
        var selected = mapper.lockRevision(new RequirementRevisionQuery(actor.tenantId(), expected.revisionId()));
        requireCompleted(selected, new Query(expected.projectId(), expected.entityId(), expected.revisionId()));
        requireOriginNode(selected, binding);
        files.lockForFreeze(selected.revisionRef(), new EntityActor(actor.tenantId(), actor.actorId(), null));
        var current = fact(selected, project, binding, actor);
        if (!Objects.equals(expected, current)) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return current;
    }

    private Fact fact(RequirementAnalysisRevisionDO selected, ProjectOrganizationFact project,
                      ProjectWorkBindingFact binding, TrustedActor actor) {
        var view = queries.revision(selected.getId(), new EntityActor(actor.tenantId(), actor.actorId(), null));
        var effective = mapper.selectEffective(new RequirementProjectQuery(actor.tenantId(), selected.getProjectId()));
        var form = view.form();
        if (!Objects.equals(selected.getProjectTemplateId(), binding.projectTemplateId())
                || !Objects.equals(selected.getProjectTemplateRevisionId(), binding.templateRevisionId())
                || selected.effective() && form != null && (!Objects.equals(form.templateId(), binding.dynamicFormTemplateId())
                || !Objects.equals(form.binding().formRevisionId(), binding.dynamicFormTemplateRevisionId())))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        var fileFacts = view.attachments().stream().flatMap(set -> set.activeFacts().stream())
                .sorted(Comparator.comparing(f -> f.referenceKey())).map(f -> new RequirementAnalysisFileFact(
                    f.artifactId(), f.versionNo(), f.referenceKey(), f.fileFactVersion().artifactVersion(),
                    f.fileFactVersion().referenceVersion(), f.fileFactVersion().availabilityVersion(), f.scopeVersion())).toList();
        return new Fact(selected.getProjectId(), selected.getEntityId(), selected.getId(), selected.getRevisionNo(),
                selected.getVersion(), project.projectVersion(), selected.getProjectTemplateRevisionId(), workBindingFact(binding),
                form == null ? null : new Form(form.binding().formRevisionId(), form.binding().version(),
                    form.binding().extensionDefinitionRevisionId(), form.binding().fieldBindings()),
                view.extensionDefinitionRevisionId(), view.extensionValueVersion(), view.values(), fileFacts,
                selected.getFrozenBy(), selected.getFrozenAt(), effective == null ? null : effective.getId());
    }

    private void requireCompleted(RequirementAnalysisRevisionDO selected, Query query) {
        if (selected == null || !Objects.equals(selected.getProjectId(), query.projectId())
                || query.entityId() != null && !Objects.equals(selected.getEntityId(), query.entityId())
                || !"FROZEN".equals(selected.getRevisionState()) || selected.getRevisionNo() == null
                || selected.getVersion() == null || selected.getFrozenBy() == null || selected.getFrozenAt() == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
    }

    private ProjectWorkBindingFact requireBinding(ProjectWorkBindingFact binding, ProjectOrganizationFact project) {
        if (binding == null || !Objects.equals(binding.projectId(), project.projectId())
                || !Objects.equals(binding.projectVersion(), project.projectVersion())
                || (binding.projectTaskId() == null) == (binding.projectStageId() == null)
                || (binding.projectStageId() != null ? binding.projectStageVersion() == null : binding.projectTaskVersion() == null)
                || binding.executionContractId() == null || binding.contractVersion() == null
                || binding.projectTemplateId() == null
                || !Objects.equals(binding.workBindingTypeCode(), WORK_BINDING_TARGET.workBindingTypeCode())
                || !Objects.equals(binding.targetContextCode(), WORK_BINDING_TARGET.targetContextCode())
                || !Objects.equals(binding.targetObjectType(), WORK_BINDING_TARGET.targetObjectType())
                || !Objects.equals(binding.targetObjectKey(), WORK_BINDING_TARGET.targetObjectKey())
                || binding.dynamicFormTemplateId() == null || binding.dynamicFormTemplateRevisionId() == null
                || binding.dynamicFormRevisionNo() == null || binding.dynamicFormRevisionFactVersion() == null) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        return binding;
    }

    private RequirementAnalysisWorkBindingFact workBindingFact(ProjectWorkBindingFact binding) {
        return new RequirementAnalysisWorkBindingFact(binding.projectTaskId(), binding.projectTaskVersion(),
                binding.executionContractId(), binding.contractVersion(), binding.projectTemplateId(),
                binding.sourceDefinitionVersion(), binding.templateRevisionId(), binding.templateRevisionNo(),
                binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion(),
                binding.workBindingTypeCode(), binding.targetContextCode(), binding.targetObjectType(), binding.targetObjectKey(),
                binding.projectStageId(), binding.projectStageVersion());
    }

    private ProjectWorkBindingFact originBinding(RequirementAnalysisRevisionDO root) {
        var frozen = root.getExecutionSnapshot() == null ? null : JsonUtils.parseObject(root.getExecutionSnapshot(),
                cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisExecutionAccess.Frozen.class);
        var origin = frozen == null ? null : frozen.binding();
        if (origin == null || !Objects.equals(root.getProjectId(), origin.projectId())
                || (origin.projectTaskId() == null) == (origin.projectStageId() == null))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return origin;
    }

    private void requireOriginNode(RequirementAnalysisRevisionDO root, ProjectWorkBindingFact current) {
        var origin = originBinding(root);
        if (!Objects.equals(origin.projectTaskId(), current.projectTaskId())
                || !Objects.equals(origin.projectStageId(), current.projectStageId()))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
    }

    private ProjectScopeResult requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        return scope;
    }

    private void requireProject(ProjectOrganizationFact project, Long projectId) {
        if (project == null || !Objects.equals(project.projectId(), projectId) || project.projectVersion() == null) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
    }

    private void requireQuery(Object query) {
        if (query == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
    }

    private TrustedActor trustedActor() {
        Long tenantId = TenantContextHolder.getTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (tenantId == null || actorId == null || actorId <= 0) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        return new TrustedActor(tenantId, actorId);
    }

    private void requireQueryPermission(TrustedActor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), "pms:requirement-analysis:query")) {
            throw exception(FORBIDDEN);
        }
    }

    private record TrustedActor(Long tenantId, Long actorId) {}
}
