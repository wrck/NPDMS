package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectStageExecutionRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectStageExecutionLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskExecutionRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskExecutionLookupQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectNodeExecutionApiImpl implements ProjectNodeExecutionApi {
    private final ProjectMasterMapper projects;
    private final ProjectTaskExecutionMapper executions;
    private final ProjectNodeExecutionMapper nodes;

    @Override
    public ProjectTaskExecutionContext inspect(ProjectTaskExecutionQuery query) {
        return context(executions.selectCurrent(lookup(query)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectTaskExecutionContext lockAndRevalidate(ProjectTaskExecutionContext expected) {
        if (expected == null || !expected.writable()) throw exception(PROJECT_TASK_QUERY_INVALID);
        var query = lookup(expected.query());
        // Same project-first lock order as lifecycle/plan commands; held by the Owner transaction until commit.
        lockProject(query.tenantId(), query.projectId());
        var current = context(executions.selectCurrentForUpdate(query));
        if (!current.writable() || !current.equals(expected)) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return current;
    }

    @Override
    public ProjectStageExecutionContext inspectStage(ProjectStageExecutionQuery query) {
        return stageContext(nodes.selectCurrentStageContext(stageLookup(query)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectStageExecutionContext lockAndRevalidateStage(ProjectStageExecutionContext expected) {
        if (expected == null || !expected.writable()) throw exception(PROJECT_TASK_QUERY_INVALID);
        var query = stageLookup(expected.query());
        lockProject(query.tenantId(), query.projectId());
        var current = stageContext(nodes.selectCurrentStageContextForUpdate(query));
        if (!current.writable() || !current.equals(expected)) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return current;
    }

    private void lockProject(Long tenantId, Long projectId) {
        var project = projects.selectByIdForUpdate(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId))
            throw exception(PROJECT_TASK_QUERY_INVALID);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectStageExecutionContext beginStageHandling(ProjectStageExecutionContext expected, Long actorId) {
        if (invalidId(actorId)) throw exception(PROJECT_TASK_QUERY_INVALID);
        var current = lockAndRevalidateStage(expected);
        var execution = nodes.selectById(current.executionId());
        if (execution == null) throw exception(PROJECT_TASK_QUERY_INVALID);
        if (execution.getStartedAt() != null) return current;
        if (nodes.beginStageHandlingIfCurrent(new ProjectNodeExecutionMapper.StageHandlingStart(
                TenantContextHolder.getRequiredTenantId(), current.projectId(), current.executionId(), current.planVersionId(),
                current.executionContractId(), current.executionVersion(), java.time.LocalDateTime.now(), actorId)) != 1)
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return inspectStage(current.query());
    }

    private ProjectStageExecutionLookupQuery stageLookup(ProjectStageExecutionQuery query) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0 || query == null || invalidId(query.projectId())
                || invalidId(query.stageId()) || invalidId(query.executionContractId()))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        return new ProjectStageExecutionLookupQuery(tenantId, query.projectId(), query.stageId(), query.executionContractId());
    }

    private ProjectStageExecutionContext stageContext(ProjectStageExecutionRecord row) {
        if (row == null) throw exception(PROJECT_TASK_QUERY_INVALID);
        boolean writable = "ACTIVE".equals(row.projectStatus()) && "ACTIVE".equals(row.stageStatus())
                && "ACTIVE".equals(row.executionStatus());
        return new ProjectStageExecutionContext(row.projectId(), row.projectVersion(), row.stageId(), row.stageVersion(),
                row.executionContractId(), row.contractVersion(), row.planVersionId(), row.executionId(),
                row.executionVersion(), row.roundNo(), writable);
    }

    private ProjectTaskExecutionLookupQuery lookup(ProjectTaskExecutionQuery query) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0 || query == null || invalidId(query.projectId())
                || invalidId(query.taskId()) || invalidId(query.executionContractId()))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        return new ProjectTaskExecutionLookupQuery(tenantId, query.projectId(), query.taskId(), query.executionContractId());
    }

    private ProjectTaskExecutionContext context(ProjectTaskExecutionRecord row) {
        if (row == null) throw exception(PROJECT_TASK_QUERY_INVALID);
        boolean writable = "ACTIVE".equals(row.projectStatus()) && "ACTIVE".equals(row.stageStatus())
                && "ACTIVE".equals(row.stageExecutionStatus()) && "ACTIVE".equals(row.executionStatus())
                && ("IN_PROGRESS".equals(row.taskStatus()) || "PENDING_ACCEPT".equals(row.taskStatus()));
        return new ProjectTaskExecutionContext(row.projectId(), row.projectVersion(), row.taskId(), row.taskVersion(),
                row.executionContractId(), row.contractVersion(), row.planVersionId(), row.executionId(),
                row.executionVersion(), row.roundNo(), row.stageExecutionId(), row.stageExecutionVersion(), writable, row.startedAt());
    }

    private boolean invalidId(Long value) { return value == null || value <= 0; }
}
