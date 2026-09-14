package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;

/** Node execution availability only; business Owners must also enforce their permissions, scope and state. */
public interface ProjectNodeExecutionApi {
    ProjectTaskExecutionContext inspect(ProjectTaskExecutionQuery query);

    /** Requires the Owner's write transaction; rejects stale, inactive or closed execution contexts. */
    ProjectTaskExecutionContext lockAndRevalidate(ProjectTaskExecutionContext expected);

    ProjectStageExecutionContext inspectStage(ProjectStageExecutionQuery query);

    /** Same Owner transaction and stale-context rejection as task handling, without requiring a child task. */
    ProjectStageExecutionContext lockAndRevalidateStage(ProjectStageExecutionContext expected);

    /** Owner writes mark actual stage work in the same transaction, protecting its binding from later plan edits. */
    ProjectStageExecutionContext beginStageHandling(ProjectStageExecutionContext expected, Long actorId);
}
