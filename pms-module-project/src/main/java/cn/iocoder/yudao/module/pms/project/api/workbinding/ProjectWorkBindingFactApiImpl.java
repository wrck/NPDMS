package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.PreparationWorkBindingSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/** 基于既有ProjectTask ExecutionContract的PRE-02冻结事实实现。 */
@Service
@RequiredArgsConstructor
public class ProjectWorkBindingFactApiImpl implements ProjectWorkBindingFactApi {

    private final ProjectMasterMapper projectMapper;
    private final ProjectWorkBindingFactMapper factMapper;

    @Override
    public ProjectWorkBindingFact inspect(ProjectWorkBindingFactQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || query.projectId() == null || query.projectId() <= 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        List<ProjectWorkBindingFactRecord> records = factMapper.selectCurrentFacts(new ProjectWorkBindingFactLookupQuery(
                tenantId, query.projectId(), PreparationWorkBindingSchema.BINDING_TYPE,
                PreparationWorkBindingSchema.TARGET_CONTEXT, PreparationWorkBindingSchema.TARGET_OBJECT_TYPE,
                PreparationWorkBindingSchema.TARGET_OBJECT_KEY));
        if (records == null || records.size() != 1) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        ProjectWorkBindingFactRecord record = records.getFirst();
        requireRecord(record, tenantId, query.projectId());
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
        requireContract(contract, task, tenantId, query.executionContractId());
        if (!Objects.equals(contract.getContractVersion(), query.expectedContractVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        return toFact(project, task, contract);
    }

    private void validateRevalidation(ProjectWorkBindingFactRevalidationQuery query) {
        if (query == null || invalidId(query.projectId()) || invalidId(query.projectTaskId())
                || invalidId(query.executionContractId()) || invalidVersion(query.expectedProjectTaskVersion())
                || invalidVersion(query.expectedContractVersion()) || invalidVersion(query.expectedProjectVersion())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private boolean invalidId(Long value) {
        return value == null || value <= 0;
    }

    private boolean invalidVersion(Integer value) {
        return value == null || value < 0;
    }

    private void requireRecord(ProjectWorkBindingFactRecord record, Long tenantId, Long projectId) {
        if (record == null || !Objects.equals(record.tenantId(), tenantId)
                || !Objects.equals(record.projectId(), projectId) || record.projectVersion() == null
                || invalidId(record.projectTaskId()) || record.projectTaskVersion() == null
                || invalidId(record.executionContractId()) || invalidId(record.templateTaskDefinitionId())
                || !Objects.equals(record.sourceDefinitionId(), record.templateTaskDefinitionId())
                || record.sourceDefinitionVersion() == null || record.sourceDefinitionVersion() <= 0
                || record.contractVersion() == null || record.contractVersion() <= 0
                || !exactTarget(record.workBindingTypeCode(), record.targetContextCode(),
                record.targetObjectType(), record.targetObjectKey())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private void requireContract(ProjectTaskExecutionContractDO contract, ProjectTaskInstanceDO task,
                                 Long tenantId, Long executionContractId) {
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !Objects.equals(contract.getProjectTaskId(), task.getId())
                || !Objects.equals(contract.getId(), executionContractId)
                || invalidId(contract.getTemplateTaskDefinitionId())
                || !Objects.equals(contract.getTemplateTaskDefinitionId(), task.getSourceDefinitionId())
                || contract.getSourceDefinitionVersion() == null || contract.getSourceDefinitionVersion() <= 0
                || contract.getContractVersion() == null || contract.getContractVersion() <= 0
                || !exactTarget(contract.getWorkBindingTypeCode(), contract.getTargetContextCode(),
                contract.getTargetObjectType(), contract.getTargetObjectKey())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private boolean exactTarget(String bindingType, String context, String objectType, String objectKey) {
        return PreparationWorkBindingSchema.BINDING_TYPE.equals(bindingType)
                && PreparationWorkBindingSchema.TARGET_CONTEXT.equals(context)
                && PreparationWorkBindingSchema.TARGET_OBJECT_TYPE.equals(objectType)
                && PreparationWorkBindingSchema.TARGET_OBJECT_KEY.equals(objectKey);
    }

    private ProjectWorkBindingFact toFact(ProjectWorkBindingFactRecord record) {
        PreparationWorkBindingSchema.ParsedBinding binding = parseFrozen(record.bindingParameterSnapshot());
        return new ProjectWorkBindingFact(record.projectId(), record.projectVersion(), record.projectTaskId(),
                record.projectTaskVersion(), record.executionContractId(), record.contractVersion(),
                record.templateTaskDefinitionId(), record.sourceDefinitionVersion(), record.workBindingTypeCode(),
                record.targetContextCode(), record.targetObjectType(), record.targetObjectKey(),
                binding.preparationTemplateCode(), binding.preparationTemplateRevision(),
                binding.fixedFormCatalogVersion(), binding.itemConfigurationSnapshot());
    }

    private ProjectWorkBindingFact toFact(ProjectMasterDO project, ProjectTaskInstanceDO task,
                                          ProjectTaskExecutionContractDO contract) {
        PreparationWorkBindingSchema.ParsedBinding binding = parseFrozen(contract.getBindingParameterSnapshot());
        return new ProjectWorkBindingFact(project.getId(), project.getVersion(), task.getId(), task.getVersion(),
                contract.getId(), contract.getContractVersion(), contract.getTemplateTaskDefinitionId(),
                contract.getSourceDefinitionVersion(), contract.getWorkBindingTypeCode(),
                contract.getTargetContextCode(), contract.getTargetObjectType(), contract.getTargetObjectKey(),
                binding.preparationTemplateCode(), binding.preparationTemplateRevision(),
                binding.fixedFormCatalogVersion(), binding.itemConfigurationSnapshot());
    }

    private PreparationWorkBindingSchema.ParsedBinding parseFrozen(String snapshot) {
        try {
            return PreparationWorkBindingSchema.parseFrozen(snapshot);
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return tenantId;
    }
}
