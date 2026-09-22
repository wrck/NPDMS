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
    private final RequirementAnalysisMapper mapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectOrganizationFactApi organizationFactApi;
    private final RequirementAnalysisEntityQueryService queries;
    private final RequirementAnalysisRevisionFiles files;
    private final cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisExecutionAccess executionAccess;

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
        if (independentOrigin(selected) != null) return fact(selected, project, null, actor);
        var binding = originBinding(selected);
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
        mapper.lockCurrent(new RequirementEntityQuery(actor.tenantId(), expected.entityId()));
        var selected = mapper.lockRevision(new RequirementRevisionQuery(actor.tenantId(), expected.revisionId()));
        requireCompleted(selected, new Query(expected.projectId(), expected.entityId(), expected.revisionId()));
        var binding = independentOrigin(selected) == null ? originBinding(selected) : null;
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
        var independent = binding == null ? independentOrigin(selected) : null;
        if (binding == null && independent == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        Long templateId = binding == null ? independent.projectTemplateId() : binding.projectTemplateId();
        Long templateRevisionId = binding == null ? independent.templateRevisionId() : binding.templateRevisionId();
        if (!Objects.equals(selected.getProjectTemplateId(), templateId)
                || !Objects.equals(selected.getProjectTemplateRevisionId(), templateRevisionId))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        var fileFacts = view.attachments().stream().flatMap(set -> set.activeFacts().stream())
                .sorted(Comparator.comparing(f -> f.referenceKey())).map(f -> new RequirementAnalysisFileFact(
                    f.artifactId(), f.versionNo(), f.referenceKey(), f.fileFactVersion().artifactVersion(),
                    f.fileFactVersion().referenceVersion(), f.fileFactVersion().availabilityVersion(), f.scopeVersion())).toList();
        return new Fact(selected.getProjectId(), selected.getEntityId(), selected.getId(), selected.getRevisionNo(),
                selected.getVersion(), project.projectVersion(), selected.getProjectTemplateRevisionId(), binding == null ? null : workBindingFact(binding),
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
        var target = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
        if (origin == null || !Objects.equals(root.getProjectId(), origin.projectId())
                || (origin.projectTaskId() == null) == (origin.projectStageId() == null)
                || !Objects.equals(target.workBindingTypeCode(), origin.workBindingTypeCode())
                || !Objects.equals(target.targetContextCode(), origin.targetContextCode())
                || !Objects.equals(target.targetObjectType(), origin.targetObjectType())
                || !Objects.equals(target.targetObjectKey(), origin.targetObjectKey())
                || origin.executionContractId() == null || origin.contractVersion() == null)
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return origin;
    }

    /** Independent results freeze Owner configuration without inventing a task/stage origin. */
    private cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisExecutionAccess.Frozen independentOrigin(
            RequirementAnalysisRevisionDO root) {
        var frozen = root.getExecutionSnapshot() == null ? null : JsonUtils.parseObject(root.getExecutionSnapshot(),
                cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisExecutionAccess.Frozen.class);
        if (frozen == null || !frozen.independent()) return null;
        frozen = executionAccess.frozen(root.getProjectId(), root.getExecutionSnapshot());
        if (!Objects.equals(root.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(root.getTenantId(), frozen.configurationTenantId())
                || !Objects.equals(root.getProjectTemplateId(), frozen.projectTemplateId())
                || !Objects.equals(root.getProjectTemplateRevisionId(), frozen.templateRevisionId()))
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return frozen;
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
