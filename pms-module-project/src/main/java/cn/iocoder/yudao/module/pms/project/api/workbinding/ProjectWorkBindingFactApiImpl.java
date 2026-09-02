package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTemplateRevisionFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectSatisfactionTaskFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTemplateRevisionFactQuery;
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

    private static final String SATISFACTION_TASK_CODE = "T-SAT-SURVEY";

    private final ProjectMasterMapper projectMapper;
    private final ProjectWorkBindingFactMapper factMapper;

    @Override
    public ProjectWorkBindingFact inspect(ProjectWorkBindingFactQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || query.projectId() == null || query.projectId() <= 0
                || !supportedTarget(query.target())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectWorkBindingTarget target = query.target();
        List<ProjectWorkBindingFactRecord> records = factMapper.selectCurrentFacts(new ProjectWorkBindingFactLookupQuery(
                tenantId, query.projectId(), target.workBindingTypeCode(), target.targetContextCode(),
                target.targetObjectType(), target.targetObjectKey()));
        if (records == null || records.size() != 1) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectWorkBindingFactRecord record = records.getFirst();
        requireRecord(record, tenantId, query.projectId(), target);
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
        ProjectTemplateRevisionFactRecord revision = factMapper.selectTemplateRevisionFact(
                new ProjectTemplateRevisionFactQuery(tenantId, contract.getTemplateTaskDefinitionId()));
        requireTemplateRevision(revision, contract.getTemplateTaskDefinitionId());
        return toFact(project, task, contract, revision);
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
        if (!SATISFACTION_TASK_CODE.equals(record.taskCode())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
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
        if (!SATISFACTION_TASK_CODE.equals(record.taskCode())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
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
                || invalidId(record.executionContractId()) || invalidId(record.templateTaskDefinitionId())
                || !Objects.equals(record.sourceDefinitionId(), record.templateTaskDefinitionId())
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
                || invalidId(contract.getTemplateTaskDefinitionId())
                || !Objects.equals(contract.getTemplateTaskDefinitionId(), task.getSourceDefinitionId())
                || contract.getSourceDefinitionVersion() == null || contract.getSourceDefinitionVersion() <= 0
                || contract.getContractVersion() == null || contract.getContractVersion() <= 0
                || !exactTarget(contract.getWorkBindingTypeCode(), contract.getTargetContextCode(),
                contract.getTargetObjectType(), contract.getTargetObjectKey(), target)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private void requireTemplateRevision(ProjectTemplateRevisionFactRecord revision, Long definitionId) {
        if (revision == null || !Objects.equals(revision.templateTaskDefinitionId(), definitionId)
                || invalidId(revision.templateRevisionId())
                || revision.templateRevisionNo() == null || revision.templateRevisionNo() < 0) {
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
                record.templateTaskDefinitionId(), record.sourceDefinitionVersion(), record.workBindingTypeCode(),
                record.targetContextCode(), record.targetObjectType(), record.targetObjectKey(),
                binding.preparationTemplateCode(), binding.preparationTemplateRevision(),
                binding.fixedFormCatalogVersion(), binding.itemConfigurationSnapshot(),
                record.templateRevisionId(), record.templateRevisionNo(), record.bindingParameterSnapshot(),
                binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion());
    }

    private ProjectWorkBindingFact toFact(ProjectMasterDO project, ProjectTaskInstanceDO task,
                                          ProjectTaskExecutionContractDO contract,
                                          ProjectTemplateRevisionFactRecord revision) {
        BindingProjection binding = parseFrozen(contract.getTargetObjectKey(), contract.getBindingParameterSnapshot());
        return new ProjectWorkBindingFact(project.getId(), project.getVersion(), task.getId(), task.getVersion(),
                contract.getId(), contract.getContractVersion(), contract.getTemplateTaskDefinitionId(),
                contract.getSourceDefinitionVersion(), contract.getWorkBindingTypeCode(),
                contract.getTargetContextCode(), contract.getTargetObjectType(), contract.getTargetObjectKey(),
                binding.preparationTemplateCode(), binding.preparationTemplateRevision(),
                binding.fixedFormCatalogVersion(), binding.itemConfigurationSnapshot(),
                revision.templateRevisionId(), revision.templateRevisionNo(), contract.getBindingParameterSnapshot(),
                binding.dynamicFormTemplateId(), binding.dynamicFormTemplateRevisionId(),
                binding.dynamicFormRevisionNo(), binding.dynamicFormRevisionFactVersion());
    }

    private BindingProjection parseFrozen(String targetObjectKey, String snapshot) {
        try {
            if (PreparationWorkBindingSchema.TARGET_OBJECT_KEY.equals(targetObjectKey)) {
                PreparationWorkBindingSchema.ParsedBinding binding = PreparationWorkBindingSchema.parseFrozen(snapshot);
                return new BindingProjection(binding.preparationTemplateCode(),
                        binding.preparationTemplateRevision(), binding.fixedFormCatalogVersion(),
                        binding.itemConfigurationSnapshot(), null, null, null, null);
            }
            if (RequirementAnalysisWorkBindingSchema.TARGET_OBJECT_KEY.equals(targetObjectKey)) {
                RequirementAnalysisWorkBindingSchema.ParsedBinding binding =
                        RequirementAnalysisWorkBindingSchema.parseFrozen(snapshot);
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
