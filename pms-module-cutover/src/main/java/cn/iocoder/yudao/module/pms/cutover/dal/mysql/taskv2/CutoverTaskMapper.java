package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskAssessmentLinkUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskChecklistSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskSourceInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskApprovalTransitionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverTaskMapper extends BaseMapperX<CutoverTaskDO> {

    default PageResult<CutoverTaskDO> selectPage(CutoverTaskPageQuery query) {
        if (query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty()) {
            return PageResult.empty();
        }
        return selectPage(query.pageParam(), new LambdaQueryWrapperX<CutoverTaskDO>()
                .eq(CutoverTaskDO::getTenantId, query.tenantId())
                .in(CutoverTaskDO::getProjectId, query.visibleProjectIds())
                .eqIfPresent(CutoverTaskDO::getProjectId, query.projectId())
                .eqIfPresent(CutoverTaskDO::getTaskStatus, query.taskStatus())
                .eqIfPresent(CutoverTaskDO::getCurrentStage, query.currentStage())
                .orderByDesc(CutoverTaskDO::getScheduledTime)
                .orderByDesc(CutoverTaskDO::getId));
    }

    CutoverTaskDO selectForUpdate(@Param("query") CutoverTaskRowQuery query);
    int linkAssessmentIfMatch(@Param("query") CutoverTaskAssessmentLinkUpdate query);
    int transitionIfMatch(@Param("query") CutoverTaskTransitionUpdate query);
    int submitChecklistIfMatch(@Param("query") CutoverTaskChecklistSubmitUpdate query);
    int submitPlanIfMatch(@Param("query") CutoverTaskPlanSubmitUpdate query);
    Integer selectMaxStageHistorySequence(@Param("query") CutoverTaskRowQuery query);
    int returnToPlanForSourceInvalidation(@Param("query") CutoverTaskSourceInvalidationUpdate query);
    int transitionFromApprovalIfMatch(@Param("query") CutoverTaskApprovalTransitionUpdate query);
}
