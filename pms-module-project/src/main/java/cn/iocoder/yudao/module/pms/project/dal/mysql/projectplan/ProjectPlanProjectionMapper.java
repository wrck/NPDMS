package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

/** Project-owned current projections only; frozen execution results and Owner business tables are not updated here. */
@Mapper
public interface ProjectPlanProjectionMapper {
    int gateCodeForRename(@Param("query") NodeProjectionChange query);
    int updateGateDefinition(@Param("query") GateDefinitionUpdate query);
    int retirePendingGate(@Param("query") NodeProjectionChange query);
    int retireGateReference(@Param("query") GateReferenceRetirement query);
    record GateDefinitionUpdate(Long tenantId, Long projectId, Long id, Integer expectedVersion,
                                ProjectGateInstanceDO definition, String updater) { }
    record GateReferenceRetirement(Long tenantId, Long projectId, Long gateId, Long referenceId,
                                   Integer expectedVersion, String updater) { }
    java.util.List<ProjectMilestoneInstanceDO> selectMilestonesForUpdate(@Param("query") ProjectPlanScopeQuery query);
    int milestoneCodeForRename(@Param("query") NodeProjectionChange query);
    int updateMilestoneDefinition(@Param("query") MilestoneDefinitionUpdate query);
    int retirePendingMilestone(@Param("query") NodeProjectionChange query);
    record MilestoneDefinitionUpdate(Long tenantId, Long projectId, Long id, Integer expectedVersion,
                                     ProjectMilestoneInstanceDO definition, String updater) { }
    int updateStageDefinition(@Param("query") StageDefinitionUpdate query);
    int updateTaskDefinition(@Param("query") TaskDefinitionUpdate query);
    int stageCodeForRename(@Param("query") NodeProjectionChange query);
    int taskCodeForRename(@Param("query") NodeProjectionChange query);
    int retireUnstartedStage(@Param("query") NodeProjectionChange query);
    int retireUnstartedTask(@Param("query") NodeProjectionChange query);
    int closeTaskContract(@Param("query") ContractClosure query);
    int closeStageContract(@Param("query") ContractClosure query);
    int deleteCurrentTaskPaths(@Param("query") ProjectPlanScopeQuery query);
    int advanceTaskTreeVersion(@Param("query") TaskTreeVersionUpdate query);
    record StageDefinitionUpdate(Long tenantId, Long projectId, Long id, Integer expectedVersion,
                                 ProjectStageInstanceDO definition, String updater) { }
    record TaskDefinitionUpdate(Long tenantId, Long projectId, Long id, Integer expectedVersion,
                                ProjectTaskInstanceDO definition, String updater) { }
    record NodeProjectionChange(Long tenantId, Long projectId, Long id, Integer expectedVersion, String updater) { }
    record ContractClosure(Long tenantId, Long projectId, Long nodeInstanceId, Long contractId,
                           Integer expectedVersion, LocalDateTime closedAt, String updater) { }
    record TaskTreeVersionUpdate(Long tenantId, Long projectId, Long expectedVersion, String updater) { }
}
