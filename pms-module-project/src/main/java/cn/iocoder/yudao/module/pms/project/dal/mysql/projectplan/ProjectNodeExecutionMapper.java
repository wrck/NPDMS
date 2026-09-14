package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectStageExecutionLookupQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectNodeExecutionMapper extends BaseMapperX<ProjectNodeExecutionDO> {
    ProjectStageExecutionRecord selectCurrentStageContext(@Param("query") ProjectStageExecutionLookupQuery query);
    ProjectStageExecutionRecord selectCurrentStageContextForUpdate(@Param("query") ProjectStageExecutionLookupQuery query);
    List<ProjectNodeExecutionDO> selectCurrentForUpdate(@Param("query") ProjectPlanScopeQuery query);
    List<ProjectNodeExecutionDO> selectCurrent(@Param("query") ProjectPlanScopeQuery query);
    List<ProjectNodeExecutionDO> selectHistory(@Param("query") ProjectPlanScopeQuery query);
    int activateIfPending(@Param("query") Activation query);
    int beginStageHandlingIfCurrent(@Param("query") StageHandlingStart query);
    int submitIfCurrent(@Param("query") Submission query);
    int finishIfActive(@Param("query") Finish query);
    int recordTaskTransition(@Param("query") TaskTransition query);
    int rebaseUnfinishedIfCurrent(@Param("query") PlanRebase query);
    int retireUnstartedIfCurrent(@Param("query") PlanRetirement query);
    int selectNextRoundNo(@Param("query") NodeRoundSequence query);
    record NodeRoundSequence(Long tenantId, Long projectId, String nodeKey) { }
    record PlanRebase(Long tenantId, Long projectId, Long executionId, Integer expectedVersion,
                      Long oldPlanVersionId, Long newPlanVersionId, Long expectedContractId,
                      Long newContractId, String updater) { }
    record PlanRetirement(Long tenantId, Long projectId, Long executionId, Integer expectedVersion,
                          Long planVersionId, String updater) { }
    record Activation(Long tenantId, Long projectId, Long planVersionId, Long nodeInstanceId, String nodeKind, LocalDateTime occurredAt) { }
    record StageHandlingStart(Long tenantId, Long projectId, Long executionId, Long planVersionId, Long contractId,
                              Integer expectedVersion, LocalDateTime occurredAt, Long actorId) { }
    record Submission(Long tenantId, Long projectId, Long executionId, Integer expectedVersion,
                      Long actorId, LocalDateTime occurredAt, String note) { }
    record Finish(Long tenantId, Long projectId, Long executionId, Integer expectedVersion,
                  LocalDateTime occurredAt, String resultSnapshot) { }
    record TaskTransition(Long tenantId, Long projectId, Long taskId, Long contractId, String action,
                          LocalDateTime occurredAt, Long actorId, String evidence) { }
}
