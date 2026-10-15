package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskAssessmentLinkUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskChecklistSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskSourceInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskApprovalTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskArchiveUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverGovernanceGuardQuery;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverTaskMapper extends BaseMapperX<CutoverTaskDO> {

    default List<CutoverTaskDO> selectListForGovernanceGuard(CutoverGovernanceGuardQuery query) {
        if (query.projectIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<CutoverTaskDO>()
                .eq(CutoverTaskDO::getTenantId, query.tenantId())
                .in(CutoverTaskDO::getProjectId, query.projectIds())
                .orderByAsc(CutoverTaskDO::getProjectId, CutoverTaskDO::getId));
    }

    CutoverTaskDO selectForUpdate(@Param("query") CutoverTaskRowQuery query);
    int linkAssessmentIfMatch(@Param("query") CutoverTaskAssessmentLinkUpdate query);
    int transitionIfMatch(@Param("query") CutoverTaskTransitionUpdate query);
    int submitChecklistIfMatch(@Param("query") CutoverTaskChecklistSubmitUpdate query);
    int updateMatchingInputIfMatch(@Param("query") cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskMatchingInputUpdate query);
    int submitPlanIfMatch(@Param("query") CutoverTaskPlanSubmitUpdate query);
    Integer selectMaxStageHistorySequence(@Param("query") CutoverTaskRowQuery query);
    int returnToPlanForSourceInvalidation(@Param("query") CutoverTaskSourceInvalidationUpdate query);
    int transitionFromApprovalIfMatch(@Param("query") CutoverTaskApprovalTransitionUpdate query);
    int archiveFromP6IfMatch(@Param("query") CutoverTaskArchiveUpdate query);
    int reviseIfMatch(@Param("query") cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRevisionUpdate query);
}
