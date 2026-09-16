package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.node.ObjectNode;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskProjectQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectSatisfactionTaskFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectSatisfactionTaskFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectSatisfactionTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.PreparationWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.domain.template.RequirementAnalysisWorkBindingSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/** 基于既有ProjectTask ExecutionContract的受控目标冻结事实实现。 */
@Service
@RequiredArgsConstructor
public class ProjectWorkBindingFactApiImpl implements ProjectWorkBindingFactApi {


    private final ProjectMasterMapper projectMapper;
    private final ProjectWorkBindingFactMapper factMapper;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper graph;

    @Override
    public ProjectWorkBindingFact inspectStage(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || invalidId(query.projectId()) || invalidId(query.projectStageId()) || !supportedTarget(query.target()))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        var project = projectMapper.selectById(query.projectId());
        if (project == null || !Objects.equals(tenantId, project.getTenantId())) throw exception(PROJECT_TASK_QUERY_INVALID);
        requireFrozenProjectTemplateIdentity(project);
        var scope = new cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery(tenantId, query.projectId());
        var stages = graph.selectStages(scope).stream().filter(row -> query.projectStageId().equals(row.getId())).toList();
        var contracts = graph.selectContracts(scope).stream().filter(row -> query.projectStageId().equals(row.getStageId())).toList();
        if (stages.size() != 1 || contracts.size() != 1) throw exception(PROJECT_TASK_QUERY_INVALID);
        var stage = stages.getFirst(); var contract = contracts.getFirst();
        if (!Objects.equals(stage.getTenantId(), tenantId) || !Objects.equals(stage.getProjectId(), project.getId())
                || !Objects.equals(contract.getTenantId(), tenantId) || !Objects.equals(contract.getProjectId(), project.getId())
                || contract.getEffectiveTo() != null || !Objects.equals(stage.getGraphVersion(), contract.getGraphVersion())
                || blank(contract.getSourceNodeKey()) || invalidVersion(stage.getVersion())
                || contract.getBindingVersion() == null || contract.getBindingVersion() <= 0)
            throw exception(PROJECT_TASK_QUERY_INVALID);
        var snapshot = JsonUtils.parseObject(contract.getDefinitionSnapshot(), cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.class);
        var frozen = snapshot.getStages().stream().filter(node -> contract.getSourceNodeKey().equals(node.getNodeKey())
                && stage.getCode().equals(node.getCode())).toList();
        if (frozen.size() != 1 || frozen.getFirst().getBinding() == null) throw exception(PROJECT_TASK_QUERY_INVALID);
        var binding = frozen.getFirst().getBinding();
        if (!Objects.equals(JsonUtils.parseTree(JsonUtils.toJsonString(binding)), JsonUtils.parseTree(contract.getBindingSnapshot()))
                || !Objects.equals(binding.getType(), contract.getBindingType())
                || !exactTarget(binding.getType(), binding.getTargetContextCode(), binding.getTargetObjectType(), binding.getTargetObjectKey(), query.target()))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        String parameters = JsonUtils.toJsonString(binding.getParameters());
        var owner = parseFrozen(binding.getTargetObjectKey(), parameters);
        return new ProjectWorkBindingFact(project.getId(), project.getVersion(), null, null, contract.getId(), contract.getBindingVersion(),
                project.getLifecycleTemplateId(), null, binding.getType(), binding.getTargetContextCode(), binding.getTargetObjectType(),
                binding.getTargetObjectKey(), owner.preparationTemplateCode(), owner.preparationTemplateRevision(),
                owner.fixedFormCatalogVersion(), owner.itemConfigurationSnapshot(), project.getLifecycleTemplateRevisionId(),
                project.getLifecycleTemplateRevisionNo(), parameters, owner.dynamicFormTemplateId(), owner.dynamicFormTemplateRevisionId(),
                owner.dynamicFormRevisionNo(), owner.dynamicFormRevisionFactVersion(), stage.getId(), stage.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectWorkBindingFact lockAndRevalidateStage(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactRevalidationQuery query) {
        if (query == null || invalidId(query.projectId()) || invalidId(query.projectStageId()) || !supportedTarget(query.target()))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        Long tenantId = trustedTenantId();
        var project = projectMapper.selectByIdForUpdate(query.projectId());
        if (project == null || !Objects.equals(tenantId, project.getTenantId())) throw exception(PROJECT_TASK_QUERY_INVALID);
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        var stages = graph.selectStagesForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery(
                tenantId, query.projectId())).stream().filter(row -> query.projectStageId().equals(row.getId())).toList();
        if (stages.size() != 1 || !Objects.equals(stages.getFirst().getTenantId(), tenantId)
                || !Objects.equals(stages.getFirst().getProjectId(), query.projectId())) throw exception(PROJECT_TASK_QUERY_INVALID);
        if (!Objects.equals(stages.getFirst().getVersion(), query.expectedProjectStageVersion())) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        var lookup = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactQuery(query.projectId(), query.projectStageId(), query.target());
        // Reading a completed stage's frozen binding is not a request to reopen it for business writes.
        var locked = inspectStage(lookup);
        requireStageVersions(query, locked);
        return locked;
    }

    private void requireStageVersions(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactRevalidationQuery expected,
                                      ProjectWorkBindingFact actual) {
        if (!Objects.equals(expected.executionContractId(), actual.executionContractId())
                || !Objects.equals(expected.expectedProjectStageVersion(), actual.projectStageVersion())
                || !Objects.equals(expected.expectedContractVersion(), actual.contractVersion())
                || !Objects.equals(expected.expectedProjectVersion(), actual.projectVersion())) throw exception(PROJECT_TASK_VERSION_CONFLICT);
    }

    @Override
    public ProjectWorkBindingFact inspect(ProjectWorkBindingFactQuery query) {
        if (query == null) throw exception(PROJECT_TASK_QUERY_INVALID);
        return inspectCurrent(query.projectId(),null,query.target());
    }

    @Override
    public ProjectWorkBindingFact inspectTask(cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTaskFactQuery query) {
        if (query == null || invalidId(query.projectTaskId())) throw exception(PROJECT_TASK_QUERY_INVALID);
        return inspectCurrent(query.projectId(),query.projectTaskId(),query.target());
    }

    private ProjectWorkBindingFact inspectCurrent(Long projectId, Long taskId, ProjectWorkBindingTarget target) {
        Long tenantId = trustedTenantId();
        if (invalidId(projectId) || !supportedTarget(target)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        List<ProjectWorkBindingFactRecord> records = factMapper.selectCurrentFacts(new ProjectWorkBindingFactLookupQuery(
                tenantId, projectId, target.workBindingTypeCode(), target.targetContextCode(),
                target.targetObjectType(), target.targetObjectKey(), taskId));
        if (records == null || records.size() != 1) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectWorkBindingFactRecord record = records.getFirst();
        requireRecord(record, tenantId, projectId, target);
        if (taskId != null && !Objects.equals(taskId,record.projectTaskId())) throw exception(PROJECT_TASK_QUERY_INVALID);
        return toFact(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectWorkBindingFact lockAndRevalidate(ProjectWorkBindingFactRevalidationQuery query) {
        Long tenantId = trustedTenantId();
        validateRevalidation(query);
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(query.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        requireFrozenProjectTemplateIdentity(project);

        ProjectWorkBindingFactLockQuery lockQuery = new ProjectWorkBindingFactLockQuery(
                tenantId, query.projectId(), query.projectTaskId());
        ProjectTaskInstanceDO task = factMapper.selectProjectTaskForUpdate(lockQuery);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)
                || !Objects.equals(task.getProjectId(), query.projectId())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        if (!Objects.equals(task.getVersion(), query.expectedProjectTaskVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }

        ProjectTaskExecutionContractDO contract = factMapper.selectCurrentContractForUpdate(lockQuery);
        requireContract(contract, task, tenantId, query.executionContractId(), query.target());
        if (!Objects.equals(contract.getContractVersion(), query.expectedContractVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        return toFact(project, task, contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSatisfactionTaskFact lockCurrentSatisfactionTask(ProjectSatisfactionTaskIdentityQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || invalidId(query.projectId()) || invalidId(query.projectTaskId())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectSatisfactionTaskFactRecord record = uniqueSatisfactionTask(
                tenantId, query.projectId(), query.projectTaskId());
        requireSatisfactionTask(record, tenantId, query.projectId(), query.projectTaskId());
        return toSatisfactionTaskFact(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSatisfactionTaskFact lockCurrentSatisfactionTaskByProject(
            ProjectSatisfactionTaskProjectQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || invalidId(query.projectId())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        List<ProjectSatisfactionTaskFactRecord> records = factMapper.selectProjectSatisfactionTaskForUpdate(
                new ProjectSatisfactionTaskProjectLockQuery(tenantId, query.projectId()));
        if (records == null || records.size() != 1) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectSatisfactionTaskFactRecord record = records.getFirst();
        requireSatisfactionTask(record, tenantId, query.projectId(), record.projectTaskId());
        return toSatisfactionTaskFact(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSatisfactionTaskFact lockAndRevalidateSatisfactionTask(ProjectSatisfactionTaskFactQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || invalidId(query.projectId()) || invalidId(query.projectTaskId())
                || invalidVersion(query.expectedProjectTaskVersion())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectSatisfactionTaskFactRecord record = uniqueSatisfactionTask(
                tenantId, query.projectId(), query.projectTaskId());
        requireSatisfactionTask(record, tenantId, query.projectId(), query.projectTaskId());
        if (!Objects.equals(record.projectTaskVersion(), query.expectedProjectTaskVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        return toSatisfactionTaskFact(record);
    }

    private ProjectSatisfactionTaskFactRecord uniqueSatisfactionTask(Long tenantId, Long projectId,
                                                                     Long projectTaskId) {
        List<ProjectSatisfactionTaskFactRecord> records = factMapper.selectSatisfactionTaskForUpdate(
                new ProjectSatisfactionTaskFactLockQuery(tenantId, projectId, projectTaskId));
        if (records == null || records.size() != 1) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return records.getFirst();
    }

    private void requireSatisfactionTask(ProjectSatisfactionTaskFactRecord record, Long tenantId,
                                         Long projectId, Long projectTaskId) {
        if (!Objects.equals(record.tenantId(), tenantId) || !Objects.equals(record.projectId(), projectId)
                || !Objects.equals(record.projectTaskId(), projectTaskId) || blank(record.taskCode())
                || invalidVersion(record.projectTaskVersion()) || blank(record.satisfactionTiming())
                || invalidId(record.templateId()) || invalidId(record.templateRevisionId())
                || record.templateVersion() == null || record.templateVersion() <= 0
                || blank(record.ruleVersion()) || record.threshold() == null
                || record.threshold().signum() < 0 || invalidId(record.currentAssigneeUserId())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private ProjectSatisfactionTaskFact toSatisfactionTaskFact(ProjectSatisfactionTaskFactRecord record) {
        return new ProjectSatisfactionTaskFact(record.projectId(), record.projectTaskId(), record.taskCode(),
                record.projectTaskVersion(), record.satisfactionTiming(), record.templateId(),
                record.templateRevisionId(), record.templateVersion(), record.ruleVersion(), record.threshold(),
                record.currentAssigneeUserId());
    }

    private void validateRevalidation(ProjectWorkBindingFactRevalidationQuery query) {
        if (query == null || invalidId(query.projectId()) || invalidId(query.projectTaskId())
                || invalidId(query.executionContractId()) || invalidVersion(query.expectedProjectTaskVersion())
                || invalidVersion(query.expectedContractVersion()) || invalidVersion(query.expectedProjectVersion())
                || !supportedTarget(query.target())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private boolean invalidId(Long value) {
        return value == null || value <= 0;
    }

    private boolean invalidVersion(Integer value) {
        return value == null || value < 0;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void requireRecord(ProjectWorkBindingFactRecord record, Long tenantId, Long projectId,
                               ProjectWorkBindingTarget target) {
        if (record == null || !Objects.equals(record.tenantId(), tenantId)
                || !Objects.equals(record.projectId(), projectId) || record.projectVersion() == null
                || invalidId(record.projectTaskId()) || record.projectTaskVersion() == null
                || invalidId(record.executionContractId()) || invalidId(record.projectTemplateId())
                || record.sourceDefinitionVersion() == null || record.sourceDefinitionVersion() <= 0
                || record.contractVersion() == null || record.contractVersion() <= 0
                || invalidId(record.templateRevisionId())
                || record.templateRevisionNo() == null || record.templateRevisionNo() < 0
                || !exactTarget(record.workBindingTypeCode(), record.targetContextCode(),
                record.targetObjectType(), record.targetObjectKey(), target)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private void requireContract(ProjectTaskExecutionContractDO contract, ProjectTaskInstanceDO task,
                                 Long tenantId, Long executionContractId, ProjectWorkBindingTarget target) {
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !Objects.equals(contract.getProjectTaskId(), task.getId())
                || !Objects.equals(contract.getId(), executionContractId)
                || contract.getSourceDefinitionVersion() == null || contract.getSourceDefinitionVersion() <= 0
                || contract.getContractVersion() == null || contract.getContractVersion() <= 0
                || !exactTarget(contract.getWorkBindingTypeCode(), contract.getTargetContextCode(),
                contract.getTargetObjectType(), contract.getTargetObjectKey(), target)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private void requireFrozenProjectTemplateIdentity(ProjectMasterDO project) {
        if (invalidId(project.getLifecycleTemplateId()) || invalidId(project.getLifecycleTemplateRevisionId())
                || project.getLifecycleTemplateRevisionNo() == null
                || project.getLifecycleTemplateRevisionNo() < 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private boolean exactTarget(String bindingType, String context, String objectType, String objectKey,
                                ProjectWorkBindingTarget target) {
        return Objects.equals(target.workBindingTypeCode(), bindingType)
                && Objects.equals(target.targetContextCode(), context)
                && Objects.equals(target.targetObjectType(), objectType)
                && Objects.equals(target.targetObjectKey(), objectKey);
    }

    private boolean supportedTarget(ProjectWorkBindingTarget target) {
        return target != null && target.isSupported();
    }

    private ProjectWorkBindingFact toFact(ProjectWorkBindingFactRecord record) {
        BindingProjection binding = parseFrozen(record.targetObjectKey(), record.bindingParameterSnapshot());
        return new ProjectWorkBindingFact(record.projectId(), record.projectVersion(), record.projectTaskId(),
                record.projectTaskVersion(), record.executionContractId(), record.contractVersion(),
                record.projectTemplateId(), record.sourceDefinitionVersion(), record.workBindingTypeCode(),
                record.targetContextCode(), record.targetObjectType(), record.targetObjectKey(),
                binding.preparationTemplateCode(), binding.preparationTemplateRevision(),
                binding.fixedFormCatalogVersion(), binding.itemConfigurationSnapshot(),
                record.templateRevisionId(), record.templateRevisionNo(), record.bindingParameterSnapshot(),
                binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion());
    }

    private ProjectWorkBindingFact toFact(ProjectMasterDO project, ProjectTaskInstanceDO task,
                                          ProjectTaskExecutionContractDO contract) {
        BindingProjection binding = parseFrozen(contract.getTargetObjectKey(), contract.getBindingParameterSnapshot());
        return new ProjectWorkBindingFact(project.getId(), project.getVersion(), task.getId(), task.getVersion(),
                contract.getId(), contract.getContractVersion(), project.getLifecycleTemplateId(),
                contract.getSourceDefinitionVersion(), contract.getWorkBindingTypeCode(),
                contract.getTargetContextCode(), contract.getTargetObjectType(), contract.getTargetObjectKey(),
                binding.preparationTemplateCode(), binding.preparationTemplateRevision(),
                binding.fixedFormCatalogVersion(), binding.itemConfigurationSnapshot(),
                project.getLifecycleTemplateRevisionId(), project.getLifecycleTemplateRevisionNo(),
                contract.getBindingParameterSnapshot(),
                binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion());
    }

    private BindingProjection parseFrozen(String targetObjectKey, String snapshot) {
        try {
            // The unified binding carries view routing beside Owner form parameters.
            // Owner schemas remain closed: only these known routing fields are outside their responsibility.
            var parameters = JsonUtils.parseTree(snapshot);
            if (!(parameters instanceof ObjectNode ownerParameters))
                throw new IllegalArgumentException("binding parameters must be an object");
            ownerParameters.remove("businessViewRevisionId");
            ownerParameters.remove("instanceResolutionStrategy");
            ownerParameters.remove("contextMapping");
            String ownerSnapshot = ownerParameters.toString();
            if (PreparationWorkBindingSchema.TARGET_OBJECT_KEY.equals(targetObjectKey)) {
                PreparationWorkBindingSchema.ParsedBinding binding = PreparationWorkBindingSchema.parseFrozen(ownerSnapshot);
                return new BindingProjection(binding.preparationTemplateCode(),
                        binding.preparationTemplateRevision(), binding.fixedFormCatalogVersion(),
                        binding.itemConfigurationSnapshot(), null, null, null, null);
            }
            if (RequirementAnalysisWorkBindingSchema.TARGET_OBJECT_KEY.equals(targetObjectKey)) {
                RequirementAnalysisWorkBindingSchema.ParsedBinding binding =
                        RequirementAnalysisWorkBindingSchema.parseFrozen(ownerSnapshot);
                return new BindingProjection(null, null, null, null,
                        binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                        binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion());
            }
            throw new IllegalArgumentException("unsupported WorkBinding target");
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private record BindingProjection(
            String preparationTemplateCode,
            Integer preparationTemplateRevision,
            Integer fixedFormCatalogVersion,
            String itemConfigurationSnapshot,
            Long dynamicFormTemplateId,
            Long dynamicFormTemplateRevisionId,
            Integer dynamicFormRevisionNo,
            Integer dynamicFormRevisionFactVersion) {
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return tenantId;
    }
}
