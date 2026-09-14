package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectPlanVersionMapper extends BaseMapperX<ProjectPlanVersionDO> {
    ProjectPlanVersionDO selectEffective(@Param("query") ProjectPlanScopeQuery query);
    java.util.List<ProjectPlanVersionDO> selectHistory(@Param("query") ProjectPlanScopeQuery query);
    ProjectPlanVersionDO selectDraft(@Param("query") ProjectPlanScopeQuery query);
    int selectNextRevisionNo(@Param("query") ProjectPlanScopeQuery query);
    int saveDraftIfCurrent(@Param("query") DraftUpdate query);
    int attachInitialPlan(@Param("query") InitialPlanBinding query);
    int closeProjectIfActive(@Param("query") RuleClosure query);
    int recordClosureIfOpen(@Param("query") RuleClosure query);
    int promoteDraftIfCurrent(@Param("query") Activation query);
    int supersedeEffectiveIfCurrent(@Param("query") Activation query);
    int attachActivatedPlan(@Param("query") Activation query);
    record Activation(Long tenantId, Long projectId, Long oldPlanVersionId, Long draftId,
                      Integer expectedDraftVersion, Integer expectedProjectVersion,
                      String executionSnapshot, java.time.LocalDateTime effectiveAt, String updater) { }
    record InitialPlanBinding(Long tenantId, Long projectId, Long planVersionId) { }
    record DraftUpdate(Long tenantId, Long projectId, Long draftId, Integer expectedVersion,
                       Long basePlanVersionId, String designer, String updater) { }
    record RuleClosure(Long tenantId, Long projectId, Long planVersionId, Integer expectedProjectVersion,
                       java.time.LocalDateTime closedAt, String evidence, String updater) { }
}
