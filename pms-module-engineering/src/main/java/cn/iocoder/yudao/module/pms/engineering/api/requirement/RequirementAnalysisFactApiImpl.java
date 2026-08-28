package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
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
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID;

/** 向SCH-01公开明确完成PRE-04及其冻结PLATFORM实例事实。 */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisFactApiImpl implements RequirementAnalysisFactApi {
    private static final DynamicFormProviderKey PROVIDER = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
    private static final String FILE_PURPOSE_PREFIX = "FORM_FIELD_ATTACHMENT/";
    private static final ProjectWorkBindingTarget WORK_BINDING_TARGET =
            ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;

    private final RequirementAnalysisRootMapper rootMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectOrganizationFactApi organizationFactApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final DynamicFormBusinessInstanceApi dynamicFormApi;

    @Override
    @Transactional(readOnly = true)
    public RequirementAnalysisFact inspect(RequirementAnalysisFactQuery query) {
        requireQuery(query);
        TrustedActor actor = trustedActor();
        requireQueryPermission(actor);
        requireScope(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.actorId(),
                query.projectId(), ProjectScopeApi.ACTION_VIEW)), query.projectId());
        ProjectOrganizationFact project = organizationFactApi.inspect(new ProjectOrganizationFactQuery(query.projectId()));
        requireProject(project, query.projectId());
        ProjectWorkBindingFact binding = requireBinding(workBindingFactApi.inspect(
                new ProjectWorkBindingFactQuery(query.projectId(), WORK_BINDING_TARGET)), project);
        PreparationDO selected = query.preparationId() == null
                ? rootMapper.selectEffective(new RequirementAnalysisProjectQuery(actor.tenantId(), query.projectId()))
                : rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), query.preparationId()));
        if (selected == null && query.preparationId() == null) return null;
        requireCompleted(selected, query.projectId());
        PreparationDO effective = rootMapper.selectEffective(
                new RequirementAnalysisProjectQuery(actor.tenantId(), query.projectId()));
        DynamicFormInstanceFact form = inspectForm(selected, actor);
        requireComposition(selected, binding, form,
                effective != null && Objects.equals(selected.getId(), effective.getId()));
        return fact(selected, effective, project, binding, form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementAnalysisFact lockAndRevalidate(RequirementAnalysisFactRevalidationQuery query) {
        requireQuery(query);
        TrustedActor actor = trustedActor();
        requireQueryPermission(actor);
        ProjectScopeResult inspectedScope = requireScope(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW)), query.projectId());
        requireScope(projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(),
                actor.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW, inspectedScope.treeVersion())),
                query.projectId());
        ProjectOrganizationFact project = organizationFactApi.lockAndRevalidate(
                new ProjectOrganizationFactRevalidationQuery(query.projectId(), query.expectedProjectVersion()));
        requireProject(project, query.projectId());
        requireExpectedQuery(query);
        RequirementAnalysisWorkBindingFact expectedBinding = query.expectedFactVector().workBindingFact();
        ProjectWorkBindingFact binding = requireBinding(workBindingFactApi.lockAndRevalidate(
                new ProjectWorkBindingFactRevalidationQuery(query.projectId(), expectedBinding.projectTaskId(),
                        expectedBinding.executionContractId(), expectedBinding.projectTaskVersion(),
                        expectedBinding.executionContractVersion(), query.expectedProjectVersion(),
                        WORK_BINDING_TARGET)), project);
        PreparationDO selected = rootMapper.selectForUpdate(
                new RequirementAnalysisRowQuery(actor.tenantId(), query.preparationId()));
        requireCompleted(selected, query.projectId());
        requireExpectedRoot(query, selected, project);
        PreparationDO effective = rootMapper.selectEffectiveForUpdate(
                new RequirementAnalysisProjectQuery(actor.tenantId(), query.projectId()));
        DynamicFormInstanceFact inspected = inspectForm(selected, actor);
        DynamicFormInstanceFact locked = dynamicFormApi.lockAndRevalidateInstance(
                new DynamicFormInstanceRevalidationQuery(actor.actorId(), inspected));
        requireComposition(selected, binding, locked,
                effective != null && Objects.equals(selected.getId(), effective.getId()));
        RequirementAnalysisFact current = fact(selected, effective, project, binding, locked);
        if (!Objects.equals(query.expectedFactVector(), current.factVector())) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
        return current;
    }

    private DynamicFormInstanceFact inspectForm(PreparationDO root, TrustedActor actor) {
        return dynamicFormApi.inspectInstance(new DynamicFormInstanceQuery(actor.tenantId(), actor.actorId(),
                PROVIDER, new DynamicFormOwnerKey(PROVIDER.ownerContext(), PROVIDER.objectType(),
                String.valueOf(root.getId())), root.getDynamicFormInstanceId(), DynamicFormBusinessAction.READ));
    }

    private RequirementAnalysisFact fact(PreparationDO selected, PreparationDO effective,
                                         ProjectOrganizationFact project, ProjectWorkBindingFact binding,
                                         DynamicFormInstanceFact form) {
        Map<String, List<RequirementAnalysisFileFact>> filesByField = fileFacts(form);
        List<RequirementAnalysisSectionFact> fields = form.fields().stream()
                .sorted(Comparator.comparing(DynamicFormFieldDescriptor::fieldKey))
                .map(field -> new RequirementAnalysisSectionFact(field.fieldKey(), field.fieldKey(),
                        "DYNAMIC_FORM", field.componentType(), field.required(), 0,
                        JsonUtils.toJsonString(field), JsonUtils.toJsonString(form.ordinaryValues().get(field.fieldKey())),
                        form.instanceVersion(), filesByField.getOrDefault(field.fieldKey(), List.of())))
                .toList();
        List<RequirementAnalysisFileFact> allFiles = fields.stream().flatMap(field -> field.fileFacts().stream()).toList();
        boolean currentEffective = effective != null && Objects.equals(effective.getId(), selected.getId());
        RequirementAnalysisWorkBindingFact workBinding = workBindingFact(binding);
        RequirementAnalysisFactVector vector = new RequirementAnalysisFactVector(selected.getProjectId(),
                project.projectVersion(), workBinding, selected.getId(), selected.getBusinessVersion(),
                selected.getContentVersion(), selected.getTemplateRevisionId(), form.templateId(),
                form.templateRevisionId(), form.templateRevisionNo(), form.revisionFactVersion(),
                form.instanceId(), form.instanceVersion(), form.engineCode(), form.designerVersion(),
                form.rendererVersion(), selected.getCompletedAt(), currentEffective,
                effective == null ? null : effective.getId(),
                effective == null ? null : effective.getBusinessVersion(), fields);
        return new RequirementAnalysisFact(selected.getProjectId(), selected.getId(), selected.getBusinessVersion(),
                selected.getStatusCode(), selected.getContentVersion(), project.projectVersion(),
                selected.getTemplateRevisionId(), workBinding, form.templateId(), form.instanceId(),
                form.instanceVersion(), form.templateRevisionNo(), form.revisionFactVersion(), form.engineCode(),
                form.designerVersion(), form.rendererVersion(),
                selected.getCompletedBy(), selected.getCompletedAt(), currentEffective,
                effective == null ? null : effective.getId(), effective == null ? null : effective.getBusinessVersion(),
                fields, allFiles, vector);
    }

    private Map<String, List<RequirementAnalysisFileFact>> fileFacts(DynamicFormInstanceFact form) {
        Map<String, List<RequirementAnalysisFileFact>> result = new LinkedHashMap<>();
        for (FileReferenceSetFact set : form.controlledFileFacts()) {
            String purpose = set.key().purposeCode();
            String field = purpose.startsWith(FILE_PURPOSE_PREFIX)
                    ? purpose.substring(FILE_PURPOSE_PREFIX.length()) : purpose;
            result.put(field, set.activeFacts().stream().map(this::fileFact).toList());
        }
        return result;
    }

    private RequirementAnalysisFileFact fileFact(FileArtifactVersionFact fact) {
        return new RequirementAnalysisFileFact(fact.artifactId(), fact.versionNo(), fact.referenceKey(),
                fact.fileFactVersion().artifactVersion(), fact.fileFactVersion().referenceVersion(),
                fact.fileFactVersion().availabilityVersion(), fact.scopeVersion());
    }

    private void requireExpectedRoot(RequirementAnalysisFactRevalidationQuery query, PreparationDO selected,
                                     ProjectOrganizationFact project) {
        if (!Objects.equals(selected.getBusinessVersion(), query.expectedBusinessVersion())
                || !Objects.equals(selected.getContentVersion(), query.expectedContentVersion())
                || !Objects.equals(selected.getTemplateRevisionId(), query.expectedTemplateRevision())
                || !Objects.equals(project.projectVersion(), query.expectedProjectVersion())) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
    }

    private void requireExpectedQuery(RequirementAnalysisFactRevalidationQuery query) {
        RequirementAnalysisFactVector vector = query.expectedFactVector();
        if (!Objects.equals(vector.projectId(), query.projectId())
                || !Objects.equals(vector.preparationId(), query.preparationId())
                || !Objects.equals(vector.businessVersion(), query.expectedBusinessVersion())
                || !Objects.equals(vector.contentVersion(), query.expectedContentVersion())
                || !Objects.equals(vector.projectVersion(), query.expectedProjectVersion())
                || !Objects.equals(vector.templateRevision(), query.expectedTemplateRevision())
                || vector.workBindingFact() == null) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
    }

    private ProjectWorkBindingFact requireBinding(ProjectWorkBindingFact binding, ProjectOrganizationFact project) {
        if (binding == null || !Objects.equals(binding.projectId(), project.projectId())
                || !Objects.equals(binding.projectVersion(), project.projectVersion())
                || binding.projectTaskId() == null || binding.projectTaskVersion() == null
                || binding.executionContractId() == null || binding.contractVersion() == null
                || binding.templateTaskDefinitionId() == null || binding.sourceDefinitionVersion() == null
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

    private void requireComposition(PreparationDO root, ProjectWorkBindingFact binding,
                                    DynamicFormInstanceFact form, boolean currentEffective) {
        if (form == null || !Objects.equals(root.getDynamicFormInstanceId(), form.instanceId())
                || !Objects.equals(root.getTemplateId(), binding.templateTaskDefinitionId())
                || !Objects.equals(root.getTemplateRevisionId(), binding.templateRevisionId())
                || currentEffective && (!Objects.equals(form.templateId(), binding.dynamicFormTemplateId())
                || !Objects.equals(form.templateRevisionId(), binding.dynamicFormTemplateRevisionId())
                || !Objects.equals(form.templateRevisionNo(), binding.dynamicFormRevisionNo())
                || !Objects.equals(form.revisionFactVersion(), binding.dynamicFormRevisionFactVersion()))
                || form.engineCode() == null || form.designerVersion() == null || form.rendererVersion() == null) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
    }

    private RequirementAnalysisWorkBindingFact workBindingFact(ProjectWorkBindingFact binding) {
        return new RequirementAnalysisWorkBindingFact(binding.projectTaskId(), binding.projectTaskVersion(),
                binding.executionContractId(), binding.contractVersion(), binding.templateTaskDefinitionId(),
                binding.sourceDefinitionVersion(), binding.templateRevisionId(), binding.templateRevisionNo(),
                binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion(),
                binding.workBindingTypeCode(), binding.targetContextCode(), binding.targetObjectType(), binding.targetObjectKey());
    }

    private void requireCompleted(PreparationDO selected, Long projectId) {
        if (selected == null || !Objects.equals(selected.getProjectId(), projectId)
                || !"COMPLETED".equals(selected.getStatusCode()) || selected.getBusinessVersion() == null
                || selected.getContentVersion() == null || selected.getTemplateRevisionId() == null
                || selected.getDynamicFormInstanceId() == null || selected.getCompletedBy() == null
                || selected.getCompletedAt() == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
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
