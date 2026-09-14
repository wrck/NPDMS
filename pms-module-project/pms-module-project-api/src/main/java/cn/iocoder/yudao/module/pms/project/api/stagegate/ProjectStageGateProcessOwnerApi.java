package cn.iocoder.yudao.module.pms.project.api.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionSelectionQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartCommand;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcess;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcessQuery;

import java.util.List;

/** PMS 流程定义校验及 Gate 启动、实例事实契约；定义校验也供阶段/任务的直接审批绑定复用。 */
public interface ProjectStageGateProcessOwnerApi {

    ProjectStageGateProcessDefinitionFact inspectDefinitionKey(ProjectStageGateProcessDefinitionQuery query);

    List<ProjectStageGateProcessDefinitionFact> listSelectableDefinitions(
            ProjectStageGateProcessDefinitionSelectionQuery query);

    ProjectStageGateProcessStartFact startProcess(ProjectStageGateProcessStartCommand command);

    /** Empty means no running work; an unavailable or untrusted query must throw, never return an empty fallback. */
    List<ProjectStageGateRunningProcess> inspectRunning(ProjectStageGateRunningProcessQuery query);
}
